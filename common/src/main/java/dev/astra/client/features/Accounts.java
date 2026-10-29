package dev.astra.client.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.astra.client.Astra;
import dev.astra.client.social.Notifications;
import dev.astra.client.ui.Theme;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The in-game account switcher. Microsoft sign-ins live in the Astra app, so the game asks the app
 * (over the private link it was started with: -Dastra.bridge / -Dastra.bridgeToken) for the saved
 * accounts and a fresh session, then swaps the game's session without restarting.
 */
public final class Accounts {

    public static final class Account {
        public final String id;
        public final String name;
        public final boolean active;
        public final boolean expired;

        Account(String id, String name, boolean active, boolean expired) {
            this.id = id;
            this.name = name;
            this.active = active;
            this.expired = expired;
        }
    }

    /** Called on the game thread with null on success, or an error message. */
    public interface Callback {
        void done(String error);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Accounts INSTANCE = new Accounts();

    private final String bridge = System.getProperty("astra.bridge");
    private final String secret = System.getProperty("astra.bridgeToken");
    private final ConcurrentLinkedQueue<Runnable> mainThread = new ConcurrentLinkedQueue<Runnable>();
    private volatile List<Account> accounts = Collections.emptyList();
    private volatile boolean loading;
    private volatile String busy; // what's happening right now ("Switching to X..."), or null

    public static Accounts get() {
        return INSTANCE;
    }

    /** False when the game wasn't started by the Astra app (then there's nothing to switch to). */
    public boolean available() {
        return bridge != null && bridge.startsWith("http://127.0.0.1:") && secret != null;
    }

    public List<Account> list() {
        return accounts;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getBusy() {
        return busy;
    }

    /** Runs finished work on the game thread (called every tick by Astra). */
    public void tick() {
        Runnable r;
        while ((r = mainThread.poll()) != null) {
            r.run();
        }
    }

    public void refresh() {
        if (!available() || loading) {
            return;
        }
        loading = true;
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Account> out = new ArrayList<Account>();
                    for (JsonElement e : call("GET", "/accounts", null).getAsJsonArray("accounts")) {
                        JsonObject o = e.getAsJsonObject();
                        out.add(new Account(o.get("id").getAsString(), o.get("name").getAsString(),
                                o.has("active") && o.get("active").getAsBoolean(),
                                o.has("expired") && o.get("expired").getAsBoolean()));
                    }
                    accounts = Collections.unmodifiableList(out);
                } catch (Exception ignored) {
                    // the screen shows "can't reach the app"
                } finally {
                    loading = false;
                }
            }
        });
    }

    /** Switches the game to another saved account (leaving the current world first). */
    public void switchTo(final Account account, final Callback cb) {
        if (busy != null) {
            return;
        }
        busy = "Switching to " + account.name + "...";
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("id", account.id);
                    final JsonObject s = call("POST", "/switch", body.toString());
                    final String name = s.get("name").getAsString();
                    final String uuid = s.get("uuid").getAsString();
                    final String token = s.get("accessToken").getAsString();
                    final String astraToken = s.has("astraToken") && !s.get("astraToken").isJsonNull()
                            ? s.get("astraToken").getAsString() : null;
                    mainThread.add(new Runnable() {
                        @Override
                        public void run() {
                            apply(name, uuid, token, astraToken);
                            busy = null;
                            refresh();
                            cb.done(null);
                        }
                    });
                } catch (final Exception e) {
                    mainThread.add(new Runnable() {
                        @Override
                        public void run() {
                            busy = null;
                            refresh();
                            cb.done(e.getMessage() == null ? "Couldn't switch accounts." : e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /** Opens Microsoft's sign-in window (from the Astra app) to add an account. */
    public void add(final Callback cb) {
        if (busy != null) {
            return;
        }
        busy = "Finish signing in in the Microsoft window (it may be behind the game)...";
        async(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    call("POST", "/add", "{}");
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String err = error;
                mainThread.add(new Runnable() {
                    @Override
                    public void run() {
                        busy = null;
                        refresh();
                        cb.done(err);
                    }
                });
            }
        });
    }

    private void apply(String name, String uuid, String accessToken, String astraToken) {
        Astra astra = Astra.get();
        if (Astra.platform().isInGame()) {
            Astra.platform().leaveWorld();
        }
        Astra.platform().setSession(name, uuid, accessToken);
        if (astra.getCosmetics() != null) {
            astra.getCosmetics().switchAccount(astraToken);
        }
        astra.getSocial().reset();
        Notifications.push("Now playing as " + name, astraToken == null
                ? "Cosmetics and friends will load when the Astra server answers" : "Cosmetics and friends are loading", Theme.accent());
    }

    // ------------------------------------------------------------------ HTTP

    @SuppressWarnings("deprecation")
    private JsonObject call(String method, String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(bridge + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(path.equals("/add") ? 10 * 60 * 1000 : 60 * 1000); // signing in takes a while
        c.setRequestProperty("X-Astra-Bridge", secret);
        c.setRequestProperty("Content-Type", "application/json");
        try {
            if (body != null) {
                byte[] bytes = body.getBytes(UTF8);
                c.setDoOutput(true);
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
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
                in.close();
            }
            JsonObject json;
            try {
                json = new JsonParser().parse(new String(buf.toByteArray(), UTF8)).getAsJsonObject();
            } catch (Exception e) {
                json = new JsonObject();
            }
            if (status >= 400) {
                throw new IOException(json.has("error") ? json.get("error").getAsString() : "The Astra app answered " + status);
            }
            return json;
        } catch (java.net.ConnectException e) {
            throw new IOException("Can't reach the Astra app. Is it still open?");
        } finally {
            c.disconnect();
        }
    }

    private static void async(Runnable r) {
        Thread t = new Thread(r, "Astra accounts");
        t.setDaemon(true);
        t.start();
    }
}
