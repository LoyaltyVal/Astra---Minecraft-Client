package dev.astra.client.replay;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.ReplayModule;
import dev.astra.client.platform.Platform;
import dev.astra.client.social.Notifications;
import dev.astra.client.ui.Theme;
import dev.astra.client.util.Render;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Replays: records the packets the server sends you (plus your own movement, which the server
 * never sends back) into an .areplay file, and plays one back in a replay world where you fly
 * around as a spectator, pause, change speed, jump around the timeline or watch from a player.
 *
 * The version code calls {@link #onPacket} for every packet received and {@link #onConnectionClosed};
 * {@link Astra} calls {@link #tick} and {@link #renderHud}.
 */
public final class Replay {

    private static final Replay INSTANCE = new Replay();

    public static Replay get() {
        return INSTANCE;
    }

    private Replay() {
    }

    /** This version's replay support, or null where replays aren't available yet. */
    public static ReplayBackend backend() {
        Platform p = Astra.platform();
        return p == null ? null : p.replay();
    }

    public static File folder() {
        return new File(Astra.platform().getGameDir(), "replays");
    }

    private static ReplayModule module() {
        Astra a = Astra.get();
        return a == null ? null : a.getModules().get(ReplayModule.class);
    }

    // ================================================================== recording

    private volatile ReplayFile.Writer writer;
    private volatile Object recordingConnection;
    private final byte[][] lastEquipment = new byte[5][];
    private int noWorldTicks;

    public boolean isRecording() {
        return writer != null;
    }

    /** Called on the network thread for every packet a server connection receives. */
    public void onPacket(Object connection, Object packet) {
        ReplayBackend b = backend();
        if (b == null || loaded != null) {
            return;
        }
        if (b.isJoinPacket(packet)) {
            if (connection != recordingConnection) {
                stopRecording(false);
            }
            if (writer == null && shouldRecord(b)) {
                startRecording(connection, b);
            }
        }
        ReplayFile.Writer w = writer;
        if (w == null || connection != recordingConnection) {
            return;
        }
        int id = b.packetId(packet);
        if (id < 0) {
            return;
        }
        try {
            w.packet(id, b.writePacket(packet));
        } catch (Throwable t) {
            // One packet that won't serialise (a mod's custom data) shouldn't end the recording.
        }
    }

    /** The server connection closed: finish its recording. */
    public void onConnectionClosed(Object connection) {
        if (connection != null && connection == recordingConnection) {
            stopRecording(true);
        }
    }

    private boolean shouldRecord(ReplayBackend b) {
        ReplayModule m = module();
        if (m == null || !m.isEnabled() || !m.recordAutomatically()) {
            return false;
        }
        String server = b instanceof ServerName ? ((ServerName) b).currentServer() : null;
        return !"singleplayer".equals(server) || m.recordSingleplayer();
    }

    /** Lets a backend say which server a new connection is to (from the network thread). */
    public interface ServerName {
        String currentServer();

        /** Your account's UUID (so the replay can show your skin). */
        String sessionUuid();
    }

    private void startRecording(Object connection, ReplayBackend b) {
        try {
            ReplayInfo info = new ReplayInfo();
            info.minecraftVersion = Astra.platform().minecraftVersion();
            String server = b instanceof ServerName ? ((ServerName) b).currentServer() : null;
            info.server = server == null ? "unknown" : server;
            info.playerName = Astra.platform().getSessionName();
            String uuid = b instanceof ServerName ? ((ServerName) b).sessionUuid() : null;
            info.playerUuid = uuid == null ? "" : uuid;
            info.startedAt = System.currentTimeMillis();
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date(info.startedAt));
            String safe = info.server.replaceAll("[^A-Za-z0-9.\\-]", "_");
            File file = new File(folder(), stamp + "_" + safe + ".areplay");
            Arrays.fill(lastEquipment, null);
            noWorldTicks = 0;
            recordingConnection = connection;
            writer = new ReplayFile.Writer(file, info);
        } catch (IOException e) {
            System.err.println("[Astra] Couldn't start a replay: " + e);
            writer = null;
            recordingConnection = null;
        }
    }

    /** Finishes the current recording (on a background thread, so the game doesn't wait). */
    public void stopRecording(final boolean announce) {
        final ReplayFile.Writer w = writer;
        writer = null;
        recordingConnection = null;
        if (w == null) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                w.finish();
                cleanUp();
                if (announce && w.now() > 5000) {
                    Notifications.push("Replay saved", "Right Shift → Replays to watch it", Theme.accent());
                }
            }
        }, "Astra replay finish");
        t.setDaemon(true);
        t.start();
    }

    /** Finishes the recording right away (the game is closing, so there's no time for a thread). */
    public void finishNow() {
        ReplayFile.Writer w = writer;
        writer = null;
        recordingConnection = null;
        if (w != null) {
            w.finish();
        }
    }

    /** Keeps only the newest replays (the "Keep Replays" setting). */
    private static void cleanUp() {
        ReplayModule m = module();
        int keep = m == null ? 30 : m.keepCount();
        List<File> all = ReplaysList.files();
        for (int i = keep; i < all.size(); i++) {
            File f = all.get(i);
            f.delete();
            ReplayFile.infoFile(f).delete();
        }
    }

    private void tickRecording() {
        ReplayFile.Writer w = writer;
        ReplayBackend b = backend();
        if (w == null || b == null) {
            return;
        }
        if (!Astra.platform().isInGame()) {
            // Between worlds (a server switch) for a moment is normal; for long, you've left.
            if (++noWorldTicks > 100) {
                stopRecording(true);
            }
            return;
        }
        noWorldTicks = 0;
        if (w.isFull()) {
            stopRecording(false);
            Notifications.push("Replay stopped", "It reached the size limit; it's saved.", Theme.OFF);
            return;
        }
        SelfFrame f = b.captureSelf();
        if (f != null) {
            w.self(f);
        }
        byte[][] eq = b.captureEquipment();
        if (eq != null) {
            for (int i = 0; i < Math.min(5, eq.length); i++) {
                if (!Arrays.equals(eq[i], lastEquipment[i])) {
                    lastEquipment[i] = eq[i];
                    w.equipment(i, eq[i]);
                }
            }
        }
    }

    /** Milliseconds recorded so far. */
    public int recordingTime() {
        ReplayFile.Writer w = writer;
        return w == null ? 0 : w.now();
    }

    // ================================================================== playback

    private volatile ReplayFile.Loaded loaded;
    private File playingFile;
    private double time;
    private int cursor;
    private boolean paused;
    private float speed = 1f;
    private long lastMs;
    private int errors;

    public boolean isPlaying() {
        return loaded != null;
    }

    /** Opens a replay. Call from the game thread, outside a world. */
    public void play(File file) throws IOException {
        ReplayBackend b = backend();
        if (b == null) {
            throw new IOException("Replays aren't available on this Minecraft version yet.");
        }
        stopRecording(false);
        ReplayFile.Loaded l = ReplayFile.load(file);
        if (l.entries.isEmpty()) {
            throw new IOException("This replay is empty.");
        }
        if (Astra.platform().isInGame()) {
            Astra.platform().leaveWorld();
        }
        try {
            b.open(l.info);
        } catch (Exception e) {
            throw new IOException("Couldn't open the replay world: " + e.getMessage(), e);
        }
        loaded = l;
        playingFile = file;
        time = 0;
        cursor = 0;
        paused = false;
        speed = 1f;
        errors = 0;
        lastMs = System.currentTimeMillis();
        dispatchUntil(0, true);
    }

    public ReplayInfo info() {
        ReplayFile.Loaded l = loaded;
        return l == null ? null : l.info;
    }

    public int duration() {
        ReplayFile.Loaded l = loaded;
        return l == null ? 0 : l.duration();
    }

    public int time() {
        return (int) time;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean p) {
        paused = p;
        if (!p && time >= duration()) {
            seek(0); // play again from the start
        }
    }

    public float speed() {
        return speed;
    }

    public void setSpeed(float s) {
        speed = Math.max(0.1f, Math.min(8f, s));
    }

    /** Jumps to a moment. Going back rebuilds the world from the start, so it can take a second. */
    public void seek(int ms) {
        ReplayFile.Loaded l = loaded;
        ReplayBackend b = backend();
        if (l == null || b == null) {
            return;
        }
        int target = Math.max(0, Math.min(l.duration(), ms));
        if (target < time) {
            dev.astra.client.ui.UiScreen open = Astra.platform().getOpenUi();
            b.close();
            try {
                b.open(l.info);
            } catch (Exception e) {
                System.err.println("[Astra] Replay seek failed: " + e);
                exit();
                return;
            }
            cursor = 0;
            dispatchUntil(target, true);
            if (open != null) {
                Astra.platform().openUi(open);
            }
        } else {
            dispatchUntil(target, true);
        }
        time = target;
        lastMs = System.currentTimeMillis();
    }

    /** Leaves the replay and shows the replays list. */
    public void exit() {
        ReplayBackend b = backend();
        loaded = null;
        playingFile = null;
        if (b != null) {
            b.close();
        }
        Astra.platform().openUi(new ReplaysScreen());
    }

    private void dispatchUntil(int ms, boolean fast) {
        ReplayFile.Loaded l = loaded;
        ReplayBackend b = backend();
        if (l == null || b == null) {
            return;
        }
        List<ReplayFile.Entry> entries = l.entries;
        while (cursor < entries.size() && entries.get(cursor).time <= ms) {
            ReplayFile.Entry e = entries.get(cursor++);
            try {
                if (e.type == ReplayFile.PACKET) {
                    b.apply(e.id, e.data, fast);
                } else if (e.type == ReplayFile.SELF) {
                    b.applySelf(e.self, fast);
                } else if (e.type == ReplayFile.EQUIPMENT) {
                    b.applyEquipment(e.id, e.data);
                }
            } catch (Throwable t) {
                if (errors++ < 5) {
                    System.err.println("[Astra] Replay entry " + e.type + "/" + e.id + " at " + e.time + "ms failed: " + t);
                }
            }
            if (loaded == null) {
                return; // closed while applying
            }
        }
    }

    private void tickPlayback() {
        ReplayBackend b = backend();
        if (loaded == null || b == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long delta = Math.min(1000, now - lastMs);
        lastMs = now;
        b.tick();
        if (b.isPauseMenuOpen()) {
            Astra.platform().openUi(new ReplayControlsScreen());
        }
        ReplayModule m = module();
        if (m != null && !Astra.platform().isScreenOpen()) {
            m.handleKeys(this);
        }
        if (paused) {
            return;
        }
        time += delta * speed;
        int end = duration();
        if (time >= end) {
            time = end;
            paused = true;
        }
        dispatchUntil((int) time, false);
    }

    // ================================================================== hooks

    /** Every client tick. */
    public void tick() {
        try {
            tickRecording();
            tickPlayback();
        } catch (Throwable t) {
            if (errors++ < 5) {
                t.printStackTrace();
            }
        }
    }

    /** Every frame, over the HUD (when no screen is open). */
    public void renderHud(int width, int height) {
        ReplayModule m = module();
        if (isRecording() && m != null && m.showIndicator()) {
            boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
            String label = "REC " + clock(recordingTime());
            int w = Render.width(label) + 14;
            Render.round(width - w - 4, height - 16, w, 12, 3, 0x90000000);
            Render.round(width - w + 1, height - 12, 4, 4, 2, blink ? 0xFFFF4D5E : 0x60FF4D5E);
            Render.text(label, width - w + 8, height - 14, 0xFFFFFFFF, false);
        }
        if (isPlaying()) {
            String state = (paused ? "Paused" : "Playing") + "  " + clock(time()) + " / " + clock(duration())
                    + (speed != 1f ? "  " + speedLabel(speed) : "") + "   Esc: controls";
            int w = Render.width(state) + 12;
            Render.round(width / 2 - w / 2, height - 52, w, 13, 3, 0xA0000000);
            Render.textCentered(state, width / 2f, height - 49, 0xFFFFFFFF, false);
        }
    }

    public static String clock(int ms) {
        int s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    public static String speedLabel(float s) {
        return (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "x";
    }
}
