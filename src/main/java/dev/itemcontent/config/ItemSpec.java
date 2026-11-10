package dev.itemcontent.config;

import net.minecraft.util.Identifier;
import java.math.BigDecimal;
import java.util.*;

public record ItemSpec(Identifier id, String displayName, Identifier appearance, boolean customModel,
                       int maxCount, boolean fireproof, List<FieldSpec> fields) {
    public ItemSpec {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(appearance);
        fields = List.copyOf(Objects.requireNonNull(fields));
        safeIdentifier(id);
        safeIdentifier(appearance);
        if (!id.getNamespace().equals("itemcontent"))
            throw new IllegalArgumentException("Configured items must use itemcontent namespace");
        if (displayName.isBlank() || displayName.length() > 128) throw new IllegalArgumentException("Invalid displayName");
        if (maxCount < 1 || maxCount > 99) throw new IllegalArgumentException("maxCount must be 1..99");
        if (fields.size() > 4096) throw new IllegalArgumentException("Too many fields");
        Set<String> ids = new HashSet<>();
        for (FieldSpec field : fields)
            if (!ids.add(field.id())) throw new IllegalArgumentException("Duplicate parameter ID: " + field.id());
    }

    private static void safeIdentifier(Identifier id) {
        if (id.toString().length() > 128) throw new IllegalArgumentException("ID exceeds 128 characters: " + id);
        for (String part : id.getPath().split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("Invalid ID path: " + id);
    }

    public record FieldSpec(String id, BigDecimal defaultValue, BigDecimal min, BigDecimal max) {
        public FieldSpec {
            if (id == null || !id.contains(":")) throw new IllegalArgumentException("Parameter IDs must be namespaced");
            safeIdentifier(new Identifier(id));
            Objects.requireNonNull(defaultValue);
            Objects.requireNonNull(min);
            Objects.requireNonNull(max);
            defaultValue.movePointRight(2).intValueExact();
            min.movePointRight(2).intValueExact();
            max.movePointRight(2).intValueExact();
            if (min.compareTo(max) > 0 || defaultValue.compareTo(min) < 0 || defaultValue.compareTo(max) > 0)
                throw new IllegalArgumentException("Invalid field range: " + id);
        }
    }
}
