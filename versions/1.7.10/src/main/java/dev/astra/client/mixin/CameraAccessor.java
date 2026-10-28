package dev.astra.client.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;

/**
 * Camera (MCP: ActiveRenderInfo) keeps the world's model-view and projection matrices from the
 * last frame; waypoints use them to place their markers on the HUD exactly.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Accessor("MODEL_MATRIX")
    static FloatBuffer astra$model() {
        throw new AssertionError();
    }

    @Accessor("PROJECTION_MATRIX")
    static FloatBuffer astra$projection() {
        throw new AssertionError();
    }
}
