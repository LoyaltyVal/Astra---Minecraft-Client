package dev.astra.client.mixin.perf;

import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.Perf;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips mobs and items hidden behind walls or past the Entity Distance (players always draw). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityCullingMixin {

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;F)Z", at = @At("HEAD"), cancellable = true)
    private void astra$cull(Entity entity, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        PerformanceModule pm = Perf.m();
        if (pm == null || entity instanceof PlayerEntity) {
            return;
        }
        if (pm.entityCulling() && Perf.OCCLUSION.isHidden(entity)) {
            cir.setReturnValue(false);
            return;
        }
        double limit = pm.entityDistanceSq();
        if (limit != Double.MAX_VALUE) {
            EntityRenderDispatcher self = (EntityRenderDispatcher) (Object) this;
            double dx = entity.x - self.cameraX, dy = entity.y - self.cameraY, dz = entity.z - self.cameraZ;
            if (dx * dx + dy * dy + dz * dz > limit && !Perf.named(entity)) {
                cir.setReturnValue(false);
            }
        }
    }
}
