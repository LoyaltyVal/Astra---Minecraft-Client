package dev.astra.client.mixin;

import dev.astra.client.ui.TitleMenu;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The "Your Servers | Discover" switch at the top of the server list (Discover = Quick Join). */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Inject(method = "render", at = @At("TAIL"))
    private void astra$tabs(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (TitleMenu.enabled()) {
            TitleMenu.drawServerTabs(this.width, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void astra$click(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button == 0 && TitleMenu.enabled() && TitleMenu.clickServerTabs(this.width, mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
