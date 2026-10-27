package dev.astra.client.platform;

import dev.astra.client.ui.UiScreen;
import net.minecraft.client.gui.screen.Screen;

/** A vanilla Screen that hands every frame to an Astra {@link UiScreen}. */
public final class AstraScreen extends Screen {

    private final UiScreen ui;

    public AstraScreen(UiScreen ui) {
        this.ui = ui;
    }

    public UiScreen getUi() {
        return ui;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        ui.frame(this.width, this.height, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
