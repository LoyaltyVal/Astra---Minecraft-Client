package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the Astra rank tag ("[Owner] ", "[Admin] ", ...) in front of ranked players' name tags.
 * The label is drawn again with the tag added, and the original call is cancelled; the tag itself
 * stops it from being added twice.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Shadow
    protected abstract void renderLabelIfPresent(Entity entity, String label, double x, double y, double z, int range);

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void astra$rankTag(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity) || label == null) {
            return;
        }
        String tag = Astra.get().getCosmetics().getRankTag(entity.getUuid());
        if (tag == null || label.startsWith(tag) || !label.contains(((PlayerEntity) entity).getGameProfile().getName())) {
            return; // not ranked, already tagged, or a different line (e.g. the score under the name)
        }
        ci.cancel();
        renderLabelIfPresent(entity, tag + label, x, y, z, range);
    }
}
