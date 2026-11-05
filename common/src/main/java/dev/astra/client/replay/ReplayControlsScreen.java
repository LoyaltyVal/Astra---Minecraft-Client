package dev.astra.client.replay;

import dev.astra.client.input.Mouse;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.List;

/**
 * The replay controls (Escape while watching): play/pause, the timeline, speed, which player to
 * watch, hide the HUD, and leave. The replay keeps playing behind it; Escape goes back to flying.
 *
 *   [Free Camera] [Steve] [Alex] ...                                  [Hide HUD] [Exit Replay]
 *   [>]  1:23 / 5:00  [=========o-----------------------------]  0.25x 0.5x 1x 2x 4x
 */
public final class ReplayControlsScreen extends UiScreen {

    private static final float[] SPEEDS = {0.25f, 0.5f, 1f, 2f, 4f};
    private static final int BAR_H = 34;

    private boolean dragging;
    private int dragTarget = -1;

    // Layout.
    private int barY, playX, timelineX, timelineW, speedsX;

    private void layout() {
        barY = height - BAR_H - 6;
        playX = 12;
        timelineX = playX + 20 + 6 + Render.width("00:00 / 00:00") + 8;
        int speedsW = 0;
        for (float s : SPEEDS) {
            speedsW += Render.width(Replay.speedLabel(s)) + 10 + 2;
        }
        speedsX = width - 12 - speedsW;
        timelineW = Math.max(40, speedsX - 10 - timelineX);
    }

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        Replay r = Replay.get();
        if (!r.isPlaying()) {
            close();
            return;
        }
        layout();
        int duration = Math.max(1, r.duration());

        // While dragging, show where you'll land; jump when the mouse is released.
        if (dragging) {
            dragTarget = timelineTime(mx, duration);
            if (!Mouse.isButtonDown(0)) {
                dragging = false;
                r.seek(dragTarget);
                dragTarget = -1;
            }
        }

        // Bottom bar.
        Render.round(6, barY, width - 12, BAR_H, 6, 0xD80E0E14);
        boolean playHot = Render.inside(mx, my, playX, barY + 7, 20, 20);
        Render.round(playX, barY + 7, 20, 20, 4, playHot ? Theme.accent() : Colors.withAlpha(Theme.accent(), 0xB0));
        if (r.isPaused()) {
            // Play triangle.
            for (int i = 0; i < 6; i++) {
                Render.rect(playX + 7 + i, barY + 11 + i / 2, 1, 12 - (i / 2) * 2, Theme.TEXT);
            }
        } else {
            Render.rect(playX + 6, barY + 11, 3, 12, Theme.TEXT);
            Render.rect(playX + 11, barY + 11, 3, 12, Theme.TEXT);
        }
        int shown = dragTarget >= 0 ? dragTarget : r.time();
        Render.text(Replay.clock(shown) + " / " + Replay.clock(r.duration()), playX + 26, barY + 13, Theme.TEXT, false);

        // Timeline.
        int ty = barY + 15;
        boolean tlHot = Render.inside(mx, my, timelineX, ty - 6, timelineW, 16);
        Render.round(timelineX, ty, timelineW, 4, 2, 0x40FFFFFF);
        int filled = (int) (timelineW * (shown / (float) duration));
        Render.round(timelineX, ty, Math.max(4, filled), 4, 2, Theme.accent());
        Render.round(timelineX + filled - 4, ty - 3, 9, 10, 4, tlHot || dragging ? Theme.TEXT : 0xFFE0E0EA);
        if (tlHot && !dragging) {
            int at = timelineTime(mx, duration);
            String tip = Replay.clock(at);
            int tw = Render.width(tip) + 8;
            Render.round(mx - tw / 2, ty - 18, tw, 11, 3, 0xE0101014);
            Render.textCentered(tip, mx, ty - 16, Theme.TEXT, false);
        }

        // Speeds.
        int sx = speedsX;
        for (float s : SPEEDS) {
            String label = Replay.speedLabel(s);
            int w = Render.width(label) + 10;
            boolean on = Math.abs(r.speed() - s) < 0.01f;
            boolean hot = Render.inside(mx, my, sx, barY + 10, w, 14);
            Render.round(sx, barY + 10, w, 14, 3, on ? Theme.accent() : hot ? 0x40FFFFFF : 0x20FFFFFF);
            Render.textCentered(label, sx + w / 2f, barY + 13, Theme.TEXT, false);
            sx += w + 2;
        }

        drawTop(mx, my);
        Render.textCentered("Esc: back to flying   ·   Space: pause   ·   ← →: 5 seconds", width / 2f, barY - 11, 0xB0FFFFFF, true);
    }

    private int timelineTime(int mx, int duration) {
        float k = Math.max(0f, Math.min(1f, (mx - timelineX) / (float) timelineW));
        return (int) (k * duration);
    }

    /** Camera choice on the left, Hide HUD and Exit on the right. */
    private void drawTop(int mx, int my) {
        ReplayBackend b = Replay.backend();
        if (b == null) {
            return;
        }
        int y = 8;
        int x = 8;
        Render.text("CAMERA", x, y, Theme.SUBTEXT, true);
        y += 11;
        String watching = b.spectating();
        x = chip("Free Camera", x, y, watching == null, mx, my) + 3;
        List<String> players = b.players();
        int shown = 0;
        for (String p : players) {
            if (x + Render.width(p) + 12 > width - 160) {
                break;
            }
            x = chip(p, x, y, p.equals(watching), mx, my) + 3;
            shown++;
        }
        if (shown < players.size()) {
            Render.text("+" + (players.size() - shown) + " more", x + 2, y + 3, Theme.SUBTEXT, false);
        }

        int rx = width - 8;
        rx = chipRight("Exit Replay", rx, 8, false, mx, my, 0xC0FF5C7A) - 3;
        chipRight(b.isHudHidden() ? "Show HUD" : "Hide HUD", rx, 8, false, mx, my, 0);
        ReplayInfo info = Replay.get().info();
        if (info != null) {
            String what = (info.server.isEmpty() ? "Replay" : info.server) + (info.playerName.isEmpty() ? "" : "  ·  " + info.playerName);
            Render.text(Render.trim(what, 200), width - 8 - Render.width(Render.trim(what, 200)), 26, Theme.SUBTEXT, true);
        }
    }

    /** A chip button; returns its right edge. */
    private int chip(String label, int x, int y, boolean on, int mx, int my) {
        int w = Render.width(label) + 12;
        boolean hot = Render.inside(mx, my, x, y, w, 14);
        Render.round(x, y, w, 14, 3, on ? Theme.accent() : hot ? 0xC0303040 : 0xB0101014);
        Render.textCentered(label, x + w / 2f, y + 3, Theme.TEXT, false);
        return x + w;
    }

    /** A chip ending at {@code right}; returns its left edge. */
    private int chipRight(String label, int right, int y, boolean on, int mx, int my, int color) {
        int w = Render.width(label) + 12;
        int x = right - w;
        boolean hot = Render.inside(mx, my, x, y, w, 14);
        int bg = color != 0 ? (hot ? color | 0xFF000000 : color) : on ? Theme.accent() : hot ? 0xC0303040 : 0xB0101014;
        Render.round(x, y, w, 14, 3, bg);
        Render.textCentered(label, x + w / 2f, y + 3, Theme.TEXT, false);
        return x;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        Replay r = Replay.get();
        ReplayBackend b = Replay.backend();
        layout();
        if (Render.inside(mx, my, playX, barY + 7, 20, 20)) {
            r.setPaused(!r.isPaused());
            return;
        }
        if (Render.inside(mx, my, timelineX, barY + 9, timelineW, 16)) {
            dragging = true;
            dragTarget = timelineTime(mx, Math.max(1, r.duration()));
            return;
        }
        int sx = speedsX;
        for (float s : SPEEDS) {
            int w = Render.width(Replay.speedLabel(s)) + 10;
            if (Render.inside(mx, my, sx, barY + 10, w, 14)) {
                r.setSpeed(s);
                return;
            }
            sx += w + 2;
        }
        if (b == null) {
            return;
        }
        // Top right.
        int exitW = Render.width("Exit Replay") + 12;
        if (Render.inside(mx, my, width - 8 - exitW, 8, exitW, 14)) {
            r.exit();
            return;
        }
        String hud = b.isHudHidden() ? "Show HUD" : "Hide HUD";
        int hudW = Render.width(hud) + 12;
        if (Render.inside(mx, my, width - 8 - exitW - 3 - hudW, 8, hudW, 14)) {
            b.setHudHidden(!b.isHudHidden());
            return;
        }
        // Camera chips.
        int x = 8;
        int y = 19;
        int w = Render.width("Free Camera") + 12;
        if (Render.inside(mx, my, x, y, w, 14)) {
            b.spectate(null);
            return;
        }
        x += w + 3;
        for (String p : b.players()) {
            if (x + Render.width(p) + 12 > width - 160) {
                break;
            }
            int pw = Render.width(p) + 12;
            if (Render.inside(mx, my, x, y, pw, 14)) {
                b.spectate(p);
                return;
            }
            x += pw + 3;
        }
    }

    @Override
    protected void keyPressed(int keyCode) {
        Replay r = Replay.get();
        if (keyCode == dev.astra.client.input.Keyboard.KEY_SPACE) {
            r.setPaused(!r.isPaused());
        } else if (keyCode == dev.astra.client.input.Keyboard.KEY_LEFT) {
            r.seek(r.time() - 5000);
        } else if (keyCode == dev.astra.client.input.Keyboard.KEY_RIGHT) {
            r.seek(r.time() + 5000);
        }
    }
}
