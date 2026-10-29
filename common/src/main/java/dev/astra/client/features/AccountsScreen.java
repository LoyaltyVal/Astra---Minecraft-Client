package dev.astra.client.features;

import dev.astra.client.social.FriendsScreen;
import dev.astra.client.ui.Panels;
import dev.astra.client.ui.Theme;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.List;

/** The in-game account switcher: pick a saved account, or add one, without restarting the game. */
public final class AccountsScreen extends UiScreen {

    private static final int ROW = 28;

    private final long openedAt = System.currentTimeMillis();
    private String notice;
    private boolean noticeError;
    private long noticeAt;
    private int px, py, pw, ph, listTop;

    private static Accounts accounts() {
        return Accounts.get();
    }

    private void say(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        accounts().refresh();
    }

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        float ease = Panels.ease(openedAt);
        Panels.backdrop(width, height, ease);
        List<Accounts.Account> list = accounts().list();
        pw = Math.min(300, width - 24);
        ph = Math.min(Math.max(150, 96 + list.size() * ROW), height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - ease) * 12);
        Panels.card(px, py, pw, ph, ease);

        Render.text("Accounts", px + 12, py + 11, Theme.TEXT, false);
        String playing = "Playing as " + mc().getSessionName();
        Render.text(Render.trim(playing, pw - 90), px + pw - 12 - Math.min(Render.width(playing), pw - 90), py + 11, Theme.SUBTEXT, false);

        int x = px + 10;
        int w = pw - 20;
        listTop = py + 28;
        if (!accounts().available()) {
            Render.textCentered("Start Minecraft from the Astra app", px + pw / 2f, py + ph / 2f - 10, Theme.TEXT, false);
            Render.textCentered("to switch accounts in game.", px + pw / 2f, py + ph / 2f + 2, Theme.SUBTEXT, false);
            return;
        }
        if (list.isEmpty()) {
            Render.textCentered(accounts().isLoading() ? "Loading..." : "Can't reach the Astra app. Is it still open?",
                    px + pw / 2f, listTop + 20, Theme.SUBTEXT, false);
        }

        String current = mc().getSessionName();
        int y = listTop;
        for (Accounts.Account a : list) {
            boolean here = a.name.equalsIgnoreCase(current);
            boolean hover = accounts().getBusy() == null && Render.inside(mx, my, x, y, w, ROW - 2);
            Render.round(x, y, w, ROW - 2, 6, here ? Colors.withAlpha(Theme.accent(), 0x55) : hover ? 0x26FFFFFF : 0x12FFFFFF);
            FriendsScreen.avatar(a.name, x + 5, y + 4, 18, here ? 0xFF3DDC97 : a.expired ? 0xFFFF5C7A : 0xFF5A5A66);
            Render.text(Render.trim(a.name, w - 110), x + 29, y + 4, Theme.TEXT, false);
            String sub = here ? "Playing now" : a.expired ? "Sign-in expired: click to sign in again" : "Click to switch";
            Render.text(Render.trim(sub, w - 40), x + 29, y + 14, here ? 0xFF8FE3BE : a.expired ? 0xFFFF7A90 : Theme.SUBTEXT, false);
            y += ROW;
        }

        // Bottom: what's happening, and "add account".
        int by = py + ph - 26;
        String busy = accounts().getBusy();
        if (busy != null) {
            Render.text(Render.trim(busy, w), x + 2, by - 12, 0xFFFFD166, false);
        } else if (mc().isInGame()) {
            Render.text(Render.trim("Switching takes you out of this world or server.", w), x + 2, by - 12, 0x88A0A0B0, false);
        }
        Panels.pill(x, by, "+ Add a Microsoft account", mx, my, Colors.withAlpha(Theme.accent(), 0xC0), Theme.accent());

        if (notice != null && System.currentTimeMillis() - noticeAt < 6000) {
            Render.text(Render.trim(notice, pw), px + 10, py - 12, noticeError ? 0xFFFF7A90 : 0xFF8FE3BE, true);
        }
        Panels.hint("Your accounts are saved in the Astra app  |  Esc to close", width / 2, py + ph + 4);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0 || !accounts().available() || accounts().getBusy() != null) {
            return;
        }
        int x = px + 10;
        int w = pw - 20;
        int by = py + ph - 26;
        if (Render.inside(mx, my, x, by, Panels.pillWidth("+ Add a Microsoft account"), 16)) {
            accounts().add(new Accounts.Callback() {
                @Override
                public void done(String error) {
                    say(error == null ? "Account added. Click it to switch." : error, error != null);
                }
            });
            return;
        }
        int y = listTop;
        String current = mc().getSessionName();
        for (final Accounts.Account a : accounts().list()) {
            if (Render.inside(mx, my, x, y, w, ROW - 2)) {
                if (a.name.equalsIgnoreCase(current)) {
                    say("You're already playing as " + a.name + ".", false);
                } else if (a.expired) {
                    // Signing in again with that account replaces the expired sign-in.
                    accounts().add(new Accounts.Callback() {
                        @Override
                        public void done(String error) {
                            say(error == null ? "Signed in again. Click " + a.name + " to switch." : error, error != null);
                        }
                    });
                } else {
                    accounts().switchTo(a, new Accounts.Callback() {
                        @Override
                        public void done(String error) {
                            say(error == null ? "Now playing as " + a.name + "." : error, error != null);
                        }
                    });
                }
                return;
            }
            y += ROW;
        }
    }
}
