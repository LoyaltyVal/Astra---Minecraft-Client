package dev.astra.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astra.client.Astra;
import dev.astra.client.cosmetics.AstraApiClient;
import dev.astra.client.cosmetics.AstraSocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Friends, requests and private messages, kept in sync with the Astra server (the same lists the
 * app and website show). Everything network-related runs on a background thread; the UI only
 * reads the latest snapshot.
 */
public final class SocialManager {

    /** A friend (or someone in a request) as the server describes them. */
    public static final class Friend {
        public final String uuid;
        public final String name;
        public final int rankColor;
        public final String rankName;
        public final boolean online;
        /** In game right now. */
        public final boolean playing;
        /** Server address, "singleplayer", or null (menus or hidden). */
        public final String server;
        public final String version;

        Friend(JsonObject o) {
            uuid = str(o, "uuid");
            name = str(o, "name");
            JsonObject rank = o.has("rank") && o.get("rank").isJsonObject() ? o.getAsJsonObject("rank") : null;
            rankName = rank == null ? null : str(rank, "name");
            rankColor = rank == null ? 0xFFFFFFFF : color(str(rank, "color"));
            online = o.has("online") && o.get("online").getAsBoolean();
            JsonObject p = o.has("playing") && o.get("playing").isJsonObject() ? o.getAsJsonObject("playing") : null;
            playing = p != null;
            server = p == null ? null : str(p, "server");
            version = p == null ? null : str(p, "version");
        }

        /** "On mc.hypixel.net", "In the menus", "Offline"... */
        public String status() {
            if (playing) {
                if (server == null) {
                    return "In game" + (version == null ? "" : " · " + version);
                }
                return "singleplayer".equals(server) ? "Playing singleplayer" : "On " + server;
            }
            return online ? "Online" : "Offline";
        }

        /** True when you can join them (they're on a multiplayer server they don't hide). */
        public boolean joinable() {
            return playing && server != null && !"singleplayer".equals(server);
        }
    }

    public static final class Message {
        public final long id;
        public final String from;
        public final String to;
        public final String text;
        public final long at;

        Message(long id, String from, String to, String text, long at) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.text = text;
            this.at = at;
        }
    }

    /** Called with null on success, or an error message. */
    public interface Callback {
        void done(String error);
    }

    // Polling is only the backup: while the live connection (WebSocket) is up, changes arrive at once.
    private static final int FRIENDS_EVERY = 20 * 15; // ticks
    private static final int MESSAGES_EVERY = 20 * 3;
    private static final int MESSAGES_FAST = 20;      // while the friends menu is open
    private static final int LIVE_FRIENDS_EVERY = 20 * 120;
    private static final int LIVE_MESSAGES_EVERY = 20 * 60;

    private final ExecutorService net = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Astra friends network");
            t.setDaemon(true);
            return t;
        }
    });
    private final AtomicBoolean busy = new AtomicBoolean();

    private volatile List<Friend> friends = Collections.emptyList();
    private volatile List<Friend> incoming = Collections.emptyList();
    private volatile List<Friend> outgoing = Collections.emptyList();
    private volatile String error;
    private volatile boolean loaded;
    /** Conversations keyed by the other player's uuid, oldest message first. */
    private final Map<String, List<Message>> chats = new LinkedHashMap<String, List<Message>>();
    private final Map<String, Integer> unread = new LinkedHashMap<String, Integer>();
    private final Map<String, String> names = new LinkedHashMap<String, String>();
    private volatile long lastMessageId = -1;
    private final Set<String> knownIncoming = new HashSet<String>();
    private final Set<String> knownPlaying = new HashSet<String>();
    private boolean firstFriendsLoad = true;
    private int ticks;
    private boolean fast;
    /** The conversation open in the friends menu (messages there aren't counted as unread). */
    private volatile String reading;
    private AstraSocket socket;

    private AstraApiClient api() {
        Astra astra = Astra.get();
        return astra == null || astra.getCosmetics() == null ? null : astra.getCosmetics().getApi();
    }

    /** False when the game wasn't started from the Astra app with a signed-in account. */
    public boolean available() {
        AstraApiClient api = api();
        return api != null && api.hasToken();
    }

    public void tick() {
        if (!available()) {
            return;
        }
        ticks++;
        if (socket == null && ticks >= 40) {
            startLive();
        }
        boolean live = isLive();
        if (ticks == 40 || ticks % (live ? LIVE_FRIENDS_EVERY : FRIENDS_EVERY) == 0) {
            run(new Runnable() {
                @Override
                public void run() {
                    loadFriends();
                }
            });
        }
        if (loaded && ticks % (live ? LIVE_MESSAGES_EVERY : fast ? MESSAGES_FAST : MESSAGES_EVERY) == 0) {
            run(new Runnable() {
                @Override
                public void run() {
                    loadMessages();
                }
            });
        }
    }

    /** True while the WebSocket to the Astra server is connected. */
    public boolean isLive() {
        return socket != null && socket.isConnected();
    }

    private void startLive() {
        AstraApiClient api = api();
        socket = api == null ? null : api.openSocket(new AstraSocket.Listener() {
            @Override
            public void onEvent(JsonObject e) {
                String type = str(e, "type");
                if ("message".equals(type)) {
                    net.execute(new Runnable() {
                        @Override
                        public void run() {
                            loadMessages();
                        }
                    });
                } else if ("friends".equals(type)) {
                    net.execute(new Runnable() {
                        @Override
                        public void run() {
                            loadFriends();
                        }
                    });
                } else if ("emote".equals(type)) {
                    Astra astra = Astra.get();
                    if (astra != null && astra.getCosmetics() != null) {
                        astra.getCosmetics().emoteStarted(str(e, "uuid"), str(e, "id"));
                    }
                }
            }

            @Override
            public void onState(boolean connected) {
                if (connected && loaded) {
                    refresh(); // catch up on anything missed while disconnected
                }
            }
        });
        if (socket != null) {
            socket.start();
        }
    }

    /** Polls faster while the friends menu is open, and loads right away. */
    public void setMenuOpen(boolean open) {
        fast = open;
        if (open) {
            refresh();
        }
    }

    public void refresh() {
        if (!available()) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                loadFriends();
                loadMessages();
            }
        });
    }

    private void run(final Runnable task) {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } finally {
                    busy.set(false);
                }
            }
        });
    }

    // ------------------------------------------------------------------ loading

    private void loadFriends() {
        AstraApiClient api = api();
        if (api == null) {
            return;
        }
        try {
            JsonObject o = api.getJson("/api/friends").getAsJsonObject();
            List<Friend> f = list(o.getAsJsonArray("friends"));
            List<Friend> in = list(o.getAsJsonArray("incoming"));
            List<Friend> out = list(o.getAsJsonArray("outgoing"));
            for (Friend x : f) {
                names.put(x.uuid, x.name);
            }
            for (Friend x : in) {
                names.put(x.uuid, x.name);
                if (knownIncoming.add(x.uuid) && !firstFriendsLoad) {
                    Notifications.push("Friend request", x.name + " wants to be friends. Shift + Tab to answer.", x.rankColor);
                }
            }
            for (Friend x : f) {
                if (x.playing && knownPlaying.add(x.uuid) && !firstFriendsLoad) {
                    Notifications.push(x.name + " is online", x.status(), x.rankColor);
                } else if (!x.playing) {
                    knownPlaying.remove(x.uuid);
                }
            }
            if (firstFriendsLoad) {
                for (Friend x : f) {
                    if (x.playing) {
                        knownPlaying.add(x.uuid);
                    }
                }
            }
            firstFriendsLoad = false;
            friends = Collections.unmodifiableList(f);
            incoming = Collections.unmodifiableList(in);
            outgoing = Collections.unmodifiableList(out);
            if (lastMessageId < 0) {
                // Start from a little history so conversations aren't empty, without notifying for it.
                long last = o.has("lastMessageId") ? o.get("lastMessageId").getAsLong() : 0;
                lastMessageId = Math.max(0, last - 300);
                loadMessages(false);
            }
            loaded = true;
            error = null;
        } catch (AstraApiClient.ApiException e) {
            error = e.status == 401 ? "Your Astra sign-in expired. Restart from the Astra app." : e.getMessage();
        } catch (Exception e) {
            error = "Can't reach the Astra server.";
        }
    }

    private void loadMessages() {
        loadMessages(true);
    }

    private void loadMessages(boolean notify) {
        AstraApiClient api = api();
        if (api == null || lastMessageId < 0) {
            return;
        }
        try {
            JsonArray arr = api.getJson("/api/friends/messages?since=" + lastMessageId).getAsJsonObject().getAsJsonArray("messages");
            String self = selfUuid();
            for (JsonElement e : arr) {
                JsonObject m = e.getAsJsonObject();
                Message msg = new Message(m.get("id").getAsLong(), str(m, "from"), str(m, "to"), str(m, "text"), parseTime(str(m, "at")));
                if (msg.id <= lastMessageId) {
                    continue;
                }
                lastMessageId = msg.id;
                boolean mine = msg.from.equals(self);
                String other = mine ? msg.to : msg.from;
                names.put(msg.from, str(m, "fromName"));
                names.put(msg.to, str(m, "toName"));
                synchronized (chats) {
                    List<Message> list = chats.get(other);
                    if (list == null) {
                        list = new ArrayList<Message>();
                        chats.put(other, list);
                    }
                    list.add(msg);
                    if (list.size() > 300) {
                        list.remove(0);
                    }
                    if (!mine && !other.equals(reading)) {
                        Integer n = unread.get(other);
                        unread.put(other, n == null ? 1 : n + 1);
                    }
                }
                if (notify && !mine && !other.equals(reading)) {
                    Notifications.push(nameOf(other), msg.text, Notifications.MESSAGE);
                }
            }
        } catch (Exception e) {
            // next poll will retry
        }
    }

    // ------------------------------------------------------------------ actions

    public void request(final String name, final Callback cb) {
        action(cb, new Action() {
            @Override
            public void run(AstraApiClient api) throws Exception {
                JsonObject body = new JsonObject();
                body.addProperty("name", name.trim());
                api.postJson("/api/friends/request", body.toString());
            }
        });
    }

    public void accept(final String uuid, Callback cb) {
        action(cb, new Action() {
            @Override
            public void run(AstraApiClient api) throws Exception {
                JsonObject body = new JsonObject();
                body.addProperty("uuid", uuid);
                api.postJson("/api/friends/accept", body.toString());
            }
        });
    }

    /** Removes a friend, declines their request, or cancels yours. */
    public void remove(final String uuid, Callback cb) {
        action(cb, new Action() {
            @Override
            public void run(AstraApiClient api) throws Exception {
                JsonObject body = new JsonObject();
                body.addProperty("uuid", uuid);
                api.postJson("/api/friends/remove", body.toString());
            }
        });
    }

    public void send(final String toUuid, final String text, Callback cb) {
        action(cb, new Action() {
            @Override
            public void run(AstraApiClient api) throws Exception {
                JsonObject body = new JsonObject();
                body.addProperty("to", toUuid);
                body.addProperty("text", text);
                api.postJson("/api/friends/messages", body.toString());
                loadMessages(false);
            }
        });
    }

    private interface Action {
        void run(AstraApiClient api) throws Exception;
    }

    private void action(final Callback cb, final Action action) {
        final AstraApiClient api = api();
        if (api == null || !api.hasToken()) {
            if (cb != null) {
                cb.done("Start Minecraft from the Astra app (signed in) to use friends.");
            }
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    action.run(api);
                    loadFriends();
                } catch (Exception e) {
                    err = e.getMessage() == null ? "Something went wrong." : e.getMessage();
                }
                if (cb != null) {
                    cb.done(err);
                }
            }
        });
    }

    // ------------------------------------------------------------------ reading

    public List<Friend> getFriends() {
        return friends;
    }

    public List<Friend> getIncoming() {
        return incoming;
    }

    public List<Friend> getOutgoing() {
        return outgoing;
    }

    public String getError() {
        return error;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<Message> getChat(String uuid) {
        synchronized (chats) {
            List<Message> list = chats.get(uuid);
            return list == null ? Collections.<Message>emptyList() : new ArrayList<Message>(list);
        }
    }

    public int getUnread(String uuid) {
        synchronized (chats) {
            Integer n = unread.get(uuid);
            return n == null ? 0 : n;
        }
    }

    public int getTotalUnread() {
        synchronized (chats) {
            int total = 0;
            for (Integer n : unread.values()) {
                total += n;
            }
            return total;
        }
    }

    /** Marks a conversation as being read (null when none is open). */
    public void setReading(String uuid) {
        reading = uuid;
        if (uuid != null) {
            synchronized (chats) {
                unread.remove(uuid);
            }
        }
    }

    public String nameOf(String uuid) {
        String n = names.get(uuid);
        return n == null ? "Player" : n;
    }

    public String selfUuid() {
        Astra astra = Astra.get();
        String s = astra == null ? null : astra.getCosmetics().getSelfUuid();
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ helpers

    private static List<Friend> list(JsonArray arr) {
        List<Friend> out = new ArrayList<Friend>();
        if (arr != null) {
            for (JsonElement e : arr) {
                out.add(new Friend(e.getAsJsonObject()));
            }
        }
        return out;
    }

    static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private static long parseTime(String iso) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(iso.substring(0, 19)).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
