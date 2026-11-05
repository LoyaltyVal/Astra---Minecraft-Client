package dev.astra.client.replay;

import dev.astra.client.Astra;
import dev.astra.client.ui.Panels;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Files;
import dev.astra.client.util.Render;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Your recorded replays: where and when, how long, and Watch / Delete. */
public final class ReplaysScreen extends UiScreen {

    private static final int ROW_H = 30;

    private final long openedAt = System.currentTimeMillis();
    private final List<File> files = new ArrayList<File>();
    private final List<ReplayInfo> infos = new ArrayList<ReplayInfo>();
    private float scroll;
    private File confirmDelete;
    private String notice;
    private boolean noticeError;
    private boolean loading;

    private int px, py, pw, ph, listTop, listBottom;

    @Override
    protected void init() {
        refresh();
    }

    private void refresh() {
        files.clear();
        infos.clear();
        for (File f : ReplaysList.files()) {
            files.add(f);
            infos.add(ReplayFile.readInfo(f));
        }
    }

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        float ease = Panels.ease(openedAt);
        Panels.backdrop(width, height, ease);
        pw = Math.min(460, width - 24);
        ph = Math.min(300, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - ease) * 12);
        Panels.card(px, py, pw, ph, ease);

        Render.textCenteredScaled("Replays", px + 14 + Render.width("Replays") * 0.75f, py + 12, 1.5f, Theme.TEXT, true);
        String sub = Replay.backend() == null ? "Replays aren't available on " + Astra.platform().minecraftVersion() + " yet."
                : Replay.get().isRecording() ? "Recording now: " + Replay.clock(Replay.get().recordingTime())
                : files.isEmpty() ? "" : files.size() + (files.size() == 1 ? " replay" : " replays");
        Render.text(sub, px + 14, py + 28, Replay.get().isRecording() ? 0xFFFF7A88 : Theme.SUBTEXT, false);

        int bx = px + pw - 12;
        bx -= Panels.pill(bx - Panels.pillWidth("Close"), py + 12, "Close", mx, my, 0x30FFFFFF, 0x50FFFFFF) + 4;
        Panels.pill(bx - Panels.pillWidth("Open Folder"), py + 12, "Open Folder", mx, my, 0x30FFFFFF, 0x50FFFFFF);

        listTop = py + 44;
        listBottom = py + ph - 22;
        int max = Math.max(0, files.size() * (ROW_H + 4) - (listBottom - listTop));
        scroll = Math.max(0, Math.min(max, scroll));

        if (files.isEmpty()) {
            int cy = (listTop + listBottom) / 2 - 12;
            Render.textCentered("No replays yet", px + pw / 2f, cy, Theme.TEXT, true);
            Render.textCentered("Join a server: Astra records automatically (turn it off in the Replay mod).",
                    px + pw / 2f, cy + 14, Theme.SUBTEXT, false);
        }

        Render.scissor(px + 6, listTop, pw - 12, listBottom - listTop);
        for (int i = 0; i < files.size(); i++) {
            int y = listTop + i * (ROW_H + 4) - (int) scroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            drawRow(i, px + 12, y, pw - 24, mx, my);
        }
        Render.endScissor();

        if (loading) {
            Render.textCentered("Loading replay...", px + pw / 2f, py + ph - 16, Theme.accent(), true);
        } else if (notice != null) {
            Render.textCentered(Render.trim(notice, pw - 20), px + pw / 2f, py + ph - 16, noticeError ? 0xFFFF7A88 : Theme.SUBTEXT, false);
        } else {
            Panels.hint("Fly around, pause, change speed and watch from any player", px + pw / 2, py + ph - 16);
        }
        if (loading) {
            loading = false;
            open(files.get(pendingIndex));
        }
    }

    private int pendingIndex;

    private void drawRow(int i, int x, int y, int w, int mx, int my) {
        ReplayInfo info = infos.get(i);
        File f = files.get(i);
        boolean in = my >= listTop && my < listBottom;
        boolean hover = in && Render.inside(mx, my, x, y, w, ROW_H);
        Render.round(x, y, w, ROW_H, 5, hover ? 0x26FFFFFF : 0x14FFFFFF);
        String title = info == null ? f.getName() : (info.server.isEmpty() ? "Unknown server" : info.server);
        String when = info == null || info.startedAt == 0 ? ""
                : new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ROOT).format(new Date(info.startedAt));
        String detail = info == null ? "Couldn't read this replay"
                : when + "  ·  " + (info.durationMs > 0 ? Replay.clock((int) info.durationMs) : "unfinished")
                + "  ·  " + Math.max(1, info.sizeBytes / (1024 * 1024)) + " MB"
                + (info.minecraftVersion.isEmpty() ? "" : "  ·  " + info.minecraftVersion);
        int buttons = Panels.pillWidth("Watch") + Panels.pillWidth(confirmDelete == f ? "Sure?" : "Delete") + 14;
        Render.text(Render.trim(title, w - buttons - 10), x + 8, y + 6, Theme.TEXT, true);
        Render.text(Render.trim(detail, w - buttons - 10), x + 8, y + 17, Theme.SUBTEXT, false);
        int bx = x + w - 6;
        String del = confirmDelete == f ? "Sure?" : "Delete";
        bx -= Panels.pill(bx - Panels.pillWidth(del), y + 7, del, in ? mx : -1, my, confirmDelete == f ? 0x80FF5C7A : 0x30FFFFFF, 0x90FF5C7A) + 4;
        Panels.pill(bx - Panels.pillWidth("Watch"), y + 7, "Watch", in ? mx : -1, my,
                Colors.withAlpha(Theme.accent(), 0xC0), Theme.accent());
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        int bx = px + pw - 12;
        int closeW = Panels.pillWidth("Close");
        if (Render.inside(mx, my, bx - closeW, py + 12, closeW, 16)) {
            close();
            return;
        }
        bx -= closeW + 4;
        int folderW = Panels.pillWidth("Open Folder");
        if (Render.inside(mx, my, bx - folderW, py + 12, folderW, 16)) {
            Files.open(Replay.folder());
            return;
        }
        if (my < listTop || my >= listBottom) {
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            int y = listTop + i * (ROW_H + 4) - (int) scroll;
            int x = px + 12;
            int w = pw - 24;
            if (!Render.inside(mx, my, x, y, w, ROW_H)) {
                continue;
            }
            File f = files.get(i);
            int rx = x + w - 6;
            String del = confirmDelete == f ? "Sure?" : "Delete";
            int dw = Panels.pillWidth(del);
            if (Render.inside(mx, my, rx - dw, y + 7, dw, 16)) {
                if (confirmDelete == f) {
                    ReplaysList.delete(f);
                    confirmDelete = null;
                    refresh();
                } else {
                    confirmDelete = f;
                }
                return;
            }
            confirmDelete = null;
            int ww = Panels.pillWidth("Watch");
            if (Render.inside(mx, my, rx - dw - 4 - ww, y + 7, ww, 16) || button == 0) {
                if (Replay.backend() == null) {
                    notice = "Replays aren't available on this Minecraft version yet.";
                    noticeError = true;
                    return;
                }
                // Show "Loading..." for a frame first: big replays take a moment to read.
                pendingIndex = i;
                loading = true;
            }
            return;
        }
    }

    private void open(File f) {
        try {
            Replay.get().play(f);
        } catch (Exception e) {
            notice = e.getMessage() == null ? "Couldn't open that replay." : e.getMessage();
            noticeError = true;
        }
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
        scroll -= direction * (ROW_H + 4);
    }
}
