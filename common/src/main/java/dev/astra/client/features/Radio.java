package dev.astra.client.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astra.client.Astra;
import dev.astra.client.cosmetics.AstraApiClient;
import dev.astra.client.util.DataFiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Radio stations (from the Astra server, so the owner can change them, with built-in defaults) and
 * your radio settings: volume, last station, resume on launch (radio.json).
 */
public final class Radio {

    public static final class Station {
        public final String id;
        public final String name;
        public final String genre;
        public final String url;

        public Station(String id, String name, String genre, String url) {
            this.id = id;
            this.name = name;
            this.genre = genre;
            this.url = url;
        }
    }

    /** Used until the server answers (or when the game wasn't started from the Astra app). */
    private static final Station[] DEFAULTS = {
            new Station("groovesalad", "Groove Salad", "Ambient / downtempo", "https://ice.somafm.com/groovesalad-128-mp3"),
            new Station("fluid", "Fluid", "Instrumental hip hop", "https://ice.somafm.com/fluid-128-mp3"),
            new Station("beatblender", "Beat Blender", "Deep house", "https://ice.somafm.com/beatblender-128-mp3"),
            new Station("defcon", "DEF CON Radio", "Electronic for hacking", "https://ice.somafm.com/defcon-128-mp3"),
            new Station("lush", "Lush", "Chill vocals", "https://ice.somafm.com/lush-128-mp3"),
            new Station("dronezone", "Drone Zone", "Ambient space", "https://ice.somafm.com/dronezone-128-mp3"),
            new Station("radioparadise", "Radio Paradise", "Eclectic rock & more", "https://stream.radioparadise.com/mp3-128")
    };

    private static Radio instance;

    private volatile List<Station> stations;
    private boolean fetched;
    private String lastStation;
    private String customUrl = "";
    private boolean resume;

    public static synchronized Radio get() {
        if (instance == null) {
            instance = new Radio();
            instance.load();
        }
        return instance;
    }

    private Radio() {
        List<Station> list = new ArrayList<Station>();
        Collections.addAll(list, DEFAULTS);
        stations = Collections.unmodifiableList(list);
    }

    public List<Station> stations() {
        fetch();
        return stations;
    }

    /** Asks the Astra server for its station list once. */
    private synchronized void fetch() {
        if (fetched) {
            return;
        }
        fetched = true;
        Astra astra = Astra.get();
        final AstraApiClient api = astra == null || astra.getCosmetics() == null ? null : astra.getCosmetics().getApi();
        if (api == null) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Station> list = new ArrayList<Station>();
                    for (JsonElement e : api.getJson("/api/radio").getAsJsonObject().getAsJsonArray("stations")) {
                        JsonObject o = e.getAsJsonObject();
                        String url = str(o, "url");
                        if (url != null && url.matches("https?://.+")) {
                            list.add(new Station(str(o, "id") == null ? url : str(o, "id"), str(o, "name") == null ? url : str(o, "name"),
                                    str(o, "genre") == null ? "" : str(o, "genre"), url));
                        }
                    }
                    if (!list.isEmpty()) {
                        stations = Collections.unmodifiableList(list);
                    }
                } catch (Exception ignored) {
                    // keep the built-in list
                }
            }
        }, "Astra radio stations");
        t.setDaemon(true);
        t.start();
    }

    public Station find(String idOrUrl) {
        if (idOrUrl == null) {
            return null;
        }
        for (Station s : stations()) {
            if (s.id.equals(idOrUrl) || s.url.equals(idOrUrl)) {
                return s;
            }
        }
        return idOrUrl.matches("https?://.+") ? custom(idOrUrl) : null;
    }

    public static Station custom(String url) {
        String host = url.replaceFirst("^https?://", "").replaceFirst("[/:?].*$", "");
        return new Station(url, host, "Your stream", url);
    }

    // ------------------------------------------------------------------ playback + settings

    public void play(Station s) {
        RadioPlayer.get().play(s);
        lastStation = s.id;
        save();
    }

    public void stop() {
        RadioPlayer.get().stop();
    }

    public void setVolume(float v) {
        RadioPlayer.get().setVolume(v);
    }

    /** Saves the volume (call when the slider is let go, not every frame). */
    public void saveVolume() {
        save();
    }

    public String getCustomUrl() {
        return customUrl;
    }

    public void setCustomUrl(String url) {
        customUrl = url == null ? "" : url.trim();
        save();
    }

    public boolean isResume() {
        return resume;
    }

    public void setResume(boolean on) {
        resume = on;
        save();
    }

    /** At start-up: picks the last station back up when "resume" is on. */
    public void startUp() {
        if (resume && lastStation != null) {
            Station s = find(lastStation);
            if (s != null) {
                RadioPlayer.get().play(s);
            }
        }
    }

    private void load() {
        JsonElement json = DataFiles.read("radio.json");
        if (json == null || !json.isJsonObject()) {
            return;
        }
        JsonObject o = json.getAsJsonObject();
        if (o.has("volume")) {
            RadioPlayer.get().setVolume(o.get("volume").getAsFloat());
        }
        lastStation = str(o, "last");
        customUrl = str(o, "custom") == null ? "" : str(o, "custom");
        resume = o.has("resume") && o.get("resume").getAsBoolean();
    }

    private void save() {
        JsonObject o = new JsonObject();
        o.addProperty("volume", RadioPlayer.get().getVolume());
        if (lastStation != null) {
            o.addProperty("last", lastStation);
        }
        o.addProperty("custom", customUrl);
        o.addProperty("resume", resume);
        DataFiles.write("radio.json", o);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
