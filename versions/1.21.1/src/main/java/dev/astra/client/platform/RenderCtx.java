package dev.astra.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * What Astra draws with right now. Modern Minecraft passes a GuiGraphics into every render call
 * instead of using global GL state, so the HUD hook and AstraScreen put it here for the frame.
 */
public final class RenderCtx {

    private static GuiGraphics context;
    /** A PoseStack for drawing outside the HUD/screens (e.g. in the world); null otherwise. */
    static PoseStack world;
    /** Where in-world quads (wings) go while {@link #world} is set: the vertex buffer for a texture. */
    static Function<ResourceLocation, VertexConsumer> worldBuffers;
    static int worldLight;
    private static final PoseStack FALLBACK = new PoseStack();
    static int scroll;

    private RenderCtx() {
    }

    public static void begin(GuiGraphics ctx) {
        context = ctx;
    }

    public static void end() {
        if (context != null) {
            context.flush();
        }
        context = null;
    }

    public static void beginWorld(PoseStack matrices, Function<ResourceLocation, VertexConsumer> buffers, int light) {
        world = matrices;
        worldBuffers = buffers;
        worldLight = light;
    }

    public static void endWorld() {
        world = null;
        worldBuffers = null;
    }

    public static GuiGraphics context() {
        return context;
    }

    public static PoseStack matrices() {
        if (world != null) {
            return world;
        }
        return context != null ? context.pose() : FALLBACK;
    }

    /** Scroll wheel movement from AstraScreen, in LWJGL 2 units (120 per notch). */
    public static void addScroll(double amount) {
        scroll += amount > 0 ? 120 : amount < 0 ? -120 : 0;
    }
}
