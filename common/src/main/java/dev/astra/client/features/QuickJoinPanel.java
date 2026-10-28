package dev.astra.client.features;

import dev.astra.client.Astra;
import dev.astra.client.platform.Platform;
import dev.astra.client.social.SocialManager;
import dev.astra.client.ui.Panels;
import dev.astra.client.ui.TextField;
import dev.astra.client.ui.Theme;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick Join, shown as the Servers tab of the Friends menu: favourite servers (1-9 to join),
 * recently played ones, and how many friends are on each.
 */
public final class QuickJoinPanel {

    /** What the panel needs from the screen it sits in. */
    public interface Host {
        void say(String message, boolean error);

        /** Closes the menu (before joining a server). */
        void closeMenu();
    }

    private static final int ROW = 24;
    private static final int RECENT_ROW = 20;
    private static final int HEADER = 12;

    private final Host host;
    private final TextField address = new TextField("Add a server address", 60, new TextField.Filter() {
        @Override
        public boolean allow(char c) {
            return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ':';
        }
    });
    private float scroll;
    private int x, y, w, h, listTop, listBottom, formY;

    public QuickJoinPanel(Host host) {
        this.host = host;
    }

    private static Platform mc() {
        return Astra.platform();
    }

    private static Servers servers() {
        return Servers.get();
    }

    /** One line of the list: a favourite or a recent server, or a section title. */
    private static final class Item {
        final String title;
        final Servers.Entry entry;
        final boolean favorite;
        final int index;
        final int height;
        int top;

        Item(String title, Servers.Entry entry, boolean favorite, int index, int height) {
            this.title = title;
            this.entry = entry;
            this.favorite = favorite;
            this.index = index;
            this.height = height;
        }
    }

    private List<Item> items() {
        List<Item> out = new ArrayList<Item>();
        List<Servers.Entry> favs = servers().favorites();
        out.add(new Item("FAVOURITES  ·  press 1-9 to join", null, false, -1, HEADER));
        for (int i = 0; i < favs.size(); i++) {
            out.add(new Item(null, favs.get(i), true, i, ROW));
        }
        List<Item> recent = new ArrayList<Item>();
        for (Servers.Entry e : servers().recent()) {
            if (!servers().isFavorite(e.address)) {
                recent.add(new Item(null, e, false, -1, RECENT_ROW));
            }
        }
        if (!recent.isEmpty()) {
            out.add(new Item("RECENT", null, false, -1, HEADER + 4));
            out.addAll(recent);
        }
        int top = 0;
        for (Item it : out) {
            it.top = top;
            top += it.height + (it.title == null ? 2 : 0);
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    public void draw(int x, int y, int w, int h, int mx, int my) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        listTop = y;
        formY = y + h - 18;
        listBottom = formY - 6;

        List<Item> items = items();
        Item last = items.get(items.size() - 1);
        float max = Math.max(0, last.top + last.height - (listBottom - listTop));
        scroll = Math.max(0, Math.min(scroll, max));

        String current = mc().getServerAddress();
        Render.scissor(x - 2, listTop, w + 4, listBottom - listTop);
        for (Item it : items) {
            int ty = listTop + it.top - (int) scroll;
            if (ty + it.height < listTop || ty > listBottom) {
                continue;
            }
            if (it.title != null) {
                Render.text(it.title, x + 2, ty + it.height - 10, 0x88A0A0B0, false);
                continue;
            }
            boolean hover = my >= listTop && my < listBottom && Render.inside(mx, my, x, ty, w, it.height);
            if (it.favorite) {
                drawFavorite(it, ty, hover, mx, my, current);
            } else {
                drawRecent(it, ty, hover, mx, my);
            }
        }
        Render.endScissor();
        if (servers().favorites().isEmpty() && servers().recent().isEmpty()) {
            Render.textCentered("No servers yet", x + w / 2f, y + 40, Theme.SUBTEXT, false);
        }

        // Add form.
        boolean canAddHere = canAddHere();
        int addW = Panels.pillWidth("Add");
        int hereW = canAddHere ? Panels.pillWidth("+ This one") + 4 : 0;
        address.draw(x, formY, w - addW - hereW - 4, 18);
        Panels.pill(x + w - addW - hereW, formY + 1, "Add", mx, my, Colors.withAlpha(Theme.accent(), 0xC0), Theme.accent());
        if (canAddHere) {
            Panels.pill(x + w - hereW + 4, formY + 1, "+ This one", mx, my, 0x40FFFFFF, 0x60FFFFFF);
        }
    }

    private void drawFavorite(Item it, int ty, boolean hover, int mx, int my, String current) {
        Servers.Entry e = it.entry;
        boolean here = Servers.same(current, e.address);
        Render.round(x, ty, w, it.height, 5, hover ? 0x22FFFFFF : 0x12FFFFFF);
        if (here) {
            Render.rect(x, ty + 5, 2, it.height - 10, Theme.ON);
        }
        if (it.index < 9) {
            Render.round(x + 5, ty + 6, 12, 12, 6, Colors.withAlpha(Theme.accent(), 0x90));
            Render.textCentered(String.valueOf(it.index + 1), x + 11, ty + 8, Theme.TEXT, false);
        }
        int friends = friendsOn(e.address);
        String friendsLabel = friends == 0 ? null : friends == 1 ? "1 friend" : friends + " friends";
        int rightW = hover ? 56 : friendsLabel != null ? Render.width(friendsLabel) + 14 : 0;
        Render.text(Render.trim(e.name, w - 30 - rightW), x + 22, ty + 3, Theme.TEXT, false);
        Render.text(Render.trim(here ? "You're here" : e.address, w - 30 - rightW), x + 22, ty + 13,
                here ? 0xFF8FE3BE : Theme.SUBTEXT, false);
        int right = x + w - 4;
        if (hover) {
            icon(right - 14, ty + 5, "✕", mx, my, 0x40FF5C7A);
            icon(right - 32, ty + 5, "↓", mx, my, 0x30FFFFFF);
            icon(right - 50, ty + 5, "↑", mx, my, 0x30FFFFFF);
        } else if (friendsLabel != null) {
            int fw = Render.width(friendsLabel) + 10;
            Render.round(right - fw, ty + 6, fw, 12, 6, 0x303DDC97);
            Render.textCentered(friendsLabel, right - fw / 2f, ty + 8, 0xFF8FE3BE, false);
        }
    }

    private void drawRecent(Item it, int ty, boolean hover, int mx, int my) {
        Servers.Entry e = it.entry;
        Render.round(x, ty, w, it.height, 5, hover ? 0x22FFFFFF : 0x0CFFFFFF);
        Render.text(Render.trim(e.address, w - 80), x + 7, ty + 6, Theme.TEXT, false);
        String ago = ago(e.lastPlayed);
        Render.text(ago, x + w - 22 - Render.width(ago), ty + 6, 0x88A0A0B0, false);
        boolean starHover = Render.inside(mx, my, x + w - 17, ty + 3, 14, 14);
        Render.text("★", x + w - 13, ty + 6, starHover ? 0xFFFFD166 : 0x55FFFFFF, false);
    }

    private static void icon(int x, int y, String s, int mx, int my, int color) {
        boolean hover = Render.inside(mx, my, x, y, 14, 14);
        Render.round(x, y, 14, 14, 4, hover ? Colors.withAlpha(color, 0xC0) : color);
        Render.textCentered(s, x + 7, y + 3, Theme.TEXT, false);
    }

    // ------------------------------------------------------------------ input

    /** Returns true when the click was inside the panel. */
    public boolean click(int mx, int my) {
        if (!Render.inside(mx, my, x, y, w, h)) {
            address.setFocused(false);
            return false;
        }
        boolean canAddHere = canAddHere();
        int addW = Panels.pillWidth("Add");
        int hereW = canAddHere ? Panels.pillWidth("+ This one") + 4 : 0;
        if (Render.inside(mx, my, x + w - addW - hereW, formY + 1, addW, 16)) {
            submit();
            return true;
        }
        if (canAddHere && Render.inside(mx, my, x + w - hereW + 4, formY + 1, hereW - 4, 16)) {
            String err = servers().addFavorite(null, mc().getServerAddress());
            host.say(err == null ? "Added " + Servers.niceName(mc().getServerAddress()) + " to favourites." : err, err != null);
            return true;
        }
        if (address.click(mx, my) || my < listTop || my >= listBottom) {
            return true;
        }
        for (Item it : items()) {
            int ty = listTop + it.top - (int) scroll;
            if (it.title != null || !Render.inside(mx, my, x, ty, w, it.height)) {
                continue;
            }
            Servers.Entry e = it.entry;
            int right = x + w - 4;
            if (it.favorite) {
                if (Render.inside(mx, my, right - 14, ty + 5, 14, 14)) {
                    servers().removeFavorite(e.address);
                    host.say("Removed " + e.name + ".", false);
                } else if (Render.inside(mx, my, right - 32, ty + 5, 14, 14)) {
                    servers().move(e.address, 1);
                } else if (Render.inside(mx, my, right - 50, ty + 5, 14, 14)) {
                    servers().move(e.address, -1);
                } else {
                    join(e.address);
                }
            } else if (Render.inside(mx, my, x + w - 17, ty + 3, 14, 14)) {
                String err = servers().addFavorite(null, e.address);
                host.say(err == null ? "Added to favourites." : err, err != null);
            } else {
                join(e.address);
            }
            return true;
        }
        return true;
    }

    public void scroll(int direction) {
        scroll -= direction * ROW;
    }

    /** Number keys 1-9 join a favourite (when not typing). */
    public void keyPressed(int keyCode) {
        if (address.isFocused()) {
            return;
        }
        int n = keyCode - Keyboard.KEY_1;
        if (n >= 0 && n < 9 && n < servers().favorites().size()) {
            join(servers().favorites().get(n).address);
        }
    }

    /** Returns true when the key was used by the text box. */
    public boolean charTyped(char c, int keyCode) {
        if (!address.isFocused()) {
            return false;
        }
        if (address.type(c, keyCode)) {
            submit();
        }
        return true;
    }

    public boolean isTyping() {
        return address.isFocused();
    }

    private boolean canAddHere() {
        String a = mc().getServerAddress();
        return a != null && !"singleplayer".equals(a) && !servers().isFavorite(a);
    }

    private void submit() {
        String a = address.get().trim();
        if (a.isEmpty()) {
            address.setFocused(true);
            return;
        }
        String err = servers().addFavorite(null, a);
        if (err != null) {
            host.say(err, true);
            return;
        }
        host.say("Saved " + a + ".", false);
        address.set("");
        address.setFocused(false);
    }

    private void join(String addr) {
        if (Servers.same(mc().getServerAddress(), addr)) {
            host.say("You're already on this server.", false);
            return;
        }
        host.closeMenu();
        mc().joinServer(addr);
    }

    private static int friendsOn(String addr) {
        Astra astra = Astra.get();
        if (astra == null || astra.getSocial() == null) {
            return 0;
        }
        int n = 0;
        for (SocialManager.Friend f : astra.getSocial().getFriends()) {
            if (f.joinable() && Servers.same(f.server, addr)) {
                n++;
            }
        }
        return n;
    }

    private static String ago(long at) {
        if (at <= 0) {
            return "";
        }
        long m = (System.currentTimeMillis() - at) / 60000;
        return m < 1 ? "just now" : m < 60 ? m + " min ago" : m < 1440 ? (m / 60) + " h ago" : (m / 1440) + " d ago";
    }
}
