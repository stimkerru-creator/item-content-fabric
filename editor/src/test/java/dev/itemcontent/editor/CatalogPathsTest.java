package dev.itemcontent.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogPathsTest {
    @TempDir Path temp;
    private Path catalog(Path root) throws IOException {
        Path file=root.resolve("config/item-content/catalog.json"); Files.createDirectories(file.getParent()); Files.writeString(file,"{}"); return file;
    }
    @Test void locatesTheCatalogFromAGameFolderWithoutScanning() throws Exception {
        Path game=temp.resolve("game"); Path expected=catalog(game);
        assertEquals(expected,CatalogPaths.locate(game));
    }
    @Test void acceptsALauncherFolderWithAMinecraftChild() throws Exception {
        Path launcher=temp.resolve("launcher"); Path expected=catalog(launcher.resolve(".minecraft"));
        assertEquals(expected,CatalogPaths.locate(launcher));
    }
    @Test void acceptsTheConfigFolderAndTheCatalogsOwnFolder() throws Exception {
        Path expected=catalog(temp);
        assertEquals(expected,CatalogPaths.locate(temp.resolve("config")));
        assertEquals(expected,CatalogPaths.locate(expected.getParent()));
    }
    @Test void missingCatalogExplainsTheGameStepsAndExpectedPath() {
        IOException error=assertThrows(IOException.class,()->CatalogPaths.locate(temp));
        assertTrue(error.getMessage().contains("войдите в мир"));
        assertTrue(error.getMessage().contains("/itemcontent catalog"));
        assertTrue(error.getMessage().contains(temp.resolve("config/item-content/catalog.json").toString()));
    }
}
