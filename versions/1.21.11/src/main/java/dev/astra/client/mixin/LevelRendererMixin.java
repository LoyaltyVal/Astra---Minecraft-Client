package dev.astra.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.astra.client.platform.PlatformImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps this frame's camera matrices, so waypoints can be placed on screen. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void astra$matrices(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean renderBlockOutline, Camera camera,
                                Matrix4f modelView, Matrix4f projection, Matrix4f cullingProjection, GpuBufferSlice fog, Vector4f fogColor, boolean sky,
                                CallbackInfo ci) {
        PlatformImpl.MODEL_VIEW.clear();
        modelView.get(PlatformImpl.MODEL_VIEW);
        PlatformImpl.PROJECTION.clear();
        projection.get(PlatformImpl.PROJECTION);
    }
}
