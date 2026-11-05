package dev.astra.client.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The .areplay file: gzip of
 *
 *   "ASTRAREPLAY" + format version (int) + info JSON (UTF)
 *   then entries, each: type (byte), time in ms since the start (int), then
 *     PACKET:    packet id (int), length (int), bytes
 *     SELF:      x, y, z (double), yaw, pitch, head yaw (float), flags (byte)
 *     EQUIPMENT: slot (byte), length (int), bytes (length 0 = empty slot)
 *   and END.
 *
 * A finished recording also gets a small .json next to it with its length, for the Replays list.
 */
public final class ReplayFile {

    public static final String MAGIC = "ASTRAREPLAY";
    public static final int FORMAT = 1;

    public static final byte END = 0;
    public static final byte PACKET = 1;
    public static final byte SELF = 2;
    public static final byte EQUIPMENT = 3;

    /** Stop recording past this size so a forgotten session can't fill the disk. */
    public static final long MAX_BYTES = 400L * 1024 * 1024;

    private ReplayFile() {
    }

    /** One entry of a loaded replay. */
    public static final class Entry {
        public final byte type;
        public final int time;
        public final int id; // packet id, or equipment slot
        public final byte[] data;
        public final SelfFrame self;

        Entry(byte type, int time, int id, byte[] data, SelfFrame self) {
            this.type = type;
            this.time = time;
            this.id = id;
            this.data = data;
            this.self = self;
        }
    }

    /** A loaded replay: its info and every entry in time order. */
    public static final class Loaded {
        public final ReplayInfo info;
        public final List<Entry> entries;

        Loaded(ReplayInfo info, List<Entry> entries) {
            this.info = info;
            this.entries = entries;
        }

        public int duration() {
            return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).time;
        }
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes a recording on its own thread, so saving never stutters the game. Entries can be
     * added from the network thread and the game thread.
     */
    public static final class Writer {
        private static final byte[] STOP = new byte[0];

        private final File file;
        private final ReplayInfo info;
        private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<byte[]>();
        private final Thread thread;
        private final long start = System.currentTimeMillis();
        private volatile long written;
        private volatile boolean full;
        private volatile int lastTime;

        public Writer(File file, ReplayInfo info) throws IOException {
            this.file = file;
            this.info = info;
            file.getParentFile().mkdirs();
            final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(new FileOutputStream(file), 1 << 16), 1 << 16));
            out.writeBytes(MAGIC);
            out.writeInt(FORMAT);
            out.writeUTF(info.toJson().toString());
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            byte[] b = queue.take();
                            if (b == STOP) {
                                break;
                            }
                            out.write(b);
                            written += b.length;
                        }
                        out.writeByte(END);
                    } catch (Exception e) {
                        System.err.println("[Astra] Replay write failed: " + e);
                    } finally {
                        try {
                            out.close();
                        } catch (IOException ignored) {
                            // already closing
                        }
                    }
                }
            }, "Astra replay writer");
            thread.setDaemon(true);
            thread.start();
        }

        public int now() {
            return (int) (System.currentTimeMillis() - start);
        }

        public boolean isFull() {
            return full;
        }

        private void push(ByteArrayOutputStream bytes, int time) {
            if (written > MAX_BYTES) {
                full = true;
                return;
            }
            lastTime = Math.max(lastTime, time);
            queue.add(bytes.toByteArray());
        }

        public void packet(int id, byte[] data) {
            try {
                int time = now();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length + 13);
                DataOutputStream d = new DataOutputStream(bytes);
                d.writeByte(PACKET);
                d.writeInt(time);
                d.writeInt(id);
                d.writeInt(data.length);
                d.write(data);
                push(bytes, time);
            } catch (IOException ignored) {
                // in-memory stream
            }
        }

        public void self(SelfFrame f) {
            try {
                int time = now();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(48);
                DataOutputStream d = new DataOutputStream(bytes);
                d.writeByte(SELF);
                d.writeInt(time);
                d.writeDouble(f.x);
                d.writeDouble(f.y);
                d.writeDouble(f.z);
                d.writeFloat(f.yaw);
                d.writeFloat(f.pitch);
                d.writeFloat(f.headYaw);
                d.writeByte(f.flags);
                push(bytes, time);
            } catch (IOException ignored) {
                // in-memory stream
            }
        }

        public void equipment(int slot, byte[] data) {
            try {
                int time = now();
                byte[] b = data == null ? new byte[0] : data;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(b.length + 10);
                DataOutputStream d = new DataOutputStream(bytes);
                d.writeByte(EQUIPMENT);
                d.writeInt(time);
                d.writeByte(slot);
                d.writeInt(b.length);
                d.write(b);
                push(bytes, time);
            } catch (IOException ignored) {
                // in-memory stream
            }
        }

        /** Finishes the file and writes the .json next to it. */
        public void finish() {
            queue.add(STOP);
            try {
                thread.join(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            info.durationMs = lastTime;
            info.sizeBytes = file.length();
            writeInfo(file, info);
        }

        public File getFile() {
            return file;
        }
    }

    static File infoFile(File replay) {
        String name = replay.getName();
        int dot = name.lastIndexOf('.');
        return new File(replay.getParentFile(), (dot > 0 ? name.substring(0, dot) : name) + ".json");
    }

    static void writeInfo(File replay, ReplayInfo info) {
        try {
            java.io.Writer out = new OutputStreamWriter(new FileOutputStream(infoFile(replay)), StandardCharsets.UTF_8);
            out.write(info.toJson().toString());
            out.close();
        } catch (IOException e) {
            System.err.println("[Astra] Couldn't save replay info: " + e);
        }
    }

    // ------------------------------------------------------------------ reading

    /** The info of a replay without loading it (from the .json, or the file header). */
    public static ReplayInfo readInfo(File replay) {
        File json = infoFile(replay);
        if (json.isFile()) {
            try {
                byte[] b = java.nio.file.Files.readAllBytes(json.toPath());
                return ReplayInfo.fromJson(new JsonParser().parse(new String(b, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception ignored) {
                // fall back to the header
            }
        }
        DataInputStream in = null;
        try {
            in = open(replay);
            ReplayInfo info = header(in);
            info.sizeBytes = replay.length();
            return info;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    /** Loads a whole replay. A recording cut short (the game crashed) loads up to where it stops. */
    public static Loaded load(File replay) throws IOException {
        DataInputStream in = open(replay);
        try {
            ReplayInfo info = header(in);
            List<Entry> entries = new ArrayList<Entry>();
            try {
                while (true) {
                    byte type = in.readByte();
                    if (type == END) {
                        break;
                    }
                    int time = in.readInt();
                    if (type == PACKET) {
                        int id = in.readInt();
                        byte[] data = new byte[in.readInt()];
                        in.readFully(data);
                        entries.add(new Entry(type, time, id, data, null));
                    } else if (type == SELF) {
                        SelfFrame f = new SelfFrame(in.readDouble(), in.readDouble(), in.readDouble(),
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readByte());
                        entries.add(new Entry(type, time, 0, null, f));
                    } else if (type == EQUIPMENT) {
                        int slot = in.readByte();
                        byte[] data = new byte[in.readInt()];
                        in.readFully(data);
                        entries.add(new Entry(type, time, slot, data, null));
                    } else {
                        throw new IOException("Unknown entry " + type);
                    }
                }
            } catch (EOFException e) {
                // an unfinished recording: keep what's there
            }
            // Entries come from two threads, so put them in time order.
            java.util.Collections.sort(entries, new java.util.Comparator<Entry>() {
                @Override
                public int compare(Entry a, Entry b) {
                    return a.time < b.time ? -1 : a.time == b.time ? 0 : 1;
                }
            });
            if (info.durationMs <= 0 && !entries.isEmpty()) {
                info.durationMs = entries.get(entries.size() - 1).time;
            }
            return new Loaded(info, entries);
        } finally {
            closeQuietly(in);
        }
    }

    private static DataInputStream open(File replay) throws IOException {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(replay), 1 << 16), 1 << 16));
    }

    private static ReplayInfo header(DataInputStream in) throws IOException {
        byte[] magic = new byte[MAGIC.length()];
        in.readFully(magic);
        if (!MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an Astra replay");
        }
        int format = in.readInt();
        if (format > FORMAT) {
            throw new IOException("This replay was made by a newer Astra");
        }
        JsonObject json = new JsonParser().parse(in.readUTF()).getAsJsonObject();
        return ReplayInfo.fromJson(json);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}
