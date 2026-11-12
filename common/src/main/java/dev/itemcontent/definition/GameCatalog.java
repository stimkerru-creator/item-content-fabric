package dev.itemcontent.definition;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A data snapshot from the running game. Reading it never loads mod classes in the editor. */
public record GameCatalog(int version, List<TypeDefinition> types, List<Group> groups, List<Entry> items) {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public record Group(String id, String name) {
        public Group { Identifiers.check(id); if (name == null || name.length() > 256) throw new IllegalArgumentException("Invalid group name"); }
        @Override public String toString() { return name; }
    }
    public record Entry(String id, String name, String type, String javaClass, int maxCount, int durability,
                        boolean fireproof, List<String> groups, JsonObject properties, String icon, boolean variants) {
        public Entry {
            Identifiers.check(id); Identifiers.check(type);
            if (name == null || name.length() > 512 || javaClass == null || javaClass.length() > 512 || icon == null || icon.length() > 512)
                throw new IllegalArgumentException("Invalid catalog item");
            if (maxCount < 1 || durability < 0) throw new IllegalArgumentException("Invalid catalog item settings");
            groups = List.copyOf(groups); groups.forEach(Identifiers::check);
            properties = Objects.requireNonNull(properties).deepCopy();
        }
        @Override public String toString() { return name + "  (" + id + ")"; }
    }
    public GameCatalog {
        if (version != 1) throw new IllegalArgumentException("Unsupported catalog version");
        types = List.copyOf(types); groups = List.copyOf(groups); items = List.copyOf(items);
        if (items.size() > 65536 || types.size() > 2048 || groups.size() > 512) throw new IllegalArgumentException("Catalog limits exceeded");
        Set<String> ids = new HashSet<>(); for (var item : items) if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate catalog item: " + item.id());
        ids.clear(); for (var type : types) if (!ids.add(type.id())) throw new IllegalArgumentException("Duplicate catalog type");
        ids.clear(); for (var group : groups) if (!ids.add(group.id())) throw new IllegalArgumentException("Duplicate catalog group");
    }
    public static GameCatalog load(Path file) throws IOException {
        if (Files.size(file) > 32L * 1024 * 1024) throw new IOException("Каталог игры больше 32 МиБ");
        try { return Objects.requireNonNull(JSON.fromJson(Files.readString(file), GameCatalog.class), "Empty game catalog"); }
        catch (RuntimeException failure) { throw new IOException("Не удалось прочитать каталог игры: " + failure.getMessage(), failure); }
    }
    public void save(Path file) throws IOException {
        String data = JSON.toJson(this);
        if (data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32L * 1024 * 1024) throw new IOException("Каталог игры больше 32 МиБ");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "item-content-catalog-", ".tmp");
        try {
            Files.writeString(temporary, data);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
