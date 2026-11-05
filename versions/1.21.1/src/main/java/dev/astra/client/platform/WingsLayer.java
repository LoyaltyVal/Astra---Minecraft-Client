package dev.astra.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.astra.client.Astra;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Draws an Astra player's equipped wings (anyone's, as looked up on the Astra server). */
public final class WingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        RenderCtx.beginWorld(matrices, tex -> buffers.getBuffer(RenderType.entityTranslucent(tex)), light);
        try {
            Astra.get().getCosmetics().renderWings(player.getUUID(), ageInTicks, player.isCrouching());
        } finally {
            RenderCtx.endWorld();
        }
    }
}
