package dev.astra.client.input;

import dev.astra.client.Astra;

/** The game window, the same on every Minecraft version. Sizes are in real pixels. */
public final class Window {

    private Window() {
    }

    public static int getWidth() {
        return Astra.platform().getWindowWidth();
    }

    public static int getHeight() {
        return Astra.platform().getWindowHeight();
    }

    public static boolean isActive() {
        return Astra.platform().isWindowFocused();
    }

    public static void setTitle(String title) {
        Astra.platform().setWindowTitle(title);
    }
}
