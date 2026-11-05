package dev.astra.client.mixin;

import dev.astra.client.platform.RenderCtx;
import dev.astra.client.ui.TitleMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The "Your Servers | Discover" switch at the top of the server list (Discover = Quick Join). */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void astra$tabs(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TitleMenu.enabled()) {
            RenderCtx.begin(context);
            try {
                TitleMenu.drawServerTabs(this.width, mouseX, mouseY);
            } finally {
                RenderCtx.end();
            }
        }
    }

    /** MultiplayerScreen has no mouseClicked of its own on 1.20.4, so the mixin adds one. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && TitleMenu.enabled() && TitleMenu.clickServerTabs(this.width, (int) mouseX, (int) mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
