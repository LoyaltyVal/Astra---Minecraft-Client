package dev.astra.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Many servers spawn NPC "players" (lobby NPCs, holograms) whose tab-list entry is already gone.
 * Vanilla 1.8.9 then throws a NullPointerException for every one ("Error executing task"), which
 * floods the log. Skip them quietly instead; vanilla couldn't show them either.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class NpcSpawnFixMixin {

    @Shadow
    public abstract PlayerListEntry getPlayerListEntry(UUID uuid);

    @Inject(method = "onPlayerSpawn", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/ThreadExecutor;)V"))
    private void astra$skipNpcWithoutTabEntry(PlayerSpawnS2CPacket packet, CallbackInfo ci) {
        if (getPlayerListEntry(packet.getPlayerUuid()) == null) {
            ci.cancel();
        }
    }
}
