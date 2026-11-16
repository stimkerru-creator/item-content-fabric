package dev.itemcontent.editor;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class AuditRegressionsTest {
    @TempDir Path temp;
    private Path png(String name, int color) throws Exception {
        Path path = temp.resolve(name + ".png");
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color); ImageIO.write(image, "png", path.toFile()); return path;
    }
    private ItemProject.Entry entry(String name, String type, Path png) {
        var item = new ItemProject.Entry(); item.id = "itemcontent:" + name; item.type = "itemcontent:" + type;
        item.customModel = true; item.texture = png.toString();
        if (Set.of("bow", "crossbow").contains(type)) item.maxCount = 1;
        return item;
    }
    private JsonObject json(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    @Test void generatedWeaponModelsDoNotOverwriteLegalItemNamesInEitherOrder() throws Exception {
        Path image = png("weapon", 0xffff0000);
        for (String type : List.of("bow", "crossbow")) for (boolean reverse : List.of(false, true)) {
            var project = new ItemProject();
            var weapon = entry("test_" + type, type, image);
            for (String key : List.of("pulling_0", "pulling_1", "pulling_2", "arrow", "firework")) {
                weapon.textures.put(key, image.toString());
                project.items.add(entry("test_" + type + "_" + key, "item", image));
            }
            project.items.add(weapon); if (reverse) Collections.reverse(project.items);
            Path out = temp.resolve(type + reverse); project.export(out);
            Path pack = out.resolve("resourcepacks/item-content");
            assertEquals("minecraft:item/generated", json(pack.resolve("assets/itemcontent/models/item/test_" + type + "_pulling_0.json")).get("parent").getAsString());
            for (var value : json(pack.resolve("assets/itemcontent/models/item/test_" + type + ".json")).getAsJsonArray("overrides")) {
                String model = value.getAsJsonObject().get("model").getAsString();
                Path child = pack.resolve("assets/" + model.replace(":", "/models/") + ".json");
                assertTrue(Files.isRegularFile(child));
                assertTrue(json(child).get("parent").getAsString().startsWith("minecraft:item/" + type + "_"));
            }
        }
    }
    @Test void blockFaceTexturesDoNotOverwriteOtherBlocks() throws Exception {
        Path red = png("red", 0xffff0000), blue = png("blue", 0xff0000ff);
        for (boolean reverse : List.of(false, true)) {
            var project = new ItemProject(); var block = entry("stone", "block", red);
            block.textures.put("front", red.toString());
            project.items.add(block); project.items.add(entry("stone_front", "block", blue));
            if (reverse) Collections.reverse(project.items);
            Path out = temp.resolve("faces" + reverse); project.export(out);
            Path pack = out.resolve("resourcepacks/item-content");
            String front = json(pack.resolve("assets/itemcontent/models/block/stone.json")).getAsJsonObject("textures").get("north").getAsString();
            assertEquals(0xffff0000, ImageIO.read(pack.resolve("assets/" + front.replace(":", "/textures/") + ".png").toFile()).getRGB(0, 0));
            assertEquals(0xff0000ff, ImageIO.read(pack.resolve("assets/itemcontent/textures/block/stone_front.png").toFile()).getRGB(0, 0));
        }
    }
    @Test void maximumLengthItemIdsExportGeneratedAndImportedResources() throws Exception {
        Path image = png("long", 0xff00ff00);
        for (String type : List.of("bow", "crossbow", "block", "imported")) {
            var project = new ItemProject();
            var item = entry("a".repeat(116), type.equals("imported") ? "item" : type, image);
            assertEquals(128, item.id.length());
            item.textures.put("front", image.toString()); item.textures.put("pulling_0", image.toString());
            if (type.equals("imported")) {
                Path model = temp.resolve("long-model.json");
                Files.writeString(model, "{\"parent\":\"item/generated\"}");
                try (var source = MinecraftModel.open(model)) { item.importedModel = MinecraftModel.load(source, "imported:model"); }
                item.importedAppearance = true;
            }
            project.items.add(item); Path target = temp.resolve("long-" + type); project.export(target);
            try (var files = Files.walk(target)) {
                assertTrue(files.allMatch(path -> path.getFileName().toString().length() <= 255));
            }
        }
        // Similar IDs and different path depths remain separate before source resources begin.
        String a = "itemcontent:" + "b".repeat(21), b = a + "/part";
        assertFalse(ResourcePaths.prefix("imported", b).startsWith(ResourcePaths.prefix("imported", a)));
    }
    private Path mod() throws Exception {
        Path path = temp.resolve("item-content-fabric-test.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json")); zip.write("{\"id\":\"itemcontent\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } return path;
    }
    @Test void clearingLastMiningRuleOrRemovingLastBlockReplacesOwnedDatapackAndKeepsBackup() throws Exception {
        Path jar = mod();
        for (boolean removeBlock : List.of(false, true)) {
            Path game = temp.resolve("game" + removeBlock), world = temp.resolve("world" + removeBlock);
            Files.createDirectories(world); Files.writeString(world.resolve("level.dat"), "world");
            var project = new ItemProject(); var block = new ItemProject.Entry(); block.type = "itemcontent:block"; block.appearance = "minecraft:stone";
            block.properties.addProperty("tool", "pickaxe"); block.properties.addProperty("mining_level", 2); project.items.add(block);
            GameInstallation.install(project, game, world, jar);
            Path tag = world.resolve("datapacks/item-content/data/minecraft/tags/blocks/mineable/pickaxe.json");
            assertTrue(Files.isRegularFile(tag));
            Path foreign = world.resolve("datapacks/other/keep.txt"); Files.createDirectories(foreign.getParent()); Files.writeString(foreign, "unrelated");
            block.properties.addProperty("tool", "none"); block.properties.addProperty("mining_level", 0);
            if (removeBlock) { project.items.clear(); project.items.add(new ItemProject.Entry()); }
            assertThrows(IOException.class, () -> GameInstallation.install(project, game, null, jar));
            assertTrue(Files.isRegularFile(tag));
            GameInstallation.install(project, game, world, jar);
            assertFalse(Files.exists(tag)); assertFalse(Files.exists(world.resolve("datapacks/item-content/data")));
            assertTrue(Files.isRegularFile(world.resolve("datapacks/item-content/pack.mcmeta")));
            assertEquals("unrelated", Files.readString(foreign));
            try (var files = Files.walk(world.resolve(".item-content-backups"))) {
                assertTrue(files.anyMatch(path -> path.endsWith("mineable/pickaxe.json")), "Previous mining rule must remain in backup");
            }
        }
    }
    @Test void malformedModelGeometryIsRejectedBeforeExportAndSavedBundlesAreCheckedToo() throws Exception {
        Path modelFile = temp.resolve("model.json");
        String cube = "{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"texture\":\"#all\"}}}";
        List<String> bad = new ArrayList<>(List.of("{\"elements\":[{}]}", "{\"elements\":[" + cube.replace("[0,0,0]", "[0,0]") + "]}",
                "{\"elements\":[" + cube.replace("16,16,16", "33,16,16") + "]}", "{\"elements\":[" + cube.replace("north", "NORTH") + "]}",
                "{\"elements\":[" + cube.replace("\"texture\":\"#all\"", "\"uv\":[0,0,16]") + "]}",
                "{\"parent\":\"item/generated\",\"display\":{\"gui\":{\"rotation\":[0,1]}}}",
                "{\"parent\":\"item/generated\",\"overrides\":[{\"model\":\"item/bow\"}]}",
                "{\"parent\":\"item/generated\",\"overrides\":[{\"model\":\"item/bow\",\"predicate\":{\"pull\":\"1\"}}]}"));
        for (String value : bad) {
            Files.writeString(modelFile, value);
            try (var source = MinecraftModel.open(modelFile)) { assertThrows(IOException.class, () -> MinecraftModel.load(source, "imported:model"), value); }
            var saved = new MinecraftModel(); saved.root = "imported:model";
            saved.files.put("assets/imported/models/model.json", Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThrows(IOException.class, saved::validate, value);
        }
        for (String value : List.of("{\"elements\":[]}", "{\"elements\":[" + cube.replace("16,16,16", "0,16,16") + "]}",
                "{\"parent\":\"item/generated\",\"display\":{\"gui\":{\"scale\":[-1,0,5],\"translation\":[100,0,0]}}}")) {
            Files.writeString(modelFile, value);
            try (var source = MinecraftModel.open(modelFile)) { assertNotNull(MinecraftModel.load(source, "imported:model")); }
        }
    }
    @Test void failedUpdateRestoresThePreviousMiningDatapack() throws Exception {
        Path game = temp.resolve("rollback-game"), world = temp.resolve("rollback-world"), jar = mod();
        Files.createDirectories(world); Files.writeString(world.resolve("level.dat"), "world");
        var project = new ItemProject(); var block = new ItemProject.Entry();
        block.type = "itemcontent:block"; block.appearance = "minecraft:stone";
        block.properties.addProperty("tool", "pickaxe"); project.items.add(block);
        GameInstallation.install(project, game, world, jar);
        Path tag = world.resolve("datapacks/item-content/data/minecraft/tags/blocks/mineable/pickaxe.json");
        String before = Files.readString(tag), config = Files.readString(game.resolve("config/item-content/items.json"));
        Files.move(game.resolve("mods"), game.resolve("saved-mods"));
        Files.writeString(game.resolve("mods"), "not a directory");
        block.properties.addProperty("tool", "none");
        assertThrows(IOException.class, () -> GameInstallation.install(project, game, world, jar));
        assertEquals(before, Files.readString(tag));
        assertEquals(config, Files.readString(game.resolve("config/item-content/items.json")));
        assertEquals("world", Files.readString(world.resolve("level.dat")));
    }
    @Test void windowsJunctionCannotRedirectInstallOrExport() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path game = Files.createDirectory(temp.resolve("game")), outside = Files.createDirectory(temp.resolve("outside"));
        Path junction = game.resolve("config");
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", junction.toString(), outside.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset());
        assertEquals(0, process.waitFor(), output);
        try {
            assertFalse(Files.isSymbolicLink(junction));
            var project = new ItemProject(); project.items.add(new ItemProject.Entry()); Path jar = mod();
            assertThrows(IOException.class, () -> GameInstallation.install(project, game, null, jar));
            assertThrows(IOException.class, () -> project.export(junction.resolve("new-export")));
            try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
            assertFalse(Files.exists(game.resolve("resourcepacks")));
        } finally { Files.delete(junction); } // Remove only the junction created by this test, never its target.
    }
}
