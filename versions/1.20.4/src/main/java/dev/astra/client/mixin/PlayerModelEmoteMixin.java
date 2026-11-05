package dev.astra.client.mixin;

import dev.astra.client.social.Emotes;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: after vanilla poses the player model, blend the arms and head towards the emote pose.
 * Targets the biped model so armor follows too; the player model copies the sleeves afterwards.
 */
@Mixin(BipedEntityModel.class)
public abstract class PlayerModelEmoteMixin {

    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart hat;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void astra$emote(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
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
        hat.copyTransform(head);
    }

    private static void apply(ModelPart part, float[] p, int i, float w) {
        part.pitch = Emotes.blend(part.pitch, p[i], w);
        part.yaw = Emotes.blend(part.yaw, p[i + 1], w);
        part.roll = Emotes.blend(part.roll, p[i + 2], w);
    }
}
