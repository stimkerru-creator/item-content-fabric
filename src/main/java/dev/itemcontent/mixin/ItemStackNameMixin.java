package dev.itemcontent.mixin;

import dev.itemcontent.ItemContentMod;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class ItemStackNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void itemcontent$name(CallbackInfoReturnable<Text> info) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.hasCustomName()) return;
        Text name = ItemContentMod.configuredName(stack.getItem());
        if (name != null) info.setReturnValue(name.copy());
    }
}
