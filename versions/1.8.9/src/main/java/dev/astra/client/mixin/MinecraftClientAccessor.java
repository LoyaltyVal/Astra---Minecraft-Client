package dev.astra.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Astra call MinecraftClient#onResolutionChanged (private) after resizing the window. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Invoker("onResolutionChanged")
    void astra$resize(int width, int height);
}
