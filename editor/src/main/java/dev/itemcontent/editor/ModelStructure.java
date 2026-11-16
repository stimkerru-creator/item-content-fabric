package dev.itemcontent.editor;

import com.google.gson.*;
import java.util.*;

/** Java 1.20.1 model syntax checks; rendering and numeric coercion remain Minecraft's. */
final class ModelStructure {
    private static final Set<String> DIRECTIONS = Set.of("up", "down", "north", "south", "east", "west");
    private static final Set<String> DISPLAY = Set.of("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "head", "gui", "ground", "fixed");
    private ModelStructure() {}
    static void validate(JsonObject model) {
        if (model.has("ambientocclusion")) coercedBoolean(model.get("ambientocclusion"));
        if (model.has("gui_light") && !Set.of("front", "side").contains(model.get("gui_light").getAsString()))
            throw new IllegalArgumentException("gui_light: expected front or side");
        if (model.has("elements")) for (JsonElement value : model.getAsJsonArray("elements")) {
            JsonObject element = value.getAsJsonObject();
            vector(element.getAsJsonArray("from"), 3, -16, 32);
            vector(element.getAsJsonArray("to"), 3, -16, 32);
            if (element.has("shade")) bool(element.get("shade"));
            JsonObject faces = element.getAsJsonObject("faces");
            if (faces == null || faces.size() < 1 || faces.size() > 6)
                throw new IllegalArgumentException("faces: expected between 1 and 6 faces");
            for (var faceEntry : faces.entrySet()) {
                if (!DIRECTIONS.contains(faceEntry.getKey())) throw new IllegalArgumentException("Unknown face: " + faceEntry.getKey());
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                requiredPrimitive(face, "texture");
                if (face.has("cullface")) requiredPrimitive(face, "cullface");
                if (face.has("tintindex")) number(face.get("tintindex"));
                if (face.has("uv")) vector(face.getAsJsonArray("uv"), 4, -Float.MAX_VALUE, Float.MAX_VALUE);
                if (face.has("rotation")) {
                    int rotation = (int) number(face.get("rotation"));
                    if (!Set.of(0, 90, 180, 270).contains(rotation)) throw new IllegalArgumentException("Invalid face rotation");
                }
            }
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                vector(rotation.getAsJsonArray("origin"), 3, -Float.MAX_VALUE, Float.MAX_VALUE);
                if (!Set.of("x", "y", "z").contains(requiredPrimitive(rotation, "axis").toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Invalid rotation axis");
                float angle = number(rotation.get("angle"));
                if (angle != 0 && angle != 22.5f && angle != -22.5f && angle != 45 && angle != -45)
                    throw new IllegalArgumentException("Invalid element rotation angle");
                if (rotation.has("rescale")) coercedBoolean(rotation.get("rescale"));
            }
        }
        if (model.has("display")) {
            JsonObject display = model.getAsJsonObject("display");
            for (String key : DISPLAY) if (display.has(key)) {
                JsonObject transform = display.getAsJsonObject(key);
                for (String property : List.of("rotation", "translation", "scale"))
                    if (transform.has(property)) vector(transform.getAsJsonArray(property), 3, -Float.MAX_VALUE, Float.MAX_VALUE);
            }
        }
    }
    static float number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected a number");
        float result = value.getAsFloat();
        if (!Float.isFinite(result)) throw new IllegalArgumentException("Expected a finite number");
        return result;
    }
    private static void vector(JsonArray value, int size, float min, float max) {
        if (value == null || value.size() != size) throw new IllegalArgumentException("Expected " + size + " vector values");
        for (JsonElement component : value) {
            float coordinate = number(component);
            if (coordinate < min || coordinate > max) throw new IllegalArgumentException("Vector coordinate outside " + min + ".." + max);
        }
    }
    private static String requiredPrimitive(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing or invalid " + key);
        return value.getAsString();
    }
    private static void bool(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected a boolean");
    }
    private static void coercedBoolean(JsonElement value) {
        if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Expected a boolean primitive");
        value.getAsBoolean(); // Match JsonHelper's boolean coercion; shade uses a stricter native check.
    }
}
