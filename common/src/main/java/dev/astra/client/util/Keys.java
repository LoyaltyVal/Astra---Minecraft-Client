package dev.astra.client.util;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Key codes follow vanilla 1.7/1.8: positive values are LWJGL keyboard codes,
 * negative values are mouse buttons (-100 = left, -99 = right, -98 = middle).
 */
public final class Keys {

    public static final int NONE = Keyboard.KEY_NONE;
    public static final int MOUSE_LEFT = -100;
    public static final int MOUSE_RIGHT = -99;

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
        String name = Keyboard.getKeyName(code);
        if (name == null) {
            return "Key " + code;
        }
        // LWJGL names are upper-case ("LSHIFT"); keep short names as-is, title-case long ones.
        return name.length() <= 2 ? name : name.charAt(0) + name.substring(1).toLowerCase();
    }
}
