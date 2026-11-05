package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (the world FOV only, not your hand) and No Hurtcam. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void astra$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov) {
            cir.setReturnValue((double) Astra.get().modifyFov(cir.getReturnValue().floatValue()));
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void astra$noHurtcam(CallbackInfo ci) {
        dev.astra.client.module.impl.HurtcamModule m = Astra.get().getModules().get(dev.astra.client.module.impl.HurtcamModule.class);
        if (m != null && m.isEnabled()) {
            ci.cancel();
        }
    }
}
