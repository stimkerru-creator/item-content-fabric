package dev.itemcontent.item;
import dev.itemcontent.config.ItemSpec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
public final class ConfiguredItem extends Item {
 private final String displayName; private final String appearance; private final boolean customModel;
 public ConfiguredItem(ItemSpec spec){ super(settings(spec)); displayName=spec.displayName(); appearance=spec.appearance().toString(); customModel=spec.customModel(); }
 private static Settings settings(ItemSpec spec){ Settings settings=new Settings().maxCount(spec.maxCount()); if(spec.fireproof()) settings.fireproof(); return settings; }
 public String appearance(){ return appearance; }
 public boolean customModel(){ return customModel; }
 @Override public Text getName(ItemStack stack){ return Text.literal(displayName); }
}
