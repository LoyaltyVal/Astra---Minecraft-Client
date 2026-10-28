package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.NoGlintModule;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Enchant Glint: items say they have no shimmer (only renderers ask this). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hasEnchantmentGlint", at = @At("HEAD"), cancellable = true)
    private void astra$noGlint(CallbackInfoReturnable<Boolean> cir) {
        Astra astra = Astra.get();
        if (astra == null) {
            return;
        }
        NoGlintModule m = astra.getModules().get(NoGlintModule.class);
        if (m != null && m.hideItems()) {
            cir.setReturnValue(false);
        }
    }
}
