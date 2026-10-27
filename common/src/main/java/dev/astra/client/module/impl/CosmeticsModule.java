package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ModeSetting;
import dev.astra.client.module.setting.NumberSetting;

/** Options for capes and wings (edited from the Cosmetics page). Always enabled. */
public final class CosmeticsModule extends Module {

    private final BooleanSetting showCape;
    private final BooleanSetting showWings;
    private final BooleanSetting showOthers;
    private final BooleanSetting showRankTags;
    private final BooleanSetting shareServer;
    private final NumberSetting wingSize;
    private final NumberSetting flapSpeed;
    private final ModeSetting wingMode;

    public CosmeticsModule() {
        super("Cosmetics", "Cape, wing and rank tag options.", Category.CLIENT, true);
        showCape = setting(new BooleanSetting("Show Cape", true));
        showWings = setting(new BooleanSetting("Show Wings", true));
        showOthers = setting(new BooleanSetting("Show Others' Cosmetics", true));
        showRankTags = setting(new BooleanSetting("Show Rank Tags", true));
        shareServer = setting(new BooleanSetting("Share My Server", true));
        wingSize = setting(new NumberSetting("Wing Size", 1.0, 0.5, 2.0, 0.05));
        flapSpeed = setting(new NumberSetting("Flap Speed", 1.0, 0.0, 3.0, 0.1));
        wingMode = setting(new ModeSetting("Wing Image", "Auto",
                "Auto", "Single (points right)", "Single (points left)", "Pair"));
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Always on.
    }

    @Override
    public void loadEnabled(boolean enabled) {
        // Always on.
    }

    public boolean showCape() {
        return showCape.isOn();
    }

    public boolean showWings() {
        return showWings.isOn();
    }

    public boolean showOthers() {
        return showOthers.isOn();
    }

    public boolean showRankTags() {
        return showRankTags.isOn();
    }

    /** Show which server you're on to other players (website Players page and profile). */
    public boolean shareServer() {
        return shareServer.isOn();
    }

    public float wingSize() {
        return wingSize.getFloat();
    }

    public float flapSpeed() {
        return flapSpeed.getFloat();
    }

    public String wingMode() {
        return wingMode.get();
    }
}
