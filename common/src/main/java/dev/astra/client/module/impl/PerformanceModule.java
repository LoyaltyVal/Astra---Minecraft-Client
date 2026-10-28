package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.NumberSetting;
import org.lwjgl.opengl.Display;

/**
 * FPS helpers: a lower frame limit while the game is in the background, no fog, fewer particles
 * and no entity shadows. Every change is undone when turned off.
 */
public final class PerformanceModule extends Module {

    private final BooleanSetting limitUnfocused;
    private final NumberSetting unfocusedFps;
    private final BooleanSetting noFog;
    private final BooleanSetting minimalParticles;
    private final BooleanSetting noShadows;

    private Integer savedMaxFps;
    private Integer savedParticles;
    private Boolean savedShadows;

    public PerformanceModule() {
        super("Performance", "FPS boosts: background frame limit, no fog, fewer particles.", Category.VISUAL, true);
        limitUnfocused = setting(new BooleanSetting("Limit FPS In Background", true));
        unfocusedFps = setting(new NumberSetting("Background FPS", 30, 5, 60, 5));
        noFog = setting(new BooleanSetting("No Fog", false));
        minimalParticles = setting(new BooleanSetting("Minimal Particles", false));
        noShadows = setting(new BooleanSetting("Hide Entity Shadows", false));
    }

    @Override
    public void onTick() {
        boolean background = limitUnfocused.isOn() && !Display.isActive();
        if (background && savedMaxFps == null) {
            savedMaxFps = mc().getMaxFps();
            mc().setMaxFps((int) unfocusedFps.getFloat());
        } else if (!background && savedMaxFps != null) {
            mc().setMaxFps(savedMaxFps);
            savedMaxFps = null;
        }

        if (minimalParticles.isOn() && savedParticles == null) {
            savedParticles = mc().getParticles();
            mc().setParticles(2);
        } else if (!minimalParticles.isOn() && savedParticles != null) {
            mc().setParticles(savedParticles);
            savedParticles = null;
        }

        if (noShadows.isOn() && savedShadows == null) {
            savedShadows = mc().getEntityShadows();
            mc().setEntityShadows(false);
        } else if (!noShadows.isOn() && savedShadows != null) {
            mc().setEntityShadows(savedShadows);
            savedShadows = null;
        }
    }

    /** GameRendererMixin turns fog off right after vanilla turns it on. */
    public boolean noFog() {
        return isEnabled() && noFog.isOn();
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
        if (savedMaxFps != null) {
            mc().setMaxFps(savedMaxFps);
            savedMaxFps = null;
        }
        if (savedParticles != null) {
            mc().setParticles(savedParticles);
            savedParticles = null;
        }
        if (savedShadows != null) {
            mc().setEntityShadows(savedShadows);
            savedShadows = null;
        }
    }

    @Override
    public String getPreviewText() {
        return "FPS+";
    }
}
