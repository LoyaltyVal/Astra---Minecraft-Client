package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives your own player the cape you equipped in Astra. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    private boolean astra$isLocal() {
        return (Object) this == MinecraftClient.getInstance().field_3805;
    }

    @Inject(method = "getCapeId", at = @At("HEAD"), cancellable = true)
    private void astra$cape(CallbackInfoReturnable<Identifier> cir) {
        if (astra$isLocal()) {
            Object cape = Astra.get().getCosmetics().getCapeTexture();
            if (cape != null) {
                cir.setReturnValue((Identifier) cape);
            }
        }
    }

    /** 1.7.10 only draws a cape when this says the player has one. */
    @Inject(method = "canRenderCapeTexture", at = @At("HEAD"), cancellable = true)
    private void astra$hasCape(CallbackInfoReturnable<Boolean> cir) {
        if (astra$isLocal() && Astra.get().getCosmetics().getCapeTexture() != null) {
            cir.setReturnValue(true);
        }
    }
}
