package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.NoGlintModule;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** No Enchant Glint (armor; 1.7.10 draws armor in the player and mob renderers): the armor renderer draws the shimmer when the piece is enchanted. */
@Mixin({net.minecraft.client.render.entity.PlayerEntityRenderer.class, net.minecraft.client.render.entity.BipedEntityRenderer.class})
public abstract class ArmorGlintMixin {

    @Redirect(method = {"method_5800", "method_1564"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEnchantments()Z"))
    private boolean astra$armorGlint(ItemStack stack) {
        NoGlintModule m = Astra.get().getModules().get(NoGlintModule.class);
        return (m == null || !m.hideArmor()) && stack.hasEnchantments();
    }
}
