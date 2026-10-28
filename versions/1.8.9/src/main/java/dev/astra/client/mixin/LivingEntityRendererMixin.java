package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Your own name tag in third person / freelook (vanilla never draws it). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    /** hasLabel (MCP: RendererLivingEntity#canRenderName). */
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void astra$ownNametag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) entity == MinecraftClient.getInstance().player && !entity.isInvisible() && Astra.get().showOwnNametag()) {
            cir.setReturnValue(true);
        }
    }
}
