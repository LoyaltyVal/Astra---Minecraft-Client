package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.platform.RenderCtx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws Astra's HUD after vanilla's, and hides the vanilla parts Astra replaces. */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow
    private long healthBlinkTime;

    @Inject(method = "render", at = @At("TAIL"))
    private void astra$onRender(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
        RenderCtx.begin(context);
        try {
            Astra.get().onRenderHud(delta.getGameTimeDeltaPartialTick(false));
        } finally {
            RenderCtx.end();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"))
    private void astra$noHeartFlash(GuiGraphics context, CallbackInfo ci) {
        dev.astra.client.module.impl.ClientSettingsModule tweaks = Astra.get().tweaks();
        if (tweaks != null && tweaks.noHeartFlash()) {
            healthBlinkTime = 0L;
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void astra$hideSidebar(CallbackInfo ci) {
        if (Astra.get().hidesVanillaScoreboard()) {
            ci.cancel();
        }
    }

    /** Vanilla's effect icons (top right) would sit under Astra's Potion Effects mod. */
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void astra$hideEffects(CallbackInfo ci) {
        dev.astra.client.module.impl.hud.PotionEffectsModule m =
                Astra.get().getModules().get(dev.astra.client.module.impl.hud.PotionEffectsModule.class);
        if (m != null && m.isShownOnHud()) {
            ci.cancel();
        }
    }
}
