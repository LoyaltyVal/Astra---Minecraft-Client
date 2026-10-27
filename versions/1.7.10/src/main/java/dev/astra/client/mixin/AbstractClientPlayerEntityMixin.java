package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives every Astra player the cape they equipped (looked up on the Astra server by UUID). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    private Object astra$capeTexture() {
        return Astra.get().getCosmetics().getCapeTexture(((Entity) (Object) this).getUuid());
    }

    @Inject(method = "getCapeId", at = @At("HEAD"), cancellable = true)
    private void astra$cape(CallbackInfoReturnable<Identifier> cir) {
        Object cape = astra$capeTexture();
        if (cape != null) {
            cir.setReturnValue((Identifier) cape);
        }
    }

    /** 1.7.10 only draws a cape when this says the player has one. */
    @Inject(method = "canRenderCapeTexture", at = @At("HEAD"), cancellable = true)
    private void astra$hasCape(CallbackInfoReturnable<Boolean> cir) {
        if (astra$capeTexture() != null) {
            cir.setReturnValue(true);
        }
    }
}
