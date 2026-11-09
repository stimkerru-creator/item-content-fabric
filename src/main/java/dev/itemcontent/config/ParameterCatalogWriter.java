package dev.itemcontent.config;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** The generated catalog defers to matching definitions exported by Item Parameters. */
public final class ParameterCatalogWriter {
    private ParameterCatalogWriter() {}

    public static void write(Path target, JsonArray generated) throws IOException {
        Files.createDirectories(target.getParent());
        Map<String, JsonObject> external = new HashMap<>();
        try (var paths = Files.walk(target.getParent())) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json") && !p.equals(target)).sorted().toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 64L * 1024 * 1024)
                    throw new IOException("Invalid parameter catalog file: " + file);
                try {
                    JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonArray entries = root.has("items") ? root.getAsJsonArray("items") : single(root);
                    for (JsonElement element : entries) {
                        JsonObject item = element.getAsJsonObject();
                        String id = item.get("item").getAsString();
                        if (external.putIfAbsent(id, item) != null) throw new IOException("Duplicate parameter item: " + id);
                    }
                } catch (RuntimeException e) { throw new IOException("Invalid parameter catalog: " + file, e); }
            }
        }
        JsonArray remaining = new JsonArray();
        for (JsonElement element : generated) {
            JsonObject item = element.getAsJsonObject();
            String id = item.get("item").getAsString();
            JsonObject existing = external.get(id);
            if (existing == null) remaining.add(item);
            // No optional stats in the item constructor: the first project's
            // explicit schema remains authoritative for this registered item.
            else if (!item.getAsJsonArray("fields").isEmpty() && !fields(existing).equals(fields(item)))
                throw new IOException("Conflicting parameter definitions for " + id + ". Export the same fields and defaults from both editors.");
        }
        JsonObject root = new JsonObject(); root.addProperty("version", 1); root.add("items", remaining);
        Path temporary = Files.createTempFile(target.getParent(), "itemcontent-generated-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static JsonArray single(JsonObject root) { JsonArray array = new JsonArray(); array.add(root); return array; }
    private static Map<String, List<BigDecimal>> fields(JsonObject item) throws IOException {
        try {
            Map<String, List<BigDecimal>> result = new HashMap<>();
            for (JsonElement element : item.getAsJsonArray("fields")) {
                String id;
                List<BigDecimal> values;
                if (element.isJsonPrimitive()) {
                    id = element.getAsString();
                    values = List.of(BigDecimal.ZERO, new BigDecimal("-21474836.48"), new BigDecimal("21474836.47"));
                } else {
                    JsonObject field = element.getAsJsonObject(); id = field.get("id").getAsString();
                    values = List.of(number(field, "default", "0"), number(field, "min", "-21474836.48"), number(field, "max", "21474836.47"));
                }
                values = values.stream().map(BigDecimal::stripTrailingZeros).toList();
                if (result.putIfAbsent(id, values) != null) throw new IOException("Duplicate parameter field: " + id);
            }
            return result;
        } catch (RuntimeException e) { throw new IOException("Invalid parameter fields", e); }
    }
    private static BigDecimal number(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsBigDecimal() : new BigDecimal(fallback);
    }
}
