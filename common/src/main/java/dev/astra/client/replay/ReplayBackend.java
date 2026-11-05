package dev.astra.client.replay;

import java.util.List;

/**
 * The Minecraft-version-specific half of replays (see PlatformImpl#replay()). The shared code
 * in this package handles files, timing and the screens; the backend turns packets into bytes and
 * back, and runs the replay world.
 */
public interface ReplayBackend {

    // ------------------------------------------------------------------ recording

    /** The play-state packet id of a clientbound packet, or -1 if it isn't one. */
    int packetId(Object packet);

    /** True for the packet that starts a world session (Join Game). */
    boolean isJoinPacket(Object packet);

    /** Serialises a packet's contents (without the id). */
    byte[] writePacket(Object packet) throws Exception;

    /** Where your own player is right now, or null when there's no player. */
    SelfFrame captureSelf();

    /** Your held item and armor (slots 0..4), each serialised; null entries for empty slots. */
    byte[][] captureEquipment();

    // ------------------------------------------------------------------ playback

    /** Opens an empty replay world connection (nothing loaded until packets arrive). */
    void open(ReplayInfo info) throws Exception;

    /** Applies one recorded packet. {@code fast} while seeking: skips sounds, particles and chat. */
    void apply(int id, byte[] data, boolean fast) throws Exception;

    /** Moves the recording player (shown as a normal player in the replay); instantly when {@code fast}. */
    void applySelf(SelfFrame frame, boolean fast);

    void applyEquipment(int slot, byte[] data) throws Exception;

    /** Called every client tick while a replay is open. */
    void tick();

    /** Leaves the replay world. */
    void close();

    /** True while the vanilla pause menu is open (Escape), which the replay swaps for its controls. */
    boolean isPauseMenuOpen();

    /** Names of the players in the replay world, the recorder first. */
    List<String> players();

    /** Watches from a player's eyes, or flies freely when {@code name} is null. */
    void spectate(String name);

    /** The player being watched, or null for the free camera. */
    String spectating();

    boolean isHudHidden();

    void setHudHidden(boolean hidden);
}
