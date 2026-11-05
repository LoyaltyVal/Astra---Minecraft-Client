package dev.astra.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an option past its slider's range (Fullbright's gamma). */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {

    @Accessor("value")
    void astra$setValue(Object value);
}
