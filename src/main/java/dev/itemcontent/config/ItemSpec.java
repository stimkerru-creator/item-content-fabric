package dev.itemcontent.config;

import net.minecraft.util.Identifier;
import java.util.*;

public record ItemSpec(Identifier id, String displayName, Identifier appearance, boolean customModel,
                       int maxCount, boolean fireproof) {
    public ItemSpec {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(appearance);
        safeIdentifier(id);
        safeIdentifier(appearance);
        if (!id.getNamespace().equals("itemcontent"))
            throw new IllegalArgumentException("Configured items must use itemcontent namespace");
        if (displayName.isBlank() || displayName.length() > 128) throw new IllegalArgumentException("Invalid displayName");
        if (maxCount < 1 || maxCount > 99) throw new IllegalArgumentException("maxCount must be 1..99");

    }

    private static void safeIdentifier(Identifier id) {
        if (id.toString().length() > 128) throw new IllegalArgumentException("ID exceeds 128 characters: " + id);
        for (String part : id.getPath().split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("Invalid ID path: " + id);
    }

}
