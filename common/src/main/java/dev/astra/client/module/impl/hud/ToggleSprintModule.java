package dev.astra.client.module.impl.hud;

import dev.astra.client.module.Category;
import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import org.lwjgl.input.Keyboard;

/** Press the toggle key once to keep sprinting; press again to stop. */
public final class ToggleSprintModule extends TextHudModule {

    private final KeySetting toggleKey;
    private final BooleanSetting showText;
    private boolean toggled = true;
    private boolean keyWasDown;

    public ToggleSprintModule() {
        super("Toggle Sprint", "Sprint without holding a key.", Category.MECHANIC, true, 82, 78);
        toggleKey = setting(new KeySetting("Toggle Key", Keyboard.KEY_LCONTROL));
        showText = setting(new BooleanSetting("Show HUD Text", true));
        background.set(false);
    }

    @Override
    public void onTick() {
        if (!mc().isInGame()) {
            return;
        }
        boolean down = toggleKey.isDown() && !mc().isScreenOpen();
        if (down && !keyWasDown) {
            toggled = !toggled;
        }
        keyWasDown = down;

        if (toggled) {
            mc().sprintIfPossible();
        }
    }

    @Override
    public BooleanSetting getHudVisibility() {
        return showText;
    }

    @Override
    protected String getText() {
        if (!mc().isInGame()) {
            return null;
        }
        if (toggled) {
            return "Sprinting (Toggled)";
        }
        return mc().isSprinting() ? "Sprinting (Vanilla)" : null;
    }

    @Override
    protected String getPlaceholder() {
        return "Sprinting (Toggled)";
    }
}
