package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.ClientSettingsModule;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With potion effects active, vanilla moves the inventory to the right to fit the effect list.
 * "Center Inventory" keeps it in the middle (Astra's Potion Effects HUD shows the effects).
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen {

    @Shadow
    private boolean offsetGuiForEffects;

    protected InventoryScreenMixin(ScreenHandler handler) {
        super(handler);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void astra$centerOnOpen(CallbackInfo ci) {
        astra$center();
    }

    private void astra$center() {
        ClientSettingsModule tweaks = Astra.get().tweaks();
        if (tweaks != null && tweaks.centerInventory() && offsetGuiForEffects) {
            this.x = (this.width - this.backgroundWidth) / 2;
            offsetGuiForEffects = false;
        }
    }
}
