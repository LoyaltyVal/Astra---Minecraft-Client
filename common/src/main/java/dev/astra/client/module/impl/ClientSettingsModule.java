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
    private final BooleanSetting noHeartFlash;
    private final BooleanSetting centerInventory;
    private final BooleanSetting titleButtons;
    private final BooleanSetting ownNametag;

    public ClientSettingsModule() {
        super("Client", "Menu colours, editor options and game tweaks.", Category.CLIENT, true);
        accent = setting(new ColorSetting("Accent Color", 0xFF9B7BFF));
        dimBackground = setting(new BooleanSetting("Dim Background", true));
        showHudLabels = setting(new BooleanSetting("HUD Editor Labels", true));
        noHeartFlash = setting(new BooleanSetting("No Heart Flashing", true));
        centerInventory = setting(new BooleanSetting("Center Inventory", true));
        ownNametag = setting(new BooleanSetting("Own Nametag in F5", false));
        titleButtons = setting(new BooleanSetting("Astra Buttons on Title Screen", true));
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

    /** Stops hearts blinking every time the server re-sends your health. */
    public boolean noHeartFlash() {
        return noHeartFlash.isOn();
    }

    /** Keeps the inventory in the middle when you have potion effects (vanilla shifts it right). */
    public boolean centerInventory() {
        return centerInventory.isOn();
    }

    /** Shows your own name tag in third person. */
    /** The Friends / Quick Join / Screenshots row on the title screen. */
    public boolean titleButtons() {
        return titleButtons.isOn();
    }

    public boolean ownNametag() {
        return ownNametag.isOn();
    }
}
