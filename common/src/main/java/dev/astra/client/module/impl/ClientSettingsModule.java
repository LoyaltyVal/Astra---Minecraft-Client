package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;

/** Client-wide options, edited from the gear button in the mod menu. Always enabled. */
public final class ClientSettingsModule extends Module {

    private final ColorSetting accent;
    private final BooleanSetting dimBackground;
    private final BooleanSetting showHudLabels;

    public ClientSettingsModule() {
        super("Client", "Menu colours and editor options.", Category.CLIENT, true);
        accent = setting(new ColorSetting("Accent Color", 0xFF9B7BFF));
        dimBackground = setting(new BooleanSetting("Dim Background", true));
        showHudLabels = setting(new BooleanSetting("HUD Editor Labels", true));
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Can't be turned off.
    }

    @Override
    public void loadEnabled(boolean enabled) {
        // Always on, whatever the config says.
    }

    public int getAccent() {
        return accent.getArgb() | 0xFF000000;
    }

    public boolean isDimBackground() {
        return dimBackground.isOn();
    }

    public boolean isShowHudLabels() {
        return showHudLabels.isOn();
    }
}
