package dev.astra.client.mixin;

import dev.astra.client.module.impl.hud.ToggleSprintModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Toggle Sprint: while it's on, the player's movement code reads the Sprint key as held, so the
 * game's own rules decide when you sprint (moving forward fast enough, not using an item, not
 * blind, enough food, not against a wall). Nothing is forced, so servers see vanilla behaviour.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ToggleSprintMixin {

    @Redirect(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
    private boolean astra$sprintKeyHeld(KeyBinding key) {
        if (key.isPressed()) {
            return true;
        }
        return ToggleSprintModule.isHoldingSprint() && key == MinecraftClient.getInstance().options.sprintKey;
    }
}
