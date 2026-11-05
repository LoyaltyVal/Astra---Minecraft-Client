package dev.astra.client.platform;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * What Astra draws with right now. Modern Minecraft passes a DrawContext into every render call
 * instead of using global GL state, so the HUD hook and AstraScreen put it here for the frame.
 */
public final class RenderCtx {

    private static DrawContext context;
    /** A MatrixStack for drawing outside the HUD/screens (e.g. in the world); null otherwise. */
    private static MatrixStack world;
    private static final MatrixStack FALLBACK = new MatrixStack();
    static int scroll;

    private RenderCtx() {
    }

    public static void begin(DrawContext ctx) {
        context = ctx;
    }

    public static void end() {
        if (context != null) {
            context.draw();
        }
        context = null;
    }

    public static void beginWorld(MatrixStack matrices) {
        world = matrices;
    }

    public static void endWorld() {
        world = null;
    }

    public static DrawContext context() {
        return context;
    }

    public static MatrixStack matrices() {
        if (world != null) {
            return world;
        }
        return context != null ? context.getMatrices() : FALLBACK;
    }

    /** Scroll wheel movement from AstraScreen, in LWJGL 2 units (120 per notch). */
    public static void addScroll(double amount) {
        scroll += amount > 0 ? 120 : amount < 0 ? -120 : 0;
    }
}
