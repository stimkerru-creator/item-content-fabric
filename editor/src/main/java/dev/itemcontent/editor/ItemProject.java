package dev.itemcontent.editor;

import com.google.gson.*;
import dev.itemcontent.definition.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Item authoring with no recipes and no Minecraft runtime dependency. */
public final class ItemProject {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_BYTES = 4L * 1024 * 1024;
    public final List<Entry> items = new ArrayList<>();
    public final List<TypeDefinition> extensionTypes = new ArrayList<>();
    public final List<GameCatalog.Group> groups = new ArrayList<>();
    public Map<String, TypeDefinition> types() {
        Map<String, TypeDefinition> result = BuiltinTypes.index();
        for (var type : extensionTypes) if (result.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Повтор типа: " + type.id());
        return result;
    }
    public TypeDefinition type(Entry item) {
        TypeDefinition result = types().get(item.type);
        if (result == null) throw new IllegalArgumentException("Нет описания типа " + item.type + ". Откройте каталог игры с нужным расширением.");
        return result;
    }
    public void importTypes(GameCatalog catalog) {
        for (var type : catalog.types()) if (!type.id().startsWith("itemcontent:") && extensionTypes.stream().noneMatch(existing -> existing.id().equals(type.id()))) extensionTypes.add(type);
    }
    private String legacyDocument;
    private Path legacySource, legacyBackup;
    public boolean hasLegacyData() { return legacyDocument != null; }
    public Path legacyBackup() { return legacyBackup; }

    public static final class Entry {
        public String id = "itemcontent:new_item", name = "Новый предмет";
        public String appearance = "minecraft:iron_ingot", texture = "";
        public boolean customModel, fireproof;
        public int maxCount = 64;
        public String type = "itemcontent:item", group = "minecraft:ingredients";
        public boolean existing, categoryLocked;
        public JsonObject properties = new JsonObject(), referenceProperties = new JsonObject();
        public Map<String, String> textures = new LinkedHashMap<>();
        public Entry copy() {
            Entry e = new Entry(); e.id = id; e.name = name; e.appearance = appearance; e.texture = texture;
            e.maxCount = maxCount; e.customModel = customModel; e.fireproof = fireproof;
            e.type = type; e.group = group; e.existing = existing; e.categoryLocked = categoryLocked; e.properties = properties.deepCopy(); e.referenceProperties = referenceProperties.deepCopy(); e.textures = new LinkedHashMap<>(textures); return e;
        }
        @Override public String toString() { return name + "  (" + id + ")"; }
    }
    public void validate() {
        if (items.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы один предмет.");
        if (items.size() > 4096) throw new IllegalArgumentException("Допускается до 4096 типов предметов.");
        types();
        Set<String> groupIds = new HashSet<>();
        if (groups.size() > 256) throw new IllegalArgumentException("Не более 256 своих вкладок");
        for (var group : groups) {
            identifier(group.id());
            if (!group.id().startsWith("itemcontent:") || group.id().equals("itemcontent:keep") || !groupIds.add(group.id()) || group.name().isBlank() || group.name().length() > 128)
                throw new IllegalArgumentException("Некорректная вкладка: " + group.id());
        }
        Map<String,String> modelAliases = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (Entry item : items) {
            if (item == null) throw new IllegalArgumentException("Пустое описание предмета.");
            identifier(item.id);
            if (!item.existing && !item.id.startsWith("itemcontent:")) throw new IllegalArgumentException("ID нового предмета должен начинаться с itemcontent:");
            if (!ids.add(item.id)) throw new IllegalArgumentException("Повторяется ID предмета: " + item.id);
            if (item.name == null || item.name.isBlank() || item.name.length() > 128)
                throw new IllegalArgumentException("Название предмета должно содержать от 1 до 128 символов.");
            identifier(item.appearance); identifier(item.group);
            if (item.categoryLocked && !item.group.equals("itemcontent:keep")) throw new IllegalArgumentException("Для предмета с NBT-вариантами сохраняются исходные вкладки");
            if (item.id.equals("minecraft:air")) throw new IllegalArgumentException("Нельзя редактировать воздух");
            var definition = type(item);
            var values = definition.validate(item.properties, item.existing);
            if (!item.customModel && !definition.model().equals("chest") && !(item.existing && item.id.equals(item.appearance))) modelAliases.put(item.id,item.appearance);
            if (values.has("durability") && values.get("durability").getAsInt() > 0 && item.maxCount != 1)
                throw new IllegalArgumentException("Для предмета с прочностью размер стака должен быть 1: " + item.name);
            if (item.textures == null || item.textures.size() > 16 || item.textures.keySet().stream().anyMatch(key -> !Set.of("top", "bottom", "side", "front", "back", "armor_layer_1", "armor_layer_2", "pulling_0", "pulling_1", "pulling_2", "arrow", "firework").contains(key)))
                throw new IllegalArgumentException("Неизвестный слот текстуры");
            if (item.maxCount < 1 || item.maxCount > 99) throw new IllegalArgumentException("Размер стака: от 1 до 99.");

        }
        ModelReferences.validate(modelAliases);
    }
    private static void identifier(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Некорректный ID: " + id + ". Пример: itemcontent:steel_plate");
        String path = id.substring(id.indexOf(':') + 1);
        if (Arrays.stream(path.split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")))
            throw new IllegalArgumentException("В ID недопустимы пустые части пути, . и ..: " + id);
    }
    public void save(Path file) throws IOException {
        validate();
        JsonObject root = new JsonObject(); root.addProperty("editorVersion", 2);
        root.add("items", JSON.toJsonTree(items));
        root.add("types", JSON.toJsonTree(extensionTypes)); root.add("groups", JSON.toJsonTree(groups));
        String text = JSON.toJson(root);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Список предметов больше 4 МиБ.");
        if (legacyDocument != null) {
            Path backup = Files.createTempFile(legacySource.getParent(), legacySource.getFileName() + ".before-item-only-", ".bak");
            Files.writeString(backup, legacyDocument, StandardCharsets.UTF_8);
            legacyBackup = backup;
            legacyDocument = null;
        }
        writeAtomic(file.toAbsolutePath(), text);
    }
    public static ItemProject load(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("Список предметов больше 4 МиБ.");
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(source).getAsJsonObject();
            int version = root.get("editorVersion").getAsBigDecimal().intValueExact();
            if (version != 1 && version != 2) throw new IOException("Эта версия списка предметов не поддерживается.");
            Entry[] entries = JSON.fromJson(root.get("items"), Entry[].class);
            ItemProject result = new ItemProject(); result.items.addAll(Arrays.asList(entries));
            if (root.has("types")) result.extensionTypes.addAll(Arrays.asList(JSON.fromJson(root.get("types"), TypeDefinition[].class)));
            if (root.has("groups")) result.groups.addAll(Arrays.asList(JSON.fromJson(root.get("groups"), GameCatalog.Group[].class)));
            result.validate();
            for (JsonElement element : root.getAsJsonArray("items")) {
                JsonElement old = element.getAsJsonObject().get("fields");
                if (old != null && (!old.isJsonArray() || !old.getAsJsonArray().isEmpty())) {
                    result.legacyDocument = source;
                    result.legacySource = file.toAbsolutePath();
                    break;
                }
            }
            return result;
        } catch (RuntimeException e) { throw new IOException("Не удалось открыть список предметов: " + e.getMessage(), e); }
    }
    /** Exports to a new/empty folder without overwriting user files. */
    public void export(Path target) throws IOException {
        validate();
        Path path = target.toAbsolutePath().normalize();
        for (Path p = path; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Выберите папку без символических ссылок.");
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) throw new IOException("Путь экспорта занят файлом.");
            try (var files = Files.list(path)) {
                if (files.findAny().isPresent()) throw new IOException("Для экспорта нужна новая или пустая папка.");
            }
        }
        JsonArray definitions = new JsonArray();
        for (Entry item : items) {
            JsonObject object = new JsonObject();
            object.addProperty("id", item.id); object.addProperty("name", item.name);
            object.addProperty("appearance", item.appearance); object.addProperty("custom_model", item.customModel);
            object.addProperty("max_count", item.maxCount); object.addProperty("fireproof", item.fireproof);
            object.addProperty("type", item.type); object.addProperty("group", item.group); object.addProperty("existing", item.existing);
            object.add("properties", type(item).validate(item.properties, item.existing));
            definitions.add(object);
        }
        JsonObject config = new JsonObject(); config.addProperty("version", 2); config.add("items", definitions);
        JsonArray groupDefinitions = new JsonArray();
        for (var group : groups) { var value = new JsonObject(); value.addProperty("id", group.id()); value.addProperty("name", group.name()); value.addProperty("icon", "minecraft:brick"); groupDefinitions.add(value); }
        config.add("groups", groupDefinitions);
        String configJson = JSON.toJson(config);
        if (configJson.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Каталог предметов превышает 4 МиБ.");
        Files.createDirectories(path.getParent());
        Path staged = Files.createTempDirectory(path.getParent(), "item-content-export-");
        try {
            Path configuration = staged.resolve("config/item-content/items.json");
            Files.createDirectories(configuration.getParent()); Files.writeString(configuration, configJson, StandardCharsets.UTF_8);
            ProjectResources.write(this, staged);
            Files.writeString(staged.resolve("INSTALL.txt"),
                    "1. Copy config/item-content into the server and each client's config folder.\n"
                    + "2. Copy resourcepacks/item-content into each client's resourcepacks and enable it.\n"
                    + "3. Install item-content-fabric and Fabric API for Minecraft 1.20.1.\n"
                    + "4. For blocks, copy datapacks/item-content into each world's datapacks and reload/restart.\n"
                    + "5. Restart client and server. Give: /give @s itemcontent:<item_id>\n"
                    + "Item Content works independently. No other content or parameter mod is required.\n", StandardCharsets.UTF_8);
            if (Files.exists(path)) Files.delete(path);
            move(staged, path, false);
        } finally {
            if (Files.exists(staged)) try (var files = Files.walk(staged)) {
                for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    static void validatePng(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 16L * 1024 * 1024) throw new IOException("PNG не найдена или больше 16 МиБ: " + file);
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("Не удалось прочитать PNG: " + file);
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Файл не является изображением PNG: " + file);
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Выберите PNG.");
                reader.setInput(input);
                if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) throw new IOException("PNG должна быть не больше 4096×4096.");
                if (reader.read(0) == null) throw new IOException("Повреждённая PNG.");
            } finally { reader.dispose(); }
        }
    }
    private static void writeAtomic(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent()); Path temp = Files.createTempFile(file.getParent(), "item-content-project-", ".tmp");
        try { Files.writeString(temp, text, StandardCharsets.UTF_8); move(temp, file, true); }
        finally { Files.deleteIfExists(temp); }
    }
    private static void move(Path from, Path to, boolean replace) throws IOException {
        try {
            if (replace) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            if (replace) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); else Files.move(from, to);
        }
    }
}
