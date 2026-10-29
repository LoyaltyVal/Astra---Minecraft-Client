package dev.astra.client.features;

import dev.astra.client.ui.Panels;
import dev.astra.client.ui.TextField;
import dev.astra.client.ui.Theme;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.List;

/**
 * The radio, shown as the Radio tab of the Friends menu: what's playing, volume, the stations,
 * and a box to paste your own stream. Music keeps playing after the menu closes.
 */
public final class RadioPanel {

    private static final int TILE_H = 26;

    private final TextField url = new TextField("Paste an MP3 stream or .pls / .m3u link", 300, null);
    private boolean draggingVolume;
    private float scroll;
    private int x, y, w, h, gridTop, gridBottom, sliderX, sliderY, sliderW, formY;

    public RadioPanel() {
        url.set(Radio.get().getCustomUrl());
    }

    private static RadioPlayer player() {
        return RadioPlayer.get();
    }

    // ------------------------------------------------------------------ drawing

    public void draw(int x, int y, int w, int h, int mx, int my) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        RadioPlayer p = player();
        drawNowPlaying(mx, my, p);

        // Volume.
        sliderY = y + 56;
        Render.text("Volume", x + 2, sliderY - 3, Theme.SUBTEXT, false);
        sliderX = x + 8 + Render.width("Volume");
        String pct = Math.round(p.getVolume() * 100) + "%";
        sliderW = w - (sliderX - x) - Render.width("100%") - 8;
        if (draggingVolume) {
            Radio.get().setVolume((mx - sliderX) / (float) sliderW);
        }
        float v = p.getVolume();
        Render.round(sliderX, sliderY, sliderW, 4, 2, 0x40FFFFFF);
        Render.round(sliderX, sliderY, Math.max(4, (int) (sliderW * v)), 4, 2, Theme.accent());
        int knob = sliderX + (int) (sliderW * v);
        boolean knobHover = draggingVolume || Render.inside(mx, my, knob - 5, sliderY - 4, 10, 12);
        Render.round(knob - 4, sliderY - 3, 9, 9, 4, knobHover ? 0xFFFFFFFF : 0xFFE0E0EA);
        Render.text(pct, x + w - Render.width(pct), sliderY - 3, Theme.TEXT, false);

        drawStations(mx, my, p);
        drawForm(mx, my);
    }

    private void drawNowPlaying(int mx, int my, RadioPlayer p) {
        int bh = 46;
        Render.round(x, y, w, bh, 6, Colors.withAlpha(Theme.accent(), p.isActive() ? 0x38 : 0x18));
        equaliser(x + 9, y + 10, 24, 26, p.isPlaying() ? p.getLevel() : 0f, p.isPlaying());
        Radio.Station s = p.getStation();
        String title = s == null ? "Nothing playing" : s.name;
        String sub = p.isError() ? p.getStatus()
                : s == null ? "Pick a station below"
                : p.getNowPlaying() != null ? p.getNowPlaying()
                : p.isPlaying() ? s.genre : p.getStatus();
        String button = p.isActive() ? "Stop" : "Play";
        int bw = Panels.pillWidth(button);
        int textW = w - 52 - bw;
        Render.text(Render.trim(title, textW), x + 41, y + 11, Theme.TEXT, false);
        Render.text(Render.trim(sub, textW), x + 41, y + 24,
                p.isError() ? 0xFFFF7A90 : p.getNowPlaying() != null ? 0xFFD8D0FF : Theme.SUBTEXT, false);
        Panels.pill(x + w - bw - 8, y + 15, button, mx, my, p.isActive() ? 0x40FF5C7A : Colors.withAlpha(Theme.accent(), 0xC0),
                p.isActive() ? 0x80FF5C7A : Theme.accent());
    }

    /** Little bars that bounce with the music. */
    static void equaliser(int x, int y, int w, int h, float level, boolean on) {
        int bars = 5;
        int bw = Math.max(1, (w - (bars - 1) * 2) / bars);
        double t = System.currentTimeMillis() / 140.0;
        for (int i = 0; i < bars; i++) {
            float k = on ? (float) (0.25 + 0.75 * Math.min(1.0, level * 1.6 * (0.55 + 0.45 * Math.abs(Math.sin(t + i * 1.7))))) : 0.15f;
            int bh = Math.max(2, (int) (h * k));
            Render.rect(x + i * (bw + 2), y + h - bh, bw, bh, on ? Theme.accent() : 0x50FFFFFF);
        }
    }

    private void drawStations(int mx, int my, RadioPlayer p) {
        List<Radio.Station> list = Radio.get().stations();
        gridTop = y + 68;
        formY = y + h - 18;
        gridBottom = formY - 18; // room for the "resume" line
        int colW = (w - 6) / 2;
        int rows = (list.size() + 1) / 2;
        float max = Math.max(0, rows * (TILE_H + 4) - 4 - (gridBottom - gridTop));
        scroll = Math.max(0, Math.min(scroll, max));
        Radio.Station playing = p.getStation();
        Render.scissor(x - 2, gridTop, w + 4, gridBottom - gridTop);
        for (int i = 0; i < list.size(); i++) {
            Radio.Station s = list.get(i);
            int tx = x + (i % 2) * (colW + 6);
            int ty = gridTop + (i / 2) * (TILE_H + 4) - (int) scroll;
            if (ty + TILE_H < gridTop || ty > gridBottom) {
                continue;
            }
            boolean on = playing != null && playing.url.equals(s.url);
            boolean hover = my >= gridTop && my < gridBottom && Render.inside(mx, my, tx, ty, colW, TILE_H);
            Render.round(tx, ty, colW, TILE_H, 5, on ? Colors.withAlpha(Theme.accent(), 0x60) : hover ? 0x26FFFFFF : 0x12FFFFFF);
            if (on) {
                equaliser(tx + colW - 17, ty + 8, 11, 10, p.getLevel(), p.isPlaying());
            }
            Render.text(Render.trim(s.name, colW - 24), tx + 6, ty + 4, Theme.TEXT, false);
            Render.text(Render.trim(s.genre, colW - 24), tx + 6, ty + 14, Theme.SUBTEXT, false);
        }
        Render.endScissor();
        if (max > 0 && scroll < max) {
            Render.textCentered("▼", x + w / 2f, gridBottom - 7, 0x66FFFFFF, false);
        }
    }

    private String resumeLabel() {
        return Radio.get().isResume() ? "✓ Play again when the game starts" : "Play again when the game starts";
    }

    private void drawForm(int mx, int my) {
        int playW = Panels.pillWidth("Play link");
        url.draw(x, formY, w - playW - 6, 18);
        Panels.pill(x + w - playW, formY + 1, "Play link", mx, my, 0x40FFFFFF, 0x60FFFFFF);
        Render.text(Render.trim(resumeLabel(), w), x + 2, formY - 12, Radio.get().isResume() ? 0xFF8FE3BE : 0x88A0A0B0, false);
    }

    // ------------------------------------------------------------------ input

    /** Returns true when the click was inside the panel. */
    public boolean click(int mx, int my) {
        if (!Render.inside(mx, my, x, y, w, h)) {
            url.setFocused(false);
            return false;
        }
        RadioPlayer p = player();
        // Play / stop.
        String label = p.isActive() ? "Stop" : "Play";
        int bw = Panels.pillWidth(label);
        if (Render.inside(mx, my, x + w - bw - 8, y + 15, bw, 16)) {
            url.setFocused(false);
            if (p.isActive()) {
                Radio.get().stop();
            } else {
                List<Radio.Station> list = Radio.get().stations();
                Radio.Station s = p.getStation() != null ? p.getStation() : list.isEmpty() ? null : list.get(0);
                if (s != null) {
                    Radio.get().play(s);
                }
            }
            return true;
        }
        // Volume.
        if (Render.inside(mx, my, sliderX - 4, sliderY - 5, sliderW + 8, 14)) {
            draggingVolume = true;
            Radio.get().setVolume((mx - sliderX) / (float) sliderW);
            return true;
        }
        // Resume toggle.
        if (Render.inside(mx, my, x, formY - 13, Math.min(w, Render.width(resumeLabel()) + 4), 10)) {
            Radio.get().setResume(!Radio.get().isResume());
            return true;
        }
        // Custom link.
        int playW = Panels.pillWidth("Play link");
        if (Render.inside(mx, my, x + w - playW, formY + 1, playW, 16)) {
            playLink();
            return true;
        }
        if (url.click(mx, my)) {
            return true;
        }
        // Stations.
        if (Render.inside(mx, my, x, gridTop, w, gridBottom - gridTop)) {
            int colW = (w - 6) / 2;
            int col = mx < x + colW + 3 ? 0 : 1;
            int row = (int) ((my - gridTop + scroll) / (TILE_H + 4));
            int i = row * 2 + col;
            List<Radio.Station> list = Radio.get().stations();
            if (i >= 0 && i < list.size()) {
                Radio.Station s = list.get(i);
                if (p.getStation() != null && p.getStation().url.equals(s.url) && p.isActive()) {
                    Radio.get().stop();
                } else {
                    Radio.get().play(s);
                }
            }
        }
        return true;
    }

    public void released() {
        if (draggingVolume) {
            draggingVolume = false;
            Radio.get().saveVolume();
        }
    }

    public void scroll(int mx, int my, int direction) {
        if (my < gridTop) {
            Radio.get().setVolume(player().getVolume() + direction * 0.05f);
            Radio.get().saveVolume();
        } else {
            scroll -= direction * (TILE_H + 4);
        }
    }

    private void playLink() {
        String u = url.get().trim();
        if (!u.matches("https?://.+")) {
            url.setFocused(true);
            return;
        }
        Radio.get().setCustomUrl(u);
        Radio.get().play(Radio.custom(u));
        url.setFocused(false);
    }

    /** Returns true when the key went to the link box. */
    public boolean charTyped(char c, int keyCode) {
        if (!url.isFocused()) {
            return false;
        }
        if (url.type(c, keyCode)) {
            playLink();
        }
        return true;
    }

    public boolean isTyping() {
        return url.isFocused();
    }

    public void stopTyping() {
        url.setFocused(false);
    }
}
