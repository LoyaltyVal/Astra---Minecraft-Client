package dev.astra.client.module.impl.hud;

import dev.astra.client.Astra;
import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Keys;
import dev.astra.client.util.Render;
import org.lwjgl.input.Keyboard;

public final class KeystrokesModule extends HudModule {

    private static final int KEY = 22;
    private static final int GAP = 2;
    private static final int WIDTH = KEY * 3 + GAP * 2;
    private static final int SPACE_HEIGHT = 10;
    private static final float FADE_SPEED = 0.25f;

    private final KeySetting forward;
    private final KeySetting left;
    private final KeySetting back;
    private final KeySetting right;
    private final KeySetting jump;
    private final BooleanSetting showMouse;
    private final BooleanSetting showCps;
    private final BooleanSetting showSpace;
    private final ColorSetting pressedColor;
    private final ColorSetting textColor;

    /** Fade progress per key: W A S D LMB RMB Space. */
    private final float[] fade = new float[7];
    private long lastFrame = System.nanoTime();

    public KeystrokesModule() {
        super("Keystrokes", "Shows WASD, mouse and space presses.", true, 4, 78);
        // These default to vanilla's binds. If you rebound movement keys, change them here too.
        forward = setting(new KeySetting("Forward Key", Keyboard.KEY_W));
        left = setting(new KeySetting("Left Key", Keyboard.KEY_A));
        back = setting(new KeySetting("Back Key", Keyboard.KEY_S));
        right = setting(new KeySetting("Right Key", Keyboard.KEY_D));
        jump = setting(new KeySetting("Jump Key", Keyboard.KEY_SPACE));
        showMouse = setting(new BooleanSetting("Show Mouse", true));
        showCps = setting(new BooleanSetting("Show CPS", true));
        showSpace = setting(new BooleanSetting("Show Space", true));
        pressedColor = setting(new ColorSetting("Pressed Color", 0xFFFFFFFF));
        textColor = setting(new ColorSetting("Text Color", 0xFFFFFFFF));
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        int h = KEY * 2 + GAP;
        if (showMouse.isOn()) {
            h += GAP + KEY;
        }
        if (showSpace.isOn()) {
            h += GAP + SPACE_HEIGHT;
        }
        return h;
    }

    @Override
    protected void render(boolean editor) {
        long now = System.nanoTime();
        float frameScale = Math.min(4f, (now - lastFrame) / 16_666_667f); // 1.0 at 60 FPS
        lastFrame = now;
        boolean live = !mc().isScreenOpen() || editor;

        key(0, Keys.name(forward.getCode()), KEY + GAP, 0, KEY, KEY, live && forward.isDown(), frameScale);
        int row2 = KEY + GAP;
        key(1, Keys.name(left.getCode()), 0, row2, KEY, KEY, live && left.isDown(), frameScale);
        key(2, Keys.name(back.getCode()), KEY + GAP, row2, KEY, KEY, live && back.isDown(), frameScale);
        key(3, Keys.name(right.getCode()), (KEY + GAP) * 2, row2, KEY, KEY, live && right.isDown(), frameScale);

        int y = row2 + KEY + GAP;
        if (showMouse.isOn()) {
            int half = (WIDTH - GAP) / 2;
            String lmb = showCps.isOn() ? Astra.get().getClicks().getLeftCps() + " CPS" : "LMB";
            String rmb = showCps.isOn() ? Astra.get().getClicks().getRightCps() + " CPS" : "RMB";
            key(4, lmb, 0, y, half, KEY, live && Keys.isDown(Keys.MOUSE_LEFT), frameScale);
            key(5, rmb, half + GAP, y, WIDTH - half - GAP, KEY, live && Keys.isDown(Keys.MOUSE_RIGHT), frameScale);
            y += KEY + GAP;
        }
        if (showSpace.isOn()) {
            boolean down = live && jump.isDown();
            stepFade(6, down, frameScale);
            Render.rect(0, y, WIDTH, SPACE_HEIGHT, background(6));
            int barColor = Colors.lerp(textColor.getArgb(), 0xFF000000, fade[6]);
            Render.rect(WIDTH / 2 - 12, y + SPACE_HEIGHT / 2 - 1, 24, 2, barColor);
        }
    }

    private void key(int index, String label, int x, int y, int w, int h, boolean down, float frameScale) {
        stepFade(index, down, frameScale);
        Render.rect(x, y, w, h, background(index));
        int color = Colors.lerp(textColor.getArgb(), 0xFF000000, fade[index]);
        Render.textCentered(Render.trim(label, w - 2), x + w / 2f, y + (h - Render.fontHeight()) / 2f + 1, color,
                fade[index] < 0.5f);
    }

    private void stepFade(int index, boolean down, float frameScale) {
        float target = down ? 1f : 0f;
        fade[index] += (target - fade[index]) * Math.min(1f, FADE_SPEED * frameScale);
    }

    private int background(int index) {
        int idle = 0x73000000;
        int pressed = Colors.withAlpha(pressedColor.getArgb(), 0xC8);
        return Colors.lerp(idle, pressed, fade[index]);
    }
}
