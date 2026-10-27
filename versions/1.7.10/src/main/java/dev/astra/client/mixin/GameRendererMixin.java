package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * GameRenderer#getFov(float tickDelta, boolean changingFov) (MCP: EntityRenderer#getFOVModifier).
     * Only the world FOV is changed (changingFov = true), so your hand doesn't zoom.
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void astra$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) {
            cir.setReturnValue(Astra.get().modifyFov(cir.getReturnValue()));
        }
    }

    // ---- freelook: place the camera using the freelook angles, then put the player's back ----

    private Entity astra$swapped;
    private float astra$yaw;
    private float astra$pitch;
    private float astra$prevYaw;
    private float astra$prevPitch;

    /** GameRenderer#transformCamera (MCP: orientCamera) positions the camera from the player's rotation. */
    @Inject(method = "transformCamera", at = @At("HEAD"))
    private void astra$freelookStart(float tickDelta, CallbackInfo ci) {
        FreelookModule freelook = Astra.get().freelook();
        Entity player = MinecraftClient.getInstance().field_3805;
        if (freelook == null || player == null) {
            return;
        }
        astra$swapped = player;
        astra$yaw = player.yaw;
        astra$pitch = player.pitch;
        astra$prevYaw = player.prevYaw;
        astra$prevPitch = player.prevPitch;
        player.yaw = player.prevYaw = freelook.getYaw();
        player.pitch = player.prevPitch = freelook.getPitch();
    }

    @Inject(method = "transformCamera", at = @At("RETURN"))
    private void astra$freelookEnd(float tickDelta, CallbackInfo ci) {
        Entity player = astra$swapped;
        if (player == null) {
            return;
        }
        player.yaw = astra$yaw;
        player.pitch = astra$pitch;
        player.prevYaw = astra$prevYaw;
        player.prevPitch = astra$prevPitch;
        astra$swapped = null;
    }
}
