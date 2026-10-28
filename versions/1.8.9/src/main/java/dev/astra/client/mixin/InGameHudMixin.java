package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /** When the hearts last changed; vanilla makes them blink until a second after this. */
    @Shadow
    private long heartJumpEndTick;

    /** "No Heart Flashing": servers that re-send your health constantly make the hearts blink nonstop. */
    @Inject(method = "renderStatusBars", at = @At("HEAD"))
    private void astra$noHeartFlash(CallbackInfo ci) {
        dev.astra.client.module.impl.ClientSettingsModule tweaks = Astra.get().tweaks();
        if (tweaks != null && tweaks.noHeartFlash()) {
            heartJumpEndTick = 0L;
        }
    }

    /** InGameHud#render (MCP: GuiIngame#renderGameOverlay). Draw Astra's HUD on top. */
    @Inject(method = "render", at = @At("TAIL"))
    private void astra$onRender(float tickDelta, CallbackInfo ci) {
        Astra.get().onRenderHud(tickDelta);
    }

    /** InGameHud#renderScoreboardObjective (MCP: renderScoreboard). Skipped while Astra's Scoreboard mod draws it instead. */
    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void astra$hideSidebar(CallbackInfo ci) {
        if (Astra.get().hidesVanillaScoreboard()) {
            ci.cancel();
        }
    }

    /** InGameHud#renderBossBar. Skipped while Astra's Boss Bar mod draws it instead. */
    @Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true)
    private void astra$hideBossBar(CallbackInfo ci) {
        if (Astra.get().hidesVanillaBossBar()) {
            // Vanilla counts the bar's lifetime down while drawing it; keep doing that so it disappears.
            if (BossBar.name != null && BossBar.framesToLive > 0) {
                BossBar.framesToLive--;
            }
            ci.cancel();
        }
    }
}
