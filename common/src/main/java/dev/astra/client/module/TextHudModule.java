package dev.astra.client.module;

import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

/** A single line of text in an optional box, like "FPS: 144". */
public abstract class TextHudModule extends HudModule {

    private static final int BOX_MIN_WIDTH = 56;
    private static final int BOX_HEIGHT = 18;
    private static final int PADDING = 5;

    protected final BooleanSetting background;
    protected final NumberSetting backgroundOpacity;
    protected final BooleanSetting brackets;
    protected final BooleanSetting shadow;
    protected final ColorSetting textColor;

    protected TextHudModule(String name, String description, Category category, boolean enabledByDefault,
                            float defaultX, float defaultY) {
        super(name, description, category, enabledByDefault, defaultX, defaultY);
        background = setting(new BooleanSetting("Background", true));
        backgroundOpacity = setting(new NumberSetting("Background Opacity", 45, 0, 100, 1));
        brackets = setting(new BooleanSetting("Brackets", false));
        shadow = setting(new BooleanSetting("Text Shadow", true));
        textColor = setting(new ColorSetting("Text Color", 0xFFFFFFFF));
    }

    protected TextHudModule(String name, String description, boolean enabledByDefault, float defaultX, float defaultY) {
        this(name, description, Category.HUD, enabledByDefault, defaultX, defaultY);
    }

    /** The text to show, or null/empty to hide the module this frame. */
    protected abstract String getText();

    /** Shown in the HUD editor when {@link #getText()} is empty. */
    protected String getPlaceholder() {
        return getName();
    }

    private String displayText(boolean editor) {
        String text = getText();
        if ((text == null || text.isEmpty()) && editor) {
            text = getPlaceholder();
        }
        if (text == null || text.isEmpty()) {
            return "";
        }
        return brackets.isOn() ? "[" + text + "]" : text;
    }

    @Override
    public int getWidth() {
        int textWidth = Render.width(displayText(true));
        return background.isOn() ? Math.max(BOX_MIN_WIDTH, textWidth + PADDING * 2) : textWidth;
    }

    @Override
    public int getHeight() {
        return background.isOn() ? BOX_HEIGHT : Render.fontHeight();
    }

    @Override
    protected void render(boolean editor) {
        String text = displayText(editor);
        if (text.isEmpty()) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (background.isOn()) {
            int alpha = (int) (backgroundOpacity.get() / 100.0 * 255);
            Render.rect(0, 0, w, h, Colors.withAlpha(0x000000, alpha));
            Render.textCentered(text, w / 2f, (h - Render.fontHeight()) / 2f + 1, textColor.getArgb(), shadow.isOn());
        } else {
            Render.text(text, 0, 0, textColor.getArgb(), shadow.isOn());
        }
    }
}
