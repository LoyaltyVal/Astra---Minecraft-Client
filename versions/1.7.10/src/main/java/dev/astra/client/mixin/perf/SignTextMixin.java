package dev.astra.client.mixin.perf;

import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.Perf;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sign text past the Sign Text Distance isn't drawn (it's unreadably small; the sign still is). */
@Mixin(SignBlockEntityRenderer.class)
public abstract class SignTextMixin {

    @Unique
    private static boolean astra$far;

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDF)V", at = @At("HEAD"))
    private void astra$distance(SignBlockEntity sign, double x, double y, double z, float tickDelta, CallbackInfo ci) {
        PerformanceModule pm = Perf.m();
        astra$far = pm != null && x * x + y * y + z * z > pm.signTextDistanceSq();
    }

    @Redirect(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int astra$draw(TextRenderer font, String text, int x, int y, int color) {
        return astra$far ? 0 : font.draw(text, x, y, color);
    }
}
