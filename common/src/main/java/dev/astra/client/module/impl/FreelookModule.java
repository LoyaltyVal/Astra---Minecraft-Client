package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.ModeSetting;
import dev.astra.client.util.Keys;
import org.lwjgl.input.Keyboard;

/**
 * Freelook: hold (or toggle) a key to switch to third person and move the camera around your
 * player with the mouse, while you keep walking and facing the same way.
 *
 * Two hooks make it work (see EntityMixin and GameRendererMixin in each version):
 *  - mouse movement is sent here instead of turning the player ({@link #turn});
 *  - while the camera is placed, the player's rotation is swapped for the camera's.
 */
public final class FreelookModule extends Module {

    private final KeySetting key;
    private final ModeSetting mode;
    private final ModeSetting view;
    private final dev.astra.client.module.setting.BooleanSetting ownNametag;

    private boolean active;
    private boolean keyWasDown;
    private int savedPerspective;
    private float yaw;
    private float pitch;

    public FreelookModule() {
        super("Freelook", "Hold a key to look around your player without turning.", Category.MECHANIC, true);
        key = setting(new KeySetting("Freelook Key", Keyboard.KEY_LMENU));
        mode = setting(new ModeSetting("Mode", "Hold", "Hold", "Toggle"));
        view = setting(new ModeSetting("Camera", "Behind", "Behind", "In Front"));
        ownNametag = setting(new dev.astra.client.module.setting.BooleanSetting("Show Own Nametag", true));
    }

    @Override
    public void onTick() {
        boolean canUse = mc().isInGame() && !mc().isScreenOpen();
        boolean down = canUse && key.isDown();
        if (mode.is("Hold")) {
            if (down && !active) {
                start();
            } else if (!down && active) {
                stop();
            }
        } else if (down && !keyWasDown) {
            if (active) {
                stop();
            } else {
                start();
            }
        }
        keyWasDown = down;
        if (active && !mc().isInGame()) {
            stop();
        }
    }

    private void start() {
        yaw = mc().getPlayerYaw();
        pitch = mc().getPlayerPitch();
        savedPerspective = mc().getPerspective();
        mc().setPerspective(view.is("In Front") ? 2 : 1);
        active = true;
    }

    private void stop() {
        active = false;
        mc().setPerspective(savedPerspective);
    }

    @Override
    protected void onDisable() {
        if (active) {
            stop();
        }
    }

    /** True while the camera is detached from where the player is facing. */
    public boolean isLooking() {
        return active && isEnabled();
    }

    /**
     * Mouse movement while freelooking (same maths as vanilla's Entity#increaseTransforms).
     * Returns true when it was used, so the player itself doesn't turn.
     */
    public boolean turn(float dx, float dy) {
        if (!isLooking()) {
            return false;
        }
        yaw += dx * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch - dy * 0.15f));
        return true;
    }

    public boolean showOwnNametag() {
        return ownNametag.isOn();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public String getPreviewText() {
        return (mode.is("Hold") ? "Hold " : "Press ") + Keys.name(key.getCode());
    }
}
