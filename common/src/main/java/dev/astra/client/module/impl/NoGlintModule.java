package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;

/** Hides the purple enchantment shimmer on items and armor (ItemStackMixin, armor mixins). */
public final class NoGlintModule extends Module {

    private final BooleanSetting items;
    private final BooleanSetting armor;

    public NoGlintModule() {
        super("No Enchant Glint", "Hide the enchantment shimmer.", Category.VISUAL, false);
        items = setting(new BooleanSetting("Items", true));
        armor = setting(new BooleanSetting("Armor", true));
    }

    public boolean hideItems() {
        return isEnabled() && items.isOn();
    }

    public boolean hideArmor() {
        return isEnabled() && armor.isOn();
    }

    @Override
    public String getPreviewText() {
        return "No glint";
    }
}
