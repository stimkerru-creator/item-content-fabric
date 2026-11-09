package dev.itemcontent;

import com.google.gson.*;
import dev.itemcontent.config.ItemContentConfig;
import dev.itemcontent.config.ParameterCatalogWriter;
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
        var field = new ItemProject.Field(); field.value = "1,25"; item.fields.add(field); project.items.add(item);
        project.export(temp.resolve("export"));
        var loaded = ItemContentConfig.parse(Files.readString(temp.resolve("export/config/item-content/items.json")));
        assertEquals(1, loaded.size()); assertEquals(item.id, loaded.get(0).id().toString());
        assertEquals(item.name, loaded.get(0).displayName()); assertEquals(16, loaded.get(0).maxCount()); assertTrue(loaded.get(0).fireproof());
        assertEquals(0, new java.math.BigDecimal("1.25").compareTo(loaded.get(0).fields().get(0).defaultValue()));
    }
    private JsonArray generated() {
        return JsonParser.parseString("""
            [{"item":"itemcontent:steel_plate","fields":[{"id":"factory:mass","default":1.25,"min":0,"max":10}]}]
            """).getAsJsonArray();
    }
    @Test void matchingRecipeEditorDefinitionsAreNotGeneratedTwice() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("items")); Path external = dir.resolve("editor-items.json");
        Files.writeString(external, "{\"version\":1,\"items\":" + generated() + "}"); byte[] before = Files.readAllBytes(external);
        Path target = dir.resolve("itemcontent_generated.json"); ParameterCatalogWriter.write(target, generated());
        assertTrue(JsonParser.parseString(Files.readString(target)).getAsJsonObject().getAsJsonArray("items").isEmpty());
        assertArrayEquals(before, Files.readAllBytes(external));
    }
    @Test void standaloneDefinitionsAreGeneratedAndStaleEntriesRemoved() throws Exception {
        Path target = temp.resolve("items/itemcontent_generated.json"); ParameterCatalogWriter.write(target, generated());
        assertEquals(1, JsonParser.parseString(Files.readString(target)).getAsJsonObject().getAsJsonArray("items").size());
        ParameterCatalogWriter.write(target, new JsonArray());
        assertEquals(0, JsonParser.parseString(Files.readString(target)).getAsJsonObject().getAsJsonArray("items").size());
    }
    @Test void itemWithoutOptionalStatsAcceptsSchemaConfiguredInFirstProject() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("items"));
        Files.writeString(dir.resolve("editor-items.json"), "{\"version\":1,\"items\":" + generated() + "}");
        JsonArray registrationOnly = generated(); registrationOnly.get(0).getAsJsonObject().add("fields", new JsonArray());
        Path target = dir.resolve("itemcontent_generated.json"); ParameterCatalogWriter.write(target, registrationOnly);
        assertTrue(JsonParser.parseString(Files.readString(target)).getAsJsonObject().getAsJsonArray("items").isEmpty());
    }
    @Test void conflictingDefinitionsFailWithoutOverwritingEitherCatalog() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("items")); Path external = dir.resolve("editor-items.json");
        Files.writeString(external, "{\"version\":1,\"items\":" + generated().toString().replace("1.25", "2.50") + "}");
        Path target = dir.resolve("itemcontent_generated.json"); Files.writeString(target, "previous generated catalog");
        assertThrows(IOException.class, () -> ParameterCatalogWriter.write(target, generated()));
        assertEquals("previous generated catalog", Files.readString(target));
        assertTrue(Files.readString(external).contains("2.50"));
    }
}
