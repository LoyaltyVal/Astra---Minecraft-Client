package dev.astra.client.mixin;

import dev.astra.client.platform.PlatformImpl;
import dev.astra.client.social.Emotes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: after vanilla poses the player model, blend the arms and head towards the emote pose.
 * Targets the humanoid model so armor follows too. The hat and sleeves are children of the head
 * and arms here, so they follow on their own.
 */
@Mixin(HumanoidModel.class)
public abstract class PlayerModelEmoteMixin {

    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void astra$emote(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState)) {
            return;
        }
        float[] p = Emotes.forPlayer(PlatformImpl.playerUuid(((AvatarRenderState) state).id));
        if (p == null) {
            return;
        }
        float w = p[9];
        apply(rightArm, p, 0, w);
        apply(leftArm, p, 3, w);
        apply(head, p, 6, w);
    }

    private static void apply(ModelPart part, float[] p, int i, float w) {
        part.xRot = Emotes.blend(part.xRot, p[i], w);
        part.yRot = Emotes.blend(part.yRot, p[i + 1], w);
        part.zRot = Emotes.blend(part.zRot, p[i + 2], w);
    }
}
