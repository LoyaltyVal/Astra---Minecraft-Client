package dev.astra.client.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

/**
 * Astra's own GUI shapes on 1.21.6+, where GUI drawing is queued as render states instead of
 * drawn straight away. Holds quads in float coordinates (x, y, u, v per corner), so smooth
 * animations and rounded shapes keep working.
 */
public final class AstraGuiElement implements GuiElementRenderState {

    private final RenderPipeline pipeline;
    private final TextureSetup textures;
    private final Matrix3x2f pose;
    /** Four corners per quad: x, y, u, v. */
    private final float[] quads;
    private final int color;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;

    public AstraGuiElement(RenderPipeline pipeline, TextureSetup textures, Matrix3x2f pose, float[] quads, int color,
                           ScreenRectangle scissor) {
        this.pipeline = pipeline;
        this.textures = textures;
        this.pose = pose;
        this.quads = quads;
        this.color = color;
        this.scissor = scissor;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i + 3 < quads.length; i += 4) {
            minX = Math.min(minX, quads[i]);
            maxX = Math.max(maxX, quads[i]);
            minY = Math.min(minY, quads[i + 1]);
            maxY = Math.max(maxY, quads[i + 1]);
        }
        int x0 = (int) Math.floor(minX);
        int y0 = (int) Math.floor(minY);
        ScreenRectangle box = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0))
                .transformMaxBounds(pose);
        this.bounds = scissor != null ? scissor.intersection(box) : box;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        boolean textured = textures != TextureSetup.noTexture();
        for (int i = 0; i + 3 < quads.length; i += 4) {
            VertexConsumer v = consumer.addVertexWith2DPose(pose, quads[i], quads[i + 1]);
            if (textured) {
                v.setUv(quads[i + 2], quads[i + 3]);
            }
            v.setColor(color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textures;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
