package dev.astra.client.module.impl.hud;

import dev.astra.client.features.RadioScreen;
import dev.astra.client.features.RadioPlayer;
import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.util.Keys;

/**
 * Now Playing: the radio's current song on your HUD. The radio itself lives in the Friends menu
 * (Shift + Tab → Radio); this only shows what it's playing, and hiding it never stops the music.
 */
public final class NowPlayingModule extends TextHudModule {

    private final KeySetting key = setting(new KeySetting("Open Radio", Keys.NONE));
    private boolean wasDown;

    public NowPlayingModule() {
        super("Now Playing", "The radio's song on your HUD. The radio is in Friends (Shift + Tab).", true, 4, 196);
    }

    @Override
    public void onTick() {
        boolean down = key.getCode() != Keys.NONE && key.isDown();
        if (down && !wasDown && !mc().isScreenOpen()) {
            RadioScreen.open();
        }
        wasDown = down;
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
