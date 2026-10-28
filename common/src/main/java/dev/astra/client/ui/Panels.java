package dev.astra.client.ui;

import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

/** The floating glass-card look shared by the Friends, Quick Join, Screenshots and Waypoints screens. */
public final class Panels {

    private Panels() {
    }

    /** 0..1 eased opening animation from the moment a screen opened. */
    public static float ease(long openedAt) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static void backdrop(int width, int height, float ease) {
        Render.rect(0, 0, width, height, Colors.withAlpha(0xFF05050A, (int) (0x88 * ease)));
    }

    /** The card: a soft accent glow, the glass body and the animated colour strip on top. */
    public static void card(int x, int y, int w, int h, float ease) {
        int accent = Theme.accent();
        for (int i = 6; i >= 1; i--) {
            Render.round(x - i * 2, y - i * 2, w + i * 4, h + i * 4, 8 + i * 2, Colors.withAlpha(accent, (int) (6 * ease)));
        }
        Render.round(x, y, w, h, 8, Colors.withAlpha(0xFF0E0E14, (int) (0xF0 * ease)));
        aurora(x + 6, y, w - 12, ease);
    }

    public static void aurora(int x, int y, int w, float ease) {
        double time = System.currentTimeMillis() / 900.0;
        int accent = Theme.accent();
        int second = Colors.lerp(accent, 0xFF5AC8FA, 0.7f);
        for (int i = 0; i < w; i += 2) {
            float k = (float) (0.5 + 0.5 * Math.sin(i / 38.0 + time));
            int c = Colors.lerp(accent, second, k);
            int a = (int) ((120 + 100 * Math.sin(i / 23.0 - time * 1.3)) * ease);
            Render.rect(x + i, y, 2, 2, Colors.withAlpha(c, Math.max(30, Math.min(255, a))));
        }
    }

    /** A rounded button; returns its width so rows of them can be laid out right to left. */
    public static int pill(int x, int y, String label, int mx, int my, int color, int hover) {
        int w = Render.width(label) + 14;
        Render.round(x, y, w, 16, 8, Render.inside(mx, my, x, y, w, 16) ? hover : color);
        Render.textCentered(label, x + w / 2f, y + 4, Theme.TEXT, false);
        return w;
    }

    public static int pillWidth(String label) {
        return Render.width(label) + 14;
    }

    public static void title(String text, int x, int y) {
        Render.text(text, x, y, Theme.TEXT, false);
    }

    public static void hint(String text, int centerX, int y) {
        Render.textCentered(text, centerX, y, 0x66FFFFFF, false);
    }
}
