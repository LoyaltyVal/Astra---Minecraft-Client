package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * F11 makes a borderless window the size of your screen instead of exclusive fullscreen, so you
 * can alt-tab instantly. (MinecraftClientMixin sends F11 here while this is on.)
 */
public final class BorderlessModule extends Module {

    private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";

    private boolean active;
    private int savedX;
    private int savedY;
    private int savedW;
    private int savedH;

    public BorderlessModule() {
        super("Borderless Fullscreen", "F11 fills the screen without a border, so alt-tab is instant.", Category.MECHANIC, true);
    }

    public boolean isActive() {
        return active;
    }

    /** Called instead of vanilla's fullscreen toggle. */
    public void toggle11() {
        try {
            if (!active) {
                savedX = Display.getX();
                savedY = Display.getY();
                savedW = Display.getWidth();
                savedH = Display.getHeight();
                DisplayMode desktop = Display.getDesktopDisplayMode();
                System.setProperty(UNDECORATED, "true");
                Display.setFullscreen(false);
                Display.setResizable(false);
                // A new display mode makes LWJGL recreate the window, which picks up "undecorated".
                Display.setDisplayMode(new DisplayMode(desktop.getWidth(), desktop.getHeight()));
                Display.setLocation(0, 0);
                active = true;
                mc().resizeGame(desktop.getWidth(), desktop.getHeight());
            } else {
                System.setProperty(UNDECORATED, "false");
                Display.setDisplayMode(new DisplayMode(Math.max(854, savedW), Math.max(480, savedH)));
                Display.setResizable(true);
                Display.setLocation(Math.max(0, savedX), Math.max(0, savedY));
                active = false;
                mc().resizeGame(Display.getWidth(), Display.getHeight());
            }
        } catch (Throwable t) {
            System.err.println("[Astra] Borderless fullscreen failed: " + t);
            active = false;
        }
    }

    @Override
    protected void onDisable() {
        if (active) {
            toggle11();
        }
    }

    @Override
    public String getPreviewText() {
        return "F11";
    }
}
