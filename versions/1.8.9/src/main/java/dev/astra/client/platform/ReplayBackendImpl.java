package dev.astra.client.platform;

import com.mojang.authlib.GameProfile;
import dev.astra.client.replay.Replay;
import dev.astra.client.replay.ReplayBackend;
import dev.astra.client.replay.ReplayInfo;
import dev.astra.client.replay.SelfFrame;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.CombatEventS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.ConfirmGuiActionS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.HeldItemChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.network.packet.s2c.play.StatsUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.util.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Replays on 1.8.9.
 *
 * Recording: every clientbound play packet is written as (id, bytes) by ClientConnectionMixin;
 * your own movement and equipment are sampled every tick (the server never sends them to you).
 *
 * Playback: a ClientConnection on an in-memory netty channel with a normal ClientPlayNetworkHandler,
 * fed the recorded packets on the game thread. The Join Game packet is rewritten so you join as a
 * spectator with a different entity id; the recording player becomes an ordinary player entity
 * moved by the recorded frames. Packets that would act on "you" (health, inventories, kicks,
 * teleports after the first) are skipped.
 */
public final class ReplayBackendImpl implements ReplayBackend, Replay.ServerName {

    /** The camera's entity id (the recorder's id goes to the player entity showing them). */
    private static final int CAMERA_ID = -1337;

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // ================================================================== recording

    private boolean lastSwinging;
    private int lastSwingTicks;

    @Override
    public int packetId(Object packet) {
        if (!(packet instanceof Packet)) {
            return -1;
        }
        Integer id = NetworkState.PLAY.getRawId(NetworkSide.CLIENTBOUND, (Packet<?>) packet);
        return id == null ? -1 : id;
    }

    @Override
    public boolean isJoinPacket(Object packet) {
        return packet instanceof GameJoinS2CPacket;
    }

    @Override
    public byte[] writePacket(Object packet) throws Exception {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            ((Packet<?>) packet).write(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    @Override
    public String currentServer() {
        MinecraftClient mc = mc();
        if (mc.isIntegratedServerRunning()) {
            return "singleplayer";
        }
        net.minecraft.client.network.ServerInfo entry = mc.getCurrentServerEntry();
        return entry != null && entry.address != null ? entry.address : null;
    }

    @Override
    public String sessionUuid() {
        GameProfile p = mc().getSession().getProfile();
        return p == null || p.getId() == null ? "" : p.getId().toString().replace("-", "");
    }

    @Override
    public SelfFrame captureSelf() {
        ClientPlayerEntity p = mc().player;
        if (p == null || mc().world == null) {
            return null;
        }
        boolean swing = p.handSwinging && (!lastSwinging || p.handSwingTicks < lastSwingTicks);
        lastSwinging = p.handSwinging;
        lastSwingTicks = p.handSwingTicks;
        int flags = (p.onGround ? SelfFrame.ON_GROUND : 0)
                | (p.isSneaking() ? SelfFrame.SNEAKING : 0)
                | (p.isSprinting() ? SelfFrame.SPRINTING : 0)
                | (swing ? SelfFrame.SWING : 0)
                | (p.isInvisible() ? SelfFrame.INVISIBLE : 0);
        return new SelfFrame(p.x, p.y, p.z, p.yaw, p.pitch, p.headYaw, flags);
    }

    @Override
    public byte[][] captureEquipment() {
        ClientPlayerEntity p = mc().player;
        if (p == null) {
            return null;
        }
        byte[][] out = new byte[5][];
        out[0] = stackBytes(p.getMainHandStack());
        for (int i = 0; i < 4; i++) {
            out[i + 1] = stackBytes(p.getArmorSlot(i));
        }
        return out;
    }

    private static byte[] stackBytes(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeItemStack(stack);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    // ================================================================== playback

    private EmbeddedChannel channel;
    private ClientConnection connection;
    private ClientPlayNetworkHandler handler;
    private ReplayInfo info;
    private int recordedId = Integer.MIN_VALUE;
    private boolean positioned;

    @Override
    public void open(ReplayInfo info) throws Exception {
        MinecraftClient mc = mc();
        this.info = info;
        connection = new ClientConnection(NetworkSide.CLIENTBOUND);
        channel = new EmbeddedChannel(connection);
        connection.setState(NetworkState.PLAY);
        handler = new ClientPlayNetworkHandler(mc, new TitleScreen(), connection, mc.getSession().getProfile());
        connection.setPacketListener(handler);
        recordedId = Integer.MIN_VALUE;
        positioned = false;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void apply(int id, byte[] data, boolean fast) throws Exception {
        if (handler == null) {
            return;
        }
        Packet packet = NetworkState.PLAY.createPacket(NetworkSide.CLIENTBOUND, id);
        if (packet == null || skip(packet, data, fast)) {
            return;
        }
        byte[] bytes = data;
        boolean join = packet instanceof GameJoinS2CPacket;
        boolean respawn = packet instanceof PlayerRespawnS2CPacket;
        if (join && data.length >= 5) {
            // entity id (int), then game mode (byte): join as a spectator with our own id.
            bytes = data.clone();
            recordedId = readInt(bytes, 0);
            writeInt(bytes, 0, CAMERA_ID);
            bytes[4] = 3;
            positioned = false;
        } else if (respawn && data.length >= 6) {
            // dimension (int), difficulty (byte), game mode (byte).
            bytes = data.clone();
            bytes[5] = 3;
            positioned = false;
        } else if (packet instanceof ExplosionS2CPacket && data.length >= 12) {
            // The last three floats push "you"; the camera shouldn't be blown around.
            bytes = data.clone();
            java.util.Arrays.fill(bytes, bytes.length - 12, bytes.length, (byte) 0);
        } else if (packet instanceof PlayerPositionLookS2CPacket) {
            if (positioned) {
                return; // only the first one, to put the camera where the recording starts
            }
            positioned = true;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        packet.read(buf);
        packet.apply(handler);
        if (join || respawn) {
            spawnRecorder();
        }
    }

    /** Packets that would act on the camera as if it were the recording player. */
    private static boolean skip(Packet<?> p, byte[] data, boolean fast) {
        if (p instanceof KeepAliveS2CPacket || p instanceof DisconnectS2CPacket || p instanceof PlayerAbilitiesS2CPacket
                || p instanceof HealthUpdateS2CPacket || p instanceof ExperienceBarUpdateS2CPacket
                || p instanceof CombatEventS2CPacket || p instanceof OpenScreenS2CPacket || p instanceof CloseScreenS2CPacket
                || p instanceof InventoryS2CPacket || p instanceof ScreenHandlerSlotUpdateS2CPacket
                || p instanceof ScreenHandlerPropertyUpdateS2CPacket || p instanceof ConfirmGuiActionS2CPacket
                || p instanceof SetCameraEntityS2CPacket || p instanceof ResourcePackSendS2CPacket
                || p instanceof CustomPayloadS2CPacket || p instanceof SignEditorOpenS2CPacket
                || p instanceof HeldItemChangeS2CPacket || p instanceof StatsUpdateS2CPacket
                || p instanceof CommandSuggestionsS2CPacket) {
            return true;
        }
        // Game state 3 = game mode change, 4 = end credits.
        if (p instanceof GameStateChangeS2CPacket && data.length > 0 && (data[0] == 3 || data[0] == 4)) {
            return true;
        }
        // While jumping along the timeline, skip what only matters live.
        return fast && (p instanceof PlaySoundIdS2CPacket || p instanceof ParticleS2CPacket
                || p instanceof WorldEventS2CPacket || p instanceof ExplosionS2CPacket
                || p instanceof ChatMessageS2CPacket || p instanceof TitleS2CPacket);
    }

    /** Adds the recording player to the (new) world as a normal player. */
    private void spawnRecorder() {
        MinecraftClient mc = mc();
        if (mc.world == null || recordedId == Integer.MIN_VALUE) {
            return;
        }
        UUID uuid = parseUuid(info == null ? null : info.playerUuid);
        String name = info == null || info.playerName.isEmpty() ? "Recorder" : info.playerName;
        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Entity old = mc.world.getEntityById(recordedId);
        if (old != null) {
            mc.world.removeEntity(recordedId);
        }
        OtherClientPlayerEntity fake = new OtherClientPlayerEntity(mc.world, new GameProfile(uuid, name));
        if (mc.player != null) {
            fake.updatePositionAndAngles(mc.player.x, mc.player.y, mc.player.z, mc.player.yaw, mc.player.pitch);
        }
        mc.world.addEntity(recordedId, fake);
    }

    private OtherClientPlayerEntity recorder() {
        MinecraftClient mc = mc();
        if (mc.world == null || recordedId == Integer.MIN_VALUE) {
            return null;
        }
        Entity e = mc.world.getEntityById(recordedId);
        if (e == null && mc.player != null) {
            spawnRecorder();
            e = mc.world.getEntityById(recordedId);
        }
        return e instanceof OtherClientPlayerEntity ? (OtherClientPlayerEntity) e : null;
    }

    @Override
    public void applySelf(SelfFrame f, boolean fast) {
        OtherClientPlayerEntity p = recorder();
        if (p == null) {
            return;
        }
        if (fast) {
            p.updatePositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch);
        }
        p.updateTrackedPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch, 3, false);
        p.setHeadYaw(f.headYaw);
        p.onGround = f.has(SelfFrame.ON_GROUND);
        p.setSneaking(f.has(SelfFrame.SNEAKING));
        p.setSprinting(f.has(SelfFrame.SPRINTING));
        p.setInvisible(f.has(SelfFrame.INVISIBLE));
        if (f.has(SelfFrame.SWING) && !fast) {
            p.swingHand();
        }
    }

    @Override
    public void applyEquipment(int slot, byte[] data) throws Exception {
        OtherClientPlayerEntity p = recorder();
        if (p == null || slot < 0 || slot > 4) {
            return;
        }
        ItemStack stack = null;
        if (data != null && data.length > 0) {
            stack = new PacketByteBuf(Unpooled.wrappedBuffer(data)).readItemStack();
        }
        p.setArmorSlot(slot, stack);
    }

    @Override
    public void tick() {
        if (channel != null) {
            // Throw away what the camera "sends" (movement, settings): nobody is listening.
            while (channel.readOutbound() != null) {
                // drained
            }
        }
        MinecraftClient mc = mc();
        Entity cam = mc.getCameraEntity();
        if (cam != null && mc.player != null && cam != mc.player && (cam.removed || cam.world != mc.world)) {
            mc.setCameraEntity(mc.player); // the watched player left (or the world changed)
        }
    }

    @Override
    public void close() {
        MinecraftClient mc = mc();
        if (mc.world != null) {
            mc.world.disconnect();
            mc.connect((net.minecraft.client.world.ClientWorld) null);
        }
        if (channel != null) {
            channel.close();
        }
        channel = null;
        connection = null;
        handler = null;
        recordedId = Integer.MIN_VALUE;
    }

    @Override
    public boolean isPauseMenuOpen() {
        return mc().currentScreen instanceof GameMenuScreen;
    }

    @Override
    public List<String> players() {
        List<String> out = new ArrayList<String>();
        MinecraftClient mc = mc();
        if (mc.world == null) {
            return out;
        }
        OtherClientPlayerEntity rec = recorder();
        if (rec != null) {
            out.add(rec.getGameProfile().getName());
        }
        for (PlayerEntity p : new ArrayList<PlayerEntity>(mc.world.playerEntities)) {
            if (p == mc.player || p == rec || p.getGameProfile() == null) {
                continue;
            }
            String n = p.getGameProfile().getName();
            if (n != null && !n.isEmpty() && !out.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    @Override
    public void spectate(String name) {
        MinecraftClient mc = mc();
        if (mc.world == null || mc.player == null) {
            return;
        }
        if (name == null) {
            mc.setCameraEntity(mc.player);
            return;
        }
        for (PlayerEntity p : new ArrayList<PlayerEntity>(mc.world.playerEntities)) {
            if (p != mc.player && p.getGameProfile() != null && name.equals(p.getGameProfile().getName())) {
                mc.setCameraEntity(p);
                return;
            }
        }
    }

    @Override
    public String spectating() {
        MinecraftClient mc = mc();
        Entity cam = mc.getCameraEntity();
        if (cam == null || cam == mc.player || !(cam instanceof PlayerEntity)) {
            return null;
        }
        return ((PlayerEntity) cam).getGameProfile().getName();
    }

    @Override
    public boolean isHudHidden() {
        return mc().options.hudHidden;
    }

    @Override
    public void setHudHidden(boolean hidden) {
        mc().options.hudHidden = hidden;
    }

    // ------------------------------------------------------------------ bytes

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 255) << 24) | ((b[at + 1] & 255) << 16) | ((b[at + 2] & 255) << 8) | (b[at + 3] & 255);
    }

    private static void writeInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            String t = s.trim();
            if (t.length() == 32) {
                t = t.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
            }
            return UUID.fromString(t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
