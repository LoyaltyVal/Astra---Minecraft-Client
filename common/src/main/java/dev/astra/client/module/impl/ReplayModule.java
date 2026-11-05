package dev.astra.client.module.impl;

import dev.astra.client.input.Keyboard;
import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.replay.Replay;
import dev.astra.client.replay.ReplaysScreen;
import dev.astra.client.util.Keys;

/**
 * Replay: records your games so you can watch them back from any angle (see {@link Replay}).
 * The card's OPEN button shows your replays.
 */
public final class ReplayModule extends Module {

    private final BooleanSetting autoRecord = setting(new BooleanSetting("Record Automatically", true));
    private final BooleanSetting singleplayer = setting(new BooleanSetting("Record Singleplayer", false));
    private final BooleanSetting indicator = setting(new BooleanSetting("Show Recording Indicator", true));
    private final NumberSetting keep = setting(new NumberSetting("Keep Replays", 30, 5, 200, 5));
    private final KeySetting openKey = setting(new KeySetting("Open Replays", Keys.NONE));
    private final KeySetting pauseKey = setting(new KeySetting("Pause Replay", Keyboard.KEY_P));
    private final KeySetting backKey = setting(new KeySetting("Back 5 Seconds", Keyboard.KEY_LEFT));
    private final KeySetting forwardKey = setting(new KeySetting("Forward 5 Seconds", Keyboard.KEY_RIGHT));

    private boolean openWas, pauseWas, backWas, forwardWas;

    public ReplayModule() {
        super("Replay", "Records your games to watch back from any angle.", Category.MECHANIC, true);
    }

    public boolean recordAutomatically() {
        return autoRecord.isOn();
    }

    public boolean recordSingleplayer() {
        return singleplayer.isOn();
    }

    public boolean showIndicator() {
        return indicator.isOn();
    }

    public int keepCount() {
        return (int) keep.get().doubleValue();
    }

    @Override
    public void onTick() {
        boolean open = openKey.getCode() != Keys.NONE && openKey.isDown();
        if (open && !openWas && !mc().isScreenOpen() && !Replay.get().isPlaying()) {
            mc().openUi(new ReplaysScreen());
        }
        openWas = open;
    }

    /** Pause and skip keys while watching (called by {@link Replay} when no screen is open). */
    public void handleKeys(Replay r) {
        boolean pause = pauseKey.getCode() != Keys.NONE && pauseKey.isDown();
        boolean back = backKey.getCode() != Keys.NONE && backKey.isDown();
        boolean forward = forwardKey.getCode() != Keys.NONE && forwardKey.isDown();
        if (pause && !pauseWas) {
            r.setPaused(!r.isPaused());
        }
        if (back && !backWas) {
            r.seek(r.time() - 5000);
        }
        if (forward && !forwardWas) {
            r.seek(r.time() + 5000);
        }
        pauseWas = pause;
        backWas = back;
        forwardWas = forward;
    }

    @Override
    public void onShutdown() {
        Replay.get().finishNow();
    }

    @Override
    public dev.astra.client.ui.UiScreen createScreen() {
        return new ReplaysScreen();
    }

    @Override
    public String getPreviewText() {
        return Replay.get().isRecording() ? "Recording " + Replay.clock(Replay.get().recordingTime()) : "Replays";
    }
}
