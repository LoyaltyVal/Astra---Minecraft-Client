package dev.astra.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Astra add its wings layer to the player renderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    @SuppressWarnings("rawtypes")
    @Invoker("addLayer")
    boolean astra$addLayer(RenderLayer layer);
}
