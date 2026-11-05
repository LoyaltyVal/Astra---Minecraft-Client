package dev.astra.client.mixin;

import dev.astra.client.platform.PlatformImpl;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps this frame's camera matrices, so waypoints can be placed on screen. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void astra$matrices(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline,
                                Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap,
                                Matrix4f projection, CallbackInfo ci) {
        PlatformImpl.MODEL_VIEW.clear();
        matrices.peek().getPositionMatrix().get(PlatformImpl.MODEL_VIEW);
        PlatformImpl.PROJECTION.clear();
        projection.get(PlatformImpl.PROJECTION);
    }
}
