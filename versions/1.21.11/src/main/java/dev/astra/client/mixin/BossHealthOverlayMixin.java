package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Astra's Boss Bar mod draws the boss bar (or hides it), so vanilla's is skipped. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void astra$hide(CallbackInfo ci) {
        if (Astra.get().hidesVanillaBossBar()) {
            ci.cancel();
        }
    }
}
