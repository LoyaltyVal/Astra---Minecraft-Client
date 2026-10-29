package dev.astra.client.ui;

import dev.astra.client.Astra;
import dev.astra.client.cosmetics.CosmeticsManager;
import dev.astra.client.module.Category;
import dev.astra.client.module.HudModule;
import dev.astra.client.module.Module;
import dev.astra.client.module.impl.ClientSettingsModule;
import dev.astra.client.module.impl.CosmeticsModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.ModeSetting;
import dev.astra.client.module.setting.TextSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.module.setting.Setting;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Keys;
import dev.astra.client.util.Render;
import dev.astra.client.input.Keyboard;
import dev.astra.client.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Right Shift menu.
 *
 *                 [*] ASTRA | CLIENT
 *   [edit HUD] [  MODS  ] [ COSMETICS ] [settings]
 *   +-----------------------------------------------+
 *   | ALL HUD MECHANIC VISUAL         [Search mods] |
 *   | [card] [card] [card]                        | |
 *   +-----------------------------------------------+
 *
 * MODS: each card shows a live preview, a quick action (Hide from HUD / Add to HUD / Enable)
 * and Options. COSMETICS: capes and wings from your image folders; click to equip.
 * Enabled/equipped cards are filled with the accent colour (gear page).
 */
public final class ModMenuScreen extends UiScreen {

    private enum Page { MODS, COSMETICS }

    private static final Category[] TABS = {null, Category.HUD, Category.MECHANIC, Category.VISUAL};

    private static final int ICON_BUTTON = 24;
    private static final int PAGE_BUTTON = 96;
    private static final int TOP_H = 24;
    private static final int TAB_BAR = 20;
    private static final int GAP = 4;
    private static final int CARD_H = 110;
    private static final int PREVIEW_H = 46;
    private static final int ROW_H = 20;
    private static final int SCROLLBAR = 6;
    private static final int CORNER = 20;
    private static final int RADIUS = 3;
    private static final long OPEN_ANIMATION_MS = 180;

    private Page page = Page.MODS;
    private Category filter;
    private CosmeticsManager.Type cosmeticType = CosmeticsManager.Type.CAPE;
    private Module editing;
    private String search = "";
    private boolean searchFocused;
    private boolean showHelp;

    private float scroll;
    private float targetScroll;
    private NumberSetting draggingSlider;
    private int sliderX;
    private int sliderW;
    private KeySetting listening;
    /** The text setting being typed into, and its text before editing (restored on Escape). */
    private TextSetting typing;
    private String typingBefore;

    private final long openedAt = System.currentTimeMillis();
    private final Map<String, Float> hover = new HashMap<String, Float>();

    // Layout (recomputed every frame).
    private int pw;
    private int ph;
    private int px;
    private int py;
    private int logoY;
    private int buttonsY;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private int searchX;
    private int searchW;

    public ModMenuScreen() {
    }

    public ModMenuScreen(Module openSettingsFor) {
        this.editing = openSettingsFor;
        if (openSettingsFor instanceof CosmeticsModule) {
            this.page = Page.COSMETICS;
        }
    }

    /** Opens straight to the cosmetics page. */
    public static ModMenuScreen cosmetics() {
        ModMenuScreen screen = new ModMenuScreen();
        screen.page = Page.COSMETICS;
        return screen;
    }

    // ================================================================== layout

    private void layout() {
        pw = Math.min(400, width - 16);
        ph = Math.max(120, Math.min(260, height - 84));
        int block = 14 + 8 + TOP_H + 8 + ph;
        int top = Math.max(4, (height - block) / 2 + 6);
        logoY = top;
        buttonsY = top + 22;
        py = buttonsY + TOP_H + 8;
        px = (width - pw) / 2;

        contentX = px + GAP + 2;
        contentY = py + TAB_BAR + GAP;
        contentW = pw - GAP * 2 - 4 - SCROLLBAR;
        contentH = ph - TAB_BAR - GAP * 2;

        searchW = Math.min(120, pw / 3);
        searchX = px + pw - searchW - 4;
    }

    private int topRowX() {
        int total = ICON_BUTTON * 2 + PAGE_BUTTON * 2 + GAP * 3;
        return width / 2 - total / 2;
    }

    private int editHudX() {
        return topRowX();
    }

    private int modsX() {
        return topRowX() + ICON_BUTTON + GAP;
    }

    private int cosmeticsX() {
        return modsX() + PAGE_BUTTON + GAP;
    }

    private int settingsX() {
        return cosmeticsX() + PAGE_BUTTON + GAP;
    }

    private int cornerY() {
        return height - CORNER - 6;
    }

    private int columns() {
        return contentW >= 300 ? 3 : contentW >= 190 ? 2 : 1;
    }

    private int cardWidth() {
        int cols = columns();
        return (contentW - (cols - 1) * GAP) / cols;
    }

    private List<Module> visibleModules() {
        List<Module> list = new ArrayList<Module>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Module m : Astra.get().getModules().getModules(filter)) {
            if (m.getCategory() == Category.CLIENT) {
                continue;
            }
            if (!q.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            list.add(m);
        }
        return list;
    }

    private CosmeticsManager cosmeticsManager() {
        return Astra.get().getCosmetics();
    }

    private List<String> visibleCosmetics() {
        return cosmeticsManager().list(cosmeticType);
    }

    private int contentHeight() {
        if (editing != null) {
            return (editing.getSettings().size() + 2) * ROW_H + 24;
        }
        int count = page == Page.MODS ? visibleModules().size() : visibleCosmetics().size();
        int rows = (count + columns() - 1) / columns();
        return rows * (CARD_H + GAP) - GAP;
    }

    private float maxScroll() {
        return Math.max(0, contentHeight() - contentH);
    }

    /** Smoothly animated 0..1 hover amount for a UI element. */
    private float hoverAmount(String key, boolean hovered) {
        Float current = hover.get(key);
        float value = current == null ? 0f : current;
        value += ((hovered ? 1f : 0f) - value) * 0.3f;
        hover.put(key, value);
        return value;
    }

    // ================================================================== drawing

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        layout();
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
        scroll += (targetScroll - scroll) * 0.35f;

        if (draggingSlider != null) {
            if (Mouse.isButtonDown(0)) {
                draggingSlider.setPercent((mx - sliderX) / (double) sliderW);
            } else {
                draggingSlider = null;
                save();
            }
        }

        // Open animation: fade the backdrop in and slide everything up a few pixels.
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) OPEN_ANIMATION_MS);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        if (Theme.dimBackground()) {
            Render.rect(0, 0, width, height, Colors.withAlpha(Theme.OVERLAY, (int) (Colors.alpha(Theme.OVERLAY) * ease)));
        }
        Astra.platform().pushMatrix();
        Astra.platform().translate(0f, (1f - ease) * 10f, 0f);

        drawLogo();
        drawTopButtons(mx, my);

        Render.round(px, py, pw, ph, RADIUS + 1, Theme.PANEL);
        drawTabBar(mx, my);

        Render.scissor(contentX, contentY, contentW, contentH);
        if (editing != null) {
            drawSettings(mx, my);
        } else if (page == Page.MODS) {
            drawModGrid(mx, my);
        } else {
            drawCosmeticGrid(mx, my);
        }
        Render.endScissor();
        drawScrollbar();

        Astra.platform().popMatrix();
        drawCornerButtons(mx, my);
    }

    private void drawLogo() {
        String name = "ASTRA";
        String tag = "CLIENT";
        float nameW = Render.width(name) * 1.5f;
        int tagW = Render.width(tag);
        float total = 16 + nameW + 6 + tagW;
        float x = width / 2f - total / 2f;

        // Four-point star mark built from rects.
        int sx = (int) x + 6;
        int sy = logoY + 6;
        int a = Theme.accent();
        Render.rect(sx, sy - 6, 1, 13, a);
        Render.rect(sx - 6, sy, 13, 1, a);
        Render.rect(sx - 1, sy - 3, 3, 7, a);
        Render.rect(sx - 3, sy - 1, 7, 3, a);

        Render.textCenteredScaled(name, x + 16 + nameW / 2f, logoY, 1.5f, Theme.TEXT, true);
        Render.rect((int) (x + 16 + nameW + 3), logoY + 1, 1, 10, 0x80FFFFFF);
        Render.text(tag, x + 16 + nameW + 6, logoY + 3, Theme.SUBTEXT, true);
    }

    private void drawTopButtons(int mx, int my) {
        boolean onClient = editing instanceof ClientSettingsModule;

        // Left: HUD editor (four little boxes).
        int lx = editHudX();
        float hl = hoverAmount("top:hud", Render.inside(mx, my, lx, buttonsY, ICON_BUTTON, TOP_H));
        Render.round(lx, buttonsY, ICON_BUTTON, TOP_H, RADIUS, Colors.lerp(Theme.BUTTON, Theme.BUTTON_HOVER, hl));
        int ix = lx + 7;
        int iy = buttonsY + 7;
        Render.outline(ix, iy, 4, 4, Theme.TEXT);
        Render.outline(ix + 6, iy, 4, 4, Theme.TEXT);
        Render.outline(ix, iy + 6, 4, 4, Theme.TEXT);
        Render.outline(ix + 6, iy + 6, 4, 4, Theme.TEXT);

        pageButton("MODS", modsX(), page == Page.MODS && !onClient, mx, my);
        pageButton("COSMETICS", cosmeticsX(), page == Page.COSMETICS && !onClient, mx, my);

        // Right: client settings (a gear made of rects).
        int rx = settingsX();
        float hr = hoverAmount("top:settings", Render.inside(mx, my, rx, buttonsY, ICON_BUTTON, TOP_H));
        int rbg = Colors.lerp(Theme.BUTTON, Theme.BUTTON_HOVER, onClient ? 1f : hr);
        Render.round(rx, buttonsY, ICON_BUTTON, TOP_H, RADIUS, rbg);
        if (onClient) {
            Render.rect(rx + 3, buttonsY + TOP_H - 2, ICON_BUTTON - 6, 2, Theme.accent());
        }
        int gx = rx + ICON_BUTTON / 2;
        int gy = buttonsY + TOP_H / 2;
        Render.rect(gx - 1, gy - 7, 3, 15, Theme.TEXT);
        Render.rect(gx - 7, gy - 1, 15, 3, Theme.TEXT);
        Render.rect(gx - 5, gy - 5, 11, 11, Theme.TEXT);
        Render.rect(gx - 2, gy - 2, 5, 5, rbg | 0xFF000000);
    }

    private void pageButton(String label, int x, boolean active, int mx, int my) {
        float h = hoverAmount("top:" + label, Render.inside(mx, my, x, buttonsY, PAGE_BUTTON, TOP_H));
        Render.round(x, buttonsY, PAGE_BUTTON, TOP_H, RADIUS, Colors.lerp(Theme.BUTTON, Theme.BUTTON_HOVER, active ? 1f : h));
        if (active) {
            Render.rect(x + 8, buttonsY + TOP_H - 2, PAGE_BUTTON - 16, 2, Theme.accent());
        }
        float scale = Math.min(1.5f, (PAGE_BUTTON - 16) / (float) Render.width(label));
        Render.textCenteredScaled(label, x + PAGE_BUTTON / 2f, buttonsY + (TOP_H - 9 * scale) / 2f + 1, scale,
                active ? Theme.TEXT : Theme.SUBTEXT, true);
    }

    private void drawTabBar(int mx, int my) {
        Render.round(px, py, pw, TAB_BAR, RADIUS + 1, Theme.PANEL_BAR);
        Render.rect(px, py + TAB_BAR - RADIUS, pw, RADIUS, Theme.PANEL_BAR);
        int ty = py + 3;
        int th = TAB_BAR - 6;

        if (editing != null) {
            boolean back = Render.inside(mx, my, px + 4, ty, 44, th);
            Render.round(px + 4, ty, 44, th, 2, back ? Theme.CONTROL_HOVER : Theme.CONTROL);
            Render.textCentered("< BACK", px + 26, ty + 3, Theme.TEXT, true);
            String title = editing instanceof ClientSettingsModule ? "CLIENT SETTINGS"
                    : editing instanceof CosmeticsModule ? "COSMETIC OPTIONS"
                    : editing.getName().toUpperCase(Locale.ROOT);
            Render.text(title, px + 56, ty + 3, Theme.accent(), true);
            Render.text(Render.trim(editing.getDescription(), pw - 70 - Render.width(title)),
                    px + 62 + Render.width(title), ty + 3, Theme.SUBTEXT, true);
            return;
        }

        int tx = px + 4;
        if (page == Page.MODS) {
            for (Category tab : TABS) {
                tx += tab(tabLabel(tab), tx, ty, th, filter == tab, mx, my) + 2;
            }
            // Search box.
            Render.round(searchX, ty, searchW, th, 2, searchFocused ? Theme.CONTROL_HOVER : Theme.CONTROL);
            if (searchFocused) {
                Render.rect(searchX + 2, ty + th - 1, searchW - 4, 1, Theme.accent());
            }
            boolean caret = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
            boolean placeholder = search.isEmpty() && !searchFocused;
            String shown = placeholder ? "Search mods..." : search + (caret ? "_" : "");
            Render.text(Render.trim(shown, searchW - 8), searchX + 5, ty + 3, placeholder ? Theme.SUBTEXT : Theme.TEXT, false);
        } else {
            tx += tab("CAPES", tx, ty, th, cosmeticType == CosmeticsManager.Type.CAPE, mx, my) + 2;
            tab("WINGS", tx, ty, th, cosmeticType == CosmeticsManager.Type.WINGS, mx, my);
            int ox = px + pw - 4;
            ox -= barButton("OPTIONS", ox, ty, th, mx, my) + 3;
            barButton("REFRESH", ox, ty, th, mx, my);
        }
    }

    /** A tab; returns its width. */
    private int tab(String label, int x, int y, int h, boolean selected, int mx, int my) {
        int w = Render.width(label) + 12;
        boolean hovered = Render.inside(mx, my, x, y, w, h);
        if (selected) {
            Render.round(x, y, w, h, 2, Theme.accentCard());
        } else if (hovered) {
            Render.round(x, y, w, h, 2, Theme.CONTROL);
        }
        Render.textCentered(label, x + w / 2f, y + 3, selected || hovered ? Theme.TEXT : Theme.SUBTEXT, true);
        return w;
    }

    /** A right-aligned button in the tab bar ending at {@code right}; returns its width. */
    private int barButton(String label, int right, int y, int h, int mx, int my) {
        int w = Render.width(label) + 12;
        int x = right - w;
        boolean hovered = Render.inside(mx, my, x, y, w, h);
        Render.round(x, y, w, h, 2, hovered ? Theme.CONTROL_HOVER : Theme.CONTROL);
        Render.textCentered(label, x + w / 2f, y + 3, Theme.TEXT, true);
        return w;
    }

    private static String tabLabel(Category tab) {
        return tab == null ? "ALL" : tab.getDisplayName().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ mods page

    private void drawModGrid(int mx, int my) {
        List<Module> list = visibleModules();
        if (list.isEmpty()) {
            Render.textCentered("No mods match \"" + search + "\"", contentX + contentW / 2f, contentY + 20,
                    Theme.SUBTEXT, true);
            return;
        }
        int cols = columns();
        int cw = cardWidth();
        boolean mouseIn = Render.inside(mx, my, contentX, contentY, contentW, contentH);

        for (int i = 0; i < list.size(); i++) {
            int cx = contentX + (i % cols) * (cw + GAP);
            int cy = contentY + (i / cols) * (CARD_H + GAP) - (int) scroll;
            if (cy + CARD_H < contentY || cy > contentY + contentH) {
                continue;
            }
            drawModCard(list.get(i), cx, cy, cw, mouseIn ? mx : -1, mouseIn ? my : -1);
        }
    }

    private void drawModCard(Module m, int x, int y, int w, int mx, int my) {
        float h = hoverAmount("mod:" + m.getName(), Render.inside(mx, my, x, y, w, CARD_H));
        int bg = m.isEnabled()
                ? Colors.lerp(Theme.accentCard(), Theme.accentCardHover(), h)
                : Colors.lerp(Theme.CARD, Theme.CARD_HOVER, h);
        Render.round(x, y, w, CARD_H, RADIUS, bg);

        // Live preview.
        int previewCenterY = y + 5 + PREVIEW_H / 2;
        if (m instanceof HudModule) {
            ((HudModule) m).drawPreview(x + w / 2f, previewCenterY, w - 16, PREVIEW_H - 4);
            Astra.platform().color(1f, 1f, 1f, 1f);
        } else if (ModuleIcons.has(m)) {
            // Its picture, gently bobbing while hovered, with the short summary under it.
            float bob = (float) Math.sin(System.currentTimeMillis() / 220.0) * 1.5f * h;
            ModuleIcons.draw(m, x + w / 2f, previewCenterY - 5 - bob, 2);
            String text = m.getPreviewText();
            if (text != null && !text.isEmpty() && !text.equals(m.getName())) {
                Render.textCentered(Render.trim(text, w - 10), x + w / 2f, previewCenterY + 13,
                        m.isEnabled() ? 0xD8FFFFFF : Theme.SUBTEXT, false);
            }
        } else {
            Render.textCentered(Render.trim(m.getPreviewText(), w - 10), x + w / 2f, previewCenterY - 4, Theme.TEXT, true);
        }

        Render.textCentered(Render.trim(m.getName(), w - 8), x + w / 2f, y + PREVIEW_H + 11, Theme.TEXT, true);

        for (CardButton b : cardButtons(m, x + 4, y + CARD_H - 38, w - 8)) {
            cardButton(b.label, b.x, b.y, b.w, b.h, mx, my, false);
        }
        cardButton("OPTIONS", x + 4, y + CARD_H - 20, w - 8, 16, mx, my, true);
    }

    private void cardButton(String label, int x, int y, int w, int h, int mx, int my, boolean bold) {
        boolean bh = Render.inside(mx, my, x, y, w, h);
        Render.round(x, y, w, h, 2, bh ? Theme.CARD_BUTTON_HOVER : Theme.CARD_BUTTON);
        Render.textCentered(Render.trim(label, w - 4), x + w / 2f, y + (h - 8) / 2f, bh || bold ? Theme.TEXT : 0xFFC8C8D0, bold);
    }

    /** One quick-action button on a mod card. */
    private static final class CardButton {
        final String label;
        final Runnable action;
        final int x;
        final int y;
        final int w;
        final int h;

        CardButton(String label, Runnable action, int x, int y, int w, int h) {
            this.label = label;
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private List<CardButton> cardButtons(final Module m, int x, int y, int w) {
        List<CardButton> list = new ArrayList<CardButton>();
        int h = 14;
        Runnable toggle = new Runnable() {
            @Override
            public void run() {
                m.toggle();
            }
        };

        if (m.createScreen() != null) {
            // Waypoints, Screenshots, Radio: open their screen, or switch them on/off.
            int half = (w - 2) / 2;
            list.add(new CardButton("OPEN", new Runnable() {
                @Override
                public void run() {
                    open(m.createScreen());
                }
            }, x, y, half, h));
            list.add(new CardButton(m.isEnabled() ? "DISABLE" : "ENABLE", toggle, x + half + 2, y, w - half - 2, h));
        } else if (m instanceof HudModule && ((HudModule) m).getHudVisibility() != null && m.isEnabled()) {
            // e.g. Toggle Sprint: hide just the HUD text, or turn the whole thing off.
            final BooleanSetting visible = ((HudModule) m).getHudVisibility();
            int half = (w - 2) / 2;
            list.add(new CardButton(visible.isOn() ? "HIDE FROM HUD" : "SHOW ON HUD", new Runnable() {
                @Override
                public void run() {
                    visible.toggle();
                }
            }, x, y, half, h));
            list.add(new CardButton("DISABLE", toggle, x + half + 2, y, w - half - 2, h));
        } else if (m instanceof HudModule) {
            list.add(new CardButton(m.isEnabled() ? "HIDE FROM HUD" : "ADD TO HUD", toggle, x, y, w, h));
        } else {
            list.add(new CardButton(m.isEnabled() ? "DISABLE" : "ENABLE", toggle, x, y, w, h));
        }
        return list;
    }

    // ------------------------------------------------------------------ cosmetics page

    private void drawCosmeticGrid(int mx, int my) {
        List<String> names = visibleCosmetics();
        if (names.isEmpty()) {
            drawCosmeticsEmpty();
            return;
        }
        int cols = columns();
        int cw = cardWidth();
        boolean mouseIn = Render.inside(mx, my, contentX, contentY, contentW, contentH);
        for (int i = 0; i < names.size(); i++) {
            int cx = contentX + (i % cols) * (cw + GAP);
            int cy = contentY + (i / cols) * (CARD_H + GAP) - (int) scroll;
            if (cy + CARD_H < contentY || cy > contentY + contentH) {
                continue;
            }
            drawCosmeticCard(names.get(i), cx, cy, cw, mouseIn ? mx : -1, mouseIn ? my : -1);
        }
    }

    private void drawCosmeticsEmpty() {
        boolean capes = cosmeticType == CosmeticsManager.Type.CAPE;
        float cx = contentX + contentW / 2f;
        int y = contentY + contentH / 2 - 24;
        String status = cosmeticsManager().getStatus();
        if (status != null) {
            Render.textCentered("Cosmetics aren't available right now", cx, y, Theme.TEXT, true);
            Render.textCentered(Render.trim(status, contentW - 20), cx, y + 16, Theme.SUBTEXT, true);
            Render.textCentered("Click REFRESH to try again.", cx, y + 30, Theme.accent(), true);
            return;
        }
        Render.textCentered(capes ? "No capes yet" : "No wings yet", cx, y, Theme.TEXT, true);
        Render.textCentered("Cosmetics come from your Astra account. Staff can give you more", cx, y + 16, Theme.SUBTEXT, true);
        Render.textCentered("from the admin panel, and free ones show up for everyone.", cx, y + 28, Theme.SUBTEXT, true);
    }

    private void drawCosmeticCard(String id, int x, int y, int w, int mx, int my) {
        CosmeticsManager cm = cosmeticsManager();
        String name = cm.getName(id);
        boolean equipped = id.equals(cm.getEquipped(cosmeticType));
        float h = hoverAmount("cos:" + cosmeticType + name, Render.inside(mx, my, x, y, w, CARD_H));
        int bg = equipped
                ? Colors.lerp(Theme.accentCard(), Theme.accentCardHover(), h)
                : Colors.lerp(Theme.CARD, Theme.CARD_HOVER, h);
        Render.round(x, y, w, CARD_H, RADIUS, bg);

        // Thumbnail.
        int boxY = y + 5;
        int boxH = 68;
        CosmeticsManager.Texture tex = cm.getTexture(cosmeticType, id);
        String error = cm.getError(cosmeticType, id);
        if (tex != null && tex.getHandle() != null) {
            if (cosmeticType == CosmeticsManager.Type.CAPE) {
                // The outer face of the cape: 10x16 cape pixels at (1,1) on the 64x32 layout.
                float th = boxH;
                float tw = th * 10f / 16f;
                Render.texture(tex.getHandle(), x + (w - tw) / 2f, boxY, tw, th, 1 / 64f, 1 / 32f, 11 / 64f, 17 / 32f);
            } else {
                float scale = Math.min((w - 12) / (float) tex.width, boxH / (float) tex.height);
                float tw = tex.width * scale;
                float th = tex.height * scale;
                Render.texture(tex.getHandle(), x + (w - tw) / 2f, boxY + (boxH - th) / 2f, tw, th, 0, 0, 1, 1);
            }
            Astra.platform().color(1f, 1f, 1f, 1f);
        } else {
            String status = error != null ? "Couldn't load image" : "Loading...";
            Render.textCentered(status, x + w / 2f, boxY + boxH / 2f - 4, error != null ? Theme.OFF : Theme.SUBTEXT, true);
        }

        Render.textCentered(Render.trim(name, w - 8), x + w / 2f, y + CARD_H - 34, Theme.TEXT, true);
        cardButton(equipped ? "UNEQUIP" : "EQUIP", x + 4, y + CARD_H - 20, w - 8, 16, mx, my, true);
    }

    // ------------------------------------------------------------------ settings view

    private void drawSettings(int mx, int my) {
        int x = contentX;
        int w = contentW;
        int y = contentY - (int) scroll;
        boolean mouseIn = Render.inside(mx, my, contentX, contentY, contentW, contentH);
        boolean alwaysOn = editing.getCategory() == Category.CLIENT;

        if (!alwaysOn) {
            drawRow(x, y, w, "Enabled", mouseIn && Render.inside(mx, my, x, y, w, ROW_H - 2));
            drawSwitch(x + w - 26, y + 4, editing.isEnabled());
            y += ROW_H;
        }

        for (Setting<?> setting : editing.getSettings()) {
            boolean hovered = mouseIn && Render.inside(mx, my, x, y, w, ROW_H - 2);
            drawRow(x, y, w, setting.getName(), hovered);
            int right = x + w - 6;

            if (setting instanceof BooleanSetting) {
                drawSwitch(right - 20, y + 4, ((BooleanSetting) setting).isOn());
            } else if (setting instanceof NumberSetting) {
                NumberSetting n = (NumberSetting) setting;
                int sw = Math.min(110, w / 2);
                int sx = right - sw - 30;
                Render.rect(sx, y + 8, sw, 2, Theme.CONTROL_HOVER);
                int filled = (int) (sw * n.getPercent());
                Render.rect(sx, y + 8, filled, 2, Theme.accent());
                Render.round(sx + filled - 2, y + 5, 5, 8, 2, Theme.TEXT);
                Render.text(n.format(), right - 26, y + 5, Theme.SUBTEXT, false);
            } else if (setting instanceof ColorSetting) {
                ColorSetting c = (ColorSetting) setting;
                int count = ColorSetting.PALETTE.length;
                int sx = right - count * 11;
                for (int i = 0; i < count; i++) {
                    int color = ColorSetting.PALETTE[i];
                    int bx = sx + i * 11;
                    if ((color | 0xFF000000) == (c.getArgb() | 0xFF000000)) {
                        Render.round(bx - 1, y + 3, 11, 11, 2, Theme.TEXT);
                    }
                    Render.round(bx, y + 4, 9, 9, 2, color);
                }
            } else if (setting instanceof KeySetting) {
                KeySetting k = (KeySetting) setting;
                String label = listening == k ? "Press a key..." : Keys.name(k.getCode());
                int bw = Math.max(50, Render.width(label) + 12);
                Render.round(right - bw, y + 2, bw, 13, 2, listening == k ? Theme.accentDark() : Theme.CONTROL);
                Render.textCentered(label, right - bw / 2f, y + 5, Theme.TEXT, false);
            } else if (setting instanceof TextSetting) {
                TextSetting t = (TextSetting) setting;
                int bw = Math.min(w - Render.width(setting.getName()) - 24, 190);
                boolean active = typing == t;
                Render.round(right - bw, y + 2, bw, 13, 2, active ? Theme.accentDark() : Theme.CONTROL);
                boolean caret = active && (System.currentTimeMillis() / 500) % 2 == 0;
                String shown = t.isEmpty() && !active ? t.getPlaceholder() : t.get() + (caret ? "_" : "");
                // Show the end of long text while typing, the start otherwise.
                String fit = shown;
                while (Render.width(fit) > bw - 8 && fit.length() > 1) {
                    fit = active ? fit.substring(1) : fit.substring(0, fit.length() - 1);
                }
                Render.text(fit, right - bw + 4, y + 5, t.isEmpty() && !active ? Theme.SUBTEXT : Theme.TEXT, false);
            } else if (setting instanceof ModeSetting) {
                String label = "< " + setting.get() + " >";
                int bw = Render.width(label) + 12;
                Render.round(right - bw, y + 2, bw, 13, 2, Theme.CONTROL);
                Render.textCentered(label, right - bw / 2f, y + 5, Theme.TEXT, false);
            }
            y += ROW_H;
        }

        if (editing instanceof HudModule) {
            String reset = "Reset Position & Scale";
            int bw = Render.width(reset) + 14;
            boolean hovered = mouseIn && Render.inside(mx, my, x, y + 2, bw, 14);
            Render.round(x, y + 2, bw, 14, 2, hovered ? Theme.CONTROL_HOVER : Theme.CONTROL);
            Render.textCentered(reset, x + bw / 2f, y + 5, Theme.SUBTEXT, false);
        }
    }

    private void drawRow(int x, int y, int w, String label, boolean hovered) {
        Render.round(x, y, w, ROW_H - 2, 2, hovered ? Theme.CARD_HOVER : Theme.CARD);
        Render.text(label, x + 6, y + 5, Theme.TEXT, true);
    }

    private void drawSwitch(int x, int y, boolean on) {
        Render.round(x, y, 20, 10, 3, on ? Theme.accent() : Theme.CONTROL_HOVER);
        Render.round(on ? x + 11 : x + 1, y + 1, 8, 8, 2, Theme.TEXT);
    }

    private void drawScrollbar() {
        float max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = px + pw - GAP - SCROLLBAR + 2;
        int barH = Math.max(16, (int) (contentH * (contentH / (float) contentHeight())));
        int barY = contentY + (int) ((contentH - barH) * (scroll / max));
        Render.round(trackX, contentY, 3, contentH, 1, 0x30FFFFFF);
        Render.round(trackX, barY, 3, barH, 1, Theme.accent());
    }

    private void drawCornerButtons(int mx, int my) {
        int y = cornerY();
        // Eye: open the HUD editor.
        boolean h1 = Render.inside(mx, my, 6, y, CORNER, CORNER);
        Render.round(6, y, CORNER, CORNER, RADIUS, h1 ? Theme.BUTTON_HOVER : Theme.BUTTON);
        Render.rect(11, y + 8, 10, 4, Theme.TEXT);
        Render.rect(13, y + 7, 6, 6, Theme.TEXT);
        Render.rect(15, y + 9, 2, 2, Theme.BUTTON | 0xFF000000);
        // Help.
        int hx = 6 + CORNER + 4;
        boolean h2 = Render.inside(mx, my, hx, y, CORNER, CORNER);
        Render.round(hx, y, CORNER, CORNER, RADIUS, h2 || showHelp ? Theme.BUTTON_HOVER : Theme.BUTTON);
        Render.textCentered("?", hx + CORNER / 2f, y + 6, Theme.TEXT, true);

        if (h1) {
            Render.text("Edit HUD layout", 6, y - 11, Theme.TEXT, true);
        }
        if (showHelp) {
            int tx = hx + CORNER + 8;
            Render.text("Click a card to toggle  |  Options or right-click for settings", tx, y + 1, Theme.TEXT, true);
            Render.text("Right Shift opens this menu  |  Esc closes it", tx, y + 11, Theme.SUBTEXT, true);
        }
    }

    // ================================================================== input

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        layout();
        // The panel is drawn slightly lower while the open animation runs; ignore clicks until it settles.
        if (System.currentTimeMillis() - openedAt < OPEN_ANIMATION_MS) {
            return;
        }
        listening = null;
        searchFocused = false;
        stopTyping(true);

        if (button == 0) {
            if (Render.inside(mx, my, editHudX(), buttonsY, ICON_BUTTON, TOP_H)
                    || Render.inside(mx, my, 6, cornerY(), CORNER, CORNER)) {
                open(new HudEditorScreen());
                return;
            }
            if (Render.inside(mx, my, modsX(), buttonsY, PAGE_BUTTON, TOP_H)) {
                switchPage(Page.MODS);
                return;
            }
            if (Render.inside(mx, my, cosmeticsX(), buttonsY, PAGE_BUTTON, TOP_H)) {
                switchPage(Page.COSMETICS);
                return;
            }
            if (Render.inside(mx, my, settingsX(), buttonsY, ICON_BUTTON, TOP_H)) {
                openSettings(Astra.get().getModules().get(ClientSettingsModule.class));
                return;
            }
            if (Render.inside(mx, my, 6 + CORNER + 4, cornerY(), CORNER, CORNER)) {
                showHelp = !showHelp;
                return;
            }
        }

        if (clickTabBar(mx, my, button)) {
            return;
        }
        if (!Render.inside(mx, my, contentX, contentY, contentW, contentH)) {
            return;
        }
        if (editing != null) {
            clickSettings(mx, my);
        } else if (page == Page.MODS) {
            clickModGrid(mx, my, button);
        } else {
            clickCosmeticGrid(mx, my);
        }
    }

    private boolean clickTabBar(int mx, int my, int button) {
        int ty = py + 3;
        int th = TAB_BAR - 6;
        if (!Render.inside(mx, my, px, py, pw, TAB_BAR)) {
            return false;
        }
        if (editing != null) {
            if (button == 0 && Render.inside(mx, my, px + 4, ty, 44, th)) {
                backToGrid();
            }
            return true;
        }
        int tx = px + 4;
        if (page == Page.MODS) {
            if (Render.inside(mx, my, searchX, ty, searchW, th)) {
                searchFocused = true;
                if (button == 1) {
                    search = "";
                }
                return true;
            }
            for (Category tab : TABS) {
                int tw = Render.width(tabLabel(tab)) + 12;
                if (button == 0 && Render.inside(mx, my, tx, ty, tw, th)) {
                    filter = tab;
                    scroll = targetScroll = 0;
                    return true;
                }
                tx += tw + 2;
            }
        } else {
            int capesW = Render.width("CAPES") + 12;
            int wingsW = Render.width("WINGS") + 12;
            if (Render.inside(mx, my, tx, ty, capesW, th)) {
                cosmeticType = CosmeticsManager.Type.CAPE;
                scroll = targetScroll = 0;
            } else if (Render.inside(mx, my, tx + capesW + 2, ty, wingsW, th)) {
                cosmeticType = CosmeticsManager.Type.WINGS;
                scroll = targetScroll = 0;
            }
            int right = px + pw - 4;
            int optionsW = Render.width("OPTIONS") + 12;
            int refreshW = Render.width("REFRESH") + 12;
            if (Render.inside(mx, my, right - optionsW, ty, optionsW, th)) {
                openSettings(Astra.get().getModules().get(CosmeticsModule.class));
            } else if (Render.inside(mx, my, right - optionsW - 3 - refreshW, ty, refreshW, th)) {
                cosmeticsManager().refresh();
            }
        }
        return true;
    }

    private void clickModGrid(int mx, int my, int button) {
        List<Module> list = visibleModules();
        int cols = columns();
        int cw = cardWidth();
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int cx = contentX + (i % cols) * (cw + GAP);
            int cy = contentY + (i / cols) * (CARD_H + GAP) - (int) scroll;
            if (!Render.inside(mx, my, cx, cy, cw, CARD_H)) {
                continue;
            }
            if (button == 1 || Render.inside(mx, my, cx + 4, cy + CARD_H - 20, cw - 8, 16)) {
                openSettings(m);
                return;
            }
            for (CardButton b : cardButtons(m, cx + 4, cy + CARD_H - 38, cw - 8)) {
                if (Render.inside(mx, my, b.x, b.y, b.w, b.h)) {
                    b.action.run();
                    save();
                    return;
                }
            }
            // Anywhere else on the card toggles the module.
            m.toggle();
            save();
            return;
        }
    }

    private void clickCosmeticGrid(int mx, int my) {
        List<String> names = visibleCosmetics();
        int cols = columns();
        int cw = cardWidth();
        for (int i = 0; i < names.size(); i++) {
            int cx = contentX + (i % cols) * (cw + GAP);
            int cy = contentY + (i / cols) * (CARD_H + GAP) - (int) scroll;
            if (Render.inside(mx, my, cx, cy, cw, CARD_H)) {
                String id = names.get(i);
                boolean equipped = id.equals(cosmeticsManager().getEquipped(cosmeticType));
                cosmeticsManager().equip(cosmeticType, equipped ? null : id);
                return;
            }
        }
    }

    private void clickSettings(int mx, int my) {
        int x = contentX;
        int w = contentW;
        int y = contentY - (int) scroll;
        int right = x + w - 6;

        if (editing.getCategory() != Category.CLIENT) {
            if (Render.inside(mx, my, x, y, w, ROW_H - 2)) {
                editing.toggle();
                save();
                return;
            }
            y += ROW_H;
        }

        for (Setting<?> setting : editing.getSettings()) {
            if (Render.inside(mx, my, x, y, w, ROW_H - 2)) {
                if (setting instanceof BooleanSetting) {
                    ((BooleanSetting) setting).toggle();
                } else if (setting instanceof NumberSetting) {
                    int sw = Math.min(110, w / 2);
                    int sx = right - sw - 30;
                    if (mx >= sx - 4 && mx <= sx + sw + 4) {
                        draggingSlider = (NumberSetting) setting;
                        sliderX = sx;
                        sliderW = sw;
                        draggingSlider.setPercent((mx - sx) / (double) sw);
                    }
                } else if (setting instanceof ColorSetting) {
                    int count = ColorSetting.PALETTE.length;
                    int sx = right - count * 11;
                    int index = (mx - sx) / 11;
                    if (mx >= sx && index >= 0 && index < count) {
                        ((ColorSetting) setting).set(ColorSetting.PALETTE[index]);
                    }
                } else if (setting instanceof KeySetting) {
                    listening = (KeySetting) setting;
                } else if (setting instanceof TextSetting) {
                    stopTyping(true);
                    typing = (TextSetting) setting;
                    typingBefore = typing.get();
                } else if (setting instanceof ModeSetting) {
                    ((ModeSetting) setting).cycle();
                }
                save();
                return;
            }
            y += ROW_H;
        }

        if (editing instanceof HudModule) {
            int bw = Render.width("Reset Position & Scale") + 14;
            if (Render.inside(mx, my, x, y + 2, bw, 14)) {
                ((HudModule) editing).resetPosition();
                save();
            }
        }
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll - direction * 30));
    }

    private void stopTyping(boolean keep) {
        if (typing != null) {
            if (!keep) {
                typing.set(typingBefore);
            }
            typing = null;
            save();
        }
    }

    @Override
    public boolean usesEscape() {
        return typing != null || listening != null;
    }

    @Override
    public void charTyped(char c, int keyCode) {
        if (typing == null) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            stopTyping(false);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_TAB) {
            stopTyping(true);
        } else if (keyCode == Keyboard.KEY_BACK) {
            String v = typing.get();
            if (!v.isEmpty()) {
                typing.set(v.substring(0, v.length() - 1));
            }
        } else if (c >= 32 && c != 127 && c != '\u00a7') {
            typing.set(typing.get() + c);
        }
    }

    @Override
    protected void keyPressed(int keyCode) {
        if (typing != null) {
            return; // typed characters arrive through charTyped
        }
        if (listening != null) {
            // Backspace/Delete clears the bind. (Escape closes the menu, handled by vanilla.)
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                listening.set(Keys.NONE);
            } else if (keyCode != Keyboard.KEY_ESCAPE) {
                listening.set(keyCode);
            }
            listening = null;
            save();
            return;
        }
        if (searchFocused) {
            typeInSearch(keyCode);
            return;
        }
        if (keyCode == Astra.MENU_KEY) {
            close();
        }
    }

    private void typeInSearch(int keyCode) {
        if (keyCode == Keyboard.KEY_BACK) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_TAB) {
            searchFocused = false;
        } else if (keyCode == Keyboard.KEY_SPACE) {
            search += " ";
        } else {
            String name = Keyboard.getKeyName(keyCode);
            if (name != null && name.length() == 1 && Character.isLetterOrDigit(name.charAt(0)) && search.length() < 24) {
                search += name.toLowerCase(Locale.ROOT);
            }
        }
        scroll = targetScroll = 0;
    }

    private void switchPage(Page next) {
        stopTyping(true);
        page = next;
        editing = null;
        listening = null;
        scroll = targetScroll = 0;
    }

    private void openSettings(Module m) {
        editing = m;
        if (m instanceof CosmeticsModule) {
            page = Page.COSMETICS; // keep the COSMETICS tab lit while editing cosmetic options
        }
        listening = null;
        scroll = targetScroll = 0;
    }

    private void backToGrid() {
        stopTyping(true);
        editing = null;
        listening = null;
        scroll = targetScroll = 0;
    }
}
