package dev.astra.client.mixin.perf;

import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.Perf;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft asks the graphics driver for errors several times every frame; each ask makes the CPU
 * wait for the GPU to catch up. The answer is only ever logged, so Skip GL Error Checks skips it.
 */
@Mixin(MinecraftClient.class)
public abstract class GlErrorMixin {

    @Inject(method = "setGlErrorMessage", at = @At("HEAD"), cancellable = true)
    private void astra$skip(String location, CallbackInfo ci) {
        PerformanceModule pm = Perf.m();
        if (pm != null && pm.skipGlErrors()) {
            ci.cancel();
        }
    }
}
