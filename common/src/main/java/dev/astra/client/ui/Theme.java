package dev.astra.client.ui;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.ClientSettingsModule;
import dev.astra.client.util.Colors;

/** Astra's colours, in one place so the whole UI can be re-skinned. The accent comes from Client settings. */
public final class Theme {

    public static final int OVERLAY = 0x66000000;
    public static final int PANEL = 0xE6121216;
    public static final int PANEL_BAR = 0xF00C0C10;
    public static final int PANEL_BORDER = 0xFF26262E;
    public static final int CARD = 0xD8303036;
    public static final int CARD_HOVER = 0xE03A3A42;
    public static final int CARD_BUTTON = 0xD0101014;
    public static final int CARD_BUTTON_HOVER = 0xF0000000;
    public static final int CONTROL = 0xFF26262E;
    public static final int CONTROL_HOVER = 0xFF34343E;
    public static final int BUTTON = 0xE0101014;
    public static final int BUTTON_HOVER = 0xF0202028;

    public static final int ON = 0xFF3DDC97;
    public static final int OFF = 0xFFFF5C7A;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int SUBTEXT = 0xFFA0A0B0;

    /** Default accent if settings aren't loaded yet. */
    public static final int DEFAULT_ACCENT = 0xFF9B7BFF;

    private Theme() {
    }

    public static int accent() {
        Astra astra = Astra.get();
        ClientSettingsModule settings = astra == null ? null : astra.getModules().get(ClientSettingsModule.class);
        return settings == null ? DEFAULT_ACCENT : settings.getAccent();
    }

    /** Fill colour for enabled cards. */
    public static int accentCard() {
        return Colors.withAlpha(Colors.lerp(accent(), 0xFF000000, 0.2f), 0xE6);
    }

    public static int accentCardHover() {
        return Colors.withAlpha(accent(), 0xF0);
    }

    public static int accentDark() {
        return Colors.lerp(accent(), 0xFF000000, 0.45f);
    }

    public static boolean dimBackground() {
        Astra astra = Astra.get();
        ClientSettingsModule settings = astra == null ? null : astra.getModules().get(ClientSettingsModule.class);
        return settings == null || settings.isDimBackground();
    }

    public static boolean showHudLabels() {
        Astra astra = Astra.get();
        ClientSettingsModule settings = astra == null ? null : astra.getModules().get(ClientSettingsModule.class);
        return settings == null || settings.isShowHudLabels();
    }
}
