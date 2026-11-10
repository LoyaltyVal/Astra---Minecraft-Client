package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.input.Window;

/**
 * FPS helpers: a lower frame limit while the game is in the background, no fog, fewer particles
 * and no entity shadows. Every change is undone when turned off.
 *
 * On 1.8.9 and 1.7.10 (no Sodium there) it also has Astra's own rendering optimizations: skipping
 * what's hidden behind walls or off screen, distance limits, and less wasted work per frame. The
 * version mixins ask the getters below; with the module off they all return the vanilla behaviour.
 */
public final class PerformanceModule extends Module {

    private final BooleanSetting limitUnfocused;
    private final NumberSetting unfocusedFps;
    private final BooleanSetting noFog;
    private final BooleanSetting minimalParticles;
    private final BooleanSetting noShadows;

    /** True on LWJGL 2 versions (1.7.10 - 1.12.2), where the engine optimizations exist. */
    public static final boolean LEGACY = classExists("dev.astra.client.platform.legacy.LegacyPlatform");

    private Integer savedMaxFps;
    private Integer savedParticles;
    private Boolean savedShadows;

    public PerformanceModule() {
        super("Performance", "FPS boosts: skips drawing what you can't see, background frame limit, no fog, fewer particles.", Category.VISUAL, true);
        limitUnfocused = setting(new BooleanSetting("Limit FPS In Background", true));
        unfocusedFps = setting(new NumberSetting("Background FPS", 30, 5, 60, 5));
        noFog = setting(new BooleanSetting("No Fog", false));
        minimalParticles = setting(new BooleanSetting("Minimal Particles", false));
        noShadows = setting(new BooleanSetting("Hide Entity Shadows", false));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, PerformanceModule.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The engine optimizations are simply on with the module (no settings to tune): each one only
     * skips work you can't see, so the game looks the same, just faster.
     */
    private boolean engine() {
        return LEGACY && isEnabled();
    }

    /** Don't draw mobs, items and block entities that are hidden behind solid blocks (players always draw). */
    public boolean entityCulling() {
        return engine();
    }

    /** Don't draw particles that are off screen. */
    public boolean cullParticles() {
        return engine();
    }

    /** Remember text widths instead of measuring every string every frame. */
    public boolean fastText() {
        return engine();
    }

    /** Skip the GL error check Minecraft does several times a frame (it stalls the graphics driver). */
    public boolean skipGlErrors() {
        return engine();
    }

    /** Animated textures (water, lava, fire) keep animating. */
    public boolean animateTextures() {
        return true;
    }

    /** No extra distance limit on mobs and items (vanilla's own applies). */
    public double entityDistanceSq() {
        return Double.MAX_VALUE;
    }

    /** No extra distance limit on chests, signs and skulls (vanilla's own applies). */
    public double blockEntityDistanceSq() {
        return Double.MAX_VALUE;
    }

    /** Sign text past 24 blocks is too small to read, so it isn't drawn (the sign still is). */
    public double signTextDistanceSq() {
        return engine() ? 24 * 24 : Double.MAX_VALUE;
    }

    @Override
    public void onTick() {
        if (LEGACY) {
            mc().tickPerformance();
        }
        boolean background = limitUnfocused.isOn() && !Window.isActive();
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
