package dev.astra.client.platform.legacy;

import dev.astra.client.Astra;
import dev.astra.client.platform.Platform;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

/**
 * The parts of {@link Platform} that are the same on every LWJGL 2 version of Minecraft
 * (1.7.10 to 1.12.2, and 1.13.2 on Legacy Fabric): fixed-function GL drawing, LWJGL 2 input and
 * the Display window. Each of those versions' PlatformImpl extends this and adds the game bits.
 *
 * GL state that Minecraft caches (colour, blend, cull, textures) always goes through the
 * Platform methods, never straight to GL, so the game's state cache stays correct.
 */
public abstract class LegacyPlatform implements Platform {

    // ------------------------------------------------------------------ matrices

    @Override
    public void pushMatrix() {
        GL11.glPushMatrix();
    }

    @Override
    public void popMatrix() {
        GL11.glPopMatrix();
    }

    @Override
    public void translate(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    @Override
    public void scale(float x, float y, float z) {
        GL11.glScalef(x, y, z);
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawTexture(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        bindTexture(handle);
        color(1f, 1f, 1f, 1f);
        blend(true);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    @Override
    public void fillTriangleStrip(float[] xy, int argb) {
        texture2d(false);
        blend(true);
        color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i + 1 < xy.length; i += 2) {
            GL11.glVertex2f(xy[i], xy[i + 1]);
        }
        GL11.glEnd();
        texture2d(true);
        color(1f, 1f, 1f, 1f);
    }

    @Override
    public void drawWorldQuad(Object handle, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        bindTexture(handle);
        // Smooth filtering: the art is drawn much smaller than the image, so nearest-pixel
        // sampling (Minecraft's default) makes jagged, shimmering edges.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        color(1f, 1f, 1f, 1f);
        blend(true);
        cull(false);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3f(0f, 0f, 1f);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(x0, y0, 0f);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(x0, y1, 0f);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(x1, y1, 0f);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(x1, y0, 0f);
        GL11.glEnd();
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f); // vanilla's value
        cull(true);
        blend(false);
    }

    @Override
    public void scissor(int x, int y, int w, int h) {
        int factor = Astra.get().getScaledResolution()[2];
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * factor, Display.getHeight() - (y + h) * factor,
                Math.max(0, w * factor), Math.max(0, h * factor));
    }

    @Override
    public void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean isKeyDown(int key) {
        return key > 0 && key < org.lwjgl.input.Keyboard.KEYBOARD_SIZE && org.lwjgl.input.Keyboard.isKeyDown(key);
    }

    @Override
    public String getKeyName(int key) {
        return org.lwjgl.input.Keyboard.getKeyName(key);
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        return button >= 0 && button < org.lwjgl.input.Mouse.getButtonCount() && org.lwjgl.input.Mouse.isButtonDown(button);
    }

    @Override
    public int getMouseButtonCount() {
        return org.lwjgl.input.Mouse.getButtonCount();
    }

    @Override
    public int consumeScroll() {
        return org.lwjgl.input.Mouse.getDWheel();
    }

    // ------------------------------------------------------------------ window

    @Override
    public int getWindowWidth() {
        return Display.getWidth();
    }

    @Override
    public int getWindowHeight() {
        return Display.getHeight();
    }

    @Override
    public boolean isWindowFocused() {
        return Display.isActive();
    }

    @Override
    public void setWindowTitle(String title) {
        Display.setTitle(title);
    }

    private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";
    private boolean borderless;
    private int savedX, savedY, savedW, savedH;

    @Override
    public boolean setBorderless(boolean on) {
        if (on == borderless) {
            return borderless;
        }
        try {
            if (on) {
                savedX = Display.getX();
                savedY = Display.getY();
                savedW = Display.getWidth();
                savedH = Display.getHeight();
                DisplayMode desktop = Display.getDesktopDisplayMode();
                System.setProperty(UNDECORATED, "true");
                Display.setFullscreen(false);
                Display.setResizable(false);
                // A new display mode makes LWJGL recreate the window, which picks up "undecorated".
                Display.setDisplayMode(new DisplayMode(desktop.getWidth(), desktop.getHeight()));
                Display.setLocation(0, 0);
                borderless = true;
                resizeGame(desktop.getWidth(), desktop.getHeight());
            } else {
                System.setProperty(UNDECORATED, "false");
                Display.setDisplayMode(new DisplayMode(Math.max(854, savedW), Math.max(480, savedH)));
                Display.setResizable(true);
                Display.setLocation(Math.max(0, savedX), Math.max(0, savedY));
                borderless = false;
                resizeGame(Display.getWidth(), Display.getHeight());
            }
        } catch (Throwable t) {
            System.err.println("[Astra] Borderless fullscreen failed: " + t);
            borderless = false;
        }
        return borderless;
    }
}
