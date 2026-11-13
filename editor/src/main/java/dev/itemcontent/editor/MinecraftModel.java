package dev.itemcontent.editor;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** A portable Java 1.20.1 model and its resource dependency closure. No executable mod code. */
final class MinecraftModel {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> VANILLA = vanillaResources();
    private static Set<String> vanillaResources() {
        try (var stream = MinecraftModel.class.getResourceAsStream("vanilla-resources-1.20.1.txt")) {
            if (stream == null) throw new IllegalStateException("Missing vanilla resource index");
            return Set.copyOf(new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
    static final int MAX_FILE = 16 * 1024 * 1024, MAX_TOTAL = 32 * 1024 * 1024, MAX_FILES = 512;
    String root;
    Map<String, String> files = new LinkedHashMap<>(); // pack-relative resource -> base64 bytes

    interface Source extends AutoCloseable {
        List<String> models() throws IOException;
        byte[] read(String path) throws IOException;
        @Override default void close() throws IOException {}
    }

    static Source open(Path selected) throws IOException {
        Path path = selected.toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            Path root = path.getFileName().toString().equals("assets") ? path.getParent() : path;
            return directory(root);
        }
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".zip") || path.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) return archive(path);
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            for (Path parent = path.getParent(); parent != null; parent = parent.getParent())
                if (parent.getFileName() != null && parent.getFileName().toString().equals("assets")) return directory(parent.getParent());
            return looseModel(path);
        }
        throw new IOException("Выберите модель Minecraft Java (.json), ресурс-пак (.zip) или папку с assets.");
    }

    static String selectedModel(Path path) {
        String text = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        int start = text.lastIndexOf("/assets/");
        return !text.endsWith(".json") ? null : start < 0 ? "imported:model" : modelId(text.substring(start + 1));
    }

    private static Source looseModel(Path file) throws IOException {
        Path root = file.getParent().toRealPath();
        return new Source() {
            public List<String> models() { return List.of("imported:model"); }
            public byte[] read(String name) throws IOException {
                checkPath(name); Path found = null;
                if (name.equals("assets/imported/models/model.json")) found = file;
                else {
                    String[] parts = name.split("/",4);
                    if (parts.length != 4) return null;
                    String relative = parts[3];
                    for (Path candidate : List.of(root.resolve(parts[2]).resolve(relative), root.resolve(relative), root.resolve(Path.of(relative).getFileName())))
                        if (Files.isRegularFile(candidate)) { found=candidate; break; }
                }
                if (found == null) return null;
                if (!found.toRealPath().startsWith(root)) throw new IOException("Ресурс за пределами папки модели: " + name);
                if (Files.size(found)>MAX_FILE) throw new IOException("Ресурс больше 16 МиБ: " + name);
                try (InputStream input=Files.newInputStream(found)) { return bounded(input); }
            }
        };
    }

    private static Source directory(Path folder) throws IOException {
        Path root = folder.toRealPath();
        if (!Files.isDirectory(root.resolve("assets"))) throw new IOException("В выбранной папке нет assets. Выберите корневую папку ресурс-пака.");
        return new Source() {
            public List<String> models() throws IOException {
                try (var paths = Files.walk(root.resolve("assets"), 16)) {
                    return paths.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\','/'))
                        .filter(MinecraftModel::isModelPath).limit(8193).map(MinecraftModel::modelId).sorted().toList();
                }
            }
            public byte[] read(String name) throws IOException {
                checkPath(name);
                Path file = root.resolve(name);
                if (!Files.exists(file)) return null;
                if (!file.toRealPath().startsWith(root)) throw new IOException("Ресурс находится за пределами выбранной папки: " + name);
                if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE) throw new IOException("Ресурс не является файлом или больше 16 МиБ: " + name);
                try (InputStream input = Files.newInputStream(file)) { return bounded(input); }
            }
        };
    }

    private static Source archive(Path path) throws IOException {
        ZipFile zip = new ZipFile(path.toFile());
        try {
            Map<String, ZipEntry> index = new LinkedHashMap<>();
            var entries = zip.entries(); int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > 65536) throw new IOException("Слишком много файлов в ресурс-паке.");
                if (entry.isDirectory()) continue;
                checkPath(entry.getName());
                if (index.putIfAbsent(entry.getName(), entry) != null) throw new IOException("Повтор файла в ZIP: " + entry.getName());
            }
            return new Source() {
                public List<String> models() { return index.keySet().stream().filter(MinecraftModel::isModelPath).limit(8193).map(MinecraftModel::modelId).sorted().toList(); }
                public byte[] read(String name) throws IOException {
                    ZipEntry entry = index.get(name); if (entry == null) return null;
                    if (entry.getSize() > MAX_FILE) throw new IOException("Ресурс больше 16 МиБ: " + name);
                    try (InputStream input = zip.getInputStream(entry)) { return bounded(input); }
                }
                public void close() throws IOException { zip.close(); }
            };
        } catch (IOException | RuntimeException failure) { zip.close(); throw failure; }
    }

    static MinecraftModel load(Source source, String model) throws IOException {
        MinecraftModel result = new MinecraftModel(); result.root = id(model);
        result.collect(source, result.root, new HashSet<>(), 0);
        if (!result.files.containsKey(modelPath(result.root))) throw new IOException("Не найдена выбранная модель: " + model);
        result.validate(); return result;
    }

    private void collect(Source source, String model, Set<String> visited, int depth) throws IOException {
        if (!visited.add(model)) return;
        if (depth > 64) throw new IOException("Слишком глубокая цепочка моделей (больше 64).");
        String path = modelPath(model); byte[] bytes = source.read(path);
        if (bytes == null) {
            if (VANILLA.contains(path) || Set.of("minecraft:builtin/generated", "minecraft:builtin/entity").contains(model)) return;
            throw new IOException("В ресурс-паке отсутствует модель: " + model);
        }
        put(path, bytes); JsonObject json = object(bytes, path);
        checkModel(json, path);
        if (json.has("parent")) collect(source, id(json.get("parent").getAsString()), visited, depth + 1);
        if (json.has("textures")) for (var entry : json.getAsJsonObject("textures").entrySet()) {
            String value = entry.getValue().getAsString(); if (value.startsWith("#")) continue;
            String texture = id(value), texturePath = texturePath(texture); byte[] png = source.read(texturePath);
            if (png == null) {
                if (VANILLA.contains(texturePath)) continue;
                throw new IOException("Не найдена текстура «" + texture + "», указанная в " + model + ". Положите PNG рядом с JSON или добавьте её в ресурс-пак.");
            }
            put(texturePath, png); byte[] metadata = source.read(texturePath + ".mcmeta");
            if (metadata != null) { object(metadata, texturePath + ".mcmeta"); put(texturePath + ".mcmeta", metadata); }
        }
        if (json.has("overrides")) for (var override : json.getAsJsonArray("overrides"))
            collect(source, id(override.getAsJsonObject().get("model").getAsString()), visited, depth + 1);
    }

    private void put(String path, byte[] bytes) throws IOException {
        if (files.containsKey(path)) return;
        if (files.size() >= MAX_FILES || decodedSize() + bytes.length > MAX_TOTAL) throw new IOException("Модель со связанными ресурсами больше 32 МиБ или 512 файлов.");
        if (path.endsWith(".png")) validatePng(bytes, path);
        files.put(path, Base64.getEncoder().encodeToString(bytes));
    }

    void validate() throws IOException {
        id(root);
        if (files == null || files.size() > MAX_FILES || decodedSize() > MAX_TOTAL) throw new IOException("Слишком большой набор ресурсов модели.");
        for (var entry : files.entrySet()) {
            checkPath(entry.getKey()); String path = entry.getKey();
            if (!isModelPath(path) && !path.matches("assets/[a-z0-9_.-]+/textures/[a-z0-9_./-]+\\.png(\\.mcmeta)?")) throw new IOException("Неизвестный ресурс: " + path);
            byte[] bytes = decode(entry.getValue()); if (bytes.length > MAX_FILE) throw new IOException("Ресурс больше 16 МиБ: " + path);
            if (path.endsWith(".png")) validatePng(bytes, path); else object(bytes, path);
        }
        // Reconstruct the closure: catches incomplete/tampered saved documents too.
        MinecraftModel check = new MinecraftModel(); check.root = root;
        check.collect(new Source() {
            public List<String> models() { return List.of(root); }
            public byte[] read(String path) throws IOException { String value = files.get(path); return value == null ? null : decode(value); }
        }, root, new HashSet<>(), 0);
        if (!files.containsKey(modelPath(root))) throw new IOException("Нет корневой модели: " + root);
        for (String path : files.keySet()) if (isModelPath(path)) parentCycle(modelId(path), new HashSet<>(), new HashSet<>());
    }

    private void parentCycle(String model, Set<String> active, Set<String> done) throws IOException {
        if (done.contains(model) || !files.containsKey(modelPath(model))) return;
        if (!active.add(model) || active.size() > 64) throw new IOException("Зацикленная или слишком глубокая цепочка parent: " + model);
        JsonObject json = object(decode(files.get(modelPath(model))), model);
        if (json.has("parent")) parentCycle(id(json.get("parent").getAsString()), active, done);
        active.remove(model); done.add(model);
    }

    void write(Path pack, String itemId) throws IOException {
        validate();
        for (var entry : files.entrySet()) {
            String path = entry.getKey(); byte[] bytes = decode(entry.getValue());
            if (isModelPath(path)) {
                JsonObject json = object(bytes, path);
                if (json.has("parent")) json.addProperty("parent", remap(json.get("parent").getAsString(), true, itemId));
                if (json.has("textures")) for (var texture : json.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString(); if (!value.startsWith("#")) texture.setValue(new JsonPrimitive(remap(value, false, itemId)));
                }
                if (json.has("overrides")) for (var override : json.getAsJsonArray("overrides")) {
                    JsonObject o = override.getAsJsonObject(); o.addProperty("model", remap(o.get("model").getAsString(), true, itemId));
                }
                bytes = JSON.toJson(json).getBytes(StandardCharsets.UTF_8);
            }
            String[] parts = path.split("/", 4);
            Path target = pack.resolve("assets/itemcontent/" + parts[2] + "/" + (parts[2].equals("textures") ? "item/" : "") + prefix(itemId) + parts[1] + "/" + parts[3]);
            Files.createDirectories(target.getParent()); Files.write(target, bytes);
        }
    }

    String exportedRoot(String itemId) throws IOException { return remap(root, true, itemId); }
    void writeRoot(Path pack, Path target, String itemId) throws IOException {
        String model = exportedRoot(itemId);
        Path source = pack.resolve("assets/" + model.replace(":", "/models/") + ".json");
        Files.createDirectories(target.getParent()); Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
    private String remap(String value, boolean model, String itemId) throws IOException {
        String key = id(value);
        return files.containsKey(model ? modelPath(key) : texturePath(key)) ? "itemcontent:" + (model ? "" : "item/") + prefix(itemId) + key.replace(':','/') : key;
    }
    // Fixed-depth hex name prevents item IDs such as a and a/b from overlapping imported files.
    private static String prefix(String itemId) { return "imported/" + HexFormat.of().formatHex(itemId.getBytes(StandardCharsets.UTF_8)) + "/"; }
    BufferedImage texturePreview() throws IOException {
        for (var entry : files.entrySet()) if (entry.getKey().endsWith(".png")) return ImageIO.read(new ByteArrayInputStream(decode(entry.getValue())));
        return null;
    }
    long decodedSize() throws IOException { long size = 0; for (String value : files.values()) { if (value == null || value.length() > (MAX_FILE + 2L) / 3 * 4) throw new IOException("Повреждённый ресурс модели."); size += (value.length() / 4L) * 3 - (value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0); } return size; }
    private static byte[] decode(String value) throws IOException { try { return Base64.getDecoder().decode(value); } catch (IllegalArgumentException | NullPointerException e) { throw new IOException("Повреждённый ресурс модели.", e); } }
    private static byte[] bounded(InputStream stream) throws IOException { byte[] bytes = stream.readNBytes(MAX_FILE + 1); if (bytes.length > MAX_FILE) throw new IOException("Ресурс больше 16 МиБ."); return bytes; }
    private static void validatePng(byte[] bytes, String name) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Не PNG: " + name);
            var reader = readers.next();
            try { reader.setInput(input); if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096 || reader.read(0) == null) throw new IOException("Повреждённая или слишком большая PNG: " + name); }
            finally { reader.dispose(); }
        }
    }
    private static JsonObject object(byte[] bytes, String path) throws IOException {
        try { JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)); if (!json.isJsonObject()) throw new IllegalArgumentException(); return json.getAsJsonObject(); }
        catch (RuntimeException | StackOverflowError failure) { throw new IOException("Некорректный JSON Minecraft: " + path, failure); }
    }
    private static void checkModel(JsonObject json, String path) throws IOException {
        if (json.has("loader") || json.has("minecraft:geometry") || json.has("format_version") || json.has("model") || (!json.has("parent") && !json.has("elements")))
            throw new IOException("Нужна модель Java Block/Item для Minecraft 1.20.1: " + path + ". Bedrock, GeckoLib и специальные загрузчики требуют отдельного расширения.");
        try {
            if (json.has("parent")) id(json.get("parent").getAsString());
            if (json.has("textures")) for (var entry : json.getAsJsonObject("textures").entrySet()) { String value = entry.getValue().getAsString(); if (!value.startsWith("#")) id(value); }
            if (json.has("elements")) json.getAsJsonArray("elements");
            if (json.has("overrides")) for (var override : json.getAsJsonArray("overrides")) { var o = override.getAsJsonObject(); id(o.get("model").getAsString()); o.getAsJsonObject("predicate"); }
        } catch (RuntimeException failure) { throw new IOException("Некорректная структура модели: " + path, failure); }
    }
    private static String id(String value) throws IOException {
        if (value == null) throw new IOException("Не указан ID ресурса.");
        if (!value.contains(":")) value = "minecraft:" + value;
        if (value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IOException("Некорректный адрес ресурса: " + value);
        checkPath(value.replace(':','/')); return value;
    }
    private static void checkPath(String value) throws IOException {
        if (value.isBlank() || value.startsWith("/") || value.contains("\\") || value.contains(":")) throw new IOException("Недопустимый путь ресурса: " + value);
        for (String part : value.split("/", -1)) if (part.equals("..") || part.equals(".") || part.isEmpty()) throw new IOException("Недопустимый путь ресурса: " + value);
    }
    private static boolean isModelPath(String path) { return path.matches("assets/[a-z0-9_.-]+/models/[a-z0-9_./-]+\\.json"); }
    private static String modelId(String path) { String[] p = path.split("/", 4); return p[1] + ":" + p[3].substring(0, p[3].length() - 5); }
    private static String modelPath(String id) { return "assets/" + id.replace(":", "/models/") + ".json"; }
    private static String texturePath(String id) { return "assets/" + id.replace(":", "/textures/") + ".png"; }
}
