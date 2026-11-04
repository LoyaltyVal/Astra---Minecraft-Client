package dev.astra.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * Servers (Minemen, Hypixel lobbies, ...) constantly send scoreboard packets that don't match
 * what the game has: they update or remove teams and sidebar objectives that were never created,
 * create ones that already exist, or take players off teams they aren't on. Vanilla 1.8.9 throws
 * for every one of those ("Error executing task", hundreds per session). Apply what can be
 * applied and skip the rest, which is all vanilla could have done anyway.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class TeamPacketFixMixin {


    @Shadow
    private ClientWorld world;

    @Inject(method = "onTeam", cancellable = true, at = @At("HEAD"))
    private void astra$safeTeam(TeamS2CPacket packet, CallbackInfo ci) {
        if (world == null) {
            ci.cancel();
            return;
        }
        Scoreboard scoreboard = world.getScoreboard();
        Team team = scoreboard.getTeam(packet.getTeamName());
        int mode = packet.getMode();
        // Mode 0 creates the team; every other mode needs it to exist already.
        if (mode == 0 ? team != null : team == null) {
            ci.cancel();
            return;
        }
        if (mode == 4) {
            // Remove players: only the ones actually on this team (vanilla throws on the first stranger).
            for (Object o : new ArrayList<Object>(packet.getPlayerList())) {
                String name = String.valueOf(o);
                if (team.getPlayerList().contains(name)) {
                    scoreboard.removePlayerFromTeam(name, team);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "onScoreboardObjectiveUpdate", cancellable = true,
            at = @At("HEAD"))
    private void astra$safeObjective(ScoreboardObjectiveUpdateS2CPacket packet, CallbackInfo ci) {
        if (world == null) {
            ci.cancel();
            return;
        }
        boolean exists = world.getScoreboard().getNullableObjective(packet.getName()) != null;
        // Mode 0 adds the objective; 1 removes it and 2 updates it, which need it to exist.
        if (packet.getMode() == 0 ? exists : !exists) {
            ci.cancel();
        }
    }
}
