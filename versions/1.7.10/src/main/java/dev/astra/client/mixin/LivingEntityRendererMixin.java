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

    /** method_5781 (MCP: RendererLivingEntity#canRenderName). */
    @Inject(method = "method_5781", at = @At("HEAD"), cancellable = true)
    private void astra$ownNametag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) entity == MinecraftClient.getInstance().field_3805 && !entity.isInvisible() && Astra.get().showOwnNametag()) {
            cir.setReturnValue(true);
        }
    }
}
