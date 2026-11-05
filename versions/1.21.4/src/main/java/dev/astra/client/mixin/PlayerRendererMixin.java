package dev.astra.client.mixin;

import dev.astra.client.platform.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the wings layer to every player renderer (it draws on any Astra player wearing wings). */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void astra$addWings(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) (Object) this).astra$addLayer(new WingsLayer((RenderLayerParent) (Object) this));
    }
}
