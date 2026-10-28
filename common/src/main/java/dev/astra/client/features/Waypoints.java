package dev.astra.client.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astra.client.Astra;
import dev.astra.client.platform.Platform;
import dev.astra.client.util.DataFiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved locations, per server (or singleplayer world) and dimension, in waypoints.json (shared by
 * every version).
 */
public final class Waypoints {

    public static final int[] COLORS = {
            0xFF9B7BFF, 0xFF5AC8FA, 0xFF3DDC97, 0xFFFFD166, 0xFFFF8A4C, 0xFFFF5C7A, 0xFFF06292, 0xFFFFFFFF
    };

    public static final class Waypoint {
        public String name;
        public final int x;
        public final int y;
        public final int z;
        public final int dimension;
        public int color;
        public boolean visible = true;
        public final long created;

        public Waypoint(String name, int x, int y, int z, int dimension, int color, long created) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.color = color;
            this.created = created;
        }
    }

    private static final int MAX_PER_WORLD = 100;
    private static Waypoints instance;

    private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<String, List<Waypoint>>();

    public static synchronized Waypoints get() {
        if (instance == null) {
            instance = new Waypoints();
            instance.load();
        }
        return instance;
    }

    /** "server:hypixel.net" or "sp:My World"; null when not in a world. */
    public static String worldKey() {
        Platform p = Astra.platform();
        if (!p.isInGame()) {
            return null;
        }
        String server = p.getServerAddress();
        if (server != null && !"singleplayer".equals(server)) {
            return "server:" + Servers.normalize(server);
        }
        String world = p.getWorldName();
        return "sp:" + (world == null ? "world" : world);
    }

    /** A readable name for the current world, for the menu's title. */
    public static String worldLabel() {
        String key = worldKey();
        if (key == null) {
            return "";
        }
        return key.startsWith("server:") ? key.substring(7) : key.substring(3) + " (singleplayer)";
    }

    public static String dimensionName(int dim) {
        return dim == -1 ? "Nether" : dim == 1 ? "End" : dim == 0 ? "Overworld" : "Dimension " + dim;
    }

    /** Waypoints in the current world (every dimension). */
    public List<Waypoint> here() {
        String key = worldKey();
        List<Waypoint> list = key == null ? null : worlds.get(key);
        return list == null ? Collections.<Waypoint>emptyList() : Collections.unmodifiableList(list);
    }

    /** Adds a waypoint at the player's feet. Returns it, or null when not possible. */
    public Waypoint addHere(String name, int color) {
        String key = worldKey();
        if (key == null) {
            return null;
        }
        Platform p = Astra.platform();
        List<Waypoint> list = worlds.get(key);
        if (list == null) {
            list = new ArrayList<Waypoint>();
            worlds.put(key, list);
        }
        if (list.size() >= MAX_PER_WORLD) {
            return null;
        }
        Waypoint w = new Waypoint(name, floor(p.getPlayerX()), floor(p.getPlayerY()), floor(p.getPlayerZ()),
                p.getDimension(), color, System.currentTimeMillis());
        list.add(w);
        save();
        return w;
    }

    /** The death marker: only the latest one per world is kept. */
    public void setDeath(int x, int y, int z, int dimension) {
        String key = worldKey();
        if (key == null) {
            return;
        }
        List<Waypoint> list = worlds.get(key);
        if (list == null) {
            list = new ArrayList<Waypoint>();
            worlds.put(key, list);
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            if ("Death".equals(list.get(i).name)) {
                list.remove(i);
            }
        }
        list.add(new Waypoint("Death", x, y, z, dimension, 0xFFFF5C7A, System.currentTimeMillis()));
        save();
    }

    public void remove(Waypoint w) {
        for (List<Waypoint> list : worlds.values()) {
            if (list.remove(w)) {
                save();
                return;
            }
        }
    }

    /** Next unused name like "Waypoint 3". */
    public String nextName() {
        int n = here().size() + 1;
        while (true) {
            boolean taken = false;
            for (Waypoint w : here()) {
                if (w.name.equals("Waypoint " + n)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return "Waypoint " + n;
            }
            n++;
        }
    }

    public int nextColor() {
        return COLORS[here().size() % COLORS.length];
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    // ------------------------------------------------------------------ storage

    private void load() {
        JsonElement json = DataFiles.read("waypoints.json");
        if (json == null || !json.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> world : json.getAsJsonObject().entrySet()) {
            List<Waypoint> list = new ArrayList<Waypoint>();
            for (JsonElement e : world.getValue().getAsJsonArray()) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    Waypoint w = new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                            o.get("z").getAsInt(), o.has("dim") ? o.get("dim").getAsInt() : 0,
                            o.has("color") ? (int) Long.parseLong(o.get("color").getAsString(), 16) : COLORS[0],
                            o.has("created") ? o.get("created").getAsLong() : 0);
                    w.visible = !o.has("visible") || o.get("visible").getAsBoolean();
                    list.add(w);
                } catch (Exception ignored) {
                    // skip a broken entry
                }
            }
            worlds.put(world.getKey(), list);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> world : worlds.entrySet()) {
            if (world.getValue().isEmpty()) {
                continue;
            }
            JsonArray arr = new JsonArray();
            for (Waypoint w : world.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name);
                o.addProperty("x", w.x);
                o.addProperty("y", w.y);
                o.addProperty("z", w.z);
                o.addProperty("dim", w.dimension);
                o.addProperty("color", Integer.toHexString(w.color));
                o.addProperty("visible", w.visible);
                o.addProperty("created", w.created);
                arr.add(o);
            }
            root.add(world.getKey(), arr);
        }
        DataFiles.write("waypoints.json", root);
    }
}
