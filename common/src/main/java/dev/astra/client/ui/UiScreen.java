package dev.astra.client.ui;

import dev.astra.client.Astra;
import dev.astra.client.platform.Platform;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Base class for Astra's screens. The version-specific wrapper screen only forwards
 * render(); input is polled from LWJGL here so it works the same on every version.
 */
public abstract class UiScreen {

    protected int width;
    protected int height;

    private final boolean[] mouseWasDown = new boolean[3];
    private final boolean[] keyWasDown = new boolean[Keyboard.KEYBOARD_SIZE];
    private boolean initialized;

    /** Called by the wrapper every frame with scaled size and mouse position. */
    public final void frame(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks) {
        width = screenWidth;
        height = screenHeight;

        if (!initialized) {
            initialized = true;
            // Ignore whatever was already held when the screen opened (e.g. the menu key).
            for (int b = 0; b < mouseWasDown.length; b++) {
                mouseWasDown[b] = Mouse.isButtonDown(b);
            }
            for (int k = 1; k < keyWasDown.length; k++) {
                keyWasDown[k] = Keyboard.isKeyDown(k);
            }
            Mouse.getDWheel(); // discard scroll accumulated in-game
            init();
        }

        for (int b = 0; b < mouseWasDown.length; b++) {
            boolean down = Mouse.isButtonDown(b);
            if (down && !mouseWasDown[b]) {
                mouseClicked(mouseX, mouseY, b);
            } else if (!down && mouseWasDown[b]) {
                mouseReleased(mouseX, mouseY, b);
            }
            mouseWasDown[b] = down;
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            mouseScrolled(mouseX, mouseY, wheel > 0 ? 1 : -1);
        }

        for (int k = 1; k < keyWasDown.length; k++) {
            boolean down = Keyboard.isKeyDown(k);
            if (down && !keyWasDown[k]) {
                keyPressed(k);
            }
            keyWasDown[k] = down;
        }

        draw(mouseX, mouseY, partialTicks);
    }

    protected void init() {
    }

    protected abstract void draw(int mouseX, int mouseY, float partialTicks);

    protected void mouseClicked(int mouseX, int mouseY, int button) {
    }

    protected void mouseReleased(int mouseX, int mouseY, int button) {
    }

    /** direction: +1 = scroll up, -1 = scroll down. */
    protected void mouseScrolled(int mouseX, int mouseY, int direction) {
    }

    protected void keyPressed(int keyCode) {
    }

    /** A typed character (from the game's own keyboard events, so symbols and capitals work). */
    public void charTyped(char c, int keyCode) {
    }

    /** Runs once after the screen stops being shown (free textures, stop polling...). */
    public void onClosed() {
    }

    /** True while the screen uses Escape itself (e.g. to stop editing text) instead of closing. */
    public boolean usesEscape() {
        return false;
    }

    protected static Platform mc() {
        return Astra.platform();
    }

    protected static void open(UiScreen screen) {
        mc().openUi(screen);
    }

    protected static void close() {
        Astra.get().saveConfig();
        mc().openUi(null);
    }

    protected static void save() {
        Astra.get().saveConfig();
    }
}
