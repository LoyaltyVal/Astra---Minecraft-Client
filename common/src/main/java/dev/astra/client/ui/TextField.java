package dev.astra.client.ui;

import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;
import dev.astra.client.input.Keyboard;

/** A one-line text box for Astra's screens. Characters come in through UiScreen#charTyped. */
public final class TextField {

    public interface Filter {
        boolean allow(char c);
    }

    private final String placeholder;
    private final int maxLength;
    private final Filter filter;
    private String text = "";
    private boolean focused;
    private int x, y, w, h;

    public TextField(String placeholder, int maxLength, Filter filter) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.filter = filter;
    }

    public String get() {
        return text;
    }

    public void set(String value) {
        text = value == null ? "" : value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean f) {
        focused = f;
    }

    public void draw(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Render.round(x, y, w, h, Math.min(h / 2, 6), focused ? 0x70000000 : 0x45000000);
        if (focused) {
            Render.rect(x + 4, y + h - 1, w - 8, 1, Colors.withAlpha(Theme.accent(), 0xC0));
        }
        boolean empty = text.isEmpty();
        String shown = empty && !focused ? placeholder
                : text + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        while (Render.width(shown) > w - 12 && shown.length() > 1) {
            shown = focused ? shown.substring(1) : shown.substring(0, shown.length() - 1);
        }
        Render.text(shown, x + 6, y + (h - 8) / 2f, empty && !focused ? 0x88A0A0B0 : Theme.TEXT, false);
    }

    private static String clipboard() {
        try {
            Object data = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return data == null ? "" : data.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    /** Focuses the field if the click is on it; returns true when it was. */
    public boolean click(int mx, int my) {
        focused = Render.inside(mx, my, x, y, w, h);
        return focused;
    }

    /** Handles a typed key; returns true when Enter was pressed. */
    public boolean type(char c, int keyCode) {
        if (!focused) {
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == 156) { // 156 = numpad enter
            return true;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (ctrl && keyCode == Keyboard.KEY_V) {
            String paste = clipboard().replaceAll("[\\r\\n\\t\u00a7]", "").trim();
            for (char p : paste.toCharArray()) {
                if (text.length() < maxLength && p >= 32 && (filter == null || filter.allow(p))) {
                    text += p;
                }
            }
            return false;
        }
        if (ctrl && keyCode == Keyboard.KEY_A) {
            text = ""; // select-all + type replaces, the simple way
            return false;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
        } else if (c >= 32 && c != 127 && c != '§' && text.length() < maxLength && (filter == null || filter.allow(c))) {
            text += c;
        }
        return false;
    }
}
