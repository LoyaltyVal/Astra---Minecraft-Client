package dev.astra.client.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astra.client.util.DataFiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Favourite and recently played servers for Quick Join (servers.json, shared by every version). */
public final class Servers {

    public static final class Entry {
        public String name;
        public final String address;
        public long lastPlayed;

        Entry(String name, String address, long lastPlayed) {
            this.name = name;
            this.address = address;
            this.lastPlayed = lastPlayed;
        }
    }

    private static final int MAX_FAVORITES = 30;
    private static final int MAX_RECENT = 8;
    private static Servers instance;

    private final List<Entry> favorites = new ArrayList<Entry>();
    private final List<Entry> recent = new ArrayList<Entry>();

    public static synchronized Servers get() {
        if (instance == null) {
            instance = new Servers();
            instance.load();
        }
        return instance;
    }

    public List<Entry> favorites() {
        return Collections.unmodifiableList(favorites);
    }

    public List<Entry> recent() {
        return Collections.unmodifiableList(recent);
    }

    /** "Mc.Hypixel.NET:25565" and "hypixel.net" are the same server. */
    public static String normalize(String address) {
        if (address == null) {
            return "";
        }
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(":25565")) {
            a = a.substring(0, a.length() - 6);
        }
        if (a.endsWith(".")) {
            a = a.substring(0, a.length() - 1);
        }
        return a.replaceFirst("^(mc|play|www)\\.", "");
    }

    public static boolean same(String a, String b) {
        return !normalize(a).isEmpty() && normalize(a).equals(normalize(b));
    }

    public boolean isFavorite(String address) {
        return find(favorites, address) != null;
    }

    /** Adds a favourite; returns an error message, or null when it worked. */
    public String addFavorite(String name, String address) {
        address = address == null ? "" : address.trim();
        if (!address.matches("[A-Za-z0-9.\\-_]+(:\\d{1,5})?")) {
            return "That isn't a server address (e.g. mc.hypixel.net)";
        }
        if (isFavorite(address)) {
            return "Already a favourite";
        }
        if (favorites.size() >= MAX_FAVORITES) {
            return "You can save up to " + MAX_FAVORITES + " favourites";
        }
        String n = name == null || name.trim().isEmpty() ? niceName(address) : name.trim();
        favorites.add(new Entry(n, address, 0));
        save();
        return null;
    }

    public void removeFavorite(String address) {
        Entry e = find(favorites, address);
        if (e != null) {
            favorites.remove(e);
            save();
        }
    }

    /** Moves a favourite up (-1) or down (+1). */
    public void move(String address, int by) {
        Entry e = find(favorites, address);
        if (e == null) {
            return;
        }
        int i = favorites.indexOf(e);
        int j = Math.max(0, Math.min(favorites.size() - 1, i + by));
        favorites.remove(i);
        favorites.add(j, e);
        save();
    }

    /** Called when you join a multiplayer server. */
    public void played(String address) {
        if (address == null || address.isEmpty() || "singleplayer".equals(address)) {
            return;
        }
        long now = System.currentTimeMillis();
        Entry fav = find(favorites, address);
        if (fav != null) {
            fav.lastPlayed = now;
        }
        Entry old = find(recent, address);
        if (old != null) {
            recent.remove(old);
        }
        recent.add(0, new Entry(niceName(address), address, now));
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        save();
    }

    public void clearRecent() {
        recent.clear();
        save();
    }

    /** "mc.hypixel.net" -> "Hypixel". */
    public static String niceName(String address) {
        String host = normalize(address).replaceAll(":\\d+$", "");
        String[] parts = host.split("\\.");
        String core = parts.length >= 2 ? parts[parts.length - 2] : host;
        if (core.isEmpty()) {
            return address;
        }
        return Character.toUpperCase(core.charAt(0)) + core.substring(1);
    }

    private static Entry find(List<Entry> list, String address) {
        for (Entry e : list) {
            if (same(e.address, address)) {
                return e;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ storage

    private void load() {
        JsonElement json = DataFiles.read("servers.json");
        if (json == null || !json.isJsonObject()) {
            // First run: a few popular PvP servers to start with.
            favorites.add(new Entry("Hypixel", "mc.hypixel.net", 0));
            favorites.add(new Entry("Minemen", "minemen.club", 0));
            favorites.add(new Entry("PvP Land", "pvp.land", 0));
            return;
        }
        read(json.getAsJsonObject().getAsJsonArray("favorites"), favorites);
        read(json.getAsJsonObject().getAsJsonArray("recent"), recent);
    }

    private static void read(JsonArray arr, List<Entry> into) {
        if (arr == null) {
            return;
        }
        for (JsonElement e : arr) {
            try {
                JsonObject o = e.getAsJsonObject();
                into.add(new Entry(o.get("name").getAsString(), o.get("address").getAsString(),
                        o.has("lastPlayed") ? o.get("lastPlayed").getAsLong() : 0));
            } catch (Exception ignored) {
                // skip a broken entry
            }
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.add("favorites", write(favorites));
        root.add("recent", write(recent));
        DataFiles.write("servers.json", root);
    }

    private static JsonArray write(List<Entry> list) {
        JsonArray arr = new JsonArray();
        for (Entry e : list) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name);
            o.addProperty("address", e.address);
            o.addProperty("lastPlayed", e.lastPlayed);
            arr.add(o);
        }
        return arr;
    }
}
