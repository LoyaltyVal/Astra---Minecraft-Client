package dev.astra.client.platform;

import dev.astra.client.Astra;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.feature.FeatureRenderer;

/** Draws an Astra player's equipped wings (anyone's, as looked up on the Astra server). */
public final class WingsFeature implements FeatureRenderer<AbstractClientPlayerEntity> {

    @Override
    public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                       float age, float headYaw, float headPitch, float scale) {
        if (player.isInvisible()) {
            return;
        }
        Astra.get().getCosmetics().renderWings(player.getUuid(), age, player.isSneaking());
    }

    @Override
    public boolean combineTextures() {
        return false;
    }
}
