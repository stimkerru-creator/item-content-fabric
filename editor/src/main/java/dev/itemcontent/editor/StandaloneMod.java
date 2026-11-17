package dev.itemcontent.editor;

import com.google.gson.*;
import dev.itemcontent.definition.BuiltinTypes;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipInputStream;

/** Builds an installable Fabric mod without a compiler, external service, editor or source catalog. */
final class StandaloneMod {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final String RUNTIME_ID = "itemcontent_generated_runtime";
    record Settings(String id, String name, String version, Set<String> requiredMods) {
        Settings {
            if (id == null || !id.matches("[a-z][a-z0-9_]{1,47}") || Set.of("minecraft", "java", "fabric", "fabricloader", "fabric_api", "itemcontent", RUNTIME_ID).contains(id))
                throw new IllegalArgumentException("ID мода: 2–48 строчных латинских букв, цифр и _. Например bronze_parts. Имя должно быть своим, не minecraft или itemcontent.");
            if (name == null || name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Введите название мода (до 128 символов).");
            if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?")) throw new IllegalArgumentException("Версия мода, например 1.0.0.");
            requiredMods = Set.copyOf(requiredMods);
            for (String dependency : requiredMods) if (!dependency.matches("[a-z][a-z0-9_-]{1,63}") || dependency.equals(id)
                    || Set.of("minecraft", "java", "fabricloader", "fabric-api", "itemcontent", RUNTIME_ID).contains(dependency))
                throw new IllegalArgumentException("Некорректная дополнительная зависимость: " + dependency);
        }
    }
    record Result(Path jar, int items, Map<String, String> ids) {}
    private StandaloneMod() {}
    static ItemProject selected(ItemProject source, Set<String> selection) {
        var project = new ItemProject();
        for (var item : source.items) if (selection.contains(item.id)) {
            if (!BuiltinTypes.index().containsKey(item.type)) throw new IllegalArgumentException("Тип «" + item.type + "» предоставлен сторонним расширением. Его код нельзя автоматически переносить в самостоятельный мод. Выберите встроенный тип или подготовьте интеграцию для готового мода.");
            project.items.add(item.copy());
        }
        if (project.items.size() != selection.size()) throw new IllegalArgumentException("Выбран неизвестный или повторяющийся ID предмета.");
        for (var group : source.groups) if (project.items.stream().anyMatch(item -> item.group.equals(group.id()))) project.groups.add(group);
        Set<String> all = new HashSet<>(); for (var item : source.items) if (!item.existing) all.add(item.id);
        for (var item : project.items) {
            if (!item.customModel && !item.importedAppearance) requireSelected(item.appearance, item, all, selection);
            var properties = project.type(item).validate(item.properties, item.existing);
            if (properties.has("repair_item")) requireSelected(properties.get("repair_item").getAsString(), item, all, selection);
        }
        project.validate(); return project;
    }
    private static void requireSelected(String reference, ItemProject.Entry item, Set<String> all, Set<String> selection) {
        if (all.contains(reference) && !selection.contains(reference)) throw new IllegalArgumentException("«" + item.name + "» использует «" + reference + "». Отметьте этот предмет тоже или измените ссылку. Остальной каталог автоматически не добавляется.");
    }
    static Result build(ItemProject source, Set<String> selection, Settings settings, Path target) throws IOException {
        ItemProject project = selected(source, selection); project.validateForExport();
        Path destination = target.toAbsolutePath().normalize(); PathSafety.check(destination);
        if (!destination.getFileName().toString().endsWith(".jar")) throw new IOException("Для готового мода выберите файл .jar.");
        if (Files.exists(destination)) throw new IOException("Файл уже существует. Выберите новое имя, чтобы не затереть готовый мод: " + destination);
        Files.createDirectories(destination.getParent());
        Map<String, String> ids = new LinkedHashMap<>();
        for (var item : project.items) if (!item.existing) {
            String id = settings.id() + item.id.substring(item.id.indexOf(':'));
            dev.itemcontent.definition.Identifiers.check(id); ids.put(item.id, id);
        }
        for (var group : project.groups) {
            String id = settings.id() + group.id().substring(group.id().indexOf(':'));
            dev.itemcontent.definition.Identifiers.check(id); ids.put(group.id(), id);
        }
        Path stage = Files.createTempDirectory(destination.getParent(), ".itemcontent-mod-");
        try {
            Path exported = stage.resolve("content"); project.export(exported);
            Path result = stage.resolve("result.jar");
            byte[] template = runtime();
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(result))) {
                Set<String> written = new HashSet<>();
                put(jar, written, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                put(jar, written, "META-INF/jars/itemcontent-runtime.jar", template);
                JsonObject metadata = new JsonObject(); metadata.addProperty("schemaVersion", 1);
                metadata.addProperty("id", settings.id()); metadata.addProperty("name", settings.name()); metadata.addProperty("version", settings.version());
                metadata.addProperty("environment", "*"); metadata.addProperty("description", "Items and blocks: " + settings.name());
                JsonObject custom = new JsonObject(); custom.addProperty("itemcontent:standalone", true); metadata.add("custom", custom);
                JsonObject dependencies = new JsonObject(); dependencies.addProperty("fabricloader", ">=0.15.11"); dependencies.addProperty("minecraft", "1.20.1");
                dependencies.addProperty("java", ">=17"); dependencies.addProperty("fabric-api", "*");
                for (String mod : settings.requiredMods()) dependencies.addProperty(mod, "*");
                metadata.add("depends", dependencies);
                JsonArray jars = new JsonArray(); JsonObject nested = new JsonObject(); nested.addProperty("file", "META-INF/jars/itemcontent-runtime.jar"); jars.add(nested); metadata.add("jars", jars);
                put(jar, written, "fabric.mod.json", JSON.toJson(metadata).getBytes(StandardCharsets.UTF_8));
                JsonObject config = JsonParser.parseString(Files.readString(exported.resolve("config/item-content/items.json"))).getAsJsonObject();
                for (var value : config.getAsJsonArray("items")) {
                    JsonObject item = value.getAsJsonObject();
                    for (String key : List.of("id", "appearance", "group")) item.addProperty(key, ids.getOrDefault(item.get(key).getAsString(), item.get(key).getAsString()));
                    rewrite(item.get("properties"), ids, null);
                }
                for (var value : config.getAsJsonArray("groups")) rewrite(value, ids, null);
                put(jar, written, "itemcontent/content.json", JSON.toJson(config).getBytes(StandardCharsets.UTF_8));
                JsonObject language = new JsonObject();
                for (var item : project.items) {
                    String key = ids.getOrDefault(item.id, item.id).replace(':', '.').replace('/', '.');
                    language.addProperty("item." + key, item.name); language.addProperty("block." + key, item.name);
                }
                for (String locale : List.of("en_us", "ru_ru")) put(jar, written, "assets/" + settings.id() + "/lang/" + locale + ".json", JSON.toJson(language).getBytes(StandardCharsets.UTF_8));
                for (String folder : List.of("resourcepacks/item-content/assets", "datapacks/item-content/data")) {
                    Path root = exported.resolve(folder); if (!Files.isDirectory(root)) continue;
                    try (var files = Files.walk(root)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            String name = (folder.contains("resourcepacks") ? "assets/" : "data/") + root.relativize(file).toString().replace('\\', '/');
                            if (name.contains("/lang/")) continue;
                            boolean ownAssets = name.startsWith("assets/itemcontent/");
                            boolean originalOverride = project.items.stream().anyMatch(item -> item.existing && name.equals("assets/" + item.id.replace(":", "/models/item/") + ".json"));
                            String outputName = ownAssets && !originalOverride ? name.replaceFirst("assets/itemcontent/", "assets/" + settings.id() + "/") : name;
                            byte[] bytes = Files.readAllBytes(file);
                            if (name.endsWith(".json") || name.endsWith(".mcmeta")) {
                                JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                                rewrite(json, ids, settings.id()); bytes = JSON.toJson(json).getBytes(StandardCharsets.UTF_8);
                            }
                            put(jar, written, outputName, bytes);
                        }
                    }
                }
                put(jar, written, "ITEMS.txt", itemList(project, ids).getBytes(StandardCharsets.UTF_8));
            }
            PathSafety.check(destination);
            Files.move(result, destination); // No replacement: an existing mod always remains intact.
            return new Result(destination, project.items.size(), Map.copyOf(ids));
        } finally {
            try (var files = Files.walk(stage)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static String itemList(ItemProject project, Map<String, String> ids) {
        StringBuilder result = new StringBuilder("Minecraft 1.20.1 Fabric. Install this JAR in mods with Fabric API. No editor or Item Content installation is needed.\n\n");
        for (var item : project.items) result.append(ids.getOrDefault(item.id, item.id)).append(" = ").append(item.name).append('\n');
        return result.toString();
    }
    private static byte[] runtime() throws IOException {
        try (var input = StandaloneMod.class.getResourceAsStream("standalone-runtime.jar")) {
            if (input == null) throw new IOException("В комплекте редактора отсутствует шаблон готового мода. Скачайте полный выпуск редактора.");
            byte[] bytes = input.readAllBytes();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) if (entry.getName().equals("fabric.mod.json")) {
                    var manifest = JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!RUNTIME_ID.equals(manifest.get("id").getAsString())) break;
                    return bytes;
                }
            }
            throw new IOException("Повреждён шаблон готового мода.");
        }
    }
    private static void rewrite(JsonElement value, Map<String, String> ids, String resourceNamespace) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            JsonElement child = entry.getValue();
            if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) entry.setValue(new JsonPrimitive(reference(child.getAsString(), ids, resourceNamespace)));
            else rewrite(child, ids, resourceNamespace);
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray(); for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) array.set(i, new JsonPrimitive(reference(child.getAsString(), ids, resourceNamespace)));
                else rewrite(child, ids, resourceNamespace);
            }
        }
    }
    private static String reference(String value, Map<String, String> ids, String resourceNamespace) {
        if (ids.containsKey(value)) return ids.get(value);
        return resourceNamespace != null && value.startsWith("itemcontent:") ? resourceNamespace + value.substring("itemcontent".length()) : value;
    }
    private static void put(JarOutputStream jar, Set<String> written, String name, byte[] bytes) throws IOException {
        if (!written.add(name)) throw new IOException("Конфликт ресурсов мода: " + name);
        JarEntry entry = new JarEntry(name); entry.setTime(0); jar.putNextEntry(entry); jar.write(bytes); jar.closeEntry();
    }
}
