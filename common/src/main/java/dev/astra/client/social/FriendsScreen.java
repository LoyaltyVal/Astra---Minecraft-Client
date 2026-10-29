package dev.astra.client.social;

import dev.astra.client.Astra;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;
import dev.astra.client.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The friends menu (Shift + Tab): a floating glass card with your friends on the left and the
 * conversation on the right. Friends show what server they're on and can be joined in one click.
 */
public final class FriendsScreen extends UiScreen {

    private static final int FIELD_NONE = 0;
    private static final int FIELD_ADD = 1;
    private static final int FIELD_MESSAGE = 2;

    private final long openedAt = System.currentTimeMillis();
    private boolean requestsTab;
    /** The Servers tab: Quick Join (favourite and recent servers). */
    private boolean serversTab;
    private final dev.astra.client.features.QuickJoinPanel servers = new dev.astra.client.features.QuickJoinPanel(
            new dev.astra.client.features.QuickJoinPanel.Host() {
                @Override
                public void say(String message, boolean error) {
                    FriendsScreen.this.say(message, error);
                }

                @Override
                public void closeMenu() {
                    close();
                }
            });
    /** The Radio tab. */
    private boolean radioTab;
    private final dev.astra.client.features.RadioPanel radio = new dev.astra.client.features.RadioPanel();
    private String selected;
    private int focus = FIELD_NONE;
    private String addText = "";
    private String messageText = "";
    private float listScroll;
    private float chatScroll; // pixels scrolled up from the newest message
    private String notice;
    private boolean noticeError;
    private long noticeAt;
    private String confirmRemove;

    // Layout, worked out every frame.
    private int px, py, pw, ph, leftW;

    public FriendsScreen() {
    }

    /** Opens straight on the Servers (Quick Join) tab. */
    public static FriendsScreen servers() {
        FriendsScreen s = new FriendsScreen();
        s.serversTab = true;
        return s;
    }

    /** Opens straight on the Radio tab. */
    public static FriendsScreen radio() {
        FriendsScreen s = new FriendsScreen();
        s.radioTab = true;
        return s;
    }

    private static SocialManager social() {
        return Astra.get().getSocial();
    }

    @Override
    protected void init() {
        social().setMenuOpen(true);
        List<SocialManager.Friend> friends = social().getFriends();
        // Open on whoever messaged you, else the first friend in game.
        for (SocialManager.Friend f : friends) {
            if (social().getUnread(f.uuid) > 0) {
                select(f.uuid);
                break;
            }
        }
        if (selected == null && !serversTab && !radioTab && !social().getIncoming().isEmpty()) {
            requestsTab = true;
        }
    }

    private void select(String uuid) {
        selected = uuid;
        chatScroll = 0;
        confirmRemove = null;
        focus = uuid == null ? FIELD_NONE : FIELD_MESSAGE;
        social().setReading(uuid);
    }

    private void say(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeAt = System.currentTimeMillis();
    }

    private SocialManager.Callback report(final String success) {
        return new SocialManager.Callback() {
            @Override
            public void done(String error) {
                if (error != null) {
                    say(error, true);
                } else if (success != null) {
                    say(success, false);
                }
            }
        };
    }

    private SocialManager.Friend friend(String uuid) {
        for (SocialManager.Friend f : social().getFriends()) {
            if (f.uuid.equals(uuid)) {
                return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);

        Render.rect(0, 0, width, height, Colors.withAlpha(0xFF05050A, (int) (0x88 * ease)));

        pw = Math.min(440, width - 24);
        ph = Math.min(250, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - ease) * 12);
        leftW = Math.max(130, pw * 38 / 100);

        // Soft glow behind the card, then the card itself.
        int accent = Theme.accent();
        for (int i = 6; i >= 1; i--) {
            Render.round(px - i * 2, py - i * 2, pw + i * 4, ph + i * 4, 8 + i * 2, Colors.withAlpha(accent, (int) (6 * ease)));
        }
        Render.round(px, py, pw, ph, 8, Colors.withAlpha(0xFF0E0E14, (int) (0xF0 * ease)));
        Render.round(px, py, leftW, ph, 8, Colors.withAlpha(0xFF14141C, (int) (0xF0 * ease)));
        Render.rect(px + leftW - 8, py, 8, ph, Colors.withAlpha(0xFF14141C, (int) (0xF0 * ease)));
        Render.rect(px + leftW, py + 8, 1, ph - 16, 0x22FFFFFF);
        aurora(px + 6, py, pw - 12, ease);

        drawLeft(mx, my);
        if (radioTab) {
            int rx = px + leftW + 12;
            int rw = pw - leftW - 24;
            dev.astra.client.features.RadioPlayer rp = dev.astra.client.features.RadioPlayer.get();
            Render.text("Radio", rx, py + 12, Theme.TEXT, false);
            String status = rp.getStatus();
            Render.text(Render.trim(status, rw - 40), rx + rw - Math.min(Render.width(status), rw - 40), py + 12,
                    rp.isError() ? 0xFFFF7A90 : rp.isPlaying() ? 0xFF8FE3BE : Theme.SUBTEXT, false);
            radio.draw(rx, py + 26, rw, ph - 36, mx, my);
        } else if (serversTab) {
            int rx = px + leftW + 12;
            int rw = pw - leftW - 24;
            Render.text("Servers", rx, py + 12, Theme.TEXT, false);
            Render.text("Quick Join", rx + Render.width("Servers") + 6, py + 12, Theme.SUBTEXT, false);
            servers.draw(rx, py + 26, rw, ph - 36, mx, my);
            if (notice != null && System.currentTimeMillis() - noticeAt < 6000) {
                Render.text(Render.trim(notice, pw - 20), px + 10, py - 12, noticeError ? 0xFFFF7A90 : 0xFF8FE3BE, true);
            }
        } else if (requestsTab) {
            drawRequests(mx, my);
        } else {
            drawChat(mx, my);
        }

        // Footer hint.
        Render.textCentered("Shift + Tab or Esc to close", width / 2f, py + ph + 4, 0x66FFFFFF, false);
    }

    /** A thin animated colour band along the top edge: the card's signature. */
    private void aurora(int x, int y, int w, float ease) {
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

    private String[] tabLabels() {
        int req = social().getIncoming().size();
        return new String[]{"All", req > 0 ? "Requests " + req : "Requests", "Servers", "Radio"};
    }

    private int currentTab() {
        return radioTab ? 3 : serversTab ? 2 : requestsTab ? 1 : 0;
    }

    private void setTab(int tab) {
        requestsTab = tab == 1;
        serversTab = tab == 2;
        radioTab = tab == 3;
        if (!radioTab) {
            radio.stopTyping();
        }
    }

    /** {x, width} of each tab, sized to its label. */
    private int[] tabs(int x, int w) {
        String[] labels = tabLabels();
        int n = labels.length;
        int total = 0;
        int[] tw = new int[n];
        for (int i = 0; i < n; i++) {
            tw[i] = Render.width(labels[i]);
            total += tw[i];
        }
        int extra = Math.max(0, (w - total) / n);
        int[] out = new int[n * 2];
        int cx = x;
        for (int i = 0; i < n; i++) {
            int segW = i == n - 1 ? x + w - cx : tw[i] + extra;
            out[i * 2] = cx;
            out[i * 2 + 1] = segW;
            cx += segW;
        }
        return out;
    }

    private void drawLeft(int mx, int my) {
        SocialManager s = social();
        int x = px + 10;
        int y = py + 10;
        int w = leftW - 20;

        Render.text("Friends", x, y, Theme.TEXT, false);
        // Top right: everyone in game on Astra (the list below is only your friends).
        int total = s.getAstraOnline();
        if (total >= 0) {
            String count = total + " on Astra";
            int cw = Render.width(count);
            Render.text(count, x + w - cw, y, Theme.SUBTEXT, false);
            Render.round(x + w - cw - 7, y + 2, 4, 4, 2, 0xFF3DDC97);
        }
        y += 14;

        // Segmented tabs: All | Requests | Servers | Radio.
        int[] seg = tabs(x, w);
        int tab = currentTab();
        Render.round(x, y, w, 14, 7, 0x40000000);
        Render.round(seg[tab * 2], y, seg[tab * 2 + 1], 14, 7, Colors.withAlpha(Theme.accent(), 0xC0));
        String[] labels = tabLabels();
        int req = s.getIncoming().size();
        for (int i = 0; i < labels.length; i++) {
            int color = i == 1 && req > 0 && tab != 1 ? 0xFFFFD166
                    : i == 3 && tab != 3 && dev.astra.client.features.RadioPlayer.get().isPlaying() ? 0xFF8FE3BE : Theme.TEXT;
            Render.textCentered(labels[i], seg[i * 2] + seg[i * 2 + 1] / 2f, y + 3, color, false);
        }
        y += 20;

        // Friend cards.
        int listTop = y;
        int listBottom = py + ph - 34;
        List<SocialManager.Friend> friends = s.getFriends();
        int rowH = 26;
        float maxScroll = Math.max(0, friends.size() * rowH - (listBottom - listTop));
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        Render.scissor(x - 2, listTop, w + 4, listBottom - listTop);
        int ry = listTop - (int) listScroll;
        for (SocialManager.Friend f : friends) {
            if (ry + rowH > listTop - rowH && ry < listBottom) {
                boolean hover = Render.inside(mx, my, x, ry, w, rowH - 2) && my >= listTop && my < listBottom;
                boolean sel = currentTab() == 0 && f.uuid.equals(selected);
                if (sel) {
                    Render.round(x, ry, w, rowH - 2, 5, Colors.withAlpha(Theme.accent(), 0x55));
                    Render.rect(x, ry + 5, 2, rowH - 12, Theme.accent());
                } else if (hover) {
                    Render.round(x, ry, w, rowH - 2, 5, 0x18FFFFFF);
                }
                avatar(f.name, x + 4, ry + 3, 18, f.playing ? 0xFF3DDC97 : f.online ? 0xFFFFD166 : 0xFF5A5A66);
                Render.text(Render.trim(f.name, w - 40), x + 27, ry + 3, f.rankColor, false);
                Render.text(Render.trim(f.status(), w - 32), x + 27, ry + 13, f.playing ? 0xFF8FE3BE : Theme.SUBTEXT, false);
                int unread = s.getUnread(f.uuid);
                if (unread > 0) {
                    String n = unread > 9 ? "9+" : String.valueOf(unread);
                    int bw = Math.max(10, Render.width(n) + 5);
                    Render.round(x + w - bw - 3, ry + 4, bw, 10, 5, 0xFFFF5C7A);
                    Render.textCentered(n, x + w - 3 - bw / 2f, ry + 5, Theme.TEXT, false);
                }
            }
            ry += rowH;
        }
        Render.endScissor();
        if (friends.isEmpty()) {
            String msg = !s.available() ? "Start from the Astra app" : !s.isLoaded() ? "Loading..." : "No friends yet";
            Render.textCentered(msg, x + w / 2f, listTop + 20, Theme.SUBTEXT, false);
            if (s.isLoaded() && s.available()) {
                Render.textCentered("Add one below", x + w / 2f, listTop + 32, 0x88A0A0B0, false);
            }
        }

        // Add friend field.
        int fy = py + ph - 28;
        boolean active = focus == FIELD_ADD;
        Render.round(x, fy, w, 18, 9, active ? 0x60000000 : 0x40000000);
        if (active) {
            Render.outline(x + 3, fy, w - 6, 1, Colors.withAlpha(Theme.accent(), 0x80));
        }
        Render.round(x + w - 18, fy + 2, 14, 14, 7, Colors.withAlpha(Theme.accent(), 0xD0));
        Render.textCentered("+", x + w - 11, fy + 5, Theme.TEXT, false);
        String shown = addText.isEmpty() && !active ? "Add a friend by name" : addText + caret(active);
        Render.text(fitEnd(shown, w - 30), x + 8, fy + 5, addText.isEmpty() && !active ? 0x88A0A0B0 : Theme.TEXT, false);

        // Last result (e.g. "Request sent").
        if (notice != null && System.currentTimeMillis() - noticeAt < 6000) {
            int c = noticeError ? 0xFFFF7A90 : 0xFF8FE3BE;
            Render.text(Render.trim(notice, pw - 20), px + 10, py - 12, c, true);
        } else if (s.getError() != null) {
            Render.text(Render.trim(s.getError(), pw - 20), px + 10, py - 12, 0xFFFF7A90, true);
        }
    }

    private void drawChat(int mx, int my) {
        SocialManager s = social();
        int x = px + leftW + 12;
        int w = pw - leftW - 24;
        SocialManager.Friend f = selected == null ? null : friend(selected);
        if (f == null) {
            int cy = py + ph / 2 - 20;
            Render.textCenteredScaled("✦", x + w / 2f, cy - 10, 2f, Colors.withAlpha(Theme.accent(), 0xC0), false);
            Render.textCentered("Pick a friend to chat", x + w / 2f, cy + 14, Theme.TEXT, false);
            Render.textCentered("See where they play and join them", x + w / 2f, cy + 26, Theme.SUBTEXT, false);
            return;
        }

        // Header: avatar, name, status, actions.
        int y = py + 10;
        avatar(f.name, x, y, 24, f.playing ? 0xFF3DDC97 : f.online ? 0xFFFFD166 : 0xFF5A5A66);
        Render.text(f.name, x + 30, y + 2, f.rankColor, false);
        if (f.rankName != null && !"Player".equals(f.rankName)) {
            Render.text(f.rankName, x + 34 + Render.width(f.name), y + 2, Colors.withAlpha(f.rankColor, 0x99), false);
        }
        Render.text(Render.trim(f.status(), w - 120), x + 30, y + 13, f.playing ? 0xFF8FE3BE : Theme.SUBTEXT, false);

        int bx = x + w;
        String removeLabel = f.uuid.equals(confirmRemove) ? "Sure?" : "Remove";
        int rw = Render.width(removeLabel) + 12;
        bx -= rw;
        pill(bx, y + 4, rw, 16, removeLabel, mx, my, 0x30FF5C7A, 0x70FF5C7A);
        if (f.joinable()) {
            int jw = Render.width("Join") + 16;
            bx -= jw + 4;
            pill(bx, y + 4, jw, 16, "Join", mx, my, Colors.withAlpha(0xFF3DDC97, 0x70), Colors.withAlpha(0xFF3DDC97, 0xB0));
        }
        Render.rect(x, y + 30, w, 1, 0x18FFFFFF);

        // Messages, newest at the bottom.
        int top = y + 36;
        int bottom = py + ph - 34;
        List<SocialManager.Message> chat = s.getChat(f.uuid);
        String me = s.selfUuid();
        int maxBubble = w * 3 / 4;
        List<Object[]> laid = new ArrayList<Object[]>();
        int total = 0;
        long lastAt = 0;
        for (SocialManager.Message m : chat) {
            List<String> lines = wrap(m.text, maxBubble - 12);
            int h = lines.size() * 10 + 8;
            boolean gap = m.at - lastAt > 10 * 60000L;
            lastAt = m.at;
            laid.add(new Object[]{m, lines, h, gap});
            total += h + 4 + (gap ? 14 : 0);
        }
        float maxScroll = Math.max(0, total - (bottom - top));
        chatScroll = Math.max(0, Math.min(chatScroll, maxScroll));
        Render.scissor(x, top, w, bottom - top);
        int cy = bottom - total + (int) chatScroll;
        for (Object[] row : laid) {
            SocialManager.Message m = (SocialManager.Message) row[0];
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) row[1];
            int h = (Integer) row[2];
            if ((Boolean) row[3]) {
                Render.textCentered(time(m.at), x + w / 2f, cy + 2, 0x66FFFFFF, false);
                cy += 14;
            }
            if (cy + h >= top && cy <= bottom) {
                boolean mine = m.from.equals(me);
                int bw = 0;
                for (String line : lines) {
                    bw = Math.max(bw, Render.width(line));
                }
                bw += 12;
                int bxx = mine ? x + w - bw : x;
                int color = mine ? Colors.withAlpha(Theme.accent(), 0xD0) : 0xC0262630;
                Render.round(bxx, cy, bw, h, 6, color);
                int ly = cy + 4;
                for (String line : lines) {
                    Render.text(line, bxx + 6, ly, Theme.TEXT, false);
                    ly += 10;
                }
            }
            cy += h + 4;
        }
        Render.endScissor();
        if (chat.isEmpty()) {
            Render.textCentered("Say hi to " + f.name, x + w / 2f, (top + bottom) / 2f - 4, Theme.SUBTEXT, false);
        }

        // Message box.
        int fy = py + ph - 28;
        boolean active = focus == FIELD_MESSAGE;
        Render.round(x, fy, w, 18, 9, active ? 0x60000000 : 0x40000000);
        String shown = messageText.isEmpty() && !active ? "Message " + f.name : messageText + caret(active);
        Render.text(fitEnd(shown, w - 40), x + 8, fy + 5, messageText.isEmpty() && !active ? 0x88A0A0B0 : Theme.TEXT, false);
        Render.round(x + w - 30, fy + 2, 28, 14, 7, messageText.trim().isEmpty() ? 0x30FFFFFF : Colors.withAlpha(Theme.accent(), 0xD0));
        Render.textCentered("Send", x + w - 16, fy + 5, Theme.TEXT, false);
    }

    private void drawRequests(int mx, int my) {
        SocialManager s = social();
        int x = px + leftW + 12;
        int w = pw - leftW - 24;
        int y = py + 12;
        Render.text("Friend requests", x, y, Theme.TEXT, false);
        y += 16;
        if (s.getIncoming().isEmpty() && s.getOutgoing().isEmpty()) {
            Render.textCentered("No requests right now", x + w / 2f, py + ph / 2f - 4, Theme.SUBTEXT, false);
            return;
        }
        for (SocialManager.Friend f : s.getIncoming()) {
            if (y > py + ph - 30) {
                break;
            }
            Render.round(x, y, w, 24, 5, 0x20FFFFFF);
            avatar(f.name, x + 4, y + 3, 18, 0xFFFFD166);
            Render.text(Render.trim(f.name, w - 120), x + 27, y + 3, f.rankColor, false);
            Render.text("wants to be friends", x + 27, y + 13, Theme.SUBTEXT, false);
            int dw = Render.width("Decline") + 12;
            int aw = Render.width("Accept") + 12;
            pill(x + w - dw - 4, y + 4, dw, 16, "Decline", mx, my, 0x30FFFFFF, 0x50FFFFFF);
            pill(x + w - dw - aw - 8, y + 4, aw, 16, "Accept", mx, my, Colors.withAlpha(0xFF3DDC97, 0x80), Colors.withAlpha(0xFF3DDC97, 0xC0));
            y += 28;
        }
        if (!s.getOutgoing().isEmpty()) {
            y += 4;
            Render.text("Sent", x, y, Theme.SUBTEXT, false);
            y += 12;
            for (SocialManager.Friend f : s.getOutgoing()) {
                if (y > py + ph - 30) {
                    break;
                }
                Render.round(x, y, w, 20, 5, 0x14FFFFFF);
                avatar(f.name, x + 4, y + 3, 14, 0xFF5A5A66);
                Render.text(Render.trim(f.name, w - 80), x + 23, y + 6, f.rankColor, false);
                int cw = Render.width("Cancel") + 12;
                pill(x + w - cw - 4, y + 2, cw, 16, "Cancel", mx, my, 0x30FFFFFF, 0x50FFFFFF);
                y += 24;
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        SocialManager s = social();
        int x = px + 10;
        int w = leftW - 20;
        int tabY = py + 24;
        if (Render.inside(mx, my, x, tabY, w, 14)) {
            int[] seg = tabs(x, w);
            int clicked = 0;
            for (int i = 0; i * 2 < seg.length; i++) {
                if (mx >= seg[i * 2]) {
                    clicked = i;
                }
            }
            setTab(clicked);
            social().setReading(clicked == 0 ? selected : null);
            focus = FIELD_NONE;
            return;
        }
        if (radioTab) {
            if (radio.click(mx, my)) {
                focus = FIELD_NONE;
                return;
            }
        }
        if (serversTab) {
            if (mx >= px + leftW) {
                focus = FIELD_NONE;
                servers.click(mx, my);
                return;
            }
            servers.click(mx, my); // a click on the left just takes focus off its text box
        }

        // Friend list.
        int listTop = py + 44;
        int listBottom = py + ph - 34;
        if (Render.inside(mx, my, x, listTop, w, listBottom - listTop)) {
            int index = (int) ((my - listTop + listScroll) / 26);
            List<SocialManager.Friend> friends = s.getFriends();
            if (index >= 0 && index < friends.size()) {
                setTab(0);
                select(friends.get(index).uuid);
            }
            return;
        }

        // Add field / button.
        int fy = py + ph - 28;
        if (Render.inside(mx, my, x, fy, w, 18)) {
            if (mx >= x + w - 18) {
                submitAdd();
            } else {
                focus = FIELD_ADD;
            }
            return;
        }

        int rx = px + leftW + 12;
        int rw = pw - leftW - 24;
        if (requestsTab) {
            clickRequests(mx, my, rx, rw);
            return;
        }
        SocialManager.Friend f = selected == null ? null : friend(selected);
        if (f == null) {
            focus = FIELD_NONE;
            return;
        }
        int y = py + 10;
        int bx = rx + rw;
        String removeLabel = f.uuid.equals(confirmRemove) ? "Sure?" : "Remove";
        int remW = Render.width(removeLabel) + 12;
        bx -= remW;
        if (Render.inside(mx, my, bx, y + 4, remW, 16)) {
            if (f.uuid.equals(confirmRemove)) {
                s.remove(f.uuid, report("Removed " + f.name + "."));
                select(null);
            } else {
                confirmRemove = f.uuid;
            }
            return;
        }
        confirmRemove = null;
        if (f.joinable()) {
            int jw = Render.width("Join") + 16;
            bx -= jw + 4;
            if (Render.inside(mx, my, bx, y + 4, jw, 16)) {
                String address = f.server;
                close();
                mc().joinServer(address);
                return;
            }
        }
        if (Render.inside(mx, my, rx, fy, rw, 18)) {
            if (mx >= rx + rw - 30) {
                submitMessage();
            } else {
                focus = FIELD_MESSAGE;
            }
            return;
        }
        focus = FIELD_NONE;
    }

    private void clickRequests(int mx, int my, int x, int w) {
        SocialManager s = social();
        int y = py + 28;
        for (SocialManager.Friend f : s.getIncoming()) {
            int dw = Render.width("Decline") + 12;
            int aw = Render.width("Accept") + 12;
            if (Render.inside(mx, my, x + w - dw - 4, y + 4, dw, 16)) {
                s.remove(f.uuid, report("Declined " + f.name + "."));
                return;
            }
            if (Render.inside(mx, my, x + w - dw - aw - 8, y + 4, aw, 16)) {
                s.accept(f.uuid, report("You and " + f.name + " are now friends."));
                return;
            }
            y += 28;
        }
        if (!s.getOutgoing().isEmpty()) {
            y += 16;
            for (SocialManager.Friend f : s.getOutgoing()) {
                int cw = Render.width("Cancel") + 12;
                if (Render.inside(mx, my, x + w - cw - 4, y + 2, cw, 16)) {
                    s.remove(f.uuid, report("Cancelled your request to " + f.name + "."));
                    return;
                }
                y += 24;
            }
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        radio.released();
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
        if (mx < px + leftW) {
            listScroll -= direction * 26;
        } else if (radioTab) {
            radio.scroll(mx, my, direction);
        } else if (serversTab) {
            servers.scroll(direction);
        } else {
            chatScroll += direction * 20;
        }
    }

    @Override
    protected void keyPressed(int keyCode) {
        if (keyCode == Keyboard.KEY_TAB && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            close();
        } else if (serversTab && focus == FIELD_NONE) {
            servers.keyPressed(keyCode);
        }
    }

    @Override
    public void charTyped(char c, int keyCode) {
        if (serversTab && focus == FIELD_NONE && servers.charTyped(c, keyCode)) {
            return;
        }
        if (radioTab && focus == FIELD_NONE && radio.charTyped(c, keyCode)) {
            return;
        }
        if (focus == FIELD_NONE || keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_ESCAPE) {
            return;
        }
        String text = focus == FIELD_ADD ? addText : messageText;
        int limit = focus == FIELD_ADD ? 16 : 256;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (focus == FIELD_ADD) {
                submitAdd();
            } else {
                submitMessage();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
        } else if (c >= 32 && c != 127 && c != '§' && text.length() < limit) {
            if (focus == FIELD_ADD && !(Character.isLetterOrDigit(c) || c == '_')) {
                return;
            }
            text += c;
        }
        if (focus == FIELD_ADD) {
            addText = text;
        } else {
            messageText = text;
        }
    }

    private void submitAdd() {
        final String name = addText.trim();
        if (name.isEmpty()) {
            focus = FIELD_ADD;
            return;
        }
        addText = "";
        say("Sending a request to " + name + "...", false);
        social().request(name, report("Request sent to " + name + "."));
    }

    private void submitMessage() {
        if (selected == null || messageText.trim().isEmpty()) {
            return;
        }
        String text = messageText.trim();
        messageText = "";
        chatScroll = 0;
        social().send(selected, text, report(null));
    }

    // ------------------------------------------------------------------ helpers

    /** A round "head" with the player's initial, in a colour picked from their name, and a status dot. */
    public static void avatar(String name, int x, int y, int size, int status) {
        float hue = ((name == null ? 0 : name.toLowerCase().hashCode()) & 0xFFFF) / 65535f;
        int bg = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.45f, 0.62f) & 0xFFFFFF);
        Render.round(x, y, size, size, size / 2, bg);
        String initial = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        if (size >= 20) {
            Render.textCenteredScaled(initial, x + size / 2f, y + size / 2f - 6, 1.4f, Theme.TEXT, true);
        } else {
            Render.textCentered(initial, x + size / 2f, y + size / 2f - 4, Theme.TEXT, true);
        }
        int d = Math.max(5, size / 4);
        Render.round(x + size - d, y + size - d, d + 1, d + 1, (d + 1) / 2, 0xFF0E0E14);
        Render.round(x + size - d + 1, y + size - d + 1, d - 1, d - 1, (d - 1) / 2, status);
    }

    private static void pill(int x, int y, int w, int h, String label, int mx, int my, int color, int hover) {
        Render.round(x, y, w, h, h / 2, Render.inside(mx, my, x, y, w, h) ? hover : color);
        Render.textCentered(label, x + w / 2f, y + (h - 8) / 2f, Theme.TEXT, false);
    }

    private static String caret(boolean active) {
        return active && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "";
    }

    /** Keeps the end of long text visible (what you're typing). */
    private static String fitEnd(String s, int maxWidth) {
        while (Render.width(s) > maxWidth && s.length() > 1) {
            s = s.substring(1);
        }
        return s;
    }

    static List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (Render.width(next) <= maxWidth) {
                line.setLength(0);
                line.append(next);
                continue;
            }
            if (line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            // A single word longer than the bubble is split by characters.
            String w = word;
            while (Render.width(w) > maxWidth && w.length() > 1) {
                int cut = w.length();
                while (cut > 1 && Render.width(w.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                out.add(w.substring(0, cut));
                w = w.substring(cut);
            }
            line.append(w);
        }
        if (line.length() > 0 || out.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    private static String time(long at) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                System.currentTimeMillis() - at > 20 * 3600000L ? "MMM d, HH:mm" : "HH:mm");
        return f.format(new java.util.Date(at));
    }

    /** Called by Astra when this screen stops being shown. */
    @Override
    public void onClosed() {
        social().setMenuOpen(false);
        social().setReading(null);
    }
}
