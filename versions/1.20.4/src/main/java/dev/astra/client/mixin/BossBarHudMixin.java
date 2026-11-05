package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Astra's Boss Bar mod draws the boss bar (or hides it), so vanilla's is skipped. */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void astra$hide(DrawContext context, CallbackInfo ci) {
        if (Astra.get().hidesVanillaBossBar()) {
            ci.cancel();
        }
    }
}
