package dev.astra.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the account switcher change who you're playing as. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Mutable
    @Accessor("user")
    void astra$setUser(User user);
}
