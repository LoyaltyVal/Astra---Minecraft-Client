package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.NoGlintModule;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** No Enchant Glint (armor): the armor renderer draws the shimmer when the piece is enchanted. */
@Mixin(net.minecraft.client.render.entity.feature.ArmorFeatureRenderer.class)
public abstract class ArmorGlintMixin {

    @Redirect(method = {"renderFeature"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEnchantments()Z"))
    private boolean astra$armorGlint(ItemStack stack) {
        NoGlintModule m = Astra.get().getModules().get(NoGlintModule.class);
        return (m == null || !m.hideArmor()) && stack.hasEnchantments();
    }
}
