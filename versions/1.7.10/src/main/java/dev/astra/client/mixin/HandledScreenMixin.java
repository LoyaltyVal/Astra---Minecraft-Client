package dev.astra.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hotbar keys bound to mouse buttons (e.g. Mouse 4 = slot 1) work in inventories too. Vanilla
 * only checks hotbar keys on keyboard presses; a mouse button is code (button - 100).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected abstract boolean handleHotbarKeyPressed(int keyCode);

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void astra$mouseHotbar(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button > 1 && handleHotbarKeyPressed(button - 100)) {
            ci.cancel();
        }
    }
}
