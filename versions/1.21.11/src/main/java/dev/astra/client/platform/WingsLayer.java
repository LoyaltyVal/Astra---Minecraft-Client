package dev.astra.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.astra.client.Astra;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Draws an Astra player's equipped wings (anyone's, as looked up on the Astra server). Since
 * 1.21.9 drawing is queued: the wings are handed over as custom geometry and drawn later.
 */
public final class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public WingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        final UUID uuid = PlatformImpl.playerUuid(state.id);
        Object texture = uuid == null ? null : Astra.get().getCosmetics().wingsTexture(uuid);
        if (!(texture instanceof Identifier)) {
            return;
        }
        final float age = state.ageInTicks;
        final boolean crouching = state.isCrouching;
        final int packedLight = light;
        collector.submitCustomGeometry(matrices, RenderTypes.entityTranslucent((Identifier) texture), (pose, consumer) -> {
            PoseStack stack = new PoseStack();
            stack.last().set(pose);
            RenderCtx.beginWorld(stack, tex -> consumer, packedLight);
            try {
                Astra.get().getCosmetics().renderWings(uuid, age, crouching);
            } finally {
                RenderCtx.endWorld();
            }
        });
    }
}
