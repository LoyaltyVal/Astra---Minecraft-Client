package dev.astra.client.features;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Plays an internet radio stream (MP3, the format almost every station offers) on its own thread:
 * HTTP(S) with redirects and .pls/.m3u playlists, the song title from the stream's ICY metadata,
 * MP3 decoding with JLayer, and output through Java Sound at Astra's own volume.
 */
public final class RadioPlayer {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final RadioPlayer INSTANCE = new RadioPlayer();

    private volatile Radio.Station station;
    private volatile String status = "Stopped";
    private volatile boolean error;
    private volatile String nowPlaying;
    private volatile boolean playing;
    private volatile float volume = 0.5f;
    private volatile float level;
    private volatile int generation;

    private RadioPlayer() {
    }

    public static RadioPlayer get() {
        return INSTANCE;
    }

    public Radio.Station getStation() {
        return station;
    }

    /** "Connecting...", "Playing", an error, or "Stopped". */
    public String getStatus() {
        return status;
    }

    public boolean isError() {
        return error;
    }

    /** The current song ("Artist - Title") when the station sends it, else null. */
    public String getNowPlaying() {
        return nowPlaying;
    }

    /** True while music is coming out (not while connecting). */
    public boolean isPlaying() {
        return playing;
    }

    /** True from pressing play until stop (including connecting and reconnecting). */
    public boolean isActive() {
        return station != null;
    }

    /** 0..1 loudness of the last frame, for the little equaliser. */
    public float getLevel() {
        return level;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    public synchronized void play(final Radio.Station s) {
        final int gen = ++generation;
        station = s;
        nowPlaying = null;
        playing = false;
        error = false;
        status = "Connecting...";
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loop(s, gen);
            }
        }, "Astra radio");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        generation++;
        station = null;
        playing = false;
        nowPlaying = null;
        error = false;
        level = 0;
        status = "Stopped";
    }

    private boolean current(int gen) {
        return gen == generation;
    }

    /** Plays until stopped; reconnects a few times if the stream drops. */
    private void loop(Radio.Station s, int gen) {
        int failures = 0;
        while (current(gen) && failures < 4) {
            try {
                stream(s, gen);
                failures = 0; // ended normally (server closed): reconnect right away
            } catch (Exception e) {
                failures++;
                if (current(gen)) {
                    playing = false;
                    status = failures < 4 ? "Reconnecting..." : message(e);
                    error = failures >= 4;
                }
            }
            if (current(gen)) {
                sleep(failures == 0 ? 500 : 1500L * failures);
            }
        }
        if (current(gen) && error) {
            playing = false;
            level = 0;
        }
    }

    private void stream(Radio.Station s, int gen) throws Exception {
        HttpURLConnection c = open(s.url, 0);
        InputStream raw = null;
        SourceDataLine line = null;
        try {
            String type = String.valueOf(c.getContentType()).toLowerCase(Locale.ROOT);
            raw = c.getInputStream();
            if (type.contains("scpls") || type.contains("mpegurl") || s.url.matches("(?i).*\\.(pls|m3u8?)(\\?.*)?$")) {
                String next = firstUrl(raw);
                raw.close();
                c.disconnect();
                if (next == null) {
                    throw new IOException("That playlist has no stream in it");
                }
                c = open(next, 0);
                raw = c.getInputStream();
                type = String.valueOf(c.getContentType()).toLowerCase(Locale.ROOT);
            }
            if (type.contains("aac") || type.contains("ogg") || type.contains("opus") || type.contains("flac")) {
                throw new IOException("This station isn't MP3 (it's " + type.replace("audio/", "") + ")");
            }
            if (type.contains("text/html")) {
                throw new IOException("That address is a web page, not a stream");
            }
            InputStream in = raw;
            int metaInt = c.getHeaderFieldInt("icy-metaint", 0);
            if (metaInt > 0) {
                in = new IcyStream(raw, metaInt);
            }
            Mp3Decoder decoder;
            try {
                decoder = new Mp3Decoder(new BufferedInputStream(in, 32 * 1024));
            } catch (LinkageError e) {
                throw new IOException("The radio's MP3 decoder is missing. Rebuild Astra with Start Astra.bat.");
            }
            byte[] pcm = new byte[0];
            int frames = 0;
            while (current(gen)) {
                if (!decoder.next()) {
                    return; // stream ended
                }
                if (line == null) {
                    AudioFormat format = new AudioFormat(decoder.sampleRate(), 16, decoder.channels(), true, false);
                    line = AudioSystem.getSourceDataLine(format);
                    // About half a second of buffer: smooth, but stop and volume still feel instant.
                    line.open(format, (int) (format.getFrameRate() * format.getFrameSize() / 2));
                    line.start();
                }
                short[] samples = decoder.samples();
                int n = decoder.length();
                if (pcm.length < n * 2) {
                    pcm = new byte[n * 2];
                }
                float v = volume * volume; // feels linear to the ear
                int peak = 0;
                for (int i = 0; i < n; i++) {
                    int sample = (int) (samples[i] * v);
                    pcm[i * 2] = (byte) sample;
                    pcm[i * 2 + 1] = (byte) (sample >> 8);
                    int a = Math.abs(samples[i]);
                    if (a > peak) {
                        peak = a;
                    }
                }
                level = level * 0.6f + (peak / 32768f) * 0.4f;
                if (++frames == 3) {
                    playing = true;
                    error = false;
                    status = "Playing";
                }
                line.write(pcm, 0, n * 2);
            }
        } finally {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
            if (raw != null) {
                try {
                    raw.close();
                } catch (IOException ignored) {
                    // closed
                }
            }
            c.disconnect();
        }
    }

    /** Opens the stream, following redirects (Java doesn't follow http -> https on its own). */
    private static HttpURLConnection open(String address, int hops) throws IOException {
        if (hops > 5) {
            throw new IOException("Too many redirects");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        if (c instanceof javax.net.ssl.HttpsURLConnection) {
            javax.net.ssl.SSLSocketFactory f = dev.astra.client.cosmetics.AstraApiClient.trustedSockets();
            if (f != null) {
                ((javax.net.ssl.HttpsURLConnection) c).setSSLSocketFactory(f);
            }
        }
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "AstraClient/1.0 (Minecraft radio)");
        c.setRequestProperty("Icy-MetaData", "1");
        c.setRequestProperty("Accept", "audio/mpeg, audio/*, */*");
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String next = c.getHeaderField("Location");
            c.disconnect();
            if (next == null) {
                throw new IOException("Bad redirect");
            }
            return open(new URL(new URL(address), next).toString(), hops + 1);
        }
        if (code >= 400) {
            c.disconnect();
            throw new IOException(code == 404 ? "This station is offline" : "The station answered " + code);
        }
        return c;
    }

    /** The first stream address in a .pls or .m3u playlist. */
    private static String firstUrl(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, UTF8));
        String line;
        int read = 0;
        while ((line = r.readLine()) != null && read++ < 200) {
            line = line.trim();
            int eq = line.indexOf('=');
            if (line.toLowerCase(Locale.ROOT).startsWith("file") && eq > 0) {
                line = line.substring(eq + 1).trim();
            }
            if (line.startsWith("http://") || line.startsWith("https://")) {
                return line;
            }
        }
        return null;
    }

    /**
     * Removes the ICY metadata blocks that Shoutcast/Icecast mix into the audio every metaint bytes,
     * and picks the song title out of them.
     */
    private final class IcyStream extends FilterInputStream {
        private final int metaInt;
        private int untilMeta;

        IcyStream(InputStream in, int metaInt) {
            super(in);
            this.metaInt = metaInt;
            this.untilMeta = metaInt;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (untilMeta == 0) {
                readMeta();
                untilMeta = metaInt;
            }
            int n = super.read(b, off, Math.min(len, untilMeta));
            if (n > 0) {
                untilMeta -= n;
            }
            return n;
        }

        private void readMeta() throws IOException {
            int size = in.read();
            if (size < 0) {
                throw new IOException("Stream ended");
            }
            size *= 16;
            if (size == 0) {
                return;
            }
            ByteArrayOutputStream meta = new ByteArrayOutputStream(size);
            while (meta.size() < size) {
                byte[] buf = new byte[size - meta.size()];
                int n = in.read(buf);
                if (n < 0) {
                    throw new IOException("Stream ended");
                }
                meta.write(buf, 0, n);
            }
            String text = new String(meta.toByteArray(), UTF8);
            int start = text.indexOf("StreamTitle='");
            if (start >= 0) {
                start += 13;
                int end = text.indexOf("';", start);
                String title = (end > start ? text.substring(start, end) : text.substring(start)).trim();
                nowPlaying = title.isEmpty() ? null : title;
            }
        }
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        if (e instanceof java.net.UnknownHostException) {
            return "Can't reach the station (no internet?)";
        }
        if (e instanceof javax.sound.sampled.LineUnavailableException) {
            return "Your sound device is busy";
        }
        return m == null || m.isEmpty() ? "Couldn't play this station" : m;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
