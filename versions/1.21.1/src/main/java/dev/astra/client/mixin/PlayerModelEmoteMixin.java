package dev.astra.client.mixin;

import dev.astra.client.social.Emotes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: after vanilla poses the player model, blend the arms and head towards the emote pose.
 * Targets the humanoid model so armor follows too; the player model copies the sleeves afterwards.
 */
@Mixin(HumanoidModel.class)
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

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void astra$emote(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        float[] p = Emotes.forPlayer(entity.getUUID());
        if (p == null) {
            return;
        }
        float w = p[9];
        apply(rightArm, p, 0, w);
        apply(leftArm, p, 3, w);
        apply(head, p, 6, w);
        hat.copyFrom(head);
    }

    private static void apply(ModelPart part, float[] p, int i, float w) {
        part.xRot = Emotes.blend(part.xRot, p[i], w);
        part.yRot = Emotes.blend(part.yRot, p[i + 1], w);
        part.zRot = Emotes.blend(part.zRot, p[i + 2], w);
    }
}
