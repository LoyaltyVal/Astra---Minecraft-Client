package dev.astra.client.module.impl.hud;

import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.ui.Theme;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

/** A compass strip across the top of the screen. */
public final class DirectionModule extends HudModule {

    private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final int HEIGHT = 20;

    private final NumberSetting width;
    private final NumberSetting range;
    private final BooleanSetting showDegrees;
    private final NumberSetting opacity;

    public DirectionModule() {
        super("Direction HUD", "A compass that shows where you're facing.", false, CENTER, 44);
        width = setting(new NumberSetting("Width", 200, 100, 360, 10));
        range = setting(new NumberSetting("Field of View", 180, 90, 360, 15));
        showDegrees = setting(new BooleanSetting("Show Degrees", true));
        opacity = setting(new NumberSetting("Background Opacity", 30, 0, 100, 1));
    }

    @Override
    public int getWidth() {
        return width.getInt();
    }

    @Override
    public int getHeight() {
        return HEIGHT + (showDegrees.isOn() ? 9 : 0);
    }

    @Override
    protected void render(boolean editor) {
        float yaw = mc().isInGame() ? mc().getPlayerYaw() : 137f;
        float heading = ((yaw % 360f) + 360f) % 360f; // 0 = south, 90 = west, 180 = north
        int w = getWidth();
        int top = showDegrees.isOn() ? 9 : 0;
        float pxPerDeg = w / range.getFloat();

        Render.rect(0, top, w, HEIGHT, Colors.withAlpha(0, (int) (opacity.get() * 2.55)));

        int start = (int) Math.floor((heading - range.getFloat() / 2) / 15f) * 15;
        for (int deg = start; deg <= heading + range.getFloat() / 2 + 15; deg += 15) {
            float x = w / 2f + (deg - heading) * pxPerDeg;
            if (x < 1 || x > w - 2) {
                continue;
            }
            int norm = ((deg % 360) + 360) % 360;
            if (norm % 45 == 0) {
                String label = LABELS[norm / 45];
                if (x - Render.width(label) / 2f < 0 || x + Render.width(label) / 2f > w) {
                    Render.rect((int) x, top + 2, 1, 5, 0xFFFFFFFF);
                    continue;
                }
                boolean cardinal = label.length() == 1;
                Render.rect((int) x, top + 2, 1, 5, 0xFFFFFFFF);
                Render.textCentered(label, x + 0.5f, top + 9, cardinal ? 0xFFFFFFFF : 0xFFB8B8C8, true);
            } else {
                Render.rect((int) x, top + 2, 1, 3, 0x99FFFFFF);
            }
        }

        // Centre marker.
        int cx = w / 2;
        Render.rect(cx - 1, top, 3, 2, Theme.accent());
        Render.rect(cx, top + 2, 1, 2, Theme.accent());
        if (showDegrees.isOn()) {
            Render.textCentered(String.valueOf(Math.round(heading) % 360), cx + 0.5f, 0, 0xFFFFFFFF, true);
        }
    }
}
