package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.util.Keys;
import org.lwjgl.input.Keyboard;

/** Hold the zoom key to zoom in, like OptiFine zoom. */
public final class ZoomModule extends Module {

    private final KeySetting key;
    private final NumberSetting factor;
    private final BooleanSetting smooth;
    private final BooleanSetting lowerSensitivity;

    private boolean zooming;
    private float current = 1f;
    private long lastUpdate = System.nanoTime();
    private Float savedSensitivity;

    public ZoomModule() {
        super("Zoom", "Hold a key to zoom in.", Category.MECHANIC, true);
        key = setting(new KeySetting("Zoom Key", Keyboard.KEY_C));
        factor = setting(new NumberSetting("Zoom Amount", 4, 2, 10, 0.5));
        smooth = setting(new BooleanSetting("Smooth Zoom", true));
        lowerSensitivity = setting(new BooleanSetting("Lower Sensitivity", true));
    }

    @Override
    public void onTick() {
        zooming = mc().isInGame() && !mc().isScreenOpen() && key.isDown();

        if (zooming && lowerSensitivity.isOn() && savedSensitivity == null) {
            savedSensitivity = mc().getSensitivity();
            mc().setSensitivity(savedSensitivity * 0.5f);
        } else if ((!zooming || !lowerSensitivity.isOn()) && savedSensitivity != null) {
            restoreSensitivity();
        }
    }

    @Override
    protected void onDisable() {
        zooming = false;
        current = 1f;
        restoreSensitivity();
    }

    @Override
    public void onShutdown() {
        restoreSensitivity();
    }

    private void restoreSensitivity() {
        if (savedSensitivity != null) {
            mc().setSensitivity(savedSensitivity);
            savedSensitivity = null;
        }
    }

    @Override
    public String getPreviewText() {
        return Keys.name(key.getCode()) + "  ->  " + factor.format() + "x";
    }

    /** Called from the FOV hook (can be several times per frame). */
    public float applyFov(float fov) {
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, (now - lastUpdate) / 1_000_000_000f);
        lastUpdate = now;

        float target = isEnabled() && zooming ? factor.getFloat() : 1f;
        if (smooth.isOn()) {
            current += (target - current) * Math.min(1f, seconds * 14f);
            if (Math.abs(target - current) < 0.001f) {
                current = target;
            }
        } else {
            current = target;
        }
        return fov / current;
    }
}
