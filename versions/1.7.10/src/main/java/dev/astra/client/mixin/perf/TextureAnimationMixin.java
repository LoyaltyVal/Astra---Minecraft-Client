package dev.astra.client.mixin.perf;

import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.Perf;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Texture Animations off: water, lava, fire and portals stop animating (no texture uploads each tick). */
@Mixin(SpriteAtlasTexture.class)
public abstract class TextureAnimationMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void astra$freeze(CallbackInfo ci) {
        PerformanceModule pm = Perf.m();
        if (pm != null && !pm.animateTextures()) {
            ci.cancel();
        }
    }
}
