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

    /** Typed characters go to the Astra screen; Escape still closes it unless it's editing text. */
    @Override
    protected void keyPressed(char character, int keyCode) {
        boolean escapeUsed = ui.usesEscape();
        ui.charTyped(character, keyCode);
        if (keyCode != 1 || !escapeUsed) {
            super.keyPressed(character, keyCode);
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
