package dev.astra.client.replay;

import com.google.gson.JsonObject;

/** What a replay is: who recorded it, where, when and how long. Stored in the file and next to it. */
public final class ReplayInfo {

    public String minecraftVersion = "";
    public String server = "";
    public String playerName = "";
    public String playerUuid = "";
    public long startedAt;
    public long durationMs;
    public long sizeBytes;

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("minecraftVersion", minecraftVersion);
        o.addProperty("server", server);
        o.addProperty("playerName", playerName);
        o.addProperty("playerUuid", playerUuid);
        o.addProperty("startedAt", startedAt);
        o.addProperty("durationMs", durationMs);
        o.addProperty("sizeBytes", sizeBytes);
        return o;
    }

    public static ReplayInfo fromJson(JsonObject o) {
        ReplayInfo i = new ReplayInfo();
        i.minecraftVersion = str(o, "minecraftVersion");
        i.server = str(o, "server");
        i.playerName = str(o, "playerName");
        i.playerUuid = str(o, "playerUuid");
        i.startedAt = num(o, "startedAt");
        i.durationMs = num(o, "durationMs");
        i.sizeBytes = num(o, "sizeBytes");
        return i;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long num(JsonObject o, String k) {
        try {
            return o.has(k) ? o.get(k).getAsLong() : 0L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
