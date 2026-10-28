package dev.astra.client.module.impl.hud;

import dev.astra.client.features.RadioPlayer;
import dev.astra.client.features.RadioScreen;
import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.ui.UiScreen;
import dev.astra.client.util.Keys;

/**
 * The Radio module: internet radio while you play (OPEN on its card, or a hotkey), with the
 * current song on your HUD. Disabling it stops the music.
 */
public final class NowPlayingModule extends TextHudModule {

    private final KeySetting key = setting(new KeySetting("Open Radio", Keys.NONE));
    private final BooleanSetting showSong = setting(new BooleanSetting("Show Song on HUD", true));
    private boolean wasDown;

    public NowPlayingModule() {
        super("Radio", "Internet radio while you play, with the song on your HUD.", true, 4, 196);
    }

    @Override
    public void onTick() {
        boolean down = key.getCode() != Keys.NONE && key.isDown();
        if (down && !wasDown && !mc().isScreenOpen()) {
            mc().openUi(new RadioScreen());
        }
        wasDown = down;
    }

    @Override
    protected void onDisable() {
        RadioPlayer.get().stop();
    }

    @Override
    public UiScreen createScreen() {
        return new RadioScreen();
    }

    @Override
    public BooleanSetting getHudVisibility() {
        return showSong;
    }

    @Override
    protected String getText() {
        RadioPlayer p = RadioPlayer.get();
        if (p.getStation() == null) {
            return "♪ Radio off";
        }
        String song = p.getNowPlaying();
        return "♪ " + (song != null ? song : p.getStation().name);
    }

    @Override
    protected void render(boolean editor) {
        // Only on screen while something plays (always in the HUD editor, so it can be placed).
        if (editor || RadioPlayer.get().getStation() != null) {
            super.render(editor);
        }
    }
}
