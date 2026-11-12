package dev.itemcontent;

import com.google.gson.*;
import dev.itemcontent.config.ItemContentConfig;
import dev.itemcontent.editor.ItemProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DesktopRuntimeCompatibilityTest {
    @TempDir Path temp;
    @Test void desktopOutputIsAcceptedByActualMinecraftConfigurationLoader() throws Exception {
        var project = new ItemProject(); var item = new ItemProject.Entry();
        item.id = "itemcontent:steel_plate"; item.name = "Стальная пластина"; item.maxCount = 16; item.fireproof = true;
        project.items.add(item);
        project.export(temp.resolve("export"));
        var loaded = ItemContentConfig.parse(Files.readString(temp.resolve("export/config/item-content/items.json")));
        assertEquals(1, loaded.size()); assertEquals(item.id, loaded.get(0).id().toString());
        assertEquals(item.name, loaded.get(0).displayName()); assertEquals(16, loaded.get(0).maxCount()); assertTrue(loaded.get(0).fireproof());
        assertFalse(Files.exists(temp.resolve("export/config/itemparams")));
        var config = JsonParser.parseString(Files.readString(temp.resolve("export/config/item-content/items.json"))).getAsJsonObject();
        assertFalse(config.getAsJsonArray("items").get(0).getAsJsonObject().has("fields"));
    }
    @Test void expandedShippedExampleExportsAllTypesToTheRuntimeLoader() throws Exception {
        var project = ItemProject.load(Path.of("examples/content-types.icproject")); project.export(temp.resolve("typed-example"));
        var document = ItemContentConfig.parseDocument(Files.readString(temp.resolve("typed-example/config/item-content/items.json")));
        assertEquals(7,document.items().size());
        assertTrue(document.items().stream().anyMatch(item -> item.type().equals("itemcontent:decorative_chest")));
        assertTrue(Files.exists(temp.resolve("typed-example/datapacks/item-content/data/minecraft/tags/blocks/needs_stone_tool.json")));
    }
    @Test void originalPublicConstructorAndItemMapSignatureRemainAvailable() throws Exception {
        var spec = new dev.itemcontent.config.ItemSpec(new net.minecraft.util.Identifier("itemcontent:old"),"Old",new net.minecraft.util.Identifier("minecraft:iron_ingot"),false,64,false);
        assertEquals("itemcontent:item",spec.type());
        assertTrue(ItemContentMod.class.getMethod("items").getGenericReturnType().getTypeName().contains("ConfiguredItem"));
        assertNotNull(ItemContentMod.class.getMethod("registeredItems"));
    }
    @Test void fabricManifestDoesNotRequireEitherOtherMod() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
        assertEquals(java.util.Set.of("fabricloader", "minecraft", "java", "fabric-api"), manifest.getAsJsonObject("depends").keySet());
        assertFalse(manifest.has("recommends")); assertFalse(manifest.has("suggests"));
    }
}
