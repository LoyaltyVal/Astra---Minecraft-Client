package dev.astra.client.module.impl.hud;

import dev.astra.client.Astra;
import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.util.ClickCounter;

public final class CpsModule extends TextHudModule {

    private final BooleanSetting showRight;

    public CpsModule() {
        super("CPS", "Shows your clicks per second.", true, 4, 46);
        showRight = setting(new BooleanSetting("Show Right Click", false));
    }

    @Override
    protected String getText() {
        ClickCounter clicks = Astra.get().getClicks();
        if (showRight.isOn()) {
            return "CPS: " + clicks.getLeftCps() + " | " + clicks.getRightCps();
        }
        return "CPS: " + clicks.getLeftCps();
    }
}
