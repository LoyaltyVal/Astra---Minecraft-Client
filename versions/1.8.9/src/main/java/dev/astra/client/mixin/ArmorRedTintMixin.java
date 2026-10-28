package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.OldAnimationsModule;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 red armor: armor gets the red "hurt" tint with the body (1.8 leaves armor its normal color). */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorRedTintMixin {

    @Inject(method = "combineTextures", at = @At("HEAD"), cancellable = true)
    private void astra$redArmor(CallbackInfoReturnable<Boolean> cir) {
        OldAnimationsModule a = Astra.get().getModules().get(OldAnimationsModule.class);
        if (a != null && a.redArmor()) {
            cir.setReturnValue(true);
        }
    }
}
