package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives every Astra player the cape they equipped (and the matching elytra). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void astra$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        Object cape = Astra.get().getCosmetics().getCapeTexture(((Entity) (Object) this).getUUID());
        PlayerSkin skin = cir.getReturnValue();
        if (cape instanceof ResourceLocation && skin != null) {
            ResourceLocation id = (ResourceLocation) cape;
            cir.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(), id, id, skin.model(), skin.secure()));
        }
    }
}
