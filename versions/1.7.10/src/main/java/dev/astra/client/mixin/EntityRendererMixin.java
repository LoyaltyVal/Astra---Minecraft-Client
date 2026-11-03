package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.ui.RankIcon;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ranked Astra players get their rank star (tinted in the rank's colour) in front of their name
 * tag. The label is drawn again with a little space in front of the name, and the star is drawn
 * into that space in both of vanilla's passes: faintly through walls, then normally, exactly
 * like the name text itself.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Shadow
    protected abstract void renderLabelIfPresent(Entity entity, String label, double x, double y, double z, int range);

    @Shadow
    public abstract TextRenderer getFontRenderer();

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void astra$makeRoom(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity) || label == null || label.startsWith(RankIcon.NAME_PAD)) {
            return;
        }
        if (!label.contains(((PlayerEntity) entity).getGameProfile().getName())) {
            return; // a different line (e.g. the score under the name)
        }
        if (Astra.get().getCosmetics().getRankColor(entity.getUuid()) == null) {
            return;
        }
        ci.cancel();
        renderLabelIfPresent(entity, RankIcon.NAME_PAD + label, x, y, z, range);
    }

    /** The see-through pass (vanilla draws the name at 1/8 opacity through walls). */
    @Inject(method = "renderLabelIfPresent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 0))
    private void astra$starThroughWalls(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        astra$star(entity, label, 0x20);
    }

    /** The normal pass. */
    @Inject(method = "renderLabelIfPresent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 1))
    private void astra$starNormal(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        astra$star(entity, label, 0xFF);
    }

    private void astra$star(Entity entity, String label, int alpha) {
        if (label == null || !label.startsWith(RankIcon.NAME_PAD)) {
            return;
        }
        Integer color = Astra.get().getCosmetics().getRankColor(entity.getUuid());
        if (color == null) {
            return;
        }
        // The label is centred: it starts at -width / 2, which is where the padding (and the star) goes.
        float left = -getFontRenderer().getStringWidth(label) / 2f;
        RankIcon.draw(left, -0.5f, 9f, (alpha << 24) | (color & 0xFFFFFF));
    }
}
