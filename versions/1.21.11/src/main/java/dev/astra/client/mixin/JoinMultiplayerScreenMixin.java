package dev.astra.client.mixin;

import dev.astra.client.platform.RenderCtx;
import dev.astra.client.ui.TitleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/** The "Your Servers | Discover" switch at the top of the server list (Discover = Quick Join). */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    /** JoinMultiplayerScreen has no render of its own on 1.21.11, so the mixin adds one. */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && TitleMenu.enabled() && TitleMenu.clickServerTabs(this.width, (int) event.x(), (int) event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
