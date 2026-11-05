package dev.astra.client.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The key a key binding is set to (for Toggle Sprint and Keystrokes). */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {

    @Accessor("boundKey")
    InputUtil.Key astra$boundKey();
}
