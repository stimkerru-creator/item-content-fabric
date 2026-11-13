package dev.itemcontent.editor;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftModelTest {
    @TempDir Path temp;
    private Path pack() throws Exception {
        Path pack = temp.resolve("pack");
        write(pack, "assets/demo/models/item/part.json", "{\"parent\":\"demo:item/base\",\"display\":{\"gui\":{\"rotation\":[20,30,0]}},\"overrides\":[{\"predicate\":{\"pulling\":1},\"model\":\"demo:item/pulled\"}]}");
        write(pack, "assets/demo/models/item/base.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/metal\",\"particle\":\"#layer0\"}}");
        write(pack, "assets/demo/models/item/pulled.json", "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"demo:item/metal\"}}");
        Path png = pack.resolve("assets/demo/textures/item/metal.png"); Files.createDirectories(png.getParent());
        ImageIO.write(new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB),"png",png.toFile());
        write(pack, "assets/demo/textures/item/metal.png.mcmeta", "{\"animation\":{\"frametime\":2}}");
        return pack;
    }
    private static void write(Path pack, String path, String value) throws IOException {
        Path file = pack.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, value);
    }
    private MinecraftModel load(Path pack) throws Exception {
        try (var source = MinecraftModel.open(pack)) { return MinecraftModel.load(source,"demo:item/part"); }
    }
    private ItemProject project(MinecraftModel model) {
        var project = new ItemProject(); var item = new ItemProject.Entry(); item.importedAppearance = true; item.importedModel = model; project.items.add(item); return project;
    }
    private JsonObject json(Path path) throws IOException { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private Path modelFile(Path root, String id) { return root.resolve("assets/"+id.replace(":","/models/")+".json"); }
    @Test void folderAndDirectModelSelectionCollectTheSameClosure() throws Exception {
        Path pack=pack(), file=pack.resolve("assets/demo/models/item/part.json");
        try(var source=MinecraftModel.open(file)) {
            assertEquals("demo:item/part",MinecraftModel.selectedModel(file));
            assertEquals(3,source.models().size());
            var imported=MinecraftModel.load(source,MinecraftModel.selectedModel(file));
            assertEquals(load(pack).files,imported.files); assertEquals(5,imported.files.size());
        }
    }
    @Test void standaloneJavaExportFindsSiblingPngWithoutPackDirectories() throws Exception {
        Path pack=pack(), folder=Files.createDirectory(temp.resolve("loose"));
        Path json=folder.resolve("part.json");
        Files.writeString(json,"{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"demo:item/metal\"}}");
        Files.copy(pack.resolve("assets/demo/textures/item/metal.png"),folder.resolve("metal.png"));
        Files.copy(pack.resolve("assets/demo/textures/item/metal.png.mcmeta"),folder.resolve("metal.png.mcmeta"));
        try(var source=MinecraftModel.open(json)) {
            var model=MinecraftModel.load(source,MinecraftModel.selectedModel(json));
            assertEquals(3,model.files.size());project(model).export(temp.resolve("loose-export"));
        }
    }
    @Test void nonexistentVanillaResourcesAreAlsoRejected() throws Exception {
        Path pack=pack();write(pack,"assets/demo/models/item/part.json","{\"parent\":\"minecraft:item/no_such_model\"}");
        assertThrows(IOException.class,()->load(pack));
        write(pack,"assets/demo/models/item/part.json","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"minecraft:item/no_such_texture\"}}");
        assertThrows(IOException.class,()->load(pack));
    }
    @Test void zipImportNeedsNoExtractionAndPreservesDependencyBytes() throws Exception {
        Path pack=pack(), zip=temp.resolve("models.zip");
        try(var output=new ZipOutputStream(Files.newOutputStream(zip));var paths=Files.walk(pack)) {
            for(var path:paths.filter(Files::isRegularFile).toList()) { output.putNextEntry(new ZipEntry(pack.relativize(path).toString().replace('\\','/'))); output.write(Files.readAllBytes(path)); output.closeEntry(); }
        }
        try(var source=MinecraftModel.open(zip)) { assertEquals(load(pack).files,MinecraftModel.load(source,"demo:item/part").files); }
        Path jar=temp.resolve("resources.jar");Files.copy(zip,jar);
        try(var source=MinecraftModel.open(jar)) { assertEquals(load(pack).files,MinecraftModel.load(source,"demo:item/part").files); }
    }
    @Test void saveLoadExportWorksAfterSourceDeletionAndKeepsOverridesAndDisplay() throws Exception {
        Path pack=pack(); var project=project(load(pack)); Path document=temp.resolve("portable.icproject"); project.save(document);
        try(var paths=Files.walk(pack)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        var loaded=ItemProject.load(document); Path target=temp.resolve("export"); loaded.export(target);
        Path resource=target.resolve("resourcepacks/item-content");
        var root=json(resource.resolve("assets/itemcontent/models/item/new_item.json"));
        assertTrue(root.has("display")); assertEquals(1,root.getAsJsonArray("overrides").size());
        String parent=root.get("parent").getAsString(); assertTrue(parent.startsWith("itemcontent:imported/"));
        String texture=json(modelFile(resource,parent)).getAsJsonObject("textures").get("layer0").getAsString();
        assertTrue(texture.startsWith("itemcontent:item/imported/"),"Must be in the normal Minecraft item atlas");
        Path png=resource.resolve("assets/"+texture.replace(":","/textures/")+".png");
        assertTrue(Files.isRegularFile(png)); assertEquals("{\"animation\":{\"frametime\":2}}",Files.readString(Path.of(png+".mcmeta")));
        assertEquals("#layer0",json(modelFile(resource,parent)).getAsJsonObject("textures").get("particle").getAsString());
        var config=json(target.resolve("config/item-content/items.json"));
        assertTrue(config.getAsJsonArray("items").get(0).getAsJsonObject().get("custom_model").getAsBoolean());
        assertFalse(config.toString().contains("files"));
        assertFalse(Files.exists(resource.resolve("assets/demo")),"Do not override the original resource namespace");
    }
    @Test void importedBlockUsesModelInWorldAndInventoryAndKeepsMiningTags() throws Exception {
        var project=project(load(pack())); var item=project.items.get(0); item.type="itemcontent:block"; item.properties.addProperty("mining_level",2);
        Path target=temp.resolve("block"); project.export(target); Path assets=target.resolve("resourcepacks/item-content/assets/itemcontent");
        assertTrue(json(assets.resolve("models/block/new_item.json")).has("display"));
        assertEquals("itemcontent:block/new_item",json(assets.resolve("models/item/new_item.json")).get("parent").getAsString());
        assertTrue(Files.exists(assets.resolve("blockstates/new_item.json")));
        assertTrue(Files.exists(target.resolve("datapacks/item-content/data/minecraft/tags/blocks/needs_iron_tool.json")));
    }
    @Test void copiesAndDifferentItemsCannotOverwriteImportedAssets() throws Exception {
        var project=project(load(pack())); var second=project.items.get(0).copy(); second.id="itemcontent:new_item/child";
        project.items.add(second); assertNotSame(second.importedModel.files,project.items.get(0).importedModel.files);
        Path target=temp.resolve("two"); project.export(target); Path resource=target.resolve("resourcepacks/item-content");
        String a=json(resource.resolve("assets/itemcontent/models/item/new_item.json")).get("parent").getAsString();
        String b=json(resource.resolve("assets/itemcontent/models/item/new_item/child.json")).get("parent").getAsString();
        assertNotEquals(a,b); assertTrue(Files.exists(modelFile(resource,a)));assertTrue(Files.exists(modelFile(resource,b)));
    }
    @Test void missingCustomTextureFailsWithoutChangingExistingDocument() throws Exception {
        Path pack=pack(); Files.delete(pack.resolve("assets/demo/textures/item/metal.png"));
        IOException error=assertThrows(IOException.class,()->load(pack)); assertTrue(error.getMessage().contains("demo:item/metal"));
    }
    @Test void missingCustomParentAndParentCyclesAreRejected() throws Exception {
        Path pack=pack(); Files.delete(pack.resolve("assets/demo/models/item/base.json"));
        assertThrows(IOException.class,()->load(pack));
        write(pack,"assets/demo/models/item/base.json","{\"parent\":\"demo:item/part\"}");
        assertThrows(IOException.class,()->load(pack));
    }
    @Test void incompatibleFormatsAndInvalidResourcePathsAreRejected() throws Exception {
        Path pack=pack();
        for(String value:List.of("{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[]}","{\"loader\":\"forge:obj\",\"parent\":\"item/generated\"}","{\"parent\":\"demo:../../escape\"}","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"C:/secret\"}}","{\"parent\":null}","{\"textures\":{}}")) {
            write(pack,"assets/demo/models/item/part.json",value); assertThrows(IOException.class,()->load(pack),value);
        }
    }
    @Test void tamperedSavedBundleCannotEscapeExportOrOmitDependencies() throws Exception {
        var model=load(pack()); model.files.put("assets/../outside.png",""); assertThrows(IOException.class,model::validate);
        model.files.remove("assets/../outside.png");model.files.remove("assets/demo/textures/item/metal.png");assertThrows(IOException.class,model::validate);
    }
    @Test void zipTraversalAndOversizedInflatedFileAreRejected() throws Exception {
        Path zip=temp.resolve("bad.zip");
        try(var output=new ZipOutputStream(Files.newOutputStream(zip))) {output.putNextEntry(new ZipEntry("../outside"));output.write(0);output.closeEntry();}
        assertThrows(IOException.class,()->MinecraftModel.open(zip));
        try(var output=new ZipOutputStream(Files.newOutputStream(zip))) {output.putNextEntry(new ZipEntry("assets/demo/models/item/part.json"));output.write(new byte[MinecraftModel.MAX_FILE+1]);output.closeEntry();}
        try(var source=MinecraftModel.open(zip)){assertThrows(IOException.class,()->MinecraftModel.load(source,"demo:item/part"));}
    }
    @Test void ordinaryMinecraftParentsAndTexturesRemainExternal() throws Exception {
        Path pack=pack(); write(pack,"assets/demo/models/item/part.json","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/iron_ingot\"}}");
        var model=load(pack); assertEquals(1,model.files.size()); Path out=temp.resolve("vanilla"); model.write(out,"itemcontent:test");
        assertEquals("minecraft:item/generated",json(modelFile(out,model.exportedRoot("itemcontent:test"))).get("parent").getAsString());
    }
    @Test void importedModelDoesNotRequireAnInactivePngAndSwitchingModesKeepsIt() throws Exception {
        var project=project(load(pack())); project.items.get(0).customModel=true;project.items.get(0).texture="missing.png";
        project.export(temp.resolve("model"));
        project.items.get(0).importedAppearance=false; assertThrows(IOException.class,()->project.export(temp.resolve("png")));
    }
}
