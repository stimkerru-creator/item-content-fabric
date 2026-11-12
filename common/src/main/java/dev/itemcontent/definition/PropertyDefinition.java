package dev.itemcontent.definition;

import com.google.gson.*;
import java.util.*;

/** Declarative, non-executable editor schema shared with runtime extensions. */
public record PropertyDefinition(String id, String label, String kind, JsonElement initial,
                                 double min, double max, List<String> choices,
                                 boolean editExisting, String help) {
    public PropertyDefinition {
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid property ID: " + id);
        Objects.requireNonNull(label); Objects.requireNonNull(initial); Objects.requireNonNull(help);
        if (label.isBlank() || label.length() > 160 || help.length() > 1000) throw new IllegalArgumentException("Invalid property label/help");
        if (!Set.of("number", "integer", "boolean", "text", "choice", "identifier").contains(kind)) throw new IllegalArgumentException("Unknown property kind: " + kind);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) throw new IllegalArgumentException("Invalid property bounds");
        choices = List.copyOf(choices); initial = initial.deepCopy();
        if (choices.size() > 256 || choices.stream().anyMatch(s -> s == null || s.length() > 160)) throw new IllegalArgumentException("Invalid choices");
    }
    public void validate(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) throw bad();
        JsonPrimitive p = value.getAsJsonPrimitive();
        switch (kind) {
            case "number", "integer" -> {
                if (!p.isNumber()) throw bad();
                double n = p.getAsDouble();
                if (!Double.isFinite(n) || n < min || n > max) throw bad();
                if (kind.equals("integer")) try { p.getAsBigDecimal().intValueExact(); } catch (ArithmeticException e) { throw bad(); }
            }
            case "boolean" -> { if (!p.isBoolean()) throw bad(); }
            default -> {
                if (!p.isString() || p.getAsString().length() > 512) throw bad();
                if (kind.equals("choice") && !choices.contains(p.getAsString())) throw bad();
                if (kind.equals("identifier")) Identifiers.check(p.getAsString());
            }
        }
    }
    private IllegalArgumentException bad() { return new IllegalArgumentException("Некорректное значение: " + label + " (" + id + ")"); }
    public static PropertyDefinition number(String id, String label, double value, double min, double max, boolean edit, String help) {
        return new PropertyDefinition(id, label, "number", new JsonPrimitive(value), min, max, List.of(), edit, help);
    }
    public static PropertyDefinition integer(String id, String label, int value, int min, int max, boolean edit, String help) {
        return new PropertyDefinition(id, label, "integer", new JsonPrimitive(value), min, max, List.of(), edit, help);
    }
    public static PropertyDefinition flag(String id, String label, boolean value, boolean edit, String help) {
        return new PropertyDefinition(id, label, "boolean", new JsonPrimitive(value), 0, 1, List.of(), edit, help);
    }
    public static PropertyDefinition choice(String id, String label, String value, List<String> choices, boolean edit, String help) {
        return new PropertyDefinition(id, label, "choice", new JsonPrimitive(value), 0, 0, choices, edit, help);
    }
    public static PropertyDefinition identifier(String id, String label, String value, boolean edit, String help) {
        return new PropertyDefinition(id, label, "identifier", new JsonPrimitive(value), 0, 0, List.of(), edit, help);
    }
}
