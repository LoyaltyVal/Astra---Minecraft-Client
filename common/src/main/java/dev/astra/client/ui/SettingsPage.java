package dev.astra.client.ui;

import dev.astra.client.Astra;
import dev.astra.client.input.Keyboard;
import dev.astra.client.input.Mouse;
import dev.astra.client.module.Module;
import dev.astra.client.module.ModuleManager;
import dev.astra.client.module.impl.BorderlessModule;
import dev.astra.client.module.impl.ChatModule;
import dev.astra.client.module.impl.ClientSettingsModule;
import dev.astra.client.module.impl.CosmeticsModule;
import dev.astra.client.module.impl.EmotesModule;
import dev.astra.client.module.impl.FullbrightModule;
import dev.astra.client.module.impl.HurtcamModule;
import dev.astra.client.module.impl.MenuBackgroundModule;
import dev.astra.client.module.impl.NoGlintModule;
import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.module.impl.ScreenshotsModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.ModeSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.module.setting.Setting;
import dev.astra.client.module.setting.TextSetting;
import dev.astra.client.util.Keys;
import dev.astra.client.util.Render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client settings (the gear in the Right Shift menu), in the same row style as every mod's
 * options: General / Performance / Controls tabs, grouped under headings, with a search box.
 * Every entry is a real setting of a module (nothing here is just for show).
 */
final class SettingsPage {

    // ------------------------------------------------------------------ the entries

    private interface Item {
        String label();
    }

    /** An ON/OFF switch: a boolean setting, or a module's on/off. */
    private static final class Toggle implements Item {
        final String label;
        final BooleanSetting setting;
        final Module module;

        Toggle(String label, BooleanSetting setting, Module module) {
            this.label = label;
            this.setting = setting;
            this.module = module;
        }

        boolean on() {
            return setting != null ? setting.isOn() : module.isEnabled();
        }

        void flip() {
            if (setting != null) {
                setting.toggle();
            } else {
                module.toggle();
            }
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** A full-width row: slider, picker, colour, key bind or text. */
    private static final class Row implements Item {
        final String label;
        final Setting<?> setting;

        Row(String label, Setting<?> setting) {
            this.label = label;
            this.setting = setting;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** A button that does something (e.g. open the HUD editor). */
    private static final class Action implements Item {
        final String label;
        final Runnable run;

        Action(String label, Runnable run) {
            this.label = label;
            this.run = run;
        }

        @Override
        public String label() {
            return label;
        }
    }

    private static final class Section {
        final String title;
        final List<Item> items = new ArrayList<Item>();

        Section(String title) {
            this.title = title;
        }
    }

    private static final class Tab {
        final String name;
        final List<Section> sections = new ArrayList<Section>();

        Tab(String name) {
            this.name = name;
        }
    }

    /** A clickable area recorded while drawing. */
    /** True while drawing the scrolling list (its hits only count inside the visible area). */
    private static boolean clipping;

    private static final class Hit {
        final int x;
        final int y;
        final int w;
        final int h;
        final Runnable action;
        final boolean clipped;

        Hit(int x, int y, int w, int h, Runnable action) {
            this.clipped = clipping;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    // ------------------------------------------------------------------ building

    private final List<Tab> tabs = new ArrayList<Tab>();
    private Tab building;
    private Section section;

    private void tab(String name) {
        building = new Tab(name);
        tabs.add(building);
    }

    private void section(String title) {
        section = new Section(title);
        building.sections.add(section);
    }

    private void add(Item item) {
        if (item != null) {
            section.items.add(item);
        }
    }

    private static Item module(Module m, String label) {
        return m == null ? null : new Toggle(label, null, m);
    }

    private static Item setting(Module m, String name, String label) {
        if (m == null) {
            return null;
        }
        for (Setting<?> s : m.getSettings()) {
            if (s.getName().equals(name)) {
                return s instanceof BooleanSetting ? new Toggle(label, (BooleanSetting) s, null) : new Row(label, s);
            }
        }
        return null;
    }

    private static Item setting(Module m, String name) {
        return setting(m, name, name);
    }

    private final Runnable onBack;

    SettingsPage(Runnable onBack) {
        this.onBack = onBack;
        ModuleManager mm = Astra.get().getModules();
        ClientSettingsModule client = mm.get(ClientSettingsModule.class);
        MenuBackgroundModule menuBg = mm.get(MenuBackgroundModule.class);
        CosmeticsModule cosmetics = mm.get(CosmeticsModule.class);
        EmotesModule emotes = mm.get(EmotesModule.class);
        PerformanceModule perf = mm.get(PerformanceModule.class);
        ChatModule chat = mm.get(ChatModule.class);
        ScreenshotsModule screenshots = mm.get(ScreenshotsModule.class);

        tab("GENERAL");
        section("GENERAL");
        add(module(mm.get(BorderlessModule.class), "Borderless Fullscreen"));
        add(setting(client, "Center Inventory", "Keep Inventory Centered"));
        add(setting(client, "Astra Title Screen"));
        add(setting(client, "No Heart Flashing"));
        add(setting(client, "Own Nametag in F5", "Own Nametag in Third Person"));
        add(setting(client, "Dim Background", "Dim Menu Background"));
        add(module(menuBg, "Custom Menu Background"));
        add(setting(menuBg, "Picture", "Menu Background Picture"));
        add(setting(menuBg, "Darken", "Menu Background Darken"));

        section("HUD OPTIONS");
        add(setting(client, "HUD Editor Labels"));
        add(new Action("Edit HUD Layout", new Runnable() {
            @Override
            public void run() {
                Astra.platform().openUi(new HudEditorScreen());
            }
        }));

        section("NOTIFICATION OPTIONS");
        add(setting(client, "Show Friend Statuses"));
        add(setting(client, "Show Friend Requests"));
        add(setting(client, "Show Friend Messages"));
        add(setting(screenshots, "Pop-up When Saved", "Screenshot Notifications"));
        add(setting(client, "Notifications Position"));

        section("COSMETIC");
        add(setting(cosmetics, "Show Cape", "Show Cloaks"));
        add(setting(cosmetics, "Show Wings"));
        add(setting(cosmetics, "Show Rank Stars", "Show Nametag Icon"));
        add(setting(cosmetics, "Show Others' Cosmetics"));
        add(setting(cosmetics, "Share My Server"));
        if (cosmetics != null) {
            // Anything else the cosmetics module has (wing size, flap speed, wing image...).
            for (Setting<?> s : cosmetics.getSettings()) {
                if (!(s instanceof BooleanSetting)) {
                    add(new Row(s.getName(), s));
                }
            }
        }

        section("EMOTE");
        add(module(emotes, "Emotes"));
        add(setting(emotes, "Third Person While Emoting"));
        add(setting(emotes, "Show Others' Emotes"));

        section("INTERFACE");
        add(setting(client, "Accent Color"));

        tab("PERFORMANCE");
        section("PERFORMANCE");
        add(module(perf, "Performance Boosts"));
        add(setting(perf, "Limit FPS In Background"));
        add(setting(perf, "No Fog"));
        add(setting(perf, "Minimal Particles"));
        add(setting(perf, "Hide Entity Shadows"));
        add(setting(perf, "Background FPS"));
        section("VISUAL");
        add(module(mm.get(FullbrightModule.class), "Fullbright"));
        add(module(mm.get(HurtcamModule.class), "No Hurtcam"));
        add(module(mm.get(NoGlintModule.class), "No Enchant Glint"));
        section("CHAT");
        add(module(chat, "Chat Tweaks"));
        add(setting(chat, "Long History"));
        add(setting(chat, "Stack Repeated Messages"));

        // Every key bind in the client, grouped by mod.
        tab("CONTROLS");
        for (Module m : mm.getModules()) {
            List<Item> keys = new ArrayList<Item>();
            for (Setting<?> s : m.getSettings()) {
                if (s instanceof KeySetting) {
                    keys.add(new Row(s.getName(), s));
                }
            }
            if (!keys.isEmpty()) {
                section(m.getName().toUpperCase(Locale.ROOT));
                section.items.addAll(keys);
            }
        }

        // Drop empty sections (a module that isn't in this version).
        for (Tab t : tabs) {
            for (int i = t.sections.size() - 1; i >= 0; i--) {
                if (t.sections.get(i).items.isEmpty()) {
                    t.sections.remove(i);
                }
            }
        }
    }

    // ------------------------------------------------------------------ state

    private static final int BAR = 20;
    private static final int GAP = 4;
    private static final int SCROLLBAR = 6;
    private static final int ROW_H = 20;
    private static final int HEADER_H = 14;

    private int tabIndex;
    private String search = "";
    private boolean searchFocused;
    private float scroll;
    private float targetScroll;
    private int contentHeight;

    private NumberSetting dragging;
    private int dragX;
    private int dragW;
    private KeySetting listening;
    private TextSetting typing;
    private String typingBefore;

    private final List<Hit> hits = new ArrayList<Hit>();

    // Layout of the last frame.
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;

    // ------------------------------------------------------------------ drawing

    void draw(int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        if (dragging != null) {
            if (Mouse.isButtonDown(0)) {
                dragging.setPercent((mx - dragX) / (double) dragW);
            } else {
                dragging = null;
                Astra.get().saveConfig();
            }
        }

        drawBar(x, y, w, mx, my);

        // Same content area as the mod grid.
        viewX = x + GAP + 2;
        viewY = y + BAR + GAP;
        viewW = w - GAP * 2 - 4 - SCROLLBAR;
        viewH = h - BAR - GAP * 2;

        float max = Math.max(0, contentHeight - viewH);
        targetScroll = Math.max(0, Math.min(max, targetScroll));
        scroll += (targetScroll - scroll) * 0.35f;
        scroll = Math.max(0, Math.min(max, scroll));

        boolean mouseIn = Render.inside(mx, my, viewX, viewY, viewW, viewH);
        Render.scissor(viewX, viewY, viewW, viewH);
        clipping = true;
        int end = drawSections(viewX, viewY - (int) scroll, viewW, mouseIn ? mx : -1000, mouseIn ? my : -1000);
        clipping = false;
        Render.endScissor();
        contentHeight = end - (viewY - (int) scroll);

        // Scrollbar.
        if (max > 0) {
            int trackX = x + w - GAP - SCROLLBAR + 2;
            int barH = Math.max(14, (int) (viewH * (viewH / (float) contentHeight)));
            int barY = viewY + (int) ((viewH - barH) * (scroll / max));
            Render.round(trackX, viewY, 3, viewH, 1, 0x30FFFFFF);
            Render.round(trackX, barY, 3, barH, 1, Theme.accent());
        }
    }

    /** The tab bar: back, the three tabs, and the search box (styled like the mods tab bar). */
    private void drawBar(int x, int y, int w, int mx, int my) {
        Render.round(x, y, w, BAR, 4, Theme.PANEL_BAR);
        Render.rect(x, y + BAR - 3, w, 3, Theme.PANEL_BAR);
        int ty = y + 3;
        int th = BAR - 6;

        boolean back = Render.inside(mx, my, x + 4, ty, 44, th);
        Render.round(x + 4, ty, 44, th, 2, back ? Theme.CONTROL_HOVER : Theme.CONTROL);
        Render.textCentered("< BACK", x + 26, ty + 3, Theme.TEXT, true);
        hits.add(new Hit(x + 4, ty, 44, th, onBack));

        int tx = x + 52;
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            String name = tabs.get(i).name;
            int tw = Render.width(name) + 12;
            boolean selected = i == tabIndex && search.isEmpty();
            boolean hovered = Render.inside(mx, my, tx, ty, tw, th);
            if (selected) {
                Render.round(tx, ty, tw, th, 2, Theme.accentCard());
            } else if (hovered) {
                Render.round(tx, ty, tw, th, 2, Theme.CONTROL);
            }
            Render.textCentered(name, tx + tw / 2f, ty + 3, selected || hovered ? Theme.TEXT : Theme.SUBTEXT, true);
            hits.add(new Hit(tx, ty, tw, th, new Runnable() {
                @Override
                public void run() {
                    tabIndex = index;
                    search = "";
                    scroll = targetScroll = 0;
                }
            }));
            tx += tw + 2;
        }

        // Search box.
        int sw = Math.max(50, Math.min(110, x + w - 4 - tx - 4));
        int sx = x + w - 4 - sw;
        Render.round(sx, ty, sw, th, 2, searchFocused ? Theme.CONTROL_HOVER : Theme.CONTROL);
        if (searchFocused) {
            Render.rect(sx + 2, ty + th - 1, sw - 4, 1, Theme.accent());
        }
        boolean caret = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        boolean placeholder = search.isEmpty() && !searchFocused;
        String shown = placeholder ? "Search settings..." : search + (caret ? "_" : "");
        Render.text(Render.trim(shown, sw - 8), sx + 5, ty + 3, placeholder ? Theme.SUBTEXT : Theme.TEXT, false);
        hits.add(new Hit(sx, ty, sw, th, new Runnable() {
            @Override
            public void run() {
                searchFocused = true;
            }
        }));
    }

    /** The sections to show: the current tab, or everything matching the search. */
    private List<Section> shownSections() {
        if (search.trim().isEmpty()) {
            return tabs.get(tabIndex).sections;
        }
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Section> out = new ArrayList<Section>();
        for (Tab t : tabs) {
            for (Section s : t.sections) {
                Section match = new Section(s.title);
                for (Item item : s.items) {
                    if (item.label().toLowerCase(Locale.ROOT).contains(q)) {
                        match.items.add(item);
                    }
                }
                if (!match.items.isEmpty()) {
                    out.add(match);
                }
            }
        }
        return out;
    }

    /** Draws the sections from y; returns the y after the last one. */
    private int drawSections(int x, int y, int w, int mx, int my) {
        List<Section> sections = shownSections();
        if (sections.isEmpty()) {
            Render.textCentered("No settings match \"" + search + "\"", x + w / 2f, y + 20, Theme.SUBTEXT, true);
            return y + 40;
        }
        boolean first = true;
        for (Section s : sections) {
            if (!first) {
                y += 4;
            }
            first = false;
            Render.text(s.title, x + 2, y + 3, Theme.accent(), true);
            y += HEADER_H;
            for (Item item : s.items) {
                boolean hovered = Render.inside(mx, my, x, y, w, ROW_H - 2);
                if (item instanceof Action) {
                    drawAction((Action) item, x, y, w, mx, my);
                } else {
                    Render.round(x, y, w, ROW_H - 2, 2, hovered ? Theme.CARD_HOVER : Theme.CARD);
                    Render.text(Render.trim(item.label(), w / 2), x + 6, y + 5, Theme.TEXT, true);
                    if (item instanceof Toggle) {
                        drawToggle((Toggle) item, x, y, w);
                    } else {
                        drawControl((Row) item, x, y, w, mx, my);
                    }
                }
                y += ROW_H;
            }
        }
        return y;
    }

    private void drawToggle(final Toggle t, int x, int y, int w) {
        int sx = x + w - 26;
        boolean on = t.on();
        Render.round(sx, y + 4, 20, 10, 3, on ? Theme.accent() : Theme.CONTROL_HOVER);
        Render.round(on ? sx + 11 : sx + 1, y + 5, 8, 8, 2, Theme.TEXT);
        hits.add(new Hit(x, y, w, ROW_H - 2, new Runnable() {
            @Override
            public void run() {
                t.flip();
                Astra.get().saveConfig();
            }
        }));
    }

    private void drawAction(final Action a, int x, int y, int w, int mx, int my) {
        int bw = Render.width(a.label) + 14;
        boolean hovered = Render.inside(mx, my, x, y + 2, bw, 14);
        Render.round(x, y + 2, bw, 14, 2, hovered ? Theme.CONTROL_HOVER : Theme.CONTROL);
        Render.textCentered(a.label, x + bw / 2f, y + 5, Theme.TEXT, false);
        hits.add(new Hit(x, y + 2, bw, 14, a.run));
    }

    /** The control on the right of a row: slider, picker, colours, key or text. */
    private void drawControl(final Row row, int x, int y, int w, int mx, int my) {
        int right = x + w - 6;
        final Setting<?> s = row.setting;

        if (s instanceof NumberSetting) {
            final NumberSetting n = (NumberSetting) s;
            final int sw = Math.min(110, w / 2);
            final int sx = right - sw - 30;
            Render.rect(sx, y + 8, sw, 2, Theme.CONTROL_HOVER);
            int filled = (int) (sw * n.getPercent());
            Render.rect(sx, y + 8, filled, 2, Theme.accent());
            Render.round(sx + filled - 2, y + 5, 5, 8, 2, Theme.TEXT);
            Render.text(n.format(), right - 26, y + 5, Theme.SUBTEXT, false);
            hits.add(new Hit(sx - 4, y, sw + 8, ROW_H - 2, new Runnable() {
                @Override
                public void run() {
                    dragging = n;
                    dragX = sx;
                    dragW = sw;
                    n.setPercent((clickX - sx) / (double) sw);
                }
            }));
        } else if (s instanceof ModeSetting) {
            final ModeSetting m = (ModeSetting) s;
            String label = "< " + m.get() + " >";
            int bw = Render.width(label) + 12;
            Render.round(right - bw, y + 2, bw, 13, 2, Theme.CONTROL);
            Render.textCentered(label, right - bw / 2f, y + 5, Theme.TEXT, false);
            // Left half goes back, right half forward (the whole row cycles forward too).
            hits.add(new Hit(x, y, w, ROW_H - 2, new Runnable() {
                @Override
                public void run() {
                    m.cycle();
                    Astra.get().saveConfig();
                }
            }));
            hits.add(new Hit(right - bw, y + 2, bw / 2, 13, new Runnable() {
                @Override
                public void run() {
                    m.cycleBack();
                    Astra.get().saveConfig();
                }
            }));
        } else if (s instanceof ColorSetting) {
            final ColorSetting c = (ColorSetting) s;
            int count = ColorSetting.PALETTE.length;
            int sx = right - count * 11;
            for (int i = 0; i < count; i++) {
                final int color = ColorSetting.PALETTE[i];
                int bx = sx + i * 11;
                if ((color | 0xFF000000) == (c.getArgb() | 0xFF000000)) {
                    Render.round(bx - 1, y + 3, 11, 11, 2, Theme.TEXT);
                }
                Render.round(bx, y + 4, 9, 9, 2, color);
                hits.add(new Hit(bx, y + 3, 11, 11, new Runnable() {
                    @Override
                    public void run() {
                        c.set(color);
                        Astra.get().saveConfig();
                    }
                }));
            }
        } else if (s instanceof KeySetting) {
            final KeySetting k = (KeySetting) s;
            String label = listening == k ? "Press a key..." : Keys.name(k.getCode());
            int bw = Math.max(50, Render.width(label) + 12);
            Render.round(right - bw, y + 2, bw, 13, 2, listening == k ? Theme.accentDark() : Theme.CONTROL);
            Render.textCentered(label, right - bw / 2f, y + 5, Theme.TEXT, false);
            hits.add(new Hit(x, y, w, ROW_H - 2, new Runnable() {
                @Override
                public void run() {
                    listening = k;
                }
            }));
        } else if (s instanceof TextSetting) {
            final TextSetting t = (TextSetting) s;
            int bw = Math.min(w - Render.width(row.label) - 24, 190);
            boolean active = typing == t;
            Render.round(right - bw, y + 2, bw, 13, 2, active ? Theme.accentDark() : Theme.CONTROL);
            boolean caret = active && (System.currentTimeMillis() / 500) % 2 == 0;
            String shown = t.isEmpty() && !active ? t.getPlaceholder() : t.get() + (caret ? "_" : "");
            String fit = shown;
            while (Render.width(fit) > bw - 8 && fit.length() > 1) {
                fit = active ? fit.substring(1) : fit.substring(0, fit.length() - 1);
            }
            Render.text(fit, right - bw + 4, y + 5, t.isEmpty() && !active ? Theme.SUBTEXT : Theme.TEXT, false);
            hits.add(new Hit(x, y, w, ROW_H - 2, new Runnable() {
                @Override
                public void run() {
                    typing = t;
                    typingBefore = t.get();
                }
            }));
        }
    }

    // ------------------------------------------------------------------ input

    private int clickX;

    void click(int mx, int my, int button) {
        clickX = mx;
        searchFocused = false;
        listening = null;
        stopTyping(true);
        if (button != 0) {
            return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.clipped && !Render.inside(mx, my, viewX, viewY, viewW, viewH)) {
                continue;
            }
            if (Render.inside(mx, my, h.x, h.y, h.w, h.h)) {
                h.action.run();
                return;
            }
        }
    }

    void scroll(int direction) {
        targetScroll -= direction * 30;
    }

    /** True while the page wants Escape (to cancel typing or a key bind). */
    boolean usesEscape() {
        return typing != null || listening != null || searchFocused;
    }

    /** Key presses for key binds; returns true when used. */
    boolean keyPressed(int keyCode) {
        if (listening != null) {
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_ESCAPE) {
                listening.set(Keys.NONE);
            } else {
                listening.set(keyCode);
            }
            listening = null;
            Astra.get().saveConfig();
            return true;
        }
        return typing != null || searchFocused;
    }

    /** Typed characters for the search box and text settings. */
    void charTyped(char c, int keyCode) {
        if (typing != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                stopTyping(false);
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_TAB) {
                stopTyping(true);
            } else if (keyCode == Keyboard.KEY_BACK) {
                String v = typing.get();
                if (!v.isEmpty()) {
                    typing.set(v.substring(0, v.length() - 1));
                }
            } else if (c >= 32 && c != 127 && c != '§') {
                typing.set(typing.get() + c);
            }
            return;
        }
        if (!searchFocused) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            searchFocused = false;
        } else if (keyCode == Keyboard.KEY_BACK) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
        } else if (c >= 32 && c != 127 && c != '§' && search.length() < 30) {
            search += c;
        }
        scroll = targetScroll = 0;
    }

    void stopTyping(boolean keep) {
        if (typing != null) {
            if (!keep) {
                typing.set(typingBefore);
            }
            typing = null;
            Astra.get().saveConfig();
        }
    }
}
