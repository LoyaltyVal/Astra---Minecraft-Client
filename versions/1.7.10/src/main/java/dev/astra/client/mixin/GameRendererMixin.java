package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * GameRenderer#getFov(float tickDelta, boolean changingFov) (MCP: EntityRenderer#getFOVModifier).
     * Only the world FOV is changed (changingFov = true), so your hand doesn't zoom.
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void astra$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) {
            cir.setReturnValue(Astra.get().modifyFov(cir.getReturnValue()));
        }
    }
}
