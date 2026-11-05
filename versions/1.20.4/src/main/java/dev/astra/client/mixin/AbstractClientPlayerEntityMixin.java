package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives every Astra player the cape they equipped (and the matching elytra). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void astra$cape(CallbackInfoReturnable<SkinTextures> cir) {
        Object cape = Astra.get().getCosmetics().getCapeTexture(((Entity) (Object) this).getUuid());
        SkinTextures skin = cir.getReturnValue();
        if (cape instanceof Identifier && skin != null) {
            Identifier id = (Identifier) cape;
            cir.setReturnValue(new SkinTextures(skin.texture(), skin.textureUrl(), id, id, skin.model(), skin.secure()));
        }
    }
}
