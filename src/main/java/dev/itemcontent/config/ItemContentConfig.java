package dev.itemcontent.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Startup configuration; no configuration parsing happens during item use. */
public final class ItemContentConfig {
    private static final int MAX_ITEMS = 4096, MAX_BYTES = 4 * 1024 * 1024;
    private ItemContentConfig() {}

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("item-content/items.json");
    }

    public static List<ItemSpec> load() throws IOException {
        Path file = path();
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) {
            Files.writeString(file, "{\n  \"version\": 1,\n  \"items\": []\n}\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return List.of();
        }
        if (Files.size(file) > MAX_BYTES) throw new IOException("Configuration exceeds 4 MiB");
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Also used to check editor exports against the actual runtime format. */
    public static List<ItemSpec> parse(String source) throws IOException {
        if (source == null) throw new IOException("Configuration is null");
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("Configuration exceeds 4 MiB");
        try {
            JsonObject root = JsonParser.parseString(source).getAsJsonObject();
            allow(root, "version", "items");
            if (integer(root, "version", 0) != 1) throw new IOException("Unsupported configuration version");
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) throw new IOException("items must be array");
            if (items.size() > MAX_ITEMS) throw new IOException("Too many items");
            List<ItemSpec> result = new ArrayList<>();
            Set<Identifier> ids = new HashSet<>();
            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                allow(item, "id", "name", "appearance", "custom_model", "max_count", "fireproof", "fields");
                Identifier id = identifier(required(item, "id"));
                if (!ids.add(id)) throw new IOException("Duplicate ID: " + id);
                Identifier appearance = new Identifier(item.has("appearance") ? required(item, "appearance") : "minecraft:iron_ingot");
                // Legacy exports remain readable; this module does not interpret numeric data.
                if (item.has("fields")) {
                    JsonArray legacy = item.getAsJsonArray("fields");
                    if (legacy == null) throw new IOException("Legacy fields must be an array");
                    if (!legacy.isEmpty()) org.slf4j.LoggerFactory.getLogger("itemcontent").warn(
                            "{}: legacy fields are ignored. Item Content only registers items; the source file is unchanged.", id);
                }
                result.add(new ItemSpec(id, required(item, "name"), appearance,
                        flag(item, "custom_model"), integer(item, "max_count", 64), flag(item, "fireproof")));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid item definition: " + failure.getMessage(), failure);
        }
    }

    private static Identifier identifier(String text) {
        return text.contains(":") ? new Identifier(text) : new Identifier("itemcontent", text);
    }
    private static void allow(JsonObject object, String... keys) throws IOException {
        Set<String> allowed = Set.of(keys);
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IOException("Unknown key: " + key);
    }
    private static String required(JsonObject object, String key) throws IOException {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new IOException("Missing text field: " + key);
        String text = element.getAsString().trim();
        if (text.isEmpty()) throw new IOException("Empty text field: " + key);
        return text;
    }
    private static BigDecimal number(JsonObject object, String key, BigDecimal fallback) throws IOException {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IOException("Expected number: " + key);
        return value.getAsBigDecimal();
    }
    private static int integer(JsonObject object, String key, int fallback) throws IOException {
        return number(object, key, BigDecimal.valueOf(fallback)).intValueExact();
    }
    private static boolean flag(JsonObject object, String key) throws IOException {
        if (!object.has(key)) return false;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IOException("Expected boolean: " + key);
        return value.getAsBoolean();
    }
}
