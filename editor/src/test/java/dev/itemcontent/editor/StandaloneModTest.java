package dev.itemcontent.editor;

import com.google.gson.*;
import dev.itemcontent.definition.GameCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneModTest {
    @TempDir Path temp;
    private StandaloneMod.Settings settings(String id) { return new StandaloneMod.Settings(id, "Мои предметы", "1.0.0", Set.of()); }
    private JsonObject json(JarFile jar, String name) throws Exception { try (var input = jar.getInputStream(jar.getJarEntry(name))) { return JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); } }
    @Test void exactlyTwoHundredSelectedItemsFromFourThousandBecomeOneIndependentJar() throws Exception {
        var project = new ItemProject(); Set<String> selection = new LinkedHashSet<>();
        for (int i = 0; i < 4000; i++) { var item = new ItemProject.Entry(); item.id = "itemcontent:part_" + i; item.name = "Деталь " + i; project.items.add(item); if (i < 200) selection.add(item.id); }
        var unused = project.items.get(3999); unused.customModel = true; unused.texture = "absent.png";
        var result = StandaloneMod.build(project, selection, settings("bronze_parts"), temp.resolve("bronze.jar"));
        assertEquals(200, result.items()); assertEquals(4000, project.items.size());
        try (var jar = new JarFile(result.jar().toFile())) {
            var metadata = json(jar, "fabric.mod.json"); assertEquals("bronze_parts", metadata.get("id").getAsString());
            assertFalse(metadata.getAsJsonObject("depends").has("itemcontent"));
            var items = json(jar, "itemcontent/content.json").getAsJsonArray("items"); assertEquals(200, items.size());
            assertEquals("bronze_parts:part_0", items.get(0).getAsJsonObject().get("id").getAsString());
            assertFalse(items.toString().contains("part_3999"));
            assertTrue(jar.stream().noneMatch(e -> e.getName().startsWith("config/") || e.getName().contains("catalog") || e.getName().contains(".icproject") || e.getName().endsWith(".java")));
            byte[] template; try (var input = jar.getInputStream(jar.getJarEntry("META-INF/jars/itemcontent-runtime.jar"))) { template = input.readAllBytes(); }
            try (var nested = new ZipInputStream(new ByteArrayInputStream(template))) {
                boolean metadataFound = false, classFound = false;
                for (var entry = nested.getNextEntry(); entry != null; entry = nested.getNextEntry()) {
                    assertFalse(entry.getName().contains("/editor/") || entry.getName().contains("/catalog/"), entry.getName());
                    if (entry.getName().equals("fabric.mod.json")) { assertTrue(new String(nested.readAllBytes()).contains(StandaloneMod.RUNTIME_ID)); metadataFound = true; }
                    if (entry.getName().equals("dev/itemcontent/generated/ItemContentMod.class")) classFound = true;
                }
                assertTrue(metadataFound); assertTrue(classFound);
            }
        }
    }
    @Test void selectedDependenciesAreRequiredRatherThanSilentlyIncludingTheWholeProject() throws Exception {
        var project = new ItemProject(); var a = new ItemProject.Entry(); a.id = "itemcontent:a"; a.appearance = "itemcontent:b";
        var b = new ItemProject.Entry(); b.id = "itemcontent:b"; project.items.addAll(List.of(a, b));
        assertThrows(IllegalArgumentException.class, () -> StandaloneMod.build(project, Set.of(a.id), settings("only_a"), temp.resolve("a.jar")));
        assertFalse(Files.exists(temp.resolve("a.jar")));
        assertEquals(2, StandaloneMod.build(project, Set.of(a.id, b.id), settings("both"), temp.resolve("both.jar")).items());
    }
    @Test void realModelResourcesGroupsMiningTagsAndPropertiesAreEmbeddedAndRenamed() throws Exception {
        var project = new ItemProject(); var item = new ItemProject.Entry(); item.type = "itemcontent:block"; item.importedAppearance = true;
        try (var source = MinecraftModel.open(Path.of("../examples/minecraft-models"))) { item.importedModel = MinecraftModel.load(source, "sample:item/component"); }
        item.properties.addProperty("tool", "pickaxe"); item.properties.addProperty("mining_level", 2);
        item.group = "itemcontent:parts"; project.groups.add(new GameCatalog.Group("itemcontent:parts", "Детали")); project.items.add(item);
        var result = StandaloneMod.build(project, Set.of(item.id), settings("my_bronze"), temp.resolve("model.jar"));
        try (var jar = new JarFile(result.jar().toFile())) {
            var config = json(jar, "itemcontent/content.json");
            assertEquals("my_bronze:parts", config.getAsJsonArray("groups").get(0).getAsJsonObject().get("id").getAsString());
            assertEquals("itemcontent:block", config.getAsJsonArray("items").get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("my_bronze:new_item", json(jar, "data/minecraft/tags/blocks/mineable/pickaxe.json").getAsJsonArray("values").get(0).getAsString());
            assertNotNull(jar.getEntry("assets/my_bronze/models/block/new_item.json"));
            assertTrue(jar.stream().anyMatch(e -> e.getName().startsWith("assets/my_bronze/textures/item/imported/") && e.getName().endsWith(".png")));
            assertEquals(item.name, json(jar, "assets/my_bronze/lang/ru_ru.json").get("block.my_bronze.new_item").getAsString());
            assertFalse(jar.stream().anyMatch(e -> e.getName().startsWith("assets/itemcontent/")));
        }
        assertEquals("itemcontent:new_item", item.id); assertEquals("itemcontent:parts", item.group);
    }
    @Test void failedBuildNeverReplacesAnExistingJarAndSettingsRoundTrip() throws Exception {
        var project = new ItemProject(); project.items.add(new ItemProject.Entry());
        Path output = temp.resolve("existing.jar"); Files.writeString(output, "keep this");
        assertThrows(IOException.class, () -> StandaloneMod.build(project, Set.of("itemcontent:new_item"), settings("my_items"), output));
        assertEquals("keep this", Files.readString(output));
        project.modId = "bronze_parts"; project.modName = "Бронза"; project.modVersion = "2.1.0";
        project.modItems = new ArrayList<>(List.of("itemcontent:new_item")); project.requiredMods.add("example_mod");
        Path file = temp.resolve("list.icproject"); project.save(file); var loaded = ItemProject.load(file);
        assertEquals(project.modId, loaded.modId); assertEquals(project.modName, loaded.modName); assertEquals(project.modVersion, loaded.modVersion);
        assertEquals(project.modItems, loaded.modItems); assertEquals(project.requiredMods, loaded.requiredMods);
        project.modItems.clear(); project.save(file); assertTrue(ItemProject.load(file).modItems.isEmpty());
    }
    @Test void selectionPanelCanExcludeItemsAndPreviewTheirFinalIds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var project = new ItemProject(); var first = new ItemProject.Entry(); var second = new ItemProject.Entry(); second.id = "itemcontent:second";
            project.items.addAll(List.of(first, second)); project.modItems = new ArrayList<>(List.of(first.id)); var panel = new ModSelectionPanel(project);
            assertEquals(Set.of(first.id), panel.selection());
            JTable table = (JTable) find(panel, "modItems"); table.getModel().setValueAt(false, 1, 0);
            panel.id.setText("custom_parts"); assertEquals(Set.of(first.id), panel.selection());
            assertEquals("custom_parts:new_item", table.getModel().getValueAt(0, 3));
            ((JButton) find(panel, "clearModItems")).doClick(); assertTrue(panel.selection().isEmpty());
            ((JButton) find(panel, "selectAllModItems")).doClick(); assertEquals(2, panel.selection().size());
        });
    }
    @Test void requiredModsCannotWeakenTheFabricMinecraftOrJavaRequirements() {
        for (String reserved : List.of("minecraft", "java", "fabricloader", "fabric-api", "itemcontent", StandaloneMod.RUNTIME_ID))
            assertThrows(IllegalArgumentException.class, () -> new StandaloneMod.Settings("my_items", "Items", "1.0.0", Set.of(reserved)), reserved);
        assertThrows(IllegalArgumentException.class, () -> new StandaloneMod.Settings("my_items", "Items", "1.0.0", Set.of("my_items")));
    }
    @Test void finalItemAndGroupIdsMustFitTheRuntimeLimit() {
        var project = new ItemProject(); var item = new ItemProject.Entry(); project.items.add(item);
        item.id = "itemcontent:" + "p".repeat(116);
        assertThrows(IllegalArgumentException.class, () -> StandaloneMod.build(project, Set.of(item.id), settings("longer_mod_name"), temp.resolve("long-item.jar")));
        item.id = "itemcontent:part"; item.group = "itemcontent:" + "g".repeat(116);
        project.groups.add(new GameCatalog.Group(item.group, "Parts"));
        assertThrows(IllegalArgumentException.class, () -> StandaloneMod.build(project, Set.of(item.id), settings("longer_mod_name"), temp.resolve("long-group.jar")));
        assertFalse(Files.exists(temp.resolve("long-item.jar"))); assertFalse(Files.exists(temp.resolve("long-group.jar")));
    }
    private static java.awt.Component find(java.awt.Container root, String name) {
        for (var child : root.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof java.awt.Container container) { var found = find(container, name); if (found != null) return found; } } return null;
    }
}
