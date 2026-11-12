package dev.itemcontent.editor;

import com.google.gson.*;
import dev.itemcontent.definition.TypeDefinition;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Standard resource/data pack files. No custom Minecraft renderer or recipe engine. */
final class ProjectResources {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private ProjectResources() {}
    static void write(ItemProject project, Path staged) throws IOException {
        Path pack = staged.resolve("resourcepacks/item-content");
        json(pack.resolve("pack.mcmeta"), JsonParser.parseString("{\"pack\":{\"pack_format\":15,\"description\":\"Item Content\"}}"));
        JsonObject language = new JsonObject(); Map<String, List<String>> tags = new LinkedHashMap<>();
        for (var item : project.items) {
            TypeDefinition type = project.type(item); JsonObject properties = type.validate(item.properties, item.existing);
            String namespace = item.id.substring(0, item.id.indexOf(':')), path = item.id.substring(item.id.indexOf(':') + 1);
            language.addProperty("item." + namespace + "." + path.replace('/', '.'), item.name);
            language.addProperty("block." + namespace + "." + path.replace('/', '.'), item.name);
            if (type.block() && !item.existing) block(item, type, pack, properties, tags);
            else if (item.customModel) item(item, type, pack);
            if (!item.existing && item.type.equals("itemcontent:armor") && properties.get("armor_texture").getAsString().equals("custom")) {
                for (int layer = 1; layer <= 2; layer++) {
                    String source = item.textures.get("armor_layer_" + layer);
                    if (source == null || source.isBlank()) throw new IOException("Выберите PNG слоя брони " + layer + ": " + item.name);
                    copyPng(source, pack.resolve("assets/" + namespace + "/textures/models/armor/" + path + "_layer_" + layer + ".png"));
                }
            }
        }
        json(pack.resolve("assets/itemcontent/lang/en_us.json"), language);
        json(pack.resolve("assets/itemcontent/lang/ru_ru.json"), language);
        if (!tags.isEmpty()) {
            Path data = staged.resolve("datapacks/item-content");
            json(data.resolve("pack.mcmeta"), JsonParser.parseString("{\"pack\":{\"pack_format\":15,\"description\":\"Item Content block mining tags\"}}"));
            for (var entry : tags.entrySet()) {
                JsonObject tag = new JsonObject(); tag.addProperty("replace", false); tag.add("values", JSON.toJsonTree(entry.getValue()));
                json(data.resolve("data/minecraft/tags/blocks/" + entry.getKey() + ".json"), tag);
            }
        }
    }
    private static void item(ItemProject.Entry item, TypeDefinition type, Path pack) throws IOException {
        String namespace = item.id.substring(0, item.id.indexOf(':')), path = item.id.substring(item.id.indexOf(':') + 1);
        String texture = namespace + ":item/" + path;
        copyPng(item.texture, pack.resolve("assets/" + namespace + "/textures/item/" + path + ".png"));
        String parent = type.model().equals("handheld") ? "minecraft:item/handheld" : type.model().equals("bow") ? "minecraft:item/bow" : type.model().equals("crossbow") ? "minecraft:item/crossbow" : "minecraft:item/generated";
        JsonObject model = model(parent, Map.of("layer0", texture));
        if (type.model().equals("bow") || type.model().equals("crossbow")) {
            JsonArray overrides = new JsonArray();
            for (int frame = 0; frame < 3; frame++) {
                String key = "pulling_" + frame, child = path + "_" + key;
                String childTexture = texture;
                if (item.textures.containsKey(key)) {
                    childTexture = namespace + ":item/" + child;
                    copyPng(item.textures.get(key), pack.resolve("assets/" + namespace + "/textures/item/" + child + ".png"));
                }
                json(pack.resolve("assets/" + namespace + "/models/item/" + child + ".json"), model("minecraft:item/" + type.model() + "_" + key, Map.of("layer0", childTexture)));
                JsonObject predicate = new JsonObject(); predicate.addProperty("pulling", 1);
                if (frame > 0) predicate.addProperty("pull", type.model().equals("bow") ? (frame == 1 ? 0.65 : 0.9) : (frame == 1 ? 0.58 : 1.0));
                override(overrides, predicate, namespace + ":item/" + child);
            }
            if (type.model().equals("crossbow")) for (String key : List.of("arrow", "firework")) {
                String child = path + "_" + key; String childTexture = texture;
                if (item.textures.containsKey(key)) { childTexture = namespace + ":item/" + child; copyPng(item.textures.get(key), pack.resolve("assets/" + namespace + "/textures/item/" + child + ".png")); }
                json(pack.resolve("assets/" + namespace + "/models/item/" + child + ".json"), model("minecraft:item/crossbow_" + key, Map.of("layer0", childTexture)));
                JsonObject predicate = new JsonObject(); predicate.addProperty("charged", 1); if (key.equals("firework")) predicate.addProperty("firework", 1);
                override(overrides, predicate, namespace + ":item/" + child);
            }
            model.add("overrides", overrides);
        }
        json(pack.resolve("assets/" + namespace + "/models/item/" + path + ".json"), model);
    }
    private static void block(ItemProject.Entry item, TypeDefinition type, Path pack, JsonObject properties, Map<String, List<String>> tags) throws IOException {
        String namespace = item.id.substring(0, item.id.indexOf(':')), path = item.id.substring(item.id.indexOf(':') + 1);
        Map<String, String> textures = new LinkedHashMap<>();
        if (item.customModel) {
            String all = namespace + ":block/" + path;
            copyPng(item.texture, pack.resolve("assets/" + namespace + "/textures/block/" + path + ".png"));
            textures.put("all", all);
            for (String face : List.of("top", "bottom", "side", "front", "back")) {
                String source = item.textures.get(face);
                if (source != null && !source.isBlank()) {
                    copyPng(source, pack.resolve("assets/" + namespace + "/textures/block/" + path + "_" + face + ".png"));
                    textures.put(face, namespace + ":block/" + path + "_" + face);
                } else textures.put(face, all);
            }
        }
        JsonObject blockModel;
        if (type.model().equals("chest")) {
            blockModel = new JsonObject(); blockModel.addProperty("parent", "minecraft:block/block");
            blockModel.add("textures", JSON.toJsonTree(Map.of("all", textures.getOrDefault("all", "minecraft:block/oak_planks"), "up", textures.getOrDefault("top", "#all"), "down", textures.getOrDefault("bottom", "#all"), "north", textures.getOrDefault("front", "#all"), "south", textures.getOrDefault("back", "#all"), "east", textures.getOrDefault("side", "#all"), "west", textures.getOrDefault("side", "#all"), "particle", "#all")));
            JsonArray elements = new JsonArray(); elements.add(cuboid(new int[]{1,0,1}, new int[]{15,10,15}));
            elements.add(cuboid(new int[]{1,10,1}, new int[]{15,14,15})); elements.add(cuboid(new int[]{7,7,0}, new int[]{9,11,2})); blockModel.add("elements", elements);
        } else if (!item.customModel) {
            String source = item.appearance; blockModel = model(source.substring(0, source.indexOf(':')) + ":block/" + source.substring(source.indexOf(':') + 1), Map.of());
        } else if (type.model().equals("furnace") || item.textures.size() > 0) {
            blockModel = model("minecraft:block/cube", Map.of("down", textures.get("bottom"), "up", textures.get("top"), "north", textures.get("front"), "south", textures.get("back"), "west", textures.get("side"), "east", textures.get("side"), "particle", textures.get("side")));
        } else blockModel = model("minecraft:block/cube_all", Map.of("all", textures.get("all")));
        json(pack.resolve("assets/" + namespace + "/models/block/" + path + ".json"), blockModel);
        json(pack.resolve("assets/" + namespace + "/models/item/" + path + ".json"), model(namespace + ":block/" + path, Map.of()));
        JsonObject variants = new JsonObject();
        if (type.model().equals("cube")) { JsonObject variant = new JsonObject(); variant.addProperty("model", namespace + ":block/" + path); variants.add("", variant); }
        else {
            int rotation = 0;
            for (String direction : List.of("north", "east", "south", "west")) {
                JsonObject variant = new JsonObject(); variant.addProperty("model", namespace + ":block/" + path); if (rotation != 0) variant.addProperty("y", rotation);
                variants.add("facing=" + direction, variant); rotation += 90;
            }
        }
        JsonObject states = new JsonObject(); states.add("variants", variants); json(pack.resolve("assets/" + namespace + "/blockstates/" + path + ".json"), states);
        String tool = properties.has("tool") ? properties.get("tool").getAsString() : "none";
        if (!tool.equals("none")) tags.computeIfAbsent("mineable/" + tool, k -> new ArrayList<>()).add(item.id);
        int level = properties.has("mining_level") ? properties.get("mining_level").getAsInt() : 0;
        if (level > 0) {
            if (tool.equals("none")) throw new IOException("Для уровня добычи нужен инструмент: " + item.name);
            tags.computeIfAbsent("needs_" + List.of("", "stone", "iron", "diamond").get(level) + "_tool", k -> new ArrayList<>()).add(item.id);
        }
    }
    private static JsonObject cuboid(int[] from, int[] to) {
        JsonObject element = new JsonObject(); element.add("from", JSON.toJsonTree(from)); element.add("to", JSON.toJsonTree(to));
        JsonObject faces = new JsonObject(); for (String direction : List.of("up", "down", "north", "south", "east", "west")) { JsonObject face = new JsonObject(); face.addProperty("texture", "#" + direction); faces.add(direction, face); }
        element.add("faces", faces); return element;
    }
    private static void override(JsonArray array, JsonObject predicate, String model) { JsonObject value = new JsonObject(); value.add("predicate", predicate); value.addProperty("model", model); array.add(value); }
    private static JsonObject model(String parent, Map<String, String> textures) { JsonObject value = new JsonObject(); value.addProperty("parent", parent); if (!textures.isEmpty()) value.add("textures", JSON.toJsonTree(textures)); return value; }
    private static void copyPng(String source, Path target) throws IOException {
        Path input = Path.of(source == null ? "" : source); ItemProject.validatePng(input);
        Files.createDirectories(target.getParent()); Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }
    private static void json(Path file, JsonElement value) throws IOException { Files.createDirectories(file.getParent()); Files.writeString(file, JSON.toJson(value)); }
}
