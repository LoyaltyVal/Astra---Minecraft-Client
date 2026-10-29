package dev.astra.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * MinecraftClient#onResolutionChanged (private), for resizing the window, and the session field
 * (final), for the in-game account switcher.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Invoker("onResolutionChanged")
    void astra$resize(int width, int height);

    @Mutable
    @Accessor("session")
    void astra$setSession(net.minecraft.client.util.Session session);
}
