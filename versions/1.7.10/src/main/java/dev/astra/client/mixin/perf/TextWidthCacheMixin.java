package dev.astra.client.mixin.perf;

import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.Perf;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast Text: remembers how wide each string is. HUDs, chat and the tab list measure the same text
 * every frame, character by character; now that's done once. Forgotten when the font changes.
 */
@Mixin(TextRenderer.class)
public abstract class TextWidthCacheMixin {

    @Unique
    private final Map<String, Integer> astra$widths = new ConcurrentHashMap<String, Integer>();

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void astra$cached(String text, CallbackInfoReturnable<Integer> cir) {
        if (text != null && astra$on()) {
            Integer w = astra$widths.get(text);
            if (w != null) {
                cir.setReturnValue(w);
            }
        }
    }

    @Inject(method = "getStringWidth", at = @At("RETURN"))
    private void astra$remember(String text, CallbackInfoReturnable<Integer> cir) {
        if (text != null && astra$on()) {
            if (astra$widths.size() > 4096) {
                astra$widths.clear();
            }
            astra$widths.put(text, cir.getReturnValue());
        }
    }

    @Inject(method = {"reload", "setUnicode"}, at = @At("HEAD"))
    private void astra$forget(CallbackInfo ci) {
        astra$widths.clear();
    }

    @Unique
    private static boolean astra$on() {
        PerformanceModule pm = Perf.m();
        return pm != null && pm.fastText();
    }
}
