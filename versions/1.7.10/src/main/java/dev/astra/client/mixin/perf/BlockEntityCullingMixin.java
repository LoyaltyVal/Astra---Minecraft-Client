package dev.astra.client.mixin.perf;

import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.Perf;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips chests, signs and skulls hidden behind walls or past the Block Entity Distance. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullingMixin {

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;F)V", at = @At("HEAD"), cancellable = true)
    private void astra$cull(BlockEntity be, float tickDelta, CallbackInfo ci) {
        PerformanceModule pm = Perf.m();
        if (pm == null) {
            return;
        }
        if (pm.entityCulling() && Perf.OCCLUSION.isHidden(be)) {
            ci.cancel();
            return;
        }
        double limit = pm.blockEntityDistanceSq();
        if (limit != Double.MAX_VALUE && be.getSquaredDistance(BlockEntityRenderDispatcher.CAMERA_X,
                BlockEntityRenderDispatcher.CAMERA_Y, BlockEntityRenderDispatcher.CAMERA_Z) > limit) {
            ci.cancel();
        }
    }
}
