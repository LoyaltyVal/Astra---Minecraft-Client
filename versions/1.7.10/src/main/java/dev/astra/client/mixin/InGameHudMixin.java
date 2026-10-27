package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /** InGameHud#render (MCP: GuiIngame#renderGameOverlay). Draw Astra's HUD on top. */
    @Inject(method = "render", at = @At("TAIL"))
    private void astra$onRender(CallbackInfo ci) {
        Astra.get().onRenderHud();
    }

    /** method_4943 = the sidebar renderer (MCP: GuiIngame#func_96136_a), unnamed in 1.7.10 yarn. Skipped while Astra's Scoreboard mod draws it instead. */
    @Inject(method = "method_4943", at = @At("HEAD"), cancellable = true)
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
