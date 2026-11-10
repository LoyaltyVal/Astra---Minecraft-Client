package dev.astra.client.mixin.perf;

import dev.astra.client.platform.Perf;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles that are off screen aren't built (vanilla builds every particle, even behind you). */
@Mixin(ParticleManager.class)
public abstract class ParticleCullingMixin {

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void astra$begin(Entity camera, float tickDelta, CallbackInfo ci) {
        Perf.beginParticles();
    }

    @Redirect(method = "renderParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;draw(Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void astra$draw(Particle particle, BufferBuilder buffer, Entity camera, float tickDelta,
                            float a, float b, float c, float d, float e) {
        if (Perf.particleVisible(particle)) {
            particle.draw(buffer, camera, tickDelta, a, b, c, d, e);
        }
    }
}
