package dev.astra.client.module.impl.hud;

import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.ModeSetting;

/** How many splash healing potions are in your inventory. */
public final class PotCounterModule extends TextHudModule {

    private final ModeSetting count;

    public PotCounterModule() {
        super("Pot Counter", "Counts your splash healing potions.", false, 4, 158);
        count = setting(new ModeSetting("Count", "Splash Healing", "Splash Healing", "All Healing", "All Potions"));
    }

    @Override
    protected String getText() {
        if (!mc().isInGame()) {
            return null;
        }
        int kind = count.is("All Potions") ? 2 : count.is("All Healing") ? 1 : 0;
        return "Pots: " + mc().countPotions(kind);
    }

    @Override
    protected String getPlaceholder() {
        return "Pots: 16";
    }
}
