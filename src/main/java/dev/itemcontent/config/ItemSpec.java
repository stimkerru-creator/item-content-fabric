package dev.itemcontent.config;
import net.minecraft.util.Identifier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
public record ItemSpec(Identifier id, String displayName, Identifier appearance, int maxCount, boolean fireproof, List<FieldSpec> fields) {
 public ItemSpec { Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(appearance); fields=List.copyOf(Objects.requireNonNull(fields)); if (id.getNamespace().equals("minecraft")) throw new IllegalArgumentException("minecraft namespace is reserved"); if (displayName.isBlank() || displayName.length()>128) throw new IllegalArgumentException("Invalid displayName"); if (maxCount<1 || maxCount>99) throw new IllegalArgumentException("maxCount must be 1..99"); if(fields.size()>4096) throw new IllegalArgumentException("Too many fields"); }
 public record FieldSpec(String id, BigDecimal defaultValue, BigDecimal min, BigDecimal max) { public FieldSpec { if(id==null||!id.contains(":")) throw new IllegalArgumentException("Parameter IDs must be namespaced"); Objects.requireNonNull(defaultValue); Objects.requireNonNull(min); Objects.requireNonNull(max); if(min.compareTo(max)>0||defaultValue.compareTo(min)<0||defaultValue.compareTo(max)>0) throw new IllegalArgumentException("Invalid field range: "+id); } }
}
