package dev.itemcontent.editor;

import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Item authoring with no recipes and no Minecraft runtime dependency. */
public final class ItemProject {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_BYTES = 4L * 1024 * 1024;
    public final List<Entry> items = new ArrayList<>();

    public static final class Entry {
        public String id = "itemcontent:new_item", name = "Новый предмет";
        public String appearance = "minecraft:iron_ingot", texture = "";
        public boolean customModel, fireproof;
        public int maxCount = 64;
        public List<Field> fields = new ArrayList<>();
        @Override public String toString() { return name + "  (" + id + ")"; }
    }
    public static final class Field {
        public String id = "itemcontent:mass", value = "0", min = "-21474836.48", max = "21474836.47";
    }

    public void validate() {
        if (items.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы один предмет.");
        if (items.size() > 4096) throw new IllegalArgumentException("Допускается до 4096 типов предметов.");
        Set<String> ids = new HashSet<>();
        for (Entry item : items) {
            if (item == null) throw new IllegalArgumentException("Пустое описание предмета.");
            identifier(item.id);
            if (!item.id.startsWith("itemcontent:")) throw new IllegalArgumentException("ID нового предмета должен начинаться с itemcontent:");
            if (!ids.add(item.id)) throw new IllegalArgumentException("Повторяется ID предмета: " + item.id);
            if (item.name == null || item.name.isBlank() || item.name.length() > 128)
                throw new IllegalArgumentException("Название предмета должно содержать от 1 до 128 символов.");
            identifier(item.appearance);
            if (item.maxCount < 1 || item.maxCount > 99) throw new IllegalArgumentException("Размер стака: от 1 до 99.");
            if (item.fields == null || item.fields.size() > 4096) throw new IllegalArgumentException("Допускается до 4096 параметров предмета.");
            Set<String> fieldIds = new HashSet<>();
            for (Field field : item.fields) {
                if (field == null) throw new IllegalArgumentException("Пустой параметр предмета.");
                identifier(field.id);
                if (!fieldIds.add(field.id)) throw new IllegalArgumentException("Повторяется ID параметра: " + field.id);
                BigDecimal value = decimal(field.value), min = decimal(field.min), max = decimal(field.max);
                if (min.compareTo(max) > 0 || value.compareTo(min) < 0 || value.compareTo(max) > 0)
                    throw new IllegalArgumentException("Начальное значение вне диапазона: " + field.id);
            }
        }
    }
    private static void identifier(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Некорректный ID: " + id + ". Пример: itemcontent:steel_plate");
        String path = id.substring(id.indexOf(':') + 1);
        if (Arrays.stream(path.split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")))
            throw new IllegalArgumentException("В ID недопустимы пустые части пути, . и ..: " + id);
    }
    private static BigDecimal decimal(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim().replace(',', '.'));
            value.movePointRight(2).intValueExact();
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Нужно число с точностью до сотых в диапазоне −21474836,48…21474836,47: " + text);
        }
    }
    public void save(Path file) throws IOException {
        validate();
        JsonObject root = new JsonObject(); root.addProperty("editorVersion", 1);
        root.add("items", JSON.toJsonTree(items));
        String text = JSON.toJson(root);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Список предметов больше 4 МиБ.");
        writeAtomic(file.toAbsolutePath(), text);
    }
    public static ItemProject load(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("Список предметов больше 4 МиБ.");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("editorVersion").getAsBigDecimal().intValueExact() != 1) throw new IOException("Эта версия списка предметов не поддерживается.");
            Entry[] entries = JSON.fromJson(root.get("items"), Entry[].class);
            ItemProject result = new ItemProject(); result.items.addAll(Arrays.asList(entries)); result.validate();
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
            JsonArray fields = new JsonArray();
            for (Field field : item.fields) {
                JsonObject f = new JsonObject(); f.addProperty("id", field.id);
                f.addProperty("default", decimal(field.value)); f.addProperty("min", decimal(field.min)); f.addProperty("max", decimal(field.max));
                fields.add(f);
            }
            object.add("fields", fields); definitions.add(object);
        }
        JsonObject config = new JsonObject(); config.addProperty("version", 1); config.add("items", definitions);
        String configJson = JSON.toJson(config);
        if (configJson.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Каталог предметов превышает 4 МиБ.");
        Files.createDirectories(path.getParent());
        Path staged = Files.createTempDirectory(path.getParent(), "item-content-export-");
        try {
            Path configuration = staged.resolve("config/item-content/items.json");
            Files.createDirectories(configuration.getParent()); Files.writeString(configuration, configJson, StandardCharsets.UTF_8);
            Path pack = staged.resolve("resourcepacks/item-content"); Files.createDirectories(pack);
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Item Content\"}}", StandardCharsets.UTF_8);
            for (Entry item : items) {
                if (!item.customModel) continue;
                Path source = Path.of(item.texture == null ? "" : item.texture); validatePng(source);
                String idPath = item.id.substring(item.id.indexOf(':') + 1);
                Path texture = pack.resolve("assets/itemcontent/textures/item/" + idPath + ".png");
                Path model = pack.resolve("assets/itemcontent/models/item/" + idPath + ".json");
                Files.createDirectories(texture.getParent()); Files.createDirectories(model.getParent()); Files.copy(source, texture);
                JsonObject modelJson = new JsonObject(); modelJson.addProperty("parent", "minecraft:item/generated");
                JsonObject textures = new JsonObject(); textures.addProperty("layer0", "itemcontent:item/" + idPath); modelJson.add("textures", textures);
                Files.writeString(model, JSON.toJson(modelJson), StandardCharsets.UTF_8);
            }
            Files.writeString(staged.resolve("INSTALL.txt"),
                    "1. Copy config/item-content into the server and each client's config folder.\n"
                    + "2. Copy resourcepacks/item-content into each client's resourcepacks and enable it.\n"
                    + "3. Install item-content-fabric and Fabric API for Minecraft 1.20.1.\n"
                    + "4. Restart client and server. Give: /give @s itemcontent:<item_id>\n"
                    + "The item-parameters mod is optional: it supplies individual stats and production recipes.\n", StandardCharsets.UTF_8);
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
