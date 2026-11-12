package dev.itemcontent.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.FoodComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Startup-only changes to standard item settings; never replaces a registered item instance. */
@Mixin(Item.class)
public interface ItemSettingsAccess {
    @Mutable @Accessor("maxCount") void itemcontent$maxCount(int value);
    @Mutable @Accessor("maxDamage") void itemcontent$maxDamage(int value);
    @Mutable @Accessor("fireproof") void itemcontent$fireproof(boolean value);
    @Mutable @Accessor("foodComponent") void itemcontent$food(FoodComponent value);
}
