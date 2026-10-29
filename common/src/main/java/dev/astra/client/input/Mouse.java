package dev.astra.client.input;

import dev.astra.client.Astra;

/** The mouse, the same on every Minecraft version (0 = left, 1 = right, 2 = middle). */
public final class Mouse {

    private Mouse() {
    }

    public static boolean isButtonDown(int button) {
        return button >= 0 && Astra.platform().isMouseButtonDown(button);
    }

    public static int getButtonCount() {
        return Astra.platform().getMouseButtonCount();
    }

    /** Scroll since the last call: positive = up. */
    public static int getDWheel() {
        return Astra.platform().consumeScroll();
    }
}
