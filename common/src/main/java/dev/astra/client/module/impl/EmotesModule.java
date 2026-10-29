package dev.astra.client.module.impl;

import dev.astra.client.Astra;
import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.social.EmoteWheelScreen;
import dev.astra.client.input.Keyboard;

/**
 * Emotes: hold the wheel key (B), point at an emote and let go. Everyone using Astra sees it.
 */
public final class EmotesModule extends Module {

    private final KeySetting wheelKey = setting(new KeySetting("Emote Wheel Key", Keyboard.KEY_B));
    private final BooleanSetting thirdPerson = setting(new BooleanSetting("Third Person While Emoting", true));
    private final BooleanSetting showOthers = setting(new BooleanSetting("Show Others' Emotes", true));

    private boolean wasDown;
    /** Perspective to go back to when our emote ends, or -1. */
    private int restorePerspective = -1;

    public EmotesModule() {
        super("Emotes", "An emote wheel. Other Astra players see your emotes.", Category.VISUAL, true);
    }

    @Override
    public void onTick() {
        boolean down = wheelKey.isDown();
        if (down && !wasDown && mc().isInGame() && !mc().isScreenOpen()) {
            mc().openUi(new EmoteWheelScreen(wheelKey.getCode()));
        }
        wasDown = down;

        // Switch to third person while you emote, then back.
        boolean emoting = Astra.get().getCosmetics().isSelfEmoting();
        if (emoting && thirdPerson.isOn() && restorePerspective < 0 && mc().getPerspective() == 0) {
            restorePerspective = 0;
            mc().setPerspective(1);
        } else if (!emoting && restorePerspective >= 0) {
            if (mc().getPerspective() == 1) {
                mc().setPerspective(restorePerspective);
            }
            restorePerspective = -1;
        }
    }

    public void play(String id) {
        Astra.get().getCosmetics().playEmote(id);
    }

    public boolean showOthers() {
        return showOthers.isOn();
    }

    @Override
    public String getPreviewText() {
        return "Hold " + dev.astra.client.util.Keys.name(wheelKey.getCode());
    }
}
