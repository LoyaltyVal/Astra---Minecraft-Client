package dev.astra.client.social;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.EmotesModule;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;
import dev.astra.client.input.Keyboard;

/**
 * The emote wheel. Hold the key, point at an emote and let go; or tap the key and click one.
 */
public final class EmoteWheelScreen extends UiScreen {

    private final int key;
    private final long openedAt = System.currentTimeMillis();
    private boolean released;
    private int hovered = -1;

    public EmoteWheelScreen(int key) {
        this.key = key;
    }

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 140f);
        float ease = 1f - (1f - t) * (1f - t);
        int cx = width / 2;
        int cy = height / 2;
        int count = Emotes.IDS.length;
        int radius = (int) (Math.min(78, Math.min(width, height) / 2 - 30) * (0.7f + 0.3f * ease));

        // Which slice the mouse points at (none while it's near the centre).
        double dx = mx - cx;
        double dy = my - cy;
        hovered = -1;
        if (dx * dx + dy * dy > 16 * 16) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2; // 0 = straight up
            if (angle < 0) {
                angle += Math.PI * 2;
            }
            hovered = (int) Math.round(angle / (Math.PI * 2 / count)) % count;
        }

        // Backdrop ring.
        Render.rect(0, 0, width, height, Colors.withAlpha(0xFF000000, (int) (0x50 * ease)));
        ring(cx, cy, radius + 22, radius - 22, Colors.withAlpha(0xFF0E0E14, (int) (0xC8 * ease)));
        if (hovered >= 0) {
            double a = hovered * Math.PI * 2 / count - Math.PI / 2;
            arc(cx, cy, radius + 22, radius - 22, a - Math.PI / count, a + Math.PI / count,
                    Colors.withAlpha(Theme.accent(), (int) (0x90 * ease)));
        }

        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count - Math.PI / 2;
            float ix = cx + (float) Math.cos(a) * radius;
            float iy = cy + (float) Math.sin(a) * radius;
            boolean hot = i == hovered;
            Render.textCenteredScaled(Emotes.NAMES[i], ix, iy - (hot ? 5 : 4), hot ? 1.15f : 1f,
                    hot ? Theme.TEXT : 0xFFC8C8D4, true);
        }

        // Centre: the hovered emote's name, or a hint.
        Render.round(cx - 15, cy - 15, 30, 30, 15, Colors.withAlpha(0xFF14141C, (int) (0xE6 * ease)));
        Render.textCenteredScaled("✦", cx, cy - 6, 1.4f, Colors.withAlpha(Theme.accent(), 0xFF), false);
        String hint = released ? "Click an emote" : "Release to play";
        Render.textCentered(hint, cx, cy + radius + 30, 0xAAFFFFFF, true);

        // Hold-and-release: play whatever is pointed at when the key goes up.
        if (!released && !Keyboard.isKeyDown(key)) {
            released = true;
            if (hovered >= 0) {
                play(hovered);
            }
        }
    }

    private void play(int index) {
        EmotesModule m = Astra.get().getModules().get(EmotesModule.class);
        if (m != null) {
            m.play(Emotes.IDS[index]);
        }
        close();
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button == 0 && hovered >= 0) {
            play(hovered);
        } else if (button == 1) {
            close();
        }
    }

    /** A filled ring as triangle strips. */
    private static void ring(int cx, int cy, int outer, int inner, int argb) {
        arc(cx, cy, outer, inner, 0, Math.PI * 2, argb);
    }

    private static void arc(int cx, int cy, int outer, int inner, double from, double to, int argb) {
        int steps = Math.max(8, (int) ((to - from) * 24));
        float[] xy = new float[(steps + 1) * 4];
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            xy[i * 4] = cx + c * outer;
            xy[i * 4 + 1] = cy + s * outer;
            xy[i * 4 + 2] = cx + c * inner;
            xy[i * 4 + 3] = cy + s * inner;
        }
        Astra.platform().fillTriangleStrip(xy, argb);
    }
}
