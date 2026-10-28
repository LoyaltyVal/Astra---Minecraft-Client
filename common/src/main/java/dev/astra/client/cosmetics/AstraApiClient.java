package dev.astra.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * A tiny blocking HTTP client for the Astra server. Only ever called from Astra's background
 * threads, never the render thread.
 */
public final class AstraApiClient {

    /** A failed request: {@link #status} is the HTTP status, or 0 if the server couldn't be reached. */
    public static final class ApiException extends IOException {
        public final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final String base;
    private final String token;

    public AstraApiClient(String base, String token) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.token = token == null || token.isEmpty() ? null : token;
    }

    /** HTTPS sockets that also trust current root certificates (old Java 8 builds lack them). */
    public static javax.net.ssl.SSLSocketFactory trustedSockets() {
        return AstraTrust.socketFactory();
    }

    public boolean hasToken() {
        return token != null;
    }

    /** A live connection (WebSocket) for instant updates; call start() on it. */
    public AstraSocket openSocket(AstraSocket.Listener listener) {
        return token == null ? null : new AstraSocket(base, token, listener);
    }

    public String url(String path) {
        return base + path;
    }

    public JsonElement getJson(String path) throws IOException {
        return parse(request("GET", path, null));
    }

    public JsonElement postJson(String path, String body) throws IOException {
        return parse(request("POST", path, body));
    }

    public byte[] getBytes(String path) throws IOException {
        return request("GET", path, null);
    }

    private byte[] request(String method, String path, String body) throws IOException {
        HttpURLConnection c;
        try {
            c = (HttpURLConnection) new URL(base + path).openConnection();
            if (c instanceof javax.net.ssl.HttpsURLConnection && AstraTrust.socketFactory() != null) {
                ((javax.net.ssl.HttpsURLConnection) c).setSSLSocketFactory(AstraTrust.socketFactory());
            }
        } catch (IOException e) {
            throw new ApiException(0, "Bad server address: " + base);
        }
        c.setRequestMethod(method);
        c.setConnectTimeout(6000);
        c.setReadTimeout(10000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "AstraClient");
        c.setRequestProperty("Accept", "application/json, image/*");
        if (token != null) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        try {
            if (body != null) {
                byte[] bytes = body.getBytes(UTF8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = c.getOutputStream();
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            byte[] data = in == null ? new byte[0] : readAll(in);
            if (status >= 400) {
                throw new ApiException(status, errorMessage(data, status));
            }
            return data;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(0, "Can't reach the Astra server");
        } finally {
            c.disconnect();
        }
    }

    private static String errorMessage(byte[] data, int status) {
        try {
            JsonElement e = parse(data);
            if (e.isJsonObject() && e.getAsJsonObject().has("error")) {
                return e.getAsJsonObject().get("error").getAsString();
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return "The Astra server answered " + status;
    }

    @SuppressWarnings("deprecation")
    private static JsonElement parse(byte[] data) throws IOException {
        try {
            return new JsonParser().parse(new String(data, UTF8));
        } catch (RuntimeException e) {
            throw new ApiException(0, "The Astra server sent a bad reply");
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > 8 * 1024 * 1024) {
                    throw new IOException("Reply too large");
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
