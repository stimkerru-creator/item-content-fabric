package dev.itemcontent.editor;

import java.io.IOException;
import java.nio.file.*;

/** Reject redirected write destinations, including Windows directory junctions. */
final class PathSafety {
    private PathSafety() {}
    static void check(Path target) throws IOException {
        for (Path path = target.toAbsolutePath().normalize(); path != null; path = path.getParent()) {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(path)
                    || !path.toRealPath().equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("Путь проходит через ссылку или Windows junction: " + path
                        + ". Выберите обычную папку игры или экспорта.");
        }
    }
}
