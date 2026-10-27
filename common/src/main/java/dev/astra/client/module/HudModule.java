package dev.astra.client.module;

import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.NumberSetting;
import org.lwjgl.opengl.GL11;

/**
 * A module drawn on the HUD that can be dragged and resized in the HUD editor.
 * Subclasses draw at (0, 0) in their own unscaled space; position and scale are applied here.
 *
 * Default positions may use {@link #CENTER} or {@link #END} instead of a number, so a module
 * can start centred or against the right/bottom edge whatever the screen size.
 */
public abstract class HudModule extends Module {

    /** Default-position marker: centre on this axis. */
    public static final float CENTER = -1f;
    /** Default-position marker: against the right (x) or bottom (y) edge. */
    public static final float END = -2f;

    private static final float EDGE = 2f;

    private final float defaultX;
    private final float defaultY;
    private float x;
    private float y;
    /** False until the position has been resolved from anchors or set by the user/config. */
    private boolean placed;
    protected final NumberSetting scale;

    protected HudModule(String name, String description, Category category, boolean enabledByDefault,
                        float defaultX, float defaultY) {
        super(name, description, category, enabledByDefault);
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.x = Math.max(0, defaultX);
        this.y = Math.max(0, defaultY);
        this.scale = setting(new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.05));
    }

    protected HudModule(String name, String description, boolean enabledByDefault, float defaultX, float defaultY) {
        this(name, description, Category.HUD, enabledByDefault, defaultX, defaultY);
    }

    /** Unscaled width. In the editor this should include placeholder content. */
    public abstract int getWidth();

    public abstract int getHeight();

    /** Draw at (0, 0). {@code editor} is true inside the HUD editor and menu previews (show placeholders). */
    protected abstract void render(boolean editor);

    public final void draw(boolean editor) {
        float s = getScale();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        GL11.glScalef(s, s, 1f);
        render(editor);
        GL11.glPopMatrix();
    }

    /** Draws the module centred in a box (used by the mod menu cards), shrunk to fit. */
    public void drawPreview(float centerX, float centerY, float maxWidth, float maxHeight) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float s = Math.min(1.5f, Math.min(maxWidth / w, maxHeight / h));
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX - w * s / 2f, centerY - h * s / 2f, 0f);
        GL11.glScalef(s, s, 1f);
        render(true);
        GL11.glPopMatrix();
    }

    /**
     * For modules that do something besides drawing (e.g. Toggle Sprint): a setting that hides
     * just the HUD element while the module keeps working. Null for pure HUD modules.
     */
    public BooleanSetting getHudVisibility() {
        return null;
    }

    /** Whether the HUD element should be drawn right now. */
    public boolean isShownOnHud() {
        BooleanSetting visible = getHudVisibility();
        return isEnabled() && (visible == null || visible.isOn());
    }

    /** Resolves default anchors on first use, then keeps the module fully on screen. */
    public void clampToScreen(int screenWidth, int screenHeight) {
        float w = getScaledWidth();
        float h = getScaledHeight();
        if (!placed) {
            x = resolve(defaultX, screenWidth, w);
            y = resolve(defaultY, screenHeight, h);
            placed = true;
        }
        x = Math.max(0, Math.min(x, screenWidth - w));
        y = Math.max(0, Math.min(y, screenHeight - h));
    }

    private static float resolve(float value, int screen, float size) {
        if (value == CENTER) {
            return (screen - size) / 2f;
        }
        if (value == END) {
            return screen - size - EDGE;
        }
        return value;
    }

    public void resetPosition() {
        placed = false;
        scale.reset();
    }

    /** True once the position is real (not a pending CENTER/END default). Unplaced positions aren't saved. */
    public boolean isPlaced() {
        return placed;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.placed = true;
    }

    public float getScale() {
        return scale.getFloat();
    }

    public void setScale(double value) {
        scale.set(value);
    }

    public float getScaledWidth() {
        return getWidth() * getScale();
    }

    public float getScaledHeight() {
        return getHeight() * getScale();
    }
}
