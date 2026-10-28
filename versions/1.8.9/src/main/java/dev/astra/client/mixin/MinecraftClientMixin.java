package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** MinecraftClient#tick (MCP: runTick) runs 20 times a second. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void astra$onTick(CallbackInfo ci) {
        Astra.get().onTick();
    }

    /** F11: borderless fullscreen instead of exclusive fullscreen while that module is on. */
    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void astra$borderless(CallbackInfo ci) {
        dev.astra.client.module.impl.BorderlessModule borderless =
                Astra.get().getModules().get(dev.astra.client.module.impl.BorderlessModule.class);
        if (borderless != null && (borderless.isEnabled() || borderless.isActive())) {
            borderless.toggle11();
            ci.cancel();
        }
    }
}
