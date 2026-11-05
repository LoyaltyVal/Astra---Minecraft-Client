package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Astra's tick, at the start of every client tick. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void astra$onTick(CallbackInfo ci) {
        Astra.get().onTick();
    }
}
