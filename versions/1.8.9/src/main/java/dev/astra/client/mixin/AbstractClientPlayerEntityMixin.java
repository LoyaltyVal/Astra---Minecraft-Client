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

    /*
     * Careful: in Legacy Yarn the names are swapped. getCapeId() returns the SKIN and getSkinId()
     * returns the CAPE (the cape renderer calls getSkinId()). So the cape is replaced here.
     */
    @Inject(method = "getSkinId()Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
    private void astra$cape(CallbackInfoReturnable<Identifier> cir) {
        Object cape = astra$capeTexture();
        if (cape != null) {
            cir.setReturnValue((Identifier) cape);
        }
    }
}
