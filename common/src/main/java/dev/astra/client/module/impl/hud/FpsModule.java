package dev.astra.client.module.impl.hud;

import dev.astra.client.Astra;
import dev.astra.client.module.TextHudModule;

public final class FpsModule extends TextHudModule {

    public FpsModule() {
        super("FPS", "Shows your frames per second.", true, 4, 14);
    }

    @Override
    protected String getText() {
        return "FPS: " + Astra.get().getFrames().getFps();
    }
}
