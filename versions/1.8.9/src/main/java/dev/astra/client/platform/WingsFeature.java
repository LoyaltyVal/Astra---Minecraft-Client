package dev.astra.client.platform;

import dev.astra.client.Astra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.feature.FeatureRenderer;

/** Draws equipped wings on your own player (third person and the inventory preview). */
public final class WingsFeature implements FeatureRenderer<AbstractClientPlayerEntity> {

    @Override
    public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                       float age, float headYaw, float headPitch, float scale) {
        if (player != MinecraftClient.getInstance().player || player.isInvisible()) {
            return;
        }
        Astra.get().getCosmetics().renderWings(age, player.isSneaking());
    }

    @Override
    public boolean combineTextures() {
        return false;
    }
}
