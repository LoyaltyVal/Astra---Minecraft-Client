package dev.astra.client.features;

import dev.astra.client.ui.Panels;
import dev.astra.client.ui.TextField;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;
import dev.astra.client.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/** The waypoint list for the world you're in: add, rename, recolour, hide and delete. */
public final class WaypointsScreen extends UiScreen {

    private static final int ROW = 24;

    private final long openedAt = System.currentTimeMillis();
    private final TextField newName = new TextField("Name (optional)", 32, null);
    private final TextField rename = new TextField("Name", 32, null);
    private Waypoints.Waypoint renaming;
    private float scroll;
    private String notice;
    private long noticeAt;

    private int px, py, pw, ph, listTop, listBottom, formY;

    private void say(String text) {
        notice = text;
        noticeAt = System.currentTimeMillis();
    }

    /** Current dimension first, then the others. */
    private List<Waypoints.Waypoint> rows() {
        int dim = mc().getDimension();
        List<Waypoints.Waypoint> out = new ArrayList<Waypoints.Waypoint>();
        for (Waypoints.Waypoint w : Waypoints.get().here()) {
            if (w.dimension == dim) {
                out.add(w);
            }
        }
        for (Waypoints.Waypoint w : Waypoints.get().here()) {
            if (w.dimension != dim) {
                out.add(w);
            }
        }
        return out;
    }

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        float ease = Panels.ease(openedAt);
        Panels.backdrop(width, height, ease);
        pw = Math.min(400, width - 24);
        ph = Math.min(260, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - ease) * 12);
        Panels.card(px, py, pw, ph, ease);

        Render.text("Waypoints", px + 12, py + 11, Theme.TEXT, false);
        Render.text(Render.trim(Waypoints.worldLabel(), pw - 150), px + 18 + Render.width("Waypoints"), py + 11, Theme.SUBTEXT, false);
        String coords = (int) Math.floor(mc().getPlayerX()) + ", " + (int) Math.floor(mc().getPlayerY()) + ", " + (int) Math.floor(mc().getPlayerZ());
        Render.text(coords, px + pw - 12 - Render.width(coords), py + 11, 0x88A0A0B0, false);

        listTop = py + 28;
        formY = py + ph - 28;
        listBottom = formY - 6;
        List<Waypoints.Waypoint> rows = rows();
        float max = Math.max(0, rows.size() * ROW - (listBottom - listTop));
        scroll = Math.max(0, Math.min(scroll, max));

        if (rows.isEmpty()) {
            Render.textCentered("No waypoints in this world yet", px + pw / 2f, py + ph / 2f - 12, Theme.TEXT, false);
            Render.textCentered("Add one below, or press N in game where you stand", px + pw / 2f, py + ph / 2f, Theme.SUBTEXT, false);
        }

        int x = px + 10;
        int w = pw - 20;
        int dim = mc().getDimension();
        Render.scissor(x - 2, listTop, w + 4, listBottom - listTop);
        int y = listTop - (int) scroll;
        for (Waypoints.Waypoint wp : rows) {
            if (y + ROW >= listTop && y <= listBottom) {
                boolean hover = my >= listTop && my < listBottom && Render.inside(mx, my, x, y, w, ROW - 2);
                boolean here = wp.dimension == dim;
                Render.round(x, y, w, ROW - 2, 5, hover ? 0x22FFFFFF : 0x10FFFFFF);
                // Colour dot (click to change).
                Render.round(x + 6, y + 6, 10, 10, 5, wp.visible ? wp.color : Colors.withAlpha(wp.color, 0x50));
                if (wp == renaming) {
                    rename.draw(x + 22, y + 3, Math.min(160, w - 140), 16);
                } else {
                    Render.text(Render.trim(wp.name, w - 170), x + 22, y + 3, wp.visible ? Theme.TEXT : Theme.SUBTEXT, false);
                }
                String sub = wp.x + ", " + wp.y + ", " + wp.z + (here ? "" : "  ·  " + Waypoints.dimensionName(wp.dimension));
                if (wp != renaming) {
                    Render.text(sub, x + 22, y + 12, 0x88A0A0B0, false);
                }
                int right = x + w - 4;
                if (here) {
                    double dx = wp.x + 0.5 - mc().getPlayerX();
                    double dy = wp.y + 1.0 - mc().getPlayerY();
                    double dz = wp.z + 0.5 - mc().getPlayerZ();
                    String d = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
                    Render.text(d, right - 64 - Render.width(d), y + 7, Theme.SUBTEXT, false);
                }
                icon(right - 14, y + 4, "✕", mx, my, 0x40FF5C7A);
                icon(right - 30, y + 4, wp.visible ? "◉" : "○", mx, my, 0x30FFFFFF);
                icon(right - 46, y + 4, "✎", mx, my, 0x30FFFFFF);
            }
            y += ROW;
        }
        Render.endScissor();

        // Add form.
        int addW = Panels.pillWidth("+ Add here");
        newName.draw(x, formY, w - addW - 6, 18);
        Panels.pill(x + w - addW, formY + 1, "+ Add here", mx, my, Colors.withAlpha(Theme.accent(), 0xC0), Theme.accent());

        if (notice != null && System.currentTimeMillis() - noticeAt < 4000) {
            Render.text(notice, px + 10, py - 12, 0xFF8FE3BE, true);
        }
        Panels.hint("Click the dot to change colour  |  N adds one in game  |  Esc to close", width / 2, py + ph + 4);
    }

    private static void icon(int x, int y, String s, int mx, int my, int color) {
        boolean hover = Render.inside(mx, my, x, y, 14, 14);
        Render.round(x, y, 14, 14, 4, hover ? Colors.withAlpha(color, 0xC0) : color);
        Render.textCentered(s, x + 7, y + 3, Theme.TEXT, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        int x = px + 10;
        int w = pw - 20;
        int addW = Panels.pillWidth("+ Add here");
        if (Render.inside(mx, my, x + w - addW, formY + 1, addW, 16)) {
            add();
            return;
        }
        if (newName.click(mx, my)) {
            finishRename();
            return;
        }
        if (renaming != null && rename.click(mx, my)) {
            return;
        }
        finishRename();
        if (!Render.inside(mx, my, x, listTop, w, listBottom - listTop)) {
            return;
        }
        List<Waypoints.Waypoint> rows = rows();
        int i = (int) ((my - listTop + scroll) / ROW);
        if (i < 0 || i >= rows.size()) {
            return;
        }
        Waypoints.Waypoint wp = rows.get(i);
        int y = listTop - (int) scroll + i * ROW;
        int right = x + w - 4;
        if (Render.inside(mx, my, right - 14, y + 4, 14, 14)) {
            Waypoints.get().remove(wp);
            say("Deleted " + wp.name + ".");
        } else if (Render.inside(mx, my, right - 30, y + 4, 14, 14)) {
            wp.visible = !wp.visible;
            Waypoints.get().save();
        } else if (Render.inside(mx, my, right - 46, y + 4, 14, 14)) {
            startRename(wp);
        } else if (Render.inside(mx, my, x + 4, y + 4, 14, 14)) {
            int next = 0;
            for (int c = 0; c < Waypoints.COLORS.length; c++) {
                if (Waypoints.COLORS[c] == wp.color) {
                    next = (c + 1) % Waypoints.COLORS.length;
                }
            }
            wp.color = Waypoints.COLORS[next];
            Waypoints.get().save();
        }
    }

    private void startRename(Waypoints.Waypoint wp) {
        renaming = wp;
        rename.set(wp.name);
        rename.setFocused(true);
        newName.setFocused(false);
    }

    private void finishRename() {
        if (renaming != null) {
            String n = rename.get().trim();
            if (!n.isEmpty()) {
                renaming.name = n;
                Waypoints.get().save();
            }
            renaming = null;
        }
    }

    private void add() {
        Waypoints wps = Waypoints.get();
        String name = newName.get().trim().isEmpty() ? wps.nextName() : newName.get().trim();
        Waypoints.Waypoint w = wps.addHere(name, wps.nextColor());
        if (w == null) {
            say("Can't add more waypoints here.");
            return;
        }
        newName.set("");
        newName.setFocused(false);
        say("Added " + w.name + " at " + w.x + ", " + w.y + ", " + w.z + ".");
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
        scroll -= direction * ROW;
    }

    @Override
    public boolean usesEscape() {
        return renaming != null;
    }

    @Override
    public void charTyped(char c, int keyCode) {
        if (renaming != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                renaming = null;
            } else if (rename.type(c, keyCode)) {
                finishRename();
            }
            return;
        }
        if (newName.type(c, keyCode)) {
            add();
        }
    }

    @Override
    public void onClosed() {
        finishRename();
    }
}
