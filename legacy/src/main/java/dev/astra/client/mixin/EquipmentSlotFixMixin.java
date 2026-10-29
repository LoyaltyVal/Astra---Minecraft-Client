package dev.astra.client.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Servers that also accept newer versions (ViaVersion and friends) sometimes send other players'
 * equipment for a slot old Minecraft doesn't have (the off-hand). Vanilla 1.7/1.8 then throws an
 * ArrayIndexOutOfBoundsException for every such packet ("Error executing task"). Players have
 * slots 0 (held item) to 4 (helmet); anything else is ignored, which is all vanilla could do.
 */
@Mixin(PlayerEntity.class)
public abstract class EquipmentSlotFixMixin {

    @Inject(method = "setArmorSlot", at = @At("HEAD"), cancellable = true)
    private void astra$ignoreUnknownSlot(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot < 0 || slot > 4) {
            ci.cancel();
        }
    }
}
