package dev.itemcontent.editor;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemProjectTest {
    @TempDir Path temp;
    private ItemProject project() {
        ItemProject project = new ItemProject();
        var entry = new ItemProject.Entry(); entry.id = "itemcontent:steel_plate"; entry.name = "Стальная пластина";
        project.items.add(entry); return project;
    }
    @Test void saveAndOpenPreserveItemProperties() throws Exception {
        var project = project(); var item = project.items.get(0);
        item.fireproof = true; item.maxCount = 16;
        Path path = temp.resolve("items.icproject"); project.save(path);
        var loaded = ItemProject.load(path).items.get(0);
        assertEquals(item.id, loaded.id); assertEquals(item.name, loaded.name);
        assertTrue(loaded.fireproof); assertEquals(16, loaded.maxCount);
    }
    @Test void exportProducesConfigWithNoRecipesOrDuplicatedParameterCatalog() throws Exception {
        var project = project(); Path target = temp.resolve("export"); project.export(target);
        JsonObject root = JsonParser.parseString(Files.readString(target.resolve("config/item-content/items.json"))).getAsJsonObject();
        assertEquals(1, root.get("version").getAsInt());
        assertEquals("itemcontent:steel_plate", root.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(root.getAsJsonArray("items").get(0).getAsJsonObject().has("fields"));
        assertFalse(Files.exists(target.resolve("config/itemparams"))); assertFalse(root.has("recipes"));
        assertTrue(Files.exists(target.resolve("INSTALL.txt")));
    }
    @Test void customPngExportProducesActualModelAndIdenticalTexture() throws Exception {
        var project = project(); var item = project.items.get(0);
        Path png = temp.resolve("plate.png"); ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());
        item.customModel = true; item.texture = png.toString(); Path target = temp.resolve("png-export");
        project.export(target);
        assertArrayEquals(Files.readAllBytes(png), Files.readAllBytes(target.resolve("resourcepacks/item-content/assets/itemcontent/textures/item/steel_plate.png")));
        JsonObject model = JsonParser.parseString(Files.readString(target.resolve("resourcepacks/item-content/assets/itemcontent/models/item/steel_plate.json"))).getAsJsonObject();
        assertEquals("itemcontent:item/steel_plate", model.getAsJsonObject("textures").get("layer0").getAsString());
    }
    @Test void exportAcceptsExistingEmptyFolder() throws Exception {
        Path target = Files.createDirectory(temp.resolve("empty")); project().export(target);
        assertTrue(Files.exists(target.resolve("config/item-content/items.json")));
    }
    @Test void exportNeverOverwritesOccupiedFolder() throws Exception {
        Path target = Files.createDirectory(temp.resolve("occupied")); Path keep = target.resolve("keep.txt"); Files.writeString(keep, "preserve");
        assertThrows(IOException.class, () -> project().export(target)); assertEquals("preserve", Files.readString(keep));
    }
    @Test void brokenPngLeavesTargetUntouchedAndCleansStaging() throws Exception {
        var project = project(); var item = project.items.get(0);
        Path bad = temp.resolve("bad.png"); Files.writeString(bad, "not PNG"); item.customModel = true; item.texture = bad.toString();
        Path target = temp.resolve("export"); assertThrows(IOException.class, () -> project.export(target));
        assertFalse(Files.exists(target));
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
    }
    @Test void duplicateIdsAndForeignNamespacesAreRejected() {
        var project = project(); project.items.add(project.items.get(0)); assertThrows(IllegalArgumentException.class, project::validate);
        project.items.remove(1); project.items.get(0).id = "minecraft:iron_ingot"; assertThrows(IllegalArgumentException.class, project::validate);
    }
    @Test void pathTraversalIsRejectedBeforeExport() {
        var project = project(); project.items.get(0).id = "itemcontent:../../escape";
        assertThrows(IllegalArgumentException.class, () -> project.export(temp.resolve("export")));
    }
    @Test void invalidItemCannotOverwriteSavedList() throws Exception {
        var project = project(); Path file = temp.resolve("saved.icproject"); project.save(file);
        byte[] before = Files.readAllBytes(file); project.items.get(0).maxCount = 0;
        assertThrows(IllegalArgumentException.class, () -> project.save(file));
        assertArrayEquals(before, Files.readAllBytes(file));
    }
    @Test void legacyDataIsBackedUpBeforeSavingAndNeverExported() throws Exception {
        String original = "{\"editorVersion\":1,\"items\":[{\"id\":\"itemcontent:plate\",\"name\":\"Plate\",\"fields\":[{\"id\":\"mass\",\"value\":\"12.34\"}]}]}";
        Path file = temp.resolve("legacy.icproject"); Files.writeString(file, original);
        var project = ItemProject.load(file); assertTrue(project.hasLegacyData());
        assertEquals(original, Files.readString(file));
        project.export(temp.resolve("legacy-export"));
        assertFalse(Files.readString(temp.resolve("legacy-export/config/item-content/items.json")).contains("fields"));
        assertEquals(original, Files.readString(file));
        project.items.get(0).name = "New name"; project.save(file);
        assertEquals(original, Files.readString(project.legacyBackup()));
        assertFalse(Files.readString(file).contains("fields"));
        assertEquals("New name", ItemProject.load(file).items.get(0).name);
        Path backup = project.legacyBackup(); project.save(file); assertEquals(backup, project.legacyBackup());
    }
    @Test void legacyDataIsProtectedEvenWhenSavingAsAnotherFile() throws Exception {
        Path file = temp.resolve("legacy.icproject");
        String original = "{\"editorVersion\":1,\"items\":[{\"fields\":[\"old mass\"]}]}";
        Files.writeString(file, original); var project = ItemProject.load(file);
        project.save(temp.resolve("new.icproject"));
        assertEquals(original, Files.readString(file));
        assertEquals(original, Files.readString(project.legacyBackup()));
        assertFalse(Files.readString(temp.resolve("new.icproject")).contains("fields"));
    }
    @Test void unknownProjectVersionIsRejected() throws Exception {
        Path file = temp.resolve("future.icproject"); Files.writeString(file, "{\"editorVersion\":99,\"items\":[]}");
        assertThrows(IOException.class, () -> ItemProject.load(file));
    }
    @Test void shippedExampleCanBeOpenedAndExported() throws Exception {
        ItemProject sample = ItemProject.load(Path.of("../examples/steel-plate.icproject"));
        assertEquals("itemcontent:steel_plate", sample.items.get(0).id);
        sample.export(temp.resolve("example-export"));
        assertTrue(Files.exists(temp.resolve("example-export/config/item-content/items.json")));
    }
}
