package dev.astra.client.social;

import dev.astra.client.ui.Theme;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.ArrayList;
import java.util.List;

/** Small toasts in the top-right corner (friend requests, messages, friends coming online). */
public final class Notifications {

    public static final int MESSAGE = 0xFF5AC8FA;
    private static final long SHOW_MS = 5000;
    private static final long SLIDE_MS = 220;
    private static final int MAX = 4;

    private static final class Toast {
        final String title;
        final String body;
        final int color;
        final long at = System.currentTimeMillis();

        Toast(String title, String body, int color) {
            this.title = title;
            this.body = body;
            this.color = color;
        }
    }

    private static final List<Toast> TOASTS = new ArrayList<Toast>();

    private Notifications() {
    }

    /** Safe to call from any thread. */
    public static void push(String title, String body, int color) {
        synchronized (TOASTS) {
            TOASTS.add(new Toast(title == null ? "" : title, body == null ? "" : body, color));
            while (TOASTS.size() > MAX) {
                TOASTS.remove(0);
            }
        }
    }

    /** Draws the toasts; call every frame from the HUD hook. */
    public static void render(int screenWidth) {
        List<Toast> copy;
        synchronized (TOASTS) {
            long now = System.currentTimeMillis();
            for (int i = TOASTS.size() - 1; i >= 0; i--) {
                if (now - TOASTS.get(i).at > SHOW_MS) {
                    TOASTS.remove(i);
                }
            }
            copy = new ArrayList<Toast>(TOASTS);
        }
        int w = 160;
        int y = 6;
        long now = System.currentTimeMillis();
        for (Toast t : copy) {
            long age = now - t.at;
            float in = Math.min(1f, age / (float) SLIDE_MS);
            float out = Math.min(1f, (SHOW_MS - age) / (float) SLIDE_MS);
            float k = Math.max(0f, Math.min(in, out));
            k = k * k * (3 - 2 * k);
            int x = screenWidth - 6 - (int) (w * k) + (int) ((1 - k) * 6);
            int h = 30;
            int alpha = (int) (0xE6 * k);
            if (alpha < 8) {
                continue;
            }
            Render.round(x, y, w, h, 4, Colors.withAlpha(0xFF101016, alpha));
            Render.rect(x, y + 4, 2, h - 8, Colors.withAlpha(t.color, alpha));
            // A thin progress line showing how long the toast stays.
            int left = (int) ((w - 8) * Math.max(0f, 1f - age / (float) SHOW_MS));
            Render.rect(x + 4, y + h - 2, left, 1, Colors.withAlpha(Theme.accent(), alpha / 2));
            int textAlpha = Math.max(alpha, 0x20);
            Render.text(Render.trim(t.title, w - 12), x + 7, y + 5, Colors.withAlpha(0xFFFFFFFF, textAlpha), false);
            Render.text(Render.trim(t.body, w - 12), x + 7, y + 17, Colors.withAlpha(0xFFB4B4C4, textAlpha), false);
            y += h + 4;
        }
    }
}
