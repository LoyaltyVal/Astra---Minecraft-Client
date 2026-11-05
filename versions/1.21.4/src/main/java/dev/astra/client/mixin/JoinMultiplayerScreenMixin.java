package dev.astra.client.mixin;

import dev.astra.client.platform.RenderCtx;
import dev.astra.client.ui.TitleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The "Your Servers | Discover" switch at the top of the server list (Discover = Quick Join). */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void astra$tabs(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TitleMenu.enabled()) {
            RenderCtx.begin(context);
            try {
                TitleMenu.drawServerTabs(this.width, mouseX, mouseY);
            } finally {
                RenderCtx.end();
            }
        }
    }

    /** JoinMultiplayerScreen has no mouseClicked of its own, so the mixin adds one. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && TitleMenu.enabled() && TitleMenu.clickServerTabs(this.width, (int) mouseX, (int) mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
