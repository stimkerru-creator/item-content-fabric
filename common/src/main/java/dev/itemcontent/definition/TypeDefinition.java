package dev.itemcontent.definition;

import com.google.gson.*;
import java.util.*;

/** A known behavior, not a class name to load through reflection. */
public record TypeDefinition(String id, String label, String category, String defaultGroup,
                             String model, boolean block, List<PropertyDefinition> properties, String help) {
    public TypeDefinition {
        Identifiers.check(id); Identifiers.check(defaultGroup);
        Objects.requireNonNull(label); Objects.requireNonNull(category); Objects.requireNonNull(help);
        if (label.isBlank() || label.length() > 160 || category.isBlank() || category.length() > 160 || help.length() > 2000)
            throw new IllegalArgumentException("Invalid type description");
        if (!Set.of("generated", "handheld", "bow", "crossbow", "cube", "chest", "furnace").contains(model)) throw new IllegalArgumentException("Invalid model template");
        properties = List.copyOf(properties);
        if (properties.size() > 128) throw new IllegalArgumentException("Too many behavior properties");
        Set<String> ids = new HashSet<>();
        for (var property : properties) {
            if (!ids.add(property.id())) throw new IllegalArgumentException("Duplicate behavior property: " + property.id());
            property.validate(property.initial());
        }
    }
    public JsonObject validate(JsonObject source, boolean existing) {
        if (source == null) throw new IllegalArgumentException("Настройки поведения отсутствуют");
        Map<String, PropertyDefinition> known = new HashMap<>();
        for (var p : properties) known.put(p.id(), p);
        for (var entry : source.entrySet()) {
            var p = known.get(entry.getKey());
            if (p == null || existing && !p.editExisting()) throw new IllegalArgumentException("Свойство не поддерживается: " + entry.getKey());
            p.validate(entry.getValue());
        }
        JsonObject result = source.deepCopy();
        if (!existing) for (var p : properties) if (!result.has(p.id())) result.add(p.id(), p.initial().deepCopy());
        return result;
    }
    @Override public String toString() { return label; }
}
