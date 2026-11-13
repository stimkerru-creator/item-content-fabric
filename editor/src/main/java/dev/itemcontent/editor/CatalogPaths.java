package dev.itemcontent.editor;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Resolves only known catalog locations; never scans a user's folders recursively. */
final class CatalogPaths {
    private CatalogPaths() {}
    static Path locate(Path selected) throws IOException {
        Path folder=selected.toAbsolutePath().normalize();
        for (String relative:List.of("config/item-content/catalog.json", ".minecraft/config/item-content/catalog.json",
                "item-content/catalog.json", "catalog.json")) {
            Path candidate=folder.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("В выбранной папке пока нет каталога предметов.\n"
                +"1. Откройте эту сборку Minecraft с модом Item Content и войдите в мир.\n"
                +"2. Дождитесь создания каталога или введите в чате /itemcontent catalog.\n"
                +"3. Вернитесь в редактор и выберите папку сборки ещё раз.\n\n"
                +"Искомый файл: "+folder.resolve("config/item-content/catalog.json"));
    }
}
