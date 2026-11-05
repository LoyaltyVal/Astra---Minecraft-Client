package dev.astra.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * What Astra draws with right now. Modern Minecraft passes a GuiGraphics into every render call
 * instead of using global GL state, so the HUD hook and AstraScreen put it here for the frame.
 * Since 1.21.6 the GUI is 2D only: the pose is a 3x2 matrix.
 */
public final class RenderCtx {

    private static GuiGraphics context;
    private static final Matrix3x2fStack FALLBACK = new Matrix3x2fStack(16);
    /** Astra's scissor areas (already in screen space), for its own shapes. */
    static final Deque<ScreenRectangle> SCISSORS = new ArrayDeque<ScreenRectangle>();
    static int scroll;
    /** Set while drawing in the world (wings, inside the player renderer): 3D matrices and where quads go. */
    static PoseStack world;
    static Function<ResourceLocation, VertexConsumer> worldBuffers;
    static int worldLight;
    /** The colour set with Platform.color(), multiplied into textured quads. */
    static float tintR = 1f, tintG = 1f, tintB = 1f, tintA = 1f;

    private RenderCtx() {
    }

    public static void begin(GuiGraphics ctx) {
        context = ctx;
        SCISSORS.clear();
    }

    public static void end() {
        context = null;
        SCISSORS.clear();
    }

    public static GuiGraphics context() {
        return context;
    }

    public static Matrix3x2fStack matrices() {
        return context != null ? context.pose() : FALLBACK;
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

    static ScreenRectangle scissor() {
        return SCISSORS.peek();
    }

    /** Scroll wheel movement from AstraScreen, in LWJGL 2 units (120 per notch). */
    public static void addScroll(double amount) {
        scroll += amount > 0 ? 120 : amount < 0 ? -120 : 0;
    }
}
