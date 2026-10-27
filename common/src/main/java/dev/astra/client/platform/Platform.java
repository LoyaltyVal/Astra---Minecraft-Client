package dev.astra.client.platform;

import dev.astra.client.ui.UiScreen;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Everything Astra needs from Minecraft itself.
 *
 * The shared code in /common only talks to the game through this interface, so supporting a
 * new Minecraft version means writing one PlatformImpl (plus a few mixins) for it in
 * versions/&lt;mc&gt;/src/main/java/dev/astra/client/platform/.
 */
public interface Platform {

    String minecraftVersion();

    /** True when a world is loaded and the player exists. */
    boolean isInGame();

    /** True when any screen (chat, inventory, menus, Astra's own UI) is open. */
    boolean isScreenOpen();

    /** The Astra UI currently shown, or null if none (or a vanilla screen) is open. */
    UiScreen getOpenUi();

    /** Shows an Astra UI. Pass null to close whatever screen is open. */
    void openUi(UiScreen ui);

    /** The GUI scale option (0 = auto). */
    int getGuiScale();

    /** The .minecraft-style game folder for this instance. */
    File getGameDir();

    // ---- drawing (scaled GUI coordinates) ----

    void fill(int x1, int y1, int x2, int y2, int argb);

    void drawText(String text, float x, float y, int argb, boolean shadow);

    int getTextWidth(String text);

    int getFontHeight();

    /** Draws an item icon with its durability bar and count. */
    void drawItem(Object stack, int x, int y);

    /** Draws an 18x18 potion effect icon from the inventory texture. */
    void drawEffectIcon(int iconIndex, int x, int y);

    /** Draws part of a vanilla GUI texture (e.g. "textures/gui/icons.png"), 256x256 UV space. */
    void drawGuiTexture(String path, int x, int y, int u, int v, int width, int height);

    // ---- textures & GL state for cosmetics ----

    /** Uploads an image as a texture; returns a handle for {@link #bindTexture}. */
    Object createTexture(String name, BufferedImage image);

    void bindTexture(Object handle);

    /** Sets the draw colour through the game's GL state cache (never call glColor directly). */
    void color(float r, float g, float b, float a);

    /** Standard alpha blending on/off. */
    void blend(boolean enabled);

    void cull(boolean enabled);

    void texture2d(boolean enabled);

    // ---- player ----

    double getPlayerX();

    double getPlayerY();

    double getPlayerZ();

    float getPlayerYaw();

    boolean isSprinting();

    /** Starts sprinting when vanilla would allow it (moving forward, not sneaking, enough food). */
    void sprintIfPossible();

    /** Armor from helmet to boots; null entries for empty slots. Empty array when not in game. */
    HudData.Item[] getArmor();

    /** The item in the selected hotbar slot, or null. */
    HudData.Item getHeldItem();

    List<HudData.Effect> getEffects();

    /** Ping to the server in ms, or -1 if unknown (e.g. singleplayer). */
    int getPing();

    /** The sidebar scoreboard, or null if none is shown. */
    HudData.Sidebar getSidebar();

    /** The boss bar, or null if no boss is near. */
    HudData.Boss getBossBar();

    // ---- options ----

    float getGamma();

    void setGamma(float gamma);

    float getSensitivity();

    void setSensitivity(float sensitivity);
}
