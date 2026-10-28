package dev.astra.client.features;

import dev.astra.client.ui.Panels;
import dev.astra.client.ui.TextField;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Files;
import dev.astra.client.util.Render;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Screenshot Manager: every screenshot as a thumbnail, starred ones on their own tab, and a viewer
 * with star, copy, open, show in folder, rename and delete.
 */
public final class ScreenshotsScreen extends UiScreen {

    private final long openedAt = System.currentTimeMillis();
    private final TextField renameField = new TextField("New name", 60, null);
    private boolean starredOnly;
    private List<File> files = Collections.emptyList();
    private long listedAt;
    private float scroll;
    private int viewing = -1;
    private boolean renaming;
    private boolean confirmDelete;
    private String notice;
    private boolean noticeError;
    private long noticeAt;

    private int px, py, pw, ph, gridTop, gridBottom, cols, cellW, cellH;

    private static Screenshots shots() {
        return Screenshots.get();
    }

    private void refresh() {
        files = shots().list(starredOnly);
        listedAt = System.currentTimeMillis();
        if (viewing >= files.size()) {
            viewing = files.isEmpty() ? -1 : files.size() - 1;
        }
    }

    private void say(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        refresh();
    }

    @Override
    public void onClosed() {
        shots().releaseAll();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        if (System.currentTimeMillis() - listedAt > 3000 && viewing < 0) {
            refresh(); // pick up new screenshots while the screen is open
        }
        float ease = Panels.ease(openedAt);
        Panels.backdrop(width, height, ease);
        pw = Math.min(560, width - 24);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - ease) * 12);
        Panels.card(px, py, pw, ph, ease);

        if (viewing >= 0 && viewing < files.size()) {
            drawViewer(mx, my);
        } else {
            viewing = -1;
            drawGrid(mx, my);
        }
        if (notice != null && System.currentTimeMillis() - noticeAt < 4000) {
            Render.text(notice, px + 10, py - 12, noticeError ? 0xFFFF7A90 : 0xFF8FE3BE, true);
        }
    }

    private void drawGrid(int mx, int my) {
        Render.text("Screenshots", px + 12, py + 11, Theme.TEXT, false);
        Render.text(files.size() + (starredOnly ? " starred" : ""), px + 18 + Render.width("Screenshots"), py + 11, Theme.SUBTEXT, false);

        // Tabs and folder button on the right.
        int right = px + pw - 10;
        right -= Panels.pillWidth("Open folder");
        Panels.pill(right, py + 7, "Open folder", mx, my, 0x30FFFFFF, 0x50FFFFFF);
        right -= Panels.pillWidth("★ Starred") + 4;
        Panels.pill(right, py + 7, "★ Starred", mx, my, starredOnly ? Colors.withAlpha(Theme.accent(), 0xC0) : 0x30FFFFFF,
                starredOnly ? Theme.accent() : 0x50FFFFFF);
        right -= Panels.pillWidth("All") + 4;
        Panels.pill(right, py + 7, "All", mx, my, !starredOnly ? Colors.withAlpha(Theme.accent(), 0xC0) : 0x30FFFFFF,
                !starredOnly ? Theme.accent() : 0x50FFFFFF);

        gridTop = py + 30;
        gridBottom = py + ph - 18;
        int gx = px + 10;
        int gw = pw - 20;
        cols = gw >= 420 ? 4 : gw >= 300 ? 3 : 2;
        int gap = 6;
        cellW = (gw - gap * (cols - 1)) / cols;
        cellH = cellW * 9 / 16 + 12;
        int rows = (files.size() + cols - 1) / cols;
        float max = Math.max(0, rows * (cellH + gap) - (gridBottom - gridTop));
        scroll = Math.max(0, Math.min(scroll, max));

        if (files.isEmpty()) {
            Render.textCentered(starredOnly ? "No starred screenshots yet" : "No screenshots yet",
                    px + pw / 2f, py + ph / 2f - 8, Theme.TEXT, false);
            Render.textCentered(starredOnly ? "Open one and press the star" : "Press F2 in game to take one",
                    px + pw / 2f, py + ph / 2f + 4, Theme.SUBTEXT, false);
            return;
        }

        Render.scissor(gx - 2, gridTop, gw + 4, gridBottom - gridTop);
        for (int i = 0; i < files.size(); i++) {
            int cx = gx + (i % cols) * (cellW + gap);
            int cy = gridTop + (i / cols) * (cellH + gap) - (int) scroll;
            if (cy + cellH < gridTop || cy > gridBottom) {
                continue;
            }
            File f = files.get(i);
            boolean hover = my >= gridTop && my < gridBottom && Render.inside(mx, my, cx, cy, cellW, cellH);
            int imgH = cellH - 12;
            Render.round(cx, cy, cellW, cellH, 4, hover ? Colors.withAlpha(Theme.accent(), 0x50) : 0x18FFFFFF);
            Screenshots.Picture p = shots().thumb(f);
            if (p != null && p.handle != null) {
                fit(p, cx + 1, cy + 1, cellW - 2, imgH - 1);
            } else {
                Render.textCentered("...", cx + cellW / 2f, cy + imgH / 2f - 4, 0x55FFFFFF, false);
            }
            Render.text(Render.trim(label(f), cellW - 16), cx + 3, cy + imgH + 2, hover ? Theme.TEXT : Theme.SUBTEXT, false);
            boolean star = shots().isStarred(f);
            if (star || hover) {
                boolean starHover = Render.inside(mx, my, cx + cellW - 13, cy + imgH + 1, 12, 10);
                Render.text("★", cx + cellW - 11, cy + imgH + 2, star ? 0xFFFFD166 : starHover ? 0xFFFFE6A0 : 0x66FFFFFF, false);
            }
        }
        Render.endScissor();
        Panels.hint("Click to open  |  Scroll for more  |  Esc to close", width / 2, py + ph + 4);
    }

    private void drawViewer(int mx, int my) {
        File f = files.get(viewing);
        int ix = px + 10;
        int iy = py + 28;
        int iw = pw - 20;
        int ih = ph - 28 - 30;

        // Title: name (or the rename box) and position.
        String pos = (viewing + 1) + " / " + files.size();
        if (renaming) {
            renameField.draw(px + 10, py + 7, Math.min(240, pw - 120), 16);
        } else {
            Render.text(Render.trim(f.getName(), pw - 140), px + 12, py + 11, Theme.TEXT, false);
        }
        Render.text(pos, px + pw - 12 - Render.width(pos), py + 11, Theme.SUBTEXT, false);

        Render.round(ix, iy, iw, ih, 4, 0x60000000);
        Screenshots.Picture p = shots().full(f);
        if (p == null || p.handle == null) {
            // Show the thumbnail, blurry, while the big one loads.
            Screenshots.Picture t = shots().thumb(f);
            if (t != null && t.handle != null) {
                fit(t, ix, iy, iw, ih);
            }
            Render.textCentered("Loading...", ix + iw / 2f, iy + ih - 12, 0xAAFFFFFF, true);
        } else {
            fit(p, ix, iy, iw, ih);
        }

        // Arrows.
        if (viewing > 0) {
            arrow(ix + 4, iy + ih / 2 - 10, "<", mx, my);
        }
        if (viewing < files.size() - 1) {
            arrow(ix + iw - 20, iy + ih / 2 - 10, ">", mx, my);
        }

        // Action bar.
        int by = py + ph - 24;
        int x = px + pw - 10;
        int buttonsW = 0;
        for (String a : actions(f)) {
            buttonsW += Panels.pillWidth(a) + 4;
        }
        int dateRoom = x - buttonsW - (ix + 2) - 6;
        if (dateRoom > 40) {
            Render.text(Render.trim(date(f), dateRoom), ix + 2, by + 4, Theme.SUBTEXT, false);
        }
        for (String a : actions(f)) {
            x -= Panels.pillWidth(a);
            boolean danger = a.startsWith("Delete") || a.startsWith("Sure");
            Panels.pill(x, by, a, mx, my, danger ? 0x40FF5C7A : a.startsWith("★") && shots().isStarred(f) ? 0x60FFD166 : 0x30FFFFFF,
                    danger ? 0x80FF5C7A : 0x50FFFFFF);
            x -= 4;
        }
        Panels.hint("← → to browse  |  Esc to go back", width / 2, py + ph + 4);
    }

    /** Right to left. */
    private String[] actions(File f) {
        if (renaming) {
            return new String[]{"Save", "Cancel"};
        }
        return new String[]{confirmDelete ? "Sure? Delete" : "Delete", "Rename", "Show in folder", "Open", "Copy",
                shots().isStarred(f) ? "★ Starred" : "★ Star"};
    }

    private static void arrow(int x, int y, String s, int mx, int my) {
        boolean hover = Render.inside(mx, my, x, y, 16, 20);
        Render.round(x, y, 16, 20, 4, hover ? 0xA0000000 : 0x60000000);
        Render.textCentered(s, x + 8, y + 6, Theme.TEXT, false);
    }

    /** Draws a picture as large as fits in the box, centred, keeping its shape. */
    private static void fit(Screenshots.Picture p, int x, int y, int w, int h) {
        float scale = Math.min(w / (float) p.width, h / (float) p.height);
        float dw = p.width * scale;
        float dh = p.height * scale;
        Render.texture(p.handle, x + (w - dw) / 2f, y + (h - dh) / 2f, dw, dh, 0, 0, 1, 1);
    }

    private static String label(File f) {
        return new SimpleDateFormat("MMM d, HH:mm").format(new Date(f.lastModified()));
    }

    private static String date(File f) {
        long kb = f.length() / 1024;
        return new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(f.lastModified()))
                + "  ·  " + (kb > 1024 ? String.format("%.1f MB", kb / 1024f) : kb + " KB");
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        if (viewing >= 0) {
            clickViewer(mx, my);
            return;
        }
        int right = px + pw - 10;
        right -= Panels.pillWidth("Open folder");
        if (Render.inside(mx, my, right, py + 7, Panels.pillWidth("Open folder"), 16)) {
            Files.open(Screenshots.folder());
            return;
        }
        right -= Panels.pillWidth("★ Starred") + 4;
        if (Render.inside(mx, my, right, py + 7, Panels.pillWidth("★ Starred"), 16)) {
            starredOnly = true;
            scroll = 0;
            refresh();
            return;
        }
        right -= Panels.pillWidth("All") + 4;
        if (Render.inside(mx, my, right, py + 7, Panels.pillWidth("All"), 16)) {
            starredOnly = false;
            scroll = 0;
            refresh();
            return;
        }
        if (my < gridTop || my >= gridBottom) {
            return;
        }
        int gap = 6;
        int gx = px + 10;
        for (int i = 0; i < files.size(); i++) {
            int cx = gx + (i % cols) * (cellW + gap);
            int cy = gridTop + (i / cols) * (cellH + gap) - (int) scroll;
            if (!Render.inside(mx, my, cx, cy, cellW, cellH)) {
                continue;
            }
            int imgH = cellH - 12;
            if (Render.inside(mx, my, cx + cellW - 13, cy + imgH + 1, 12, 10)) {
                shots().toggleStar(files.get(i));
                if (starredOnly) {
                    refresh();
                }
            } else {
                open(i);
            }
            return;
        }
    }

    private void open(int index) {
        viewing = index;
        renaming = false;
        confirmDelete = false;
    }

    private void clickViewer(int mx, int my) {
        File f = files.get(viewing);
        int ix = px + 10;
        int iy = py + 28;
        int iw = pw - 20;
        int ih = ph - 28 - 30;
        if (viewing > 0 && Render.inside(mx, my, ix + 4, iy + ih / 2 - 10, 16, 20)) {
            open(viewing - 1);
            return;
        }
        if (viewing < files.size() - 1 && Render.inside(mx, my, ix + iw - 20, iy + ih / 2 - 10, 16, 20)) {
            open(viewing + 1);
            return;
        }
        if (renaming && renameField.click(mx, my)) {
            return;
        }
        int by = py + ph - 24;
        int x = px + pw - 10;
        for (String a : actions(f)) {
            int w = Panels.pillWidth(a);
            x -= w;
            if (Render.inside(mx, my, x, by, w, 16)) {
                act(a, f);
                return;
            }
            x -= 4;
        }
        confirmDelete = false;
    }

    private void act(String action, final File f) {
        if (!action.startsWith("Sure")) {
            confirmDelete = false;
        }
        if (action.equals("Save")) {
            String err = shots().rename(f, renameField.get());
            if (err != null) {
                say(err, true);
                return;
            }
            renaming = false;
            String newName = renameField.get().trim();
            refresh();
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).getName().startsWith(newName.replaceAll("[\\\\/:*?\"<>|]", "") + ".")) {
                    viewing = i;
                }
            }
            say("Renamed.", false);
        } else if (action.equals("Cancel")) {
            renaming = false;
        } else if (action.equals("Delete")) {
            confirmDelete = true;
        } else if (action.startsWith("Sure")) {
            if (shots().delete(f)) {
                say("Deleted " + f.getName() + ".", false);
                refresh();
            } else {
                say("Couldn't delete it (is it open somewhere?)", true);
            }
            confirmDelete = false;
        } else if (action.equals("Rename")) {
            String name = f.getName();
            renameField.set(name.substring(0, name.lastIndexOf('.')));
            renameField.setFocused(true);
            renaming = true;
        } else if (action.equals("Show in folder")) {
            Files.reveal(f);
        } else if (action.equals("Open")) {
            Files.openFile(f);
        } else if (action.equals("Copy")) {
            say("Copying...", false);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean ok = Screenshots.copy(f);
                    say(ok ? "Copied. Paste it into Discord or anywhere." : "Couldn't copy to the clipboard.", !ok);
                }
            }, "Astra copy screenshot");
            t.setDaemon(true);
            t.start();
        } else if (action.startsWith("★")) {
            shots().toggleStar(f);
            if (starredOnly && !shots().isStarred(f)) {
                refresh();
            }
        }
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
        if (viewing >= 0) {
            open(Math.max(0, Math.min(files.size() - 1, viewing - direction)));
        } else {
            scroll -= direction * (cellH + 6);
        }
    }

    @Override
    protected void keyPressed(int keyCode) {
        if (viewing < 0 || renaming) {
            return;
        }
        if (keyCode == Keyboard.KEY_LEFT && viewing > 0) {
            open(viewing - 1);
        } else if (keyCode == Keyboard.KEY_RIGHT && viewing < files.size() - 1) {
            open(viewing + 1);
        } else if (keyCode == Keyboard.KEY_DELETE) {
            act(confirmDelete ? "Sure? Delete" : "Delete", files.get(viewing));
        }
    }

    @Override
    public boolean usesEscape() {
        return viewing >= 0;
    }

    @Override
    public void charTyped(char c, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (renaming) {
                renaming = false;
            } else if (viewing >= 0) {
                viewing = -1;
                confirmDelete = false;
            }
            return;
        }
        if (renaming && renameField.type(c, keyCode)) {
            act("Save", files.get(viewing));
        }
    }
}
