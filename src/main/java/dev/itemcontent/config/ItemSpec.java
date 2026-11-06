package dev.itemcontent.config;
import net.minecraft.util.Identifier;
import java.util.Objects;
public record ItemSpec(Identifier id, String displayName, Identifier appearance, int maxCount, boolean fireproof) {
 public ItemSpec { Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(appearance); if (id.getNamespace().equals("minecraft")) throw new IllegalArgumentException("minecraft namespace is reserved"); if (displayName.isBlank() || displayName.length()>128) throw new IllegalArgumentException("Invalid displayName"); if (maxCount<1 || maxCount>99) throw new IllegalArgumentException("maxCount must be 1..99"); }
}
