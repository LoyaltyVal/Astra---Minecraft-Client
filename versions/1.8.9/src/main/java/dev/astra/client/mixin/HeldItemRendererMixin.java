package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.OldAnimationsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 first-person animations. HeldItemRenderer#renderArmHoldingItem (MCP: renderItemInFirstPerson)
 * calls applyEquipAndSwingOffset(equip, swing) once per use action; 1.8 passes 0 as the swing while
 * an item is in use, 1.7 passed the real swing progress. Call order: 0 = none, 1 = eat/drink,
 * 2 = block, 3 = bow, 4 = not using.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    private float astra$tickDelta;

    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"))
    private void astra$capture(float tickDelta, CallbackInfo ci) {
        astra$tickDelta = tickDelta;
    }

    private float astra$swing(float vanilla, boolean use) {
        if (!use || MinecraftClient.getInstance().player == null) {
            return vanilla;
        }
        return MinecraftClient.getInstance().player.getHandSwingProgress(astra$tickDelta);
    }

    private static OldAnimationsModule astra$anim() {
        return Astra.get().getModules().get(OldAnimationsModule.class);
    }

    @ModifyArg(method = "renderArmHoldingItem", index = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V"))
    private float astra$eatSwing(float swing) {
        OldAnimationsModule a = astra$anim();
        return astra$swing(swing, a != null && a.eatSwing());
    }

    @ModifyArg(method = "renderArmHoldingItem", index = 1, at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V"))
    private float astra$blockHit(float swing) {
        OldAnimationsModule a = astra$anim();
        return astra$swing(swing, a != null && a.blockHit());
    }

    @ModifyArg(method = "renderArmHoldingItem", index = 1, at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V"))
    private float astra$bowSwing(float swing) {
        OldAnimationsModule a = astra$anim();
        return astra$swing(swing, a != null && a.bowSwing());
    }
}
