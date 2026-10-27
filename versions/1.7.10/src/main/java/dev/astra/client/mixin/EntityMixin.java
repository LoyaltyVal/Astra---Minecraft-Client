package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: while it's active, mouse movement turns the camera instead of your player. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    /** Entity#increaseTransforms (MCP: setAngles) is what the mouse calls to turn the player. */
    @Inject(method = "increaseTransforms", at = @At("HEAD"), cancellable = true)
    private void astra$freelook(float dx, float dy, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().field_3805) {
            return;
        }
        FreelookModule freelook = Astra.get().freelook();
        if (freelook != null && freelook.turn(dx, dy)) {
            ci.cancel();
        }
    }
}
