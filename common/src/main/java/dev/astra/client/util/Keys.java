package dev.astra.client.util;

import dev.astra.client.input.Keyboard;
import dev.astra.client.input.Mouse;

/**
 * Key codes follow vanilla 1.7/1.8: positive values are LWJGL keyboard codes,
 * negative values are mouse buttons (-100 = left, -99 = right, -98 = middle).
 */
public final class Keys {

    public static final int NONE = Keyboard.KEY_NONE;
    public static final int MOUSE_LEFT = -100;
    public static final int MOUSE_RIGHT = -99;

    /** Readable names for keys whose LWJGL names are cryptic ("LMENU" is Left Alt). */
    private static final java.util.Map<Integer, String> FRIENDLY = new java.util.HashMap<Integer, String>();

    static {
        Object[][] names = {
                {Keyboard.KEY_LMENU, "Left Alt"}, {Keyboard.KEY_RMENU, "Right Alt"},
                {Keyboard.KEY_LCONTROL, "Left Ctrl"}, {Keyboard.KEY_RCONTROL, "Right Ctrl"},
                {Keyboard.KEY_LSHIFT, "Left Shift"}, {Keyboard.KEY_RSHIFT, "Right Shift"},
                {Keyboard.KEY_LMETA, "Left Win"}, {Keyboard.KEY_RMETA, "Right Win"},
                {Keyboard.KEY_RETURN, "Enter"}, {Keyboard.KEY_NUMPADENTER, "Num Enter"},
                {Keyboard.KEY_BACK, "Backspace"}, {Keyboard.KEY_CAPITAL, "Caps Lock"},
                {Keyboard.KEY_PRIOR, "Page Up"}, {Keyboard.KEY_NEXT, "Page Down"},
                {Keyboard.KEY_GRAVE, "`"}, {Keyboard.KEY_MINUS, "-"}, {Keyboard.KEY_EQUALS, "="},
                {Keyboard.KEY_LBRACKET, "["}, {Keyboard.KEY_RBRACKET, "]"}, {Keyboard.KEY_SEMICOLON, ";"},
                {Keyboard.KEY_APOSTROPHE, "'"}, {Keyboard.KEY_BACKSLASH, "\\"}, {Keyboard.KEY_COMMA, ","},
                {Keyboard.KEY_PERIOD, "."}, {Keyboard.KEY_SLASH, "/"}, {Keyboard.KEY_SCROLL, "Scroll Lock"},
                {Keyboard.KEY_NUMLOCK, "Num Lock"}, {Keyboard.KEY_SYSRQ, "Print Screen"},
                {Keyboard.KEY_ADD, "Num +"}, {Keyboard.KEY_SUBTRACT, "Num -"}, {Keyboard.KEY_MULTIPLY, "Num *"},
                {Keyboard.KEY_DIVIDE, "Num /"}, {Keyboard.KEY_DECIMAL, "Num ."}, {Keyboard.KEY_APPS, "Menu"},
        };
        for (Object[] n : names) {
            FRIENDLY.put((Integer) n[0], (String) n[1]);
        }
        int[] pads = {Keyboard.KEY_NUMPAD0, Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3, Keyboard.KEY_NUMPAD4,
                Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6, Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9};
        for (int i = 0; i < pads.length; i++) {
            FRIENDLY.put(pads[i], "Num " + i);
        }
    }

    private Keys() {
    }

    public static boolean isDown(int code) {
        if (code == NONE) {
            return false;
        }
        if (code < 0) {
            int button = code + 100;
            return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }
        return code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    public static String name(int code) {
        if (code == NONE) {
            return "None";
        }
        if (code == MOUSE_LEFT) {
            return "LMB";
        }
        if (code == MOUSE_RIGHT) {
            return "RMB";
        }
        if (code < 0) {
            return "Mouse " + (code + 101);
        }
        String friendly = FRIENDLY.get(code);
        if (friendly != null) {
            return friendly;
        }
        String name = Keyboard.getKeyName(code);
        if (name == null) {
            return "Key " + code;
        }
        // LWJGL names are upper-case ("LSHIFT"); keep short names as-is, title-case long ones.
        return name.length() <= 2 ? name : name.charAt(0) + name.substring(1).toLowerCase();
    }
}
