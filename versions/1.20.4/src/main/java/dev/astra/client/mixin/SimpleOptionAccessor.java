package dev.astra.client.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an option past its slider's range (Fullbright's gamma). */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {

    @Accessor("value")
    void astra$setValue(Object value);
}
