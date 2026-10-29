package dev.astra.client.module.impl.hud;

import dev.astra.client.module.Category;
import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.input.Keyboard;

/**
 * Press your sprint key once to keep sprinting; press again to stop. Uses the Sprint key from
 * Minecraft's own Controls, so there's nothing to set up twice.
 *
 * It works exactly like holding the sprint key down (each version's ToggleSprintMixin makes the
 * game read the key as held), so vanilla decides when you may sprint: never while blocking,
 * eating, drawing a bow, blinded, starving or walking into a wall. Forcing sprint on in those
 * cases is what anticheats see as cheating, and they answer with a lag back.
 */
public final class ToggleSprintModule extends TextHudModule {

    private final BooleanSetting useMinecraftKey;
    private final KeySetting toggleKey;
    private final BooleanSetting showText;
    private boolean toggled = true;
    private boolean keyWasDown;

    /** Read by ToggleSprintMixin every tick: true = treat the sprint key as held. */
    private static volatile boolean holding;

    public static boolean isHoldingSprint() {
        return holding;
    }

    public ToggleSprintModule() {
        super("Toggle Sprint", "Sprint without holding a key.", Category.MECHANIC, true, 82, 78);
        useMinecraftKey = setting(new BooleanSetting("Use Minecraft Sprint Key", true));
        toggleKey = setting(new KeySetting("Toggle Key (if not)", Keyboard.KEY_LCONTROL));
        showText = setting(new BooleanSetting("Show HUD Text", true));
        background.set(false);
    }

    @Override
    public void onTick() {
        if (!mc().isInGame()) {
            return;
        }
        boolean down = keyDown() && !mc().isScreenOpen();
        if (down && !keyWasDown) {
            toggled = !toggled;
        }
        keyWasDown = down;

        holding = toggled;
    }

    @Override
    protected void onDisable() {
        holding = false;
    }

    private boolean keyDown() {
        if (!useMinecraftKey.isOn()) {
            return toggleKey.isDown();
        }
        int code = mc().getSprintKeyCode();
        if (code == 0) {
            return false;
        }
        return code < 0 ? dev.astra.client.input.Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
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
