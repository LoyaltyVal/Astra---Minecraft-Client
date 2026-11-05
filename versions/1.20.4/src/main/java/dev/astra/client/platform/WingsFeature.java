package dev.astra.client.platform;

import dev.astra.client.Astra;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Draws an Astra player's equipped wings (anyone's, as looked up on the Astra server). */
public final class WingsFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    public WingsFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        RenderCtx.beginWorld(matrices, buffers, light);
        try {
            Astra.get().getCosmetics().renderWings(player.getUuid(), age, player.isInSneakingPose());
        } finally {
            RenderCtx.endWorld();
        }
    }
}
