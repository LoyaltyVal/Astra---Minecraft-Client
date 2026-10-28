package dev.astra.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The live connection to the Astra server (/api/live): a small WebSocket client, since Java 8 has
 * none. It only listens (messages, friend changes, emotes) and reconnects by itself; everything the
 * player does still goes through {@link AstraApiClient}.
 */
public final class AstraSocket {

    public interface Listener {
        void onEvent(JsonObject event);

        /** Connected (true) or lost the connection (false). */
        void onState(boolean connected);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_FRAME = 1 << 20;

    private final String base;
    private final String token;
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private volatile boolean running;
    private volatile boolean connected;
    private volatile Socket socket;
    private OutputStream out;

    public AstraSocket(String base, String token, Listener listener) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.token = token;
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "Astra live connection");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
    }

    private void loop() {
        long delay = 2000;
        while (running) {
            int status = 0;
            try {
                connect();
                delay = 2000;
                connected = true;
                listener.onState(true);
                read();
            } catch (HandshakeException e) {
                status = e.status;
            } catch (Exception e) {
                // dropped; reconnect below
            } finally {
                closeQuietly();
                if (connected) {
                    connected = false;
                    listener.onState(false);
                }
            }
            if (!running) {
                break;
            }
            if (status == 401 || status == 403) {
                delay = 5 * 60_000L; // signed out: try again much later
            }
            sleep(delay + random.nextInt(1000));
            delay = Math.min(delay * 2, 60_000L);
        }
    }

    private static final class HandshakeException extends IOException {
        final int status;

        HandshakeException(int status) {
            super("WebSocket refused: " + status);
            this.status = status;
        }
    }

    // ------------------------------------------------------------------ connecting

    private void connect() throws Exception {
        URI uri = new URI(base);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : secure ? 443 : 80;
        String prefix = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");

        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), 8000);
        Socket s = plain;
        if (secure) {
            SSLSocketFactory f = AstraTrust.socketFactory();
            if (f == null) {
                f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            SSLSocket ssl = (SSLSocket) f.createSocket(plain, host, port, true);
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS"); // check the certificate is for this host
            ssl.setSSLParameters(params);
            ssl.startHandshake();
            s = ssl;
        }
        s.setSoTimeout(70_000); // the server pings every 25 s
        socket = s;
        out = s.getOutputStream();

        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String hostHeader = host + ((secure && port == 443) || (!secure && port == 80) ? "" : ":" + port);
        String request = "GET " + prefix + "/api/live HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: astra, astra-auth." + token + "\r\n"
                + "User-Agent: AstraClient\r\n\r\n";
        out.write(request.getBytes(UTF8));
        out.flush();

        InputStream in = s.getInputStream();
        String status = readLine(in);
        String[] parts = status.split(" ");
        int code = parts.length > 1 ? parseInt(parts[1]) : 0;
        String accept = null;
        String line;
        while (!(line = readLine(in)).isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Sec-WebSocket-Accept")) {
                accept = line.substring(colon + 1).trim();
            }
        }
        if (code != 101) {
            throw new HandshakeException(code);
        }
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        String expected = Base64.getEncoder().encodeToString(sha1.digest((key + GUID).getBytes(UTF8)));
        if (!expected.equals(accept)) {
            throw new IOException("Bad WebSocket handshake");
        }
    }

    // ------------------------------------------------------------------ frames

    private void read() throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (running) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) {
                throw new EOFException();
            }
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0f;
            long len = b1 & 0x7f;
            if (len == 126) {
                len = (readByte(in) << 8) | readByte(in);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | readByte(in);
                }
            }
            if (len > MAX_FRAME) {
                throw new IOException("Frame too large");
            }
            byte[] payload = new byte[(int) len];
            int off = 0;
            while (off < payload.length) {
                int n = in.read(payload, off, payload.length - off);
                if (n < 0) {
                    throw new EOFException();
                }
                off += n;
            }
            if (opcode == 0x8) {
                send(0x8, new byte[0]);
                return;
            } else if (opcode == 0x9) {
                send(0xA, payload);
            } else if (opcode == 0x1 || opcode == 0x0) {
                message.write(payload);
                if (fin) {
                    handle(new String(message.toByteArray(), UTF8));
                    message.reset();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void handle(String text) {
        try {
            JsonElement e = new JsonParser().parse(text);
            if (e.isJsonObject()) {
                listener.onEvent(e.getAsJsonObject());
            }
        } catch (Exception ignored) {
            // not an event
        }
    }

    /** Client frames must be masked. */
    private synchronized void send(int opcode, byte[] payload) throws IOException {
        if (out == null) {
            return;
        }
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | opcode);
        if (payload.length < 126) {
            f.write(0x80 | payload.length);
        } else {
            f.write(0x80 | 126);
            f.write((payload.length >> 8) & 0xff);
            f.write(payload.length & 0xff);
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        f.write(mask);
        for (int i = 0; i < payload.length; i++) {
            f.write(payload[i] ^ mask[i & 3]);
        }
        out.write(f.toByteArray());
        out.flush();
    }

    // ------------------------------------------------------------------ helpers

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
            if (line.size() > 8192) {
                throw new IOException("Header line too long");
            }
        }
        if (b < 0 && line.size() == 0) {
            throw new EOFException();
        }
        return new String(line.toByteArray(), UTF8);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void closeQuietly() {
        Socket s = socket;
        socket = null;
        out = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closed
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
