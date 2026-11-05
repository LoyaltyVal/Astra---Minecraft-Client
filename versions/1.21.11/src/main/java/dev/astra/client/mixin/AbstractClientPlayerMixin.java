package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
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
        if (cape instanceof Identifier && skin != null) {
            Identifier id = (Identifier) cape;
            // The cape is a texture Astra registered itself, so its id is also its texture path.
            ClientAsset.ResourceTexture texture = new ClientAsset.ResourceTexture(id, id);
            cir.setReturnValue(new PlayerSkin(skin.body(), texture, texture, skin.model(), skin.secure()));
        }
    }
}
