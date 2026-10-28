package dev.astra.client.features;

import dev.astra.client.ui.Panels;
import dev.astra.client.ui.TextField;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.List;

/** The radio: pick a station, see what's playing, set the volume, or paste your own stream. */
public final class RadioScreen extends UiScreen {

    private static final int TILE_H = 26;

    private final long openedAt = System.currentTimeMillis();
    private final TextField url = new TextField("Paste an MP3 stream or .pls / .m3u link", 300, null);
    private boolean draggingVolume;
    private float scroll;

    private int px, py, pw, ph, gridTop, gridBottom, sliderX, sliderY, sliderW, formY;

    private static RadioPlayer player() {
        return RadioPlayer.get();
    }

    @Override
    protected void init() {
        url.set(Radio.get().getCustomUrl());
    }

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        float ease = Panels.ease(openedAt);
        Panels.backdrop(width, height, ease);
        pw = Math.min(440, width - 24);
        ph = Math.min(268, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - ease) * 12);
        Panels.card(px, py, pw, ph, ease);

        RadioPlayer p = player();
        Render.text("Radio", px + 12, py + 11, Theme.TEXT, false);
        String status = p.getStatus();
        Render.text(status, px + pw - 12 - Render.width(status), py + 11,
                p.isError() ? 0xFFFF7A90 : p.isPlaying() ? 0xFF8FE3BE : Theme.SUBTEXT, false);

        drawNowPlaying(mx, my, p);

        // Volume.
        sliderY = py + 84;
        Render.text("Volume", px + 12, sliderY - 3, Theme.SUBTEXT, false);
        sliderX = px + 18 + Render.width("Volume");
        sliderW = pw - (sliderX - px) - 46;
        if (draggingVolume) {
            Radio.get().setVolume((mx - sliderX) / (float) sliderW);
        }
        float v = p.getVolume();
        Render.round(sliderX, sliderY, sliderW, 4, 2, 0x40FFFFFF);
        Render.round(sliderX, sliderY, Math.max(4, (int) (sliderW * v)), 4, 2, Theme.accent());
        int knob = sliderX + (int) (sliderW * v);
        boolean knobHover = draggingVolume || Render.inside(mx, my, knob - 5, sliderY - 4, 10, 12);
        Render.round(knob - 4, sliderY - 3, 9, 9, 4, knobHover ? 0xFFFFFFFF : 0xFFE0E0EA);
        String pct = Math.round(v * 100) + "%";
        Render.text(pct, px + pw - 12 - Render.width(pct), sliderY - 3, Theme.TEXT, false);

        drawStations(mx, my, p);
        drawForm(mx, my);
        Panels.hint("Music keeps playing when you close this  |  Esc to close", width / 2, py + ph + 4);
    }

    private void drawNowPlaying(int mx, int my, RadioPlayer p) {
        int x = px + 10;
        int y = py + 26;
        int w = pw - 20;
        int h = 46;
        Render.round(x, y, w, h, 6, Colors.withAlpha(Theme.accent(), p.isActive() ? 0x38 : 0x18));
        equaliser(x + 10, y + 10, 26, 26, p.isPlaying() ? p.getLevel() : 0f, p.isPlaying());
        Radio.Station s = p.getStation();
        String title = s == null ? "Nothing playing" : s.name;
        String sub = s == null ? "Pick a station below" : p.getNowPlaying() != null ? p.getNowPlaying() : s.genre;
        String button = p.isActive() ? "Stop" : "Play";
        int bw = Panels.pillWidth(button);
        Render.text(Render.trim(title, w - 70 - bw), x + 46, y + 11, Theme.TEXT, false);
        Render.text(Render.trim(sub, w - 70 - bw), x + 46, y + 24, p.getNowPlaying() != null ? 0xFFD8D0FF : Theme.SUBTEXT, false);
        Panels.pill(x + w - bw - 10, y + 15, button, mx, my, p.isActive() ? 0x40FF5C7A : Colors.withAlpha(Theme.accent(), 0xC0),
                p.isActive() ? 0x80FF5C7A : Theme.accent());
    }

    /** Little bars that bounce with the music. */
    private static void equaliser(int x, int y, int w, int h, float level, boolean on) {
        int bars = 5;
        int bw = (w - (bars - 1) * 2) / bars;
        double t = System.currentTimeMillis() / 140.0;
        for (int i = 0; i < bars; i++) {
            float k = on ? (float) (0.25 + 0.75 * Math.min(1.0, level * 1.6 * (0.55 + 0.45 * Math.abs(Math.sin(t + i * 1.7))))) : 0.15f;
            int bh = Math.max(2, (int) (h * k));
            Render.rect(x + i * (bw + 2), y + h - bh, bw, bh, on ? Theme.accent() : 0x50FFFFFF);
        }
    }

    private void drawStations(int mx, int my, RadioPlayer p) {
        List<Radio.Station> list = Radio.get().stations();
        gridTop = py + 98;
        formY = py + ph - 28;
        gridBottom = formY - 20; // room for the "resume" line
        int x = px + 10;
        int w = pw - 20;
        int colW = (w - 6) / 2;
        int rows = (list.size() + 1) / 2;
        float max = Math.max(0, rows * (TILE_H + 4) - (gridBottom - gridTop));
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
                equaliser(tx + colW - 18, ty + 8, 11, 10, p.getLevel(), p.isPlaying());
            }
            Render.text(Render.trim(s.name, colW - 26), tx + 7, ty + 4, Theme.TEXT, false);
            Render.text(Render.trim(s.genre, colW - 26), tx + 7, ty + 14, Theme.SUBTEXT, false);
        }
        Render.endScissor();
    }

    private void drawForm(int mx, int my) {
        int x = px + 10;
        int w = pw - 20;
        int playW = Panels.pillWidth("Play link");
        url.draw(x, formY, w - playW - 6, 18);
        Panels.pill(x + w - playW, formY + 1, "Play link", mx, my, 0x40FFFFFF, 0x60FFFFFF);
        String resume = Radio.get().isResume() ? "✓ Resume when the game starts" : "Resume when the game starts";
        Render.text(resume, px + 12, formY - 12, Radio.get().isResume() ? 0xFF8FE3BE : 0x88A0A0B0, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        RadioPlayer p = player();
        // Play / stop.
        String label = p.isActive() ? "Stop" : "Play";
        int bw = Panels.pillWidth(label);
        int x = px + 10;
        int w = pw - 20;
        if (Render.inside(mx, my, x + w - bw - 10, py + 26 + 15, bw, 16)) {
            if (p.isActive()) {
                Radio.get().stop();
            } else {
                Radio.Station s = p.getStation() != null ? p.getStation() : Radio.get().stations().get(0);
                Radio.get().play(s);
            }
            return;
        }
        // Volume.
        if (Render.inside(mx, my, sliderX - 4, sliderY - 5, sliderW + 8, 14)) {
            draggingVolume = true;
            Radio.get().setVolume((mx - sliderX) / (float) sliderW);
            return;
        }
        // Resume toggle.
        String resume = Radio.get().isResume() ? "✓ Resume when the game starts" : "Resume when the game starts";
        if (Render.inside(mx, my, px + 12, formY - 13, Render.width(resume), 10)) {
            Radio.get().setResume(!Radio.get().isResume());
            return;
        }
        // Custom link.
        int playW = Panels.pillWidth("Play link");
        if (Render.inside(mx, my, x + w - playW, formY + 1, playW, 16)) {
            playLink();
            return;
        }
        if (url.click(mx, my)) {
            return;
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
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        if (draggingVolume) {
            draggingVolume = false;
            Radio.get().saveVolume();
        }
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
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

    @Override
    public void charTyped(char c, int keyCode) {
        if (url.type(c, keyCode)) {
            playLink();
        }
    }
}
