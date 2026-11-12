package dev.itemcontent.editor;

import com.google.gson.*;
import dev.itemcontent.definition.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import static org.junit.jupiter.api.Assertions.*;

class ContentTypesTest {
    @TempDir Path temp;
    static JsonObject json(String source) { return JsonParser.parseString(source).getAsJsonObject(); }
    static TypeDefinition extension() { return new TypeDefinition("sample:launcher", "Launcher", "Extension", "minecraft:combat", "handheld", false,
        List.of(PropertyDefinition.number("fire_rate", "Fire rate", 5, 0.1, 100, true, "Shots per second")), "Optional provider"); }
    static GameCatalog.Entry catalogItem(String id, String type, JsonObject properties, boolean variants) {
        return new GameCatalog.Entry(id, "Source", type, "sample.NativeItem", 1, 250, false, List.of("minecraft:combat"), properties, "", variants);
    }
    @Test void oversizedModStacksDoNotBreakCatalogBrowsingOrSilentlyChangeTheirSettings() throws Exception {
        var entry=new GameCatalog.Entry("sample:large_stack","Large stack","itemcontent:item","sample.Item",1024,0,false,List.of(),new JsonObject(),"",false);
        var catalog=new GameCatalog(1,List.of(),List.of(),List.of(entry)); Path file=temp.resolve("large-stack.json");catalog.save(file);
        assertEquals(1024,GameCatalog.load(file).items().get(0).maxCount());
        assertThrows(IllegalArgumentException.class,()->CatalogDialog.fromCatalog(new ItemProject(),entry,true));
    }
    @Test void cyclicModelsFailBeforeExportWhileExistingSelfReferenceIsPreserved() {
        assertThrows(IllegalArgumentException.class, () -> ModelReferences.validate(Map.of("a:a","a:b","a:b","a:a")));
        var project = new ItemProject(); var first = new ItemProject.Entry(); first.appearance=first.id; project.items.add(first);
        assertThrows(IllegalArgumentException.class, project::validate);
        first.existing=true; project.validate();
    }
    @Test void allBuiltinDefaultsValidateAndBadNumbersAreRejected() {
        for (var type : BuiltinTypes.all()) {
            var values = type.validate(new JsonObject(), false);
            assertEquals(type.properties().size(), values.size());
            type.properties().forEach(p -> p.validate(values.get(p.id())));
        }
        var sword = BuiltinTypes.index().get("itemcontent:sword");
        assertThrows(IllegalArgumentException.class, () -> sword.validate(json("{\"durability\":1.5}"), false));
        assertThrows(IllegalArgumentException.class, () -> sword.validate(json("{\"attack_speed\":0}"), false));
        assertThrows(IllegalArgumentException.class, () -> sword.validate(json("{\"attack_damage\":\"six\"}"), false));
        assertThrows(IllegalArgumentException.class, () -> sword.validate(json("{\"typo\":1}"), false));
        assertThrows(IllegalArgumentException.class, () -> sword.validate(json("{\"attack_damage\":20}"), true));
        assertEquals(400, sword.validate(json("{\"durability\":400}"), true).get("durability").getAsInt());
    }
    @Test void externalSchemaRoundTripsAndProducesAnEditableFormWithoutLoadingModClasses() throws Exception {
        var project = new ItemProject();
        var catalog = new GameCatalog(1, List.of(extension()), List.of(), List.of());
        Path file = temp.resolve("catalog.json"); catalog.save(file); project.importTypes(GameCatalog.load(file));
        var item = new ItemProject.Entry(); item.type = "sample:launcher"; item.maxCount = 1; item.properties = json("{\"fire_rate\":7.5}"); project.items.add(item);
        project.save(temp.resolve("list.icproject")); var loaded = ItemProject.load(temp.resolve("list.icproject"));
        SwingUtilities.invokeAndWait(() -> {
            var form = new BehaviorForm(() -> {}); form.show(loaded.items.get(0), loaded.type(loaded.items.get(0)));
            var field = (JTextField) find(form, "behavior.fire_rate"); assertNotNull(field); field.setText("12,5");
            assertEquals(12.5, form.read().get("fire_rate").getAsDouble());
        });
        loaded.export(temp.resolve("export"));
        var config = json(Files.readString(temp.resolve("export/config/item-content/items.json")));
        assertEquals("sample:launcher", config.getAsJsonArray("items").get(0).getAsJsonObject().get("type").getAsString());
        Files.writeString(file, "null"); assertThrows(java.io.IOException.class, () -> GameCatalog.load(file));
    }
    @Test void copyingAndOverridingKeepOnlySupportedFieldsAndIndependentValues() {
        var project = new ItemProject();
        var source = catalogItem("minecraft:iron_sword", "itemcontent:sword", json("{\"durability\":250,\"attack_damage\":6,\"attack_speed\":1.6}"), true);
        var original = CatalogDialog.fromCatalog(project, source, true);
        assertEquals(source.id(), original.id); assertTrue(original.categoryLocked); assertEquals("itemcontent:keep", original.group);
        assertEquals(Set.of("durability"), original.properties.keySet()); assertTrue(original.referenceProperties.has("attack_damage"));
        var copy = CatalogDialog.fromCatalog(project, source, false); project.items.add(copy);
        assertFalse(copy.categoryLocked); assertFalse(copy.existing); assertNotEquals(source.id(), copy.id); project.validate();
        copy.properties.addProperty("attack_damage", 9); assertEquals(6, source.properties().get("attack_damage").getAsInt());
        var second = CatalogDialog.fromCatalog(project, source, false); assertNotEquals(copy.id, second.id);
        original.group = "minecraft:ingredients"; project.items.add(original); assertThrows(IllegalArgumentException.class, project::validate);
    }
    @Test void blockBowArmorResourcesHaveStandardGamePathsAndMiningTags() throws Exception {
        var project = new ItemProject();
        Path png = temp.resolve("own.png"); javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,2), "png", png.toFile());
        for (String type : List.of("block", "decorative_chest", "decorative_furnace", "bow", "crossbow", "armor")) {
            var e = new ItemProject.Entry(); e.id = "itemcontent:" + type; e.type = e.id; e.name = type; e.customModel = true; e.texture = png.toString();
            e.properties = project.type(e).validate(new JsonObject(), false);
            if (e.properties.has("durability")) e.maxCount = 1;
            if (type.equals("block")) { e.properties.addProperty("mining_level", 2); e.textures.put("front", png.toString()); }
            if (type.equals("armor")) { e.properties.addProperty("armor_texture", "custom"); e.textures.put("armor_layer_1", png.toString()); e.textures.put("armor_layer_2", png.toString()); }
            project.items.add(e);
        }
        Path export = temp.resolve("export"); project.export(export); Path assets = export.resolve("resourcepacks/item-content/assets/itemcontent");
        assertEquals(4, json(Files.readString(assets.resolve("blockstates/decorative_chest.json"))).getAsJsonObject("variants").size());
        assertEquals(3, json(Files.readString(assets.resolve("models/block/decorative_chest.json"))).getAsJsonArray("elements").size());
        assertEquals("itemcontent:block/block_front", json(Files.readString(assets.resolve("models/block/block.json"))).getAsJsonObject("textures").get("north").getAsString());
        assertEquals(3, json(Files.readString(assets.resolve("models/item/bow.json"))).getAsJsonArray("overrides").size());
        assertEquals(5, json(Files.readString(assets.resolve("models/item/crossbow.json"))).getAsJsonArray("overrides").size());
        assertTrue(Files.exists(assets.resolve("textures/models/armor/armor_layer_2.png")));
        var tag = json(Files.readString(export.resolve("datapacks/item-content/data/minecraft/tags/blocks/needs_iron_tool.json")));
        assertEquals("itemcontent:block", tag.getAsJsonArray("values").get(0).getAsString());
    }
    @Test void addonBlockMayHaveItsOwnPropertiesWithoutBuiltinMiningFields() throws Exception {
        var project = new ItemProject(); project.extensionTypes.add(new TypeDefinition("sample:block", "Block", "Extension", "minecraft:building_blocks", "cube", true, List.of(), ""));
        var item = new ItemProject.Entry(); item.type = "sample:block"; item.appearance = "minecraft:stone"; project.items.add(item);
        project.export(temp.resolve("addon-block")); assertTrue(Files.exists(temp.resolve("addon-block/resourcepacks/item-content/assets/itemcontent/blockstates/new_item.json")));
    }
    @Test void editorTypeSwitchAndBehaviorPreserveSettingsAndFitBothTabs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            ((JComboBox<?>) find(panel,"typeCategory")).setSelectedItem("Оружие");
            assertEquals("Меч", ((JComboBox<?>) find(panel,"itemType")).getSelectedItem().toString());
            var speed = (JTextField) find(panel,"behavior.attack_speed"); speed.setText("2.5");
            ((JButton) find(panel,"addItem")).doClick(); ((JList<?>) find(panel,"items")).setSelectedIndex(0);
            assertEquals("2.5", ((JTextField) find(panel,"behavior.attack_speed")).getText());
            for (int tab=0; tab<2; tab++) {
                ((JTabbedPane) find(panel,"detailsTabs")).setSelectedIndex(tab); panel.setSize(1264,641); layout(panel); assertInterior(panel);
            }
        });
    }
    static void layout(Container root) { root.doLayout(); for (var c:root.getComponents()) if(c instanceof Container nested) layout(nested); }
    static void assertInterior(Container root) { for (var c:root.getComponents()) if(c.isVisible()) {
        if(c instanceof JTextField field) assertTrue(field.getHeight()-field.getInsets().top-field.getInsets().bottom >= field.getFontMetrics(field.getFont()).getHeight(), field.getName());
        if(c instanceof Container nested) assertInterior(nested);
    }}
    static Component find(Container root,String name) { for(var c:root.getComponents()) { if(name.equals(c.getName())) return c; if(c instanceof Container nested) { var found=find(nested,name); if(found!=null)return found; }} return null; }
}
