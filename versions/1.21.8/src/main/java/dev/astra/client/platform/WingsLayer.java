package dev.astra.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.astra.client.Astra;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

import java.util.UUID;

/** Draws an Astra player's equipped wings (anyone's, as looked up on the Astra server). */
public final class WingsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    public WingsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        UUID uuid = PlatformImpl.playerUuid(state.id);
        if (uuid == null) {
            return;
        }
        RenderCtx.beginWorld(matrices, tex -> buffers.getBuffer(RenderType.entityTranslucent(tex)), light);
        try {
            Astra.get().getCosmetics().renderWings(uuid, state.ageInTicks, state.isCrouching);
        } finally {
            RenderCtx.endWorld();
        }
    }
}
