package dev.itemcontent.config;

import com.google.gson.JsonObject;
import dev.itemcontent.definition.Identifiers;
import net.minecraft.util.Identifier;
import java.util.Objects;

public record ItemSpec(Identifier id, String displayName, Identifier appearance, boolean customModel,
                       int maxCount, boolean fireproof, String type, String group, boolean existing,
                       JsonObject properties) {
    public ItemSpec(Identifier id, String name, Identifier appearance, boolean customModel, int count, boolean fireproof) {
        this(id, name, appearance, customModel, count, fireproof, "itemcontent:item", "minecraft:ingredients", false, new JsonObject());
    }
    public ItemSpec {
        Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(appearance);
        Identifiers.check(id.toString()); Identifiers.check(appearance.toString());
        Identifiers.check(type); Identifiers.check(group);
        properties = Objects.requireNonNull(properties).deepCopy();
        if (!existing && !id.getNamespace().equals("itemcontent")) throw new IllegalArgumentException("Новые ID должны начинаться с itemcontent:");
        if (id.toString().equals("minecraft:air")) throw new IllegalArgumentException("Нельзя изменять воздух");
        if (displayName.isBlank() || displayName.length() > 128) throw new IllegalArgumentException("Название: 1–128 символов");
        if (maxCount < 1 || maxCount > 99) throw new IllegalArgumentException("Размер стака: 1–99");
        if (properties.has("durability") && properties.get("durability").getAsInt() > 0 && maxCount != 1)
            throw new IllegalArgumentException("Предмет с прочностью должен иметь размер стака 1: " + id);
    }
    public int integer(String key) { return properties.get(key).getAsInt(); }
    public float number(String key) { return properties.get(key).getAsFloat(); }
    public String text(String key) { return properties.get(key).getAsString(); }
    public boolean flag(String key) { return properties.get(key).getAsBoolean(); }
}
