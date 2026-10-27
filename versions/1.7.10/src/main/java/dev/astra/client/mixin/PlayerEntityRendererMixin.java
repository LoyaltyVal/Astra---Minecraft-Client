package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7.10 has no render layers, so wings are drawn at the end of method_1582
 * (MCP: RenderPlayer#renderEquippedItems), which runs in the player model's space like the cape.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "method_1582", at = @At("TAIL"))
    private void astra$wings(AbstractClientPlayerEntity player, float tickDelta, CallbackInfo ci) {
        if ((Object) player != MinecraftClient.getInstance().field_3805 || player.isInvisible()) {
            return;
        }
        Astra.get().getCosmetics().renderWings(player.ticksAlive + tickDelta, player.isSneaking());
    }
}
