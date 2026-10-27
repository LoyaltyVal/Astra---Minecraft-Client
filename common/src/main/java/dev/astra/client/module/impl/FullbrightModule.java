package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;

/** Raises gamma so caves and night are fully lit. Restores your old brightness when turned off. */
public final class FullbrightModule extends Module {

    private static final float FULLBRIGHT_GAMMA = 100f;

    private Float savedGamma;

    public FullbrightModule() {
        super("Fullbright", "See clearly in the dark.", Category.VISUAL, false);
    }

    @Override
    public String getPreviewText() {
        return "Gamma 100%";
    }

    @Override
    public void onTick() {
        if (savedGamma == null) {
            // If the game was closed with fullbright on, options may hold 100; cap to vanilla's max.
            savedGamma = Math.min(1f, mc().getGamma());
        }
        if (mc().getGamma() != FULLBRIGHT_GAMMA) {
            mc().setGamma(FULLBRIGHT_GAMMA);
        }
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @Override
    public void onShutdown() {
        restore();
    }

    private void restore() {
        if (savedGamma != null) {
            mc().setGamma(savedGamma);
            savedGamma = null;
        }
    }
}
