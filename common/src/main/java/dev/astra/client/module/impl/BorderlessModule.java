package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;

/**
 * F11 makes a borderless window the size of your screen instead of exclusive fullscreen, so you
 * can alt-tab instantly. (MinecraftClientMixin sends F11 here while this is on.)
 */
public final class BorderlessModule extends Module {

    private boolean active;

    public BorderlessModule() {
        super("Borderless Fullscreen", "F11 fills the screen without a border, so alt-tab is instant.", Category.MECHANIC, true);
    }

    public boolean isActive() {
        return active;
    }

    /** Called instead of vanilla's fullscreen toggle. Each version knows how to do it for its window. */
    public void toggle11() {
        try {
            active = mc().setBorderless(!active);
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
