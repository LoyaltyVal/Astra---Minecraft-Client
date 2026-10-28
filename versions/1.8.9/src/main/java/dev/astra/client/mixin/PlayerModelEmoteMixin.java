package dev.astra.client.mixin;

import dev.astra.client.social.Emotes;
import net.minecraft.client.render.entity.model.BiPedModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: after vanilla poses the player model, blend the arms and head towards the emote pose.
 * Targets the biped model so armor follows too; 1.8.9's player model copies the sleeves afterwards.
 */
@Mixin(BiPedModel.class)
public abstract class PlayerModelEmoteMixin {

    @Shadow
    public ModelPart head;
    @Shadow
    public ModelPart hat;
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setAngles(FFFFFFLnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void astra$emote(float a, float b, float c, float d, float e, float f, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        float[] p = Emotes.forPlayer(entity.getUuid());
        if (p == null) {
            return;
        }
        float w = p[9];
        apply(rightArm, p, 0, w);
        apply(leftArm, p, 3, w);
        apply(head, p, 6, w);
        hat.posX = head.posX;
        hat.posY = head.posY;
        hat.posZ = head.posZ;
    }

    private static void apply(ModelPart part, float[] p, int i, float w) {
        part.posX = Emotes.blend(part.posX, p[i], w);
        part.posY = Emotes.blend(part.posY, p[i + 1], w);
        part.posZ = Emotes.blend(part.posZ, p[i + 2], w);
    }
}
