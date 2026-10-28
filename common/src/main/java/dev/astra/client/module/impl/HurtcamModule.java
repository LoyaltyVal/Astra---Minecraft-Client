package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;

/** Turns off the camera shake when you take damage (GameRendererMixin checks this). */
public final class HurtcamModule extends Module {

    public HurtcamModule() {
        super("No Hurtcam", "No camera shake when you get hit.", Category.VISUAL, false);
    }

    @Override
    public String getPreviewText() {
        return "No shake";
    }
}
