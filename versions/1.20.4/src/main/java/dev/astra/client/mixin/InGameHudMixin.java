package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.platform.RenderCtx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws Astra's HUD after vanilla's, and hides the vanilla parts Astra replaces. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow
    private long heartJumpEndTick;

    @Inject(method = "render", at = @At("TAIL"))
    private void astra$onRender(DrawContext context, float tickDelta, CallbackInfo ci) {
        RenderCtx.begin(context);
        try {
            Astra.get().onRenderHud(tickDelta);
        } finally {
            RenderCtx.end();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"))
    private void astra$noHeartFlash(DrawContext context, CallbackInfo ci) {
        dev.astra.client.module.impl.ClientSettingsModule tweaks = Astra.get().tweaks();
        if (tweaks != null && tweaks.noHeartFlash()) {
            heartJumpEndTick = 0L;
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void astra$hideSidebar(CallbackInfo ci) {
        if (Astra.get().hidesVanillaScoreboard()) {
            ci.cancel();
        }
    }

    /** Vanilla's effect icons (top right) would sit under Astra's Potion Effects mod. */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void astra$hideEffects(DrawContext context, CallbackInfo ci) {
        dev.astra.client.module.impl.hud.PotionEffectsModule m =
                Astra.get().getModules().get(dev.astra.client.module.impl.hud.PotionEffectsModule.class);
        if (m != null && m.isShownOnHud()) {
            ci.cancel();
        }
    }
}
