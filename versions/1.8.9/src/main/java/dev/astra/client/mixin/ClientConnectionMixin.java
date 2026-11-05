package dev.astra.client.mixin;

import dev.astra.client.replay.Replay;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds every packet the client receives to the replay recorder (see {@link Replay}). */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Shadow
    @Final
    private NetworkSide side;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
    private void astra$record(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (side == NetworkSide.CLIENTBOUND) {
            Replay.get().onPacket(this, packet);
        }
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void astra$closed(ChannelHandlerContext ctx, CallbackInfo ci) {
        Replay.get().onConnectionClosed(this);
    }
}
