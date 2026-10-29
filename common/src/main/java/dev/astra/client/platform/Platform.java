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

    // ---- 2D/3D drawing primitives (act on the current render context's matrix) ----

    void pushMatrix();

    void popMatrix();

    void translate(float x, float y, float z);

    void scale(float x, float y, float z);

    /** Rotates by angle degrees around the axis (x, y, z). */
    void rotate(float angle, float x, float y, float z);

    /** A textured quad in GUI coordinates (UVs 0..1), drawn white and alpha-blended. */
    void drawTexture(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1);

    /**
     * A solid triangle strip in GUI coordinates: xy holds x0, y0, x1, y1, ... (used for rings).
     */
    void fillTriangleStrip(float[] xy, int argb);

    /**
     * A textured quad in the XY plane of the current (world) matrix, from (x0, y0) to (x1, y1):
     * double-sided, smoothly filtered and alpha-blended (cosmetic wings).
     */
    void drawWorldQuad(Object handle, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1);

    /** Clips drawing to a rectangle in scaled GUI coordinates until {@link #endScissor()}. */
    void scissor(int x, int y, int w, int h);

    void endScissor();

    // ---- input (key codes are LWJGL 2's, as in 1.7/1.8; newer versions translate from GLFW) ----

    boolean isKeyDown(int key);

    /** The key's short name ("A", "LSHIFT", "F5"), or null for unknown codes. */
    String getKeyName(int key);

    /** 0 = left, 1 = right, 2 = middle, more for side buttons. */
    boolean isMouseButtonDown(int button);

    int getMouseButtonCount();

    /** Scroll since the last call (positive = up), like LWJGL 2's Mouse.getDWheel(). */
    int consumeScroll();

    // ---- window ----

    /** The window's size in real pixels. */
    int getWindowWidth();

    int getWindowHeight();

    boolean isWindowFocused();

    void setWindowTitle(String title);

    /** Turns borderless fullscreen on or off; returns whether it's on now. */
    boolean setBorderless(boolean on);

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

    float getPlayerPitch();

    /** 0 = first person, 1 = third person behind, 2 = third person in front. */
    int getPerspective();

    void setPerspective(int perspective);

    boolean isSprinting();

    /** The key bound to Sprint in Minecraft's controls (LWJGL code; negative = mouse button). */
    int getSprintKeyCode();

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

    /** "singleplayer", the multiplayer server's address, or null when not in a world. */
    String getServerAddress();

    /** UUIDs of every player in the loaded world (yours included), for cosmetics lookups. */
    List<String> getWorldPlayerUuids();

    /** Potions in your inventory. kind: 0 = splash healing, 1 = any healing, 2 = any potion. */
    int countPotions(int kind);

    /**
     * 1.7-style: while you use an item (block, eat, draw a bow) and hold attack, swing the arm.
     * Only the animation; nothing is sent to the server.
     */
    void swingIfUsingAndAttacking();

    /** Sends a chat message or /command as if you typed it. */
    void sendChatMessage(String message);

    /** Frees a texture made with {@link #createTexture}. */
    void deleteTexture(Object handle);

    // ---- world & camera (waypoints) ----

    float getHealth();

    /** -1 nether, 0 overworld, 1 end (other numbers for modded dimensions). */
    int getDimension();

    /** The singleplayer world's folder name, or null in multiplayer. */
    String getWorldName();

    /** Where the camera's entity is drawn this frame (interpolated), {x, y, z}. */
    double[] getRenderPos(float tickDelta);

    /** This frame's world model-view and projection matrices (16 floats each, column-major). */
    java.nio.FloatBuffer getModelViewMatrix();

    java.nio.FloatBuffer getProjectionMatrix();

    /** Leaves the current world or server (like Disconnect in the pause menu). */
    void leaveWorld();

    /** The Minecraft account the game is signed in as. */
    String getSessionName();

    /** Switches the game to another account (only while not in a world). */
    void setSession(String name, String uuid, String accessToken);

    /** Leaves the current world (if any) and connects to a multiplayer server. */
    void joinServer(String address);

    /** Resizes the game's framebuffer and GUI after the window changed size (borderless fullscreen). */
    void resizeGame(int width, int height);

    // ---- options ----

    int getMaxFps();

    void setMaxFps(int fps);

    /** 0 = all, 1 = decreased, 2 = minimal. */
    int getParticles();

    void setParticles(int level);

    boolean getEntityShadows();

    void setEntityShadows(boolean on);


    float getGamma();

    void setGamma(float gamma);

    float getSensitivity();

    void setSensitivity(float sensitivity);
}
