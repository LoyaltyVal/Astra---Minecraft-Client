package dev.astra.client.ui;

import dev.astra.client.Astra;
import dev.astra.client.features.AccountsScreen;
import dev.astra.client.features.QuickJoinScreen;
import dev.astra.client.features.ScreenshotsScreen;
import dev.astra.client.module.impl.ClientSettingsModule;
import dev.astra.client.social.FriendsScreen;
import dev.astra.client.social.SocialManager;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Files;
import dev.astra.client.util.Render;

import java.util.HashMap;
import java.util.Map;

/**
 * Astra's title screen, drawn over the menu background in place of vanilla's buttons:
 *
 *   [(o) Name v]                                       (account menu, top left)
 *
 *                              [*]
 *                       [   Singleplayer   ]
 *                       [   Multiplayer    ]
 *                       [ Friends ][Quick Join]
 *                       [   Cosmetics card   ]
 *
 *   Astra Client 1.8.9   [*][shirt][chat][camera][gear][globe][power]   Not affiliated...
 *
 * The version mixins call {@link #draw} from TitleScreen#render and {@link #click} from
 * mouseClicked; vanilla's own actions (singleplayer, options...) come back through {@link Actions}.
 */
public final class TitleMenu {

    /** What only the game itself can open. */
    public interface Actions {
        void singleplayer();

        void multiplayer();

        void options();

        void language();

        void quit();
    }

    private static final int BUTTON_W = 200;
    private static final int BUTTON_H = 20;
    private static final int ICON = 20;
    private static final int ICON_GAP = 4;
    private static final int CARD_H = 44;

    private static boolean accountOpen;
    private static final Map<String, Float> HOVER = new HashMap<String, Float>();

    private TitleMenu() {
    }

    /** True when Astra's title screen is turned on (Settings > Astra Title Screen). */
    public static boolean enabled() {
        Astra astra = Astra.get();
        ClientSettingsModule t = astra == null ? null : astra.tweaks();
        return t == null || t.titleButtons();
    }

    private static float hover(String key, boolean on) {
        Float v = HOVER.get(key);
        float value = v == null ? 0f : v;
        value += ((on ? 1f : 0f) - value) * 0.3f;
        HOVER.put(key, value);
        return value;
    }

    // ------------------------------------------------------------------ layout

    private static int logoSize(int h) {
        return h < 300 ? 40 : 52;
    }

    private static int buttonsTop(int h) {
        int block = logoSize(h) + 14 + (BUTTON_H + 4) * 3 + CARD_H;
        return Math.max(40, (h - block) / 2) + logoSize(h) + 14;
    }

    private static int iconBarX(int w) {
        int count = ICONS.length;
        return w / 2 - (count * ICON + (count - 1) * ICON_GAP) / 2;
    }

    private static int iconBarY(int h) {
        return h - ICON - 8;
    }

    private static final String[][] ICONS = {
            null, Icons.SHIRT, Icons.CHAT, Icons.CAMERA, Icons.FILM, Icons.GEAR, Icons.GLOBE, Icons.POWER};
    private static final String[] ICON_NAMES = {
            "Astra Menu", "Cosmetics", "Friends", "Screenshots", "Replays", "Options", "Language", "Quit Game"};

    private static String sessionName() {
        String n = Astra.platform().getSessionName();
        return n == null ? "Player" : n;
    }

    private static int accountButtonW() {
        return Render.width(sessionName()) + 38;
    }

    // ------------------------------------------------------------------ drawing

    public static void draw(int w, int h, int mx, int my) {
        Render.rect(0, 0, w, h, 0x38000000);
        drawCenter(w, h, mx, my);
        drawIconBar(w, h, mx, my);
        drawAccount(w, h, mx, my);

        String version = Astra.brand(Astra.platform().minecraftVersion()) + " (v" + Astra.VERSION + ")";
        Render.text(version, 6, h - 12, 0xB0FFFFFF, true);
        String legal = "Not affiliated with Mojang or Microsoft.";
        Render.text(legal, w - 6 - Render.width(legal), h - 12, 0xB0FFFFFF, true);
    }

    private static void drawCenter(int w, int h, int mx, int my) {
        int size = logoSize(h);
        int y = buttonsTop(h);
        RankIcon.draw(w / 2f - size / 2f, y - size - 14, size, 0xFFFFFFFF);

        int x = w / 2 - BUTTON_W / 2;
        button("Singleplayer", Icons.PERSON, x, y, BUTTON_W, mx, my, false);
        y += BUTTON_H + 4;
        button("Multiplayer", Icons.PEOPLE, x, y, BUTTON_W, mx, my, false);
        y += BUTTON_H + 4;
        int half = (BUTTON_W - 4) / 2;
        button("Friends", Icons.CHAT, x, y, half, mx, my, false);
        SocialManager social = Astra.get().getSocial();
        int online = social == null ? 0 : social.getAstraOnline();
        if (online > 0) {
            // Players on Astra right now, like a count badge.
            String n = String.valueOf(online);
            int bw = Render.width(n) + 8;
            Render.round(x + half - bw - 4, y + 4, bw, 12, 3, 0x60FFFFFF);
            Render.textCentered(n, x + half - bw / 2f - 4, y + 6, Theme.TEXT, false);
        }
        button("Quick Join", Icons.GLOBE, x + half + 4, y, BUTTON_W - half - 4, mx, my, true);
        y += BUTTON_H + 4;
        drawCard(x, y, mx, my);
    }

    /** A flat dark button with an icon and label (accent-tinted when {@code accent}). */
    private static void button(String label, String[] icon, int x, int y, int w, int mx, int my, boolean accent) {
        float hv = hover("b:" + label, Render.inside(mx, my, x, y, w, BUTTON_H));
        int base = accent ? Colors.lerp(Theme.accent(), 0xFF000000, 0.72f) | 0xE0000000 : 0xE0101014;
        int hot = accent ? Colors.lerp(Theme.accent(), 0xFF000000, 0.55f) | 0xF0000000 : 0xF0262630;
        Render.round(x, y, w, BUTTON_H, 3, Colors.lerp(base, hot, hv));
        int tw = Render.width(label) + 13;
        int tx = x + (w - tw) / 2;
        int color = accent ? Colors.lerp(Theme.accent(), 0xFFFFFFFF, 0.55f) : Theme.TEXT;
        Icons.draw(icon, tx, y + 6, 1, color);
        Render.text(label, tx + 13, y + 6, color, true);
    }

    /** The banner under the buttons: your cosmetics. */
    private static void drawCard(int x, int y, int mx, int my) {
        boolean hot = Render.inside(mx, my, x, y, BUTTON_W, CARD_H);
        float hv = hover("card", hot);
        int a = Theme.accent();
        // Accent fading to dark, drawn as thin vertical strips.
        for (int i = 0; i < BUTTON_W; i += 4) {
            float k = i / (float) BUTTON_W;
            int c = Colors.lerp(Colors.lerp(a, 0xFF000000, 0.25f - 0.1f * hv), 0xFF14101E, k * 0.85f);
            Render.rect(x + i, y + 1, Math.min(4, BUTTON_W - i), CARD_H - 2, c);
        }
        Render.outline(x, y, BUTTON_W, CARD_H, Colors.withAlpha(a, 0x90 + (int) (0x50 * hv)));
        Icons.draw(Icons.SHIRT, x + 10, y + 11, 2, 0xFFFFFFFF);
        Render.textCenteredScaled("COSMETICS", x + 36 + Render.width("COSMETICS") * 0.75f, y + 9, 1.5f, Theme.TEXT, true);
        Render.text("Capes, wings and emotes", x + 36, y + 26, 0xD0FFFFFF, false);
        Icons.draw(Icons.ARROW, x + BUTTON_W - 16 + (int) (2 * hv), y + 26, 1, 0xFFFFFFFF);
    }

    private static void drawIconBar(int w, int h, int mx, int my) {
        int x = iconBarX(w);
        int y = iconBarY(h);
        String tip = null;
        int tipX = 0;
        for (int i = 0; i < ICONS.length; i++) {
            int bx = x + i * (ICON + ICON_GAP);
            boolean hot = Render.inside(mx, my, bx, y, ICON, ICON);
            float hv = hover("i:" + i, hot);
            Render.round(bx, y, ICON, ICON, 4, Colors.lerp(0xE0101014, 0xF0262630, hv));
            Render.outline(bx, y, ICON, ICON, Colors.withAlpha(Theme.accent(), 0x30 + (int) (0x70 * hv)));
            if (ICONS[i] == null) {
                RankIcon.draw(bx + 3, y + 3, ICON - 6, 0xFFFFFFFF);
            } else {
                Icons.centered(ICONS[i], bx + ICON / 2f, y + ICON / 2f, 1, Theme.TEXT);
            }
            if (hot) {
                tip = ICON_NAMES[i];
                tipX = bx + ICON / 2;
            }
        }
        // A dot on Friends when you have unread messages.
        SocialManager social = Astra.get().getSocial();
        if (social != null && social.getTotalUnread() > 0) {
            int bx = x + 2 * (ICON + ICON_GAP);
            Render.round(bx + ICON - 5, y - 1, 6, 6, 3, 0xFF3DDC97);
        }
        if (tip != null) {
            int tw = Render.width(tip) + 8;
            Render.round(tipX - tw / 2, y - 15, tw, 12, 3, 0xF0101014);
            Render.textCentered(tip, tipX, y - 13, Theme.TEXT, false);
        }
    }

    private static void drawAccount(int w, int h, int mx, int my) {
        String name = sessionName();
        int bw = accountButtonW();
        boolean hot = Render.inside(mx, my, 8, 8, bw, 20);
        float hv = hover("acct", hot || accountOpen);
        Render.round(8, 8, bw, 20, 3, Colors.lerp(0xE0101014, 0xF0262630, hv));
        FriendsScreen.avatar(name, 11, 11, 14, 0xFF3DDC97);
        Render.text(name, 30, 14, Theme.TEXT, true);
        Icons.draw(Icons.CARET_DOWN, 8 + bw - 11, 13, 1, Theme.SUBTEXT);

        if (!accountOpen) {
            return;
        }
        int px = 8;
        int py = 32;
        int pw = Math.max(170, Render.width(name) + 60);
        int ph = 96;
        Render.round(px, py, pw, ph, 4, 0xF0101014);
        Render.outline(px, py, pw, ph, 0x30FFFFFF);
        FriendsScreen.avatar(name, px + 8, py + 8, 30, 0xFF3DDC97);
        Render.text(name, px + 46, py + 11, Theme.TEXT, true);
        String rank = Astra.get().getCosmetics() == null ? null : Astra.get().getCosmetics().getRankNameByName(name);
        Integer rankColor = Astra.get().getCosmetics() == null ? null : Astra.get().getCosmetics().getRankColorByName(name);
        String sub = rank != null ? rank + " on Astra" : "Playing on Astra";
        if (rankColor != null) {
            RankIcon.draw(px + 46, py + 23, 9, 0xFF000000 | rankColor);
            Render.text(sub, px + 58, py + 24, Theme.SUBTEXT, false);
        } else {
            Render.text(sub, px + 46, py + 24, Theme.SUBTEXT, false);
        }

        boolean site = websiteUrl() != null;
        panelButton("Account Settings", Icons.GEAR, px + 8, py + 46, pw - 16, mx, my, false, site);
        Render.rect(px + 8, py + 67, pw - 16, 1, 0x20FFFFFF);
        panelButton("Manage Accounts", Icons.PEOPLE, px + 8, py + 72, pw - 16, mx, my, true, true);
    }

    private static void panelButton(String label, String[] icon, int x, int y, int w, int mx, int my, boolean accent, boolean enabled) {
        boolean hot = enabled && Render.inside(mx, my, x, y, w, 18);
        int bg = accent ? (hot ? Colors.lerp(Theme.accent(), 0xFF000000, 0.35f) : Colors.lerp(Theme.accent(), 0xFF000000, 0.6f))
                : hot ? 0xFF2C2C36 : 0xFF1E1E26;
        Render.round(x, y, w, 18, 3, bg);
        int color = !enabled ? 0xFF6A6A78 : accent ? Colors.lerp(Theme.accent(), 0xFFFFFFFF, 0.6f) : Theme.TEXT;
        int tw = Render.width(label) + 13;
        int tx = x + (w - tw) / 2;
        Icons.draw(icon, tx, y + 5, 1, color);
        Render.text(label, tx + 13, y + 5, color, false);
    }

    /** The Astra website's account page, or null when the game wasn't started by the Astra app. */
    private static String websiteUrl() {
        String api = System.getProperty("astra.api");
        if (api == null || !api.trim().matches("https?://.+")) {
            return null;
        }
        String base = api.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/account";
    }

    // ------------------------------------------------------------------ input

    /** Handles a click on the title screen; always returns true (vanilla's buttons are hidden). */
    public static boolean click(int w, int h, int mx, int my, int button, Actions game) {
        if (button != 0) {
            accountOpen = false;
            return true;
        }
        // Account menu.
        if (Render.inside(mx, my, 8, 8, accountButtonW(), 20)) {
            accountOpen = !accountOpen;
            return true;
        }
        if (accountOpen) {
            int pw = Math.max(170, Render.width(sessionName()) + 60);
            accountOpen = false;
            if (Render.inside(mx, my, 16, 78, pw - 16, 18)) {
                Files.openUrl(websiteUrl());
                return true;
            }
            if (Render.inside(mx, my, 16, 104, pw - 16, 18)) {
                Astra.platform().openUi(new AccountsScreen());
                return true;
            }
            if (Render.inside(mx, my, 8, 32, pw, 96)) {
                accountOpen = true; // a click inside the card that isn't a button
                return true;
            }
        }

        int x = w / 2 - BUTTON_W / 2;
        int y = buttonsTop(h);
        int half = (BUTTON_W - 4) / 2;
        if (Render.inside(mx, my, x, y, BUTTON_W, BUTTON_H)) {
            game.singleplayer();
        } else if (Render.inside(mx, my, x, y + BUTTON_H + 4, BUTTON_W, BUTTON_H)) {
            game.multiplayer();
        } else if (Render.inside(mx, my, x, y + (BUTTON_H + 4) * 2, half, BUTTON_H)) {
            Astra.platform().openUi(new FriendsScreen());
        } else if (Render.inside(mx, my, x + half + 4, y + (BUTTON_H + 4) * 2, BUTTON_W - half - 4, BUTTON_H)) {
            QuickJoinScreen.open();
        } else if (Render.inside(mx, my, x, y + (BUTTON_H + 4) * 3, BUTTON_W, CARD_H)) {
            Astra.platform().openUi(ModMenuScreen.cosmetics());
        } else {
            clickIcon(w, h, mx, my, game);
        }
        return true;
    }

    private static void clickIcon(int w, int h, int mx, int my, Actions game) {
        int x = iconBarX(w);
        int y = iconBarY(h);
        for (int i = 0; i < ICONS.length; i++) {
            if (!Render.inside(mx, my, x + i * (ICON + ICON_GAP), y, ICON, ICON)) {
                continue;
            }
            switch (i) {
                case 0:
                    Astra.platform().openUi(new ModMenuScreen());
                    break;
                case 1:
                    Astra.platform().openUi(ModMenuScreen.cosmetics());
                    break;
                case 2:
                    Astra.platform().openUi(new FriendsScreen());
                    break;
                case 3:
                    Astra.platform().openUi(new ScreenshotsScreen());
                    break;
                case 4:
                    Astra.platform().openUi(new dev.astra.client.replay.ReplaysScreen());
                    break;
                case 5:
                    game.options();
                    break;
                case 6:
                    game.language();
                    break;
                default:
                    game.quit();
                    break;
            }
            return;
        }
    }

    // ------------------------------------------------------------------ multiplayer tabs

    private static final int TAB_W = 76;
    private static final int TAB_H = 18;

    /**
     * The "Your Servers | Discover" switch drawn at the top of the multiplayer screen (over its
     * title). Discover is Quick Join: the servers your friends and other Astra players are on.
     */
    public static void drawServerTabs(int w, int mx, int my) {
        int x = w / 2 - TAB_W - 3;
        int y = 7;
        // A dark band over the "Play Multiplayer" title (the server list starts at y = 32).
        Render.rect(0, 0, w, 32, 0xC8000000);
        Render.round(x - 3, y - 3, TAB_W * 2 + 12, TAB_H + 6, 4, 0xF0101014);
        Render.round(x, y, TAB_W, TAB_H, 3, 0xFF2A2A34);
        tabLabel("Your Servers", Icons.PEOPLE, x, y, true);
        boolean hot = Render.inside(mx, my, x + TAB_W + 6, y, TAB_W, TAB_H);
        if (hot) {
            Render.round(x + TAB_W + 6, y, TAB_W, TAB_H, 3, 0xFF1E1E26);
        }
        tabLabel("Discover", Icons.GLOBE, x + TAB_W + 6, y, hot);
    }

    private static void tabLabel(String label, String[] icon, int x, int y, boolean bright) {
        int color = bright ? Theme.TEXT : Theme.SUBTEXT;
        int tw = Render.width(label) + 12;
        int tx = x + (TAB_W - tw) / 2;
        Icons.draw(icon, tx, y + 5, 1, color);
        Render.text(label, tx + 12, y + 5, color, false);
    }

    /** True when the click hit the tab switch (and handled it). */
    public static boolean clickServerTabs(int w, int mx, int my) {
        int x = w / 2 - TAB_W - 3;
        if (Render.inside(mx, my, x + TAB_W + 6, 7, TAB_W, TAB_H)) {
            QuickJoinScreen.open();
            return true;
        }
        return Render.inside(mx, my, x - 3, 4, TAB_W * 2 + 12, TAB_H + 6);
    }
}
