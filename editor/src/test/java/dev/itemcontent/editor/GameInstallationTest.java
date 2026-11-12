package dev.itemcontent.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class GameInstallationTest {
    @TempDir Path temp;
    private ItemProject project() {
        var result=new ItemProject(); result.items.add(new ItemProject.Entry()); return result;
    }
    private Path mod(String version) throws IOException {
        Path path=temp.resolve("item-content-fabric-"+version+".jar");
        try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json")); zip.write(("{\"id\":\"itemcontent\",\"version\":\""+version+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        } return path;
    }
    @Test void installsContentAndCreativeGroupsWithoutTouchingCatalogOrOtherMods() throws Exception {
        Path game=temp.resolve("game"); Files.createDirectories(game.resolve("mods")); Files.writeString(game.resolve("mods/other.jar"),"other mod");
        Files.createDirectories(game.resolve("config/item-content")); Files.writeString(game.resolve("config/item-content/catalog.json"),"catalog");
        var project=project(); project.groups.add(new dev.itemcontent.definition.GameCatalog.Group("itemcontent:bronze","Бронза")); project.items.get(0).group="itemcontent:bronze";
        var result=GameInstallation.install(project,game,null,mod("1.2.0"));
        assertTrue(Files.exists(game.resolve("mods/item-content-fabric-1.2.0.jar")));
        assertTrue(Files.readString(game.resolve("config/item-content/items.json")).contains("itemcontent:bronze"));
        assertTrue(Files.exists(game.resolve("resourcepacks/item-content/pack.mcmeta")));
        assertEquals("catalog",Files.readString(game.resolve("config/item-content/catalog.json")));
        assertEquals("other mod",Files.readString(game.resolve("mods/other.jar")));
        assertTrue(Files.exists(result.backup().resolve("paths.txt")));
    }
    @Test void updatePreservesOldPackConfigAndModInBackup() throws Exception {
        Path game=temp.resolve("game"); Files.createDirectories(game.resolve("config/item-content"));
        Files.writeString(game.resolve("config/item-content/items.json"),"previous config");
        Files.createDirectories(game.resolve("resourcepacks/item-content")); Files.writeString(game.resolve("resourcepacks/item-content/user-note.txt"),"previous pack");
        Files.createDirectories(game.resolve("mods")); Files.copy(mod("old"),game.resolve("mods/item-content-fabric-old.jar"));
        var result=GameInstallation.install(project(),game,null,mod("1.2.0"));
        assertEquals("previous config",Files.readString(result.backup().resolve("0")));
        assertEquals("previous pack",Files.readString(result.backup().resolve("1/user-note.txt")));
        assertTrue(Files.exists(result.backup().resolve("2")));
        assertFalse(Files.exists(game.resolve("mods/item-content-fabric-old.jar")));
        assertTrue(Files.exists(game.resolve("mods/item-content-fabric-1.2.0.jar")));
    }
    @Test void failedWriteRestoresPreviouslyInstalledConfigAndPack() throws Exception {
        Path game=temp.resolve("game"); Files.createDirectories(game.resolve("config/item-content"));
        Files.writeString(game.resolve("config/item-content/items.json"),"original config");
        Files.createDirectories(game.resolve("resourcepacks/item-content")); Files.writeString(game.resolve("resourcepacks/item-content/original.txt"),"original pack");
        Files.writeString(game.resolve("mods"),"not a directory");
        assertThrows(IOException.class,() -> GameInstallation.install(project(),game,null,mod("1.2.0")));
        assertEquals("original config",Files.readString(game.resolve("config/item-content/items.json")));
        assertEquals("original pack",Files.readString(game.resolve("resourcepacks/item-content/original.txt")));
        assertEquals("not a directory",Files.readString(game.resolve("mods")));
    }
    @Test void blocksRequireAWorldAndPreserveItsOtherData() throws Exception {
        var project=project(); var item=project.items.get(0); item.type="itemcontent:block"; item.appearance="minecraft:stone";
        item.properties.addProperty("tool","pickaxe");
        Path game=temp.resolve("game"); Path jar=mod("1.2.0");
        assertThrows(IOException.class,() -> GameInstallation.install(project,game,null,jar));
        assertFalse(Files.exists(game.resolve("config/item-content/items.json")));
        Path world=temp.resolve("separate-world"); Files.createDirectories(world); Files.writeString(world.resolve("level.dat"),"world state");
        GameInstallation.install(project,game,world,jar);
        assertTrue(Files.exists(world.resolve("datapacks/item-content/pack.mcmeta")));
        assertEquals("world state",Files.readString(world.resolve("level.dat")));
    }
    @Test void missingOwnTextureIsReportedBeforeWritingIntoTheGame() throws Exception {
        var project=project(); project.items.get(0).customModel=true; project.items.get(0).name="Бронзовый меч";
        Path game=temp.resolve("not-created-game");
        IOException error=assertThrows(IOException.class,() -> GameInstallation.install(project,game,null,mod("1.2.0")));
        assertTrue(error.getMessage().contains("Бронзовый меч"));
        assertTrue(error.getMessage().contains("PNG"));
        assertFalse(Files.exists(game));
    }
    @Test void choosingResourcepacksAsTheGameRootIsRejected() throws Exception {
        Path wrong=temp.resolve("resourcepacks");
        assertThrows(IOException.class,() -> GameInstallation.install(project(),wrong,null,mod("1.2.0")));
        assertFalse(Files.exists(wrong));
    }
}
