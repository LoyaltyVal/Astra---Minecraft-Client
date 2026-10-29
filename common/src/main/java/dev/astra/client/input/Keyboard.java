package dev.astra.client.input;

import dev.astra.client.Astra;

/**
 * The keyboard, the same on every Minecraft version.
 *
 * Key codes are LWJGL 2's (what 1.7-1.12 use and what Astra saves in its config), so a keybind
 * means the same key everywhere. Versions on LWJGL 3 / GLFW translate with {@link #toGlfw} and
 * {@link #fromGlfw}.
 */
public final class Keyboard {

    public static final int KEYBOARD_SIZE = 256;
    public static final int KEY_NONE = 0;

    public static final int KEY_ESCAPE = 1, KEY_1 = 2, KEY_2 = 3, KEY_3 = 4, KEY_4 = 5, KEY_5 = 6, KEY_6 = 7,
            KEY_7 = 8, KEY_8 = 9, KEY_9 = 10, KEY_0 = 11, KEY_MINUS = 12, KEY_EQUALS = 13, KEY_BACK = 14,
            KEY_TAB = 15, KEY_Q = 16, KEY_W = 17, KEY_E = 18, KEY_R = 19, KEY_T = 20, KEY_Y = 21, KEY_U = 22,
            KEY_I = 23, KEY_O = 24, KEY_P = 25, KEY_LBRACKET = 26, KEY_RBRACKET = 27, KEY_RETURN = 28,
            KEY_LCONTROL = 29, KEY_A = 30, KEY_S = 31, KEY_D = 32, KEY_F = 33, KEY_G = 34, KEY_H = 35,
            KEY_J = 36, KEY_K = 37, KEY_L = 38, KEY_SEMICOLON = 39, KEY_APOSTROPHE = 40, KEY_GRAVE = 41,
            KEY_LSHIFT = 42, KEY_BACKSLASH = 43, KEY_Z = 44, KEY_X = 45, KEY_C = 46, KEY_V = 47, KEY_B = 48,
            KEY_N = 49, KEY_M = 50, KEY_COMMA = 51, KEY_PERIOD = 52, KEY_SLASH = 53, KEY_RSHIFT = 54,
            KEY_MULTIPLY = 55, KEY_LMENU = 56, KEY_SPACE = 57, KEY_CAPITAL = 58, KEY_F1 = 59, KEY_F2 = 60,
            KEY_F3 = 61, KEY_F4 = 62, KEY_F5 = 63, KEY_F6 = 64, KEY_F7 = 65, KEY_F8 = 66, KEY_F9 = 67,
            KEY_F10 = 68, KEY_NUMLOCK = 69, KEY_SCROLL = 70, KEY_NUMPAD7 = 71, KEY_NUMPAD8 = 72,
            KEY_NUMPAD9 = 73, KEY_SUBTRACT = 74, KEY_NUMPAD4 = 75, KEY_NUMPAD5 = 76, KEY_NUMPAD6 = 77,
            KEY_ADD = 78, KEY_NUMPAD1 = 79, KEY_NUMPAD2 = 80, KEY_NUMPAD3 = 81, KEY_NUMPAD0 = 82,
            KEY_DECIMAL = 83, KEY_F11 = 87, KEY_F12 = 88, KEY_F13 = 100, KEY_F14 = 101, KEY_F15 = 102,
            KEY_NUMPADEQUALS = 141, KEY_NUMPADENTER = 156, KEY_RCONTROL = 157, KEY_DIVIDE = 181,
            KEY_SYSRQ = 183, KEY_RMENU = 184, KEY_PAUSE = 197, KEY_HOME = 199, KEY_UP = 200, KEY_PRIOR = 201,
            KEY_LEFT = 203, KEY_RIGHT = 205, KEY_END = 207, KEY_DOWN = 208, KEY_NEXT = 209, KEY_INSERT = 210,
            KEY_DELETE = 211, KEY_LMETA = 219, KEY_RMETA = 220, KEY_APPS = 221;

    /** LWJGL 2 code, LWJGL 2 name, GLFW code. */
    private static final Object[][] TABLE = {
            {KEY_ESCAPE, "ESCAPE", 256}, {KEY_1, "1", 49}, {KEY_2, "2", 50}, {KEY_3, "3", 51}, {KEY_4, "4", 52},
            {KEY_5, "5", 53}, {KEY_6, "6", 54}, {KEY_7, "7", 55}, {KEY_8, "8", 56}, {KEY_9, "9", 57}, {KEY_0, "0", 48},
            {KEY_MINUS, "MINUS", 45}, {KEY_EQUALS, "EQUALS", 61}, {KEY_BACK, "BACK", 259}, {KEY_TAB, "TAB", 258},
            {KEY_Q, "Q", 81}, {KEY_W, "W", 87}, {KEY_E, "E", 69}, {KEY_R, "R", 82}, {KEY_T, "T", 84}, {KEY_Y, "Y", 89},
            {KEY_U, "U", 85}, {KEY_I, "I", 73}, {KEY_O, "O", 79}, {KEY_P, "P", 80}, {KEY_LBRACKET, "LBRACKET", 91},
            {KEY_RBRACKET, "RBRACKET", 93}, {KEY_RETURN, "RETURN", 257}, {KEY_LCONTROL, "LCONTROL", 341},
            {KEY_A, "A", 65}, {KEY_S, "S", 83}, {KEY_D, "D", 68}, {KEY_F, "F", 70}, {KEY_G, "G", 71}, {KEY_H, "H", 72},
            {KEY_J, "J", 74}, {KEY_K, "K", 75}, {KEY_L, "L", 76}, {KEY_SEMICOLON, "SEMICOLON", 59},
            {KEY_APOSTROPHE, "APOSTROPHE", 39}, {KEY_GRAVE, "GRAVE", 96}, {KEY_LSHIFT, "LSHIFT", 340},
            {KEY_BACKSLASH, "BACKSLASH", 92}, {KEY_Z, "Z", 90}, {KEY_X, "X", 88}, {KEY_C, "C", 67}, {KEY_V, "V", 86},
            {KEY_B, "B", 66}, {KEY_N, "N", 78}, {KEY_M, "M", 77}, {KEY_COMMA, "COMMA", 44}, {KEY_PERIOD, "PERIOD", 46},
            {KEY_SLASH, "SLASH", 47}, {KEY_RSHIFT, "RSHIFT", 344}, {KEY_MULTIPLY, "MULTIPLY", 332},
            {KEY_LMENU, "LMENU", 342}, {KEY_SPACE, "SPACE", 32}, {KEY_CAPITAL, "CAPITAL", 280},
            {KEY_F1, "F1", 290}, {KEY_F2, "F2", 291}, {KEY_F3, "F3", 292}, {KEY_F4, "F4", 293}, {KEY_F5, "F5", 294},
            {KEY_F6, "F6", 295}, {KEY_F7, "F7", 296}, {KEY_F8, "F8", 297}, {KEY_F9, "F9", 298}, {KEY_F10, "F10", 299},
            {KEY_NUMLOCK, "NUMLOCK", 282}, {KEY_SCROLL, "SCROLL", 281}, {KEY_NUMPAD7, "NUMPAD7", 327},
            {KEY_NUMPAD8, "NUMPAD8", 328}, {KEY_NUMPAD9, "NUMPAD9", 329}, {KEY_SUBTRACT, "SUBTRACT", 333},
            {KEY_NUMPAD4, "NUMPAD4", 324}, {KEY_NUMPAD5, "NUMPAD5", 325}, {KEY_NUMPAD6, "NUMPAD6", 326},
            {KEY_ADD, "ADD", 334}, {KEY_NUMPAD1, "NUMPAD1", 321}, {KEY_NUMPAD2, "NUMPAD2", 322},
            {KEY_NUMPAD3, "NUMPAD3", 323}, {KEY_NUMPAD0, "NUMPAD0", 320}, {KEY_DECIMAL, "DECIMAL", 330},
            {KEY_F11, "F11", 300}, {KEY_F12, "F12", 301}, {KEY_F13, "F13", 302}, {KEY_F14, "F14", 303},
            {KEY_F15, "F15", 304}, {KEY_NUMPADEQUALS, "NUMPADEQUALS", 336}, {KEY_NUMPADENTER, "NUMPADENTER", 335},
            {KEY_RCONTROL, "RCONTROL", 345}, {KEY_DIVIDE, "DIVIDE", 331}, {KEY_SYSRQ, "SYSRQ", 283},
            {KEY_RMENU, "RMENU", 346}, {KEY_PAUSE, "PAUSE", 284}, {KEY_HOME, "HOME", 268}, {KEY_UP, "UP", 265},
            {KEY_PRIOR, "PRIOR", 266}, {KEY_LEFT, "LEFT", 263}, {KEY_RIGHT, "RIGHT", 262}, {KEY_END, "END", 269},
            {KEY_DOWN, "DOWN", 264}, {KEY_NEXT, "NEXT", 267}, {KEY_INSERT, "INSERT", 260},
            {KEY_DELETE, "DELETE", 261}, {KEY_LMETA, "LMETA", 343}, {KEY_RMETA, "RMETA", 347}, {KEY_APPS, "APPS", 348},
    };

    private static final String[] NAMES = new String[KEYBOARD_SIZE];
    private static final int[] TO_GLFW = new int[KEYBOARD_SIZE];
    private static final int[] FROM_GLFW = new int[512];

    static {
        java.util.Arrays.fill(TO_GLFW, -1);
        for (Object[] row : TABLE) {
            int code = (Integer) row[0];
            NAMES[code] = (String) row[1];
            TO_GLFW[code] = (Integer) row[2];
            FROM_GLFW[(Integer) row[2]] = code;
        }
    }

    private Keyboard() {
    }

    public static boolean isKeyDown(int key) {
        return key > 0 && key < KEYBOARD_SIZE && Astra.platform().isKeyDown(key);
    }

    public static String getKeyName(int key) {
        return Astra.platform().getKeyName(key);
    }

    /** The built-in LWJGL 2 name for a code (for platforms without their own), or null. */
    public static String nameOf(int key) {
        return key > 0 && key < KEYBOARD_SIZE ? NAMES[key] : null;
    }

    /** LWJGL 2 code → GLFW key, or -1 when GLFW has no such key. */
    public static int toGlfw(int key) {
        return key > 0 && key < KEYBOARD_SIZE ? TO_GLFW[key] : -1;
    }

    /** GLFW key → LWJGL 2 code, or KEY_NONE. */
    public static int fromGlfw(int glfwKey) {
        return glfwKey >= 0 && glfwKey < FROM_GLFW.length ? FROM_GLFW[glfwKey] : KEY_NONE;
    }
}
