package dev.astra.client.module.impl;

import dev.astra.client.features.ScreenshotsScreen;
import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.social.Notifications;
import dev.astra.client.ui.Theme;
import dev.astra.client.util.Keys;

/** Screenshot Manager: browse, star, copy, rename and delete screenshots without leaving the game. */
public final class ScreenshotsModule extends Module {

    private final KeySetting key = setting(new KeySetting("Open Screenshots", Keys.NONE));
    private final BooleanSetting toast = setting(new BooleanSetting("Pop-up When Saved", true));

    private boolean wasDown;

    public ScreenshotsModule() {
        super("Screenshot Manager", "View and organise your screenshots in game.", Category.MECHANIC, true);
    }

    @Override
    public void onTick() {
        boolean down = key.getCode() != Keys.NONE && key.isDown();
        if (down && !wasDown && !mc().isScreenOpen()) {
            mc().openUi(new ScreenshotsScreen());
        }
        wasDown = down;
    }

    @Override
    public void onChat(String message) {
        if (toast.isOn() && message.startsWith("Saved screenshot as ")) {
            String how = key.getCode() != Keys.NONE ? "Press " + Keys.name(key.getCode()) + " to view it"
                    : "Right Shift → Screenshots to view it";
            Notifications.push("Screenshot saved", how, Theme.accent());
        }
    }

    @Override
    public dev.astra.client.ui.UiScreen createScreen() {
        return new ScreenshotsScreen();
    }

    @Override
    public String getPreviewText() {
        return "Screenshots";
    }
}
