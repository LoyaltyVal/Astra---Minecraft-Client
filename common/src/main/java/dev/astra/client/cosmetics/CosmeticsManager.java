package dev.astra.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astra.client.Astra;
import dev.astra.client.module.impl.CosmeticsModule;
import dev.astra.client.platform.Platform;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-side cosmetics: everyone's capes, wings and rank tags come from the Astra server.
 *
 * The Astra app signs you in to the server and starts the game with
 *   -Dastra.api=https://your-server   -Dastra.token=&lt;session token&gt;
 *
 * Then, on background threads:
 *   - your account (what you own and wear) is loaded from /api/me, the catalog from /api/cosmetics;
 *   - every 2 seconds the players in your world are looked up in one batch (/api/players?uuids=...),
 *     each answer cached for a minute, so every Astra player's cape, wings and rank show for you;
 *   - cosmetic images are downloaded once, turned into textures by {@link CosmeticImages}, and
 *     uploaded to the GPU on the render thread the first time they're drawn.
 * Equipping (from the in-game menu, the app or the website) is saved on the server, so everyone sees it.
 */
public final class CosmeticsManager {

    public enum Type {
        CAPE("cape"),
        WINGS("wings");

        public final String id;

        Type(String id) {
            this.id = id;
        }

        static Type of(String id) {
            return "wings".equals(id) ? WINGS : CAPE;
        }
    }

    /** One cosmetic from the server's catalog. */
    public static final class Info {
        public final String id;
        public final String name;
        public final Type type;
        public final String rarity;

        Info(String id, String name, Type type, String rarity) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.rarity = rarity;
        }
    }

    /** What the server said about one player (or that they don't use Astra). */
    static final class PlayerInfo {
        final boolean astra;
        final String rankName;
        final String rankColor;   // Minecraft colour code, e.g. "c"
        final boolean ranked;     // anything above Player gets a tag
        final String cape;
        final String wings;
        final long fetched;

        PlayerInfo(boolean astra, String rankName, String rankColor, boolean ranked,
                   String cape, String wings, long fetched) {
            this.astra = astra;
            this.rankName = rankName;
            this.rankColor = rankColor;
            this.ranked = ranked;
            this.cape = cape;
            this.wings = wings;
            this.fetched = fetched;
        }

        static PlayerInfo notAstra(long fetched) {
            return new PlayerInfo(false, null, null, false, null, null, fetched);
        }

        PlayerInfo withEquipped(Type type, String id) {
            return new PlayerInfo(true, rankName, rankColor, ranked,
                    type == Type.CAPE ? id : cape, type == Type.WINGS ? id : wings, fetched);
        }
    }

    /** A processed image, uploaded or waiting to be. */
    public static final class Texture {
        public final int width;
        public final int height;
        final BufferedImage image;
        Object handle;

        Texture(BufferedImage image) {
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        public Object getHandle() {
            return handle;
        }
    }

    private static final long LOOKUP_TTL = 60000L;        // re-ask about a player after a minute
    private static final long FORGET_AFTER = 10 * 60000L; // drop players not seen for 10 minutes
    private static final int LOOKUP_EVERY = 40;           // ticks (2 seconds)
    private static final int ACCOUNT_EVERY = 20 * 60 * 5; // ticks (5 minutes)
    private static final int BATCH = 150;

    private final AstraApiClient api;
    private final ExecutorService net = executor("Astra cosmetics network");
    private final ExecutorService images = executor("Astra cosmetics images");
    private final AtomicBoolean lookupBusy = new AtomicBoolean();

    private final Map<String, Info> catalog = new ConcurrentHashMap<String, Info>();
    private final Map<String, PlayerInfo> players = new ConcurrentHashMap<String, PlayerInfo>();
    private final Map<String, Texture> textures = new ConcurrentHashMap<String, Texture>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<String, Boolean>();
    private final Map<String, String> failed = new ConcurrentHashMap<String, String>();
    private final Map<UUID, String> keys = new ConcurrentHashMap<UUID, String>();

    private volatile String selfUuid;
    private volatile List<String> owned = Collections.emptyList();
    private volatile String status;
    private volatile boolean signedIn;
    private int ticks;
    private String reportedPresence = "";
    private int lastPresenceTick = -100000;

    /**
     * @param apiUrl the Astra server, or null when the game wasn't started by the Astra app
     * @param token  the session token from the app, or null (others' cosmetics still show)
     */
    public CosmeticsManager(String apiUrl, String token) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            api = null;
            status = "Start Minecraft from the Astra app to use cosmetics.";
            return;
        }
        api = new AstraApiClient(apiUrl.trim(), token);
        status = "Connecting to the Astra server...";
        refresh();
    }

    // ------------------------------------------------------------------ status & account

    /** A message for the cosmetics page when something isn't working, or null when all is well. */
    public String getStatus() {
        return status;
    }

    public boolean isSignedIn() {
        return signedIn;
    }

    /** Reloads the catalog and your account in the background. */
    public void refresh() {
        if (api == null) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                loadAccount();
            }
        });
    }

    private void loadAccount() {
        try {
            Map<String, Info> fresh = new ConcurrentHashMap<String, Info>();
            for (JsonElement e : api.getJson("/api/cosmetics").getAsJsonObject().getAsJsonArray("cosmetics")) {
                Info info = info(e.getAsJsonObject());
                fresh.put(info.id, info);
            }
            if (!api.hasToken()) {
                catalog.putAll(fresh);
                signedIn = false;
                status = "Sign in through the Astra app to wear cosmetics. Other players' cosmetics still show.";
                return;
            }
            JsonObject me = api.getJson("/api/me").getAsJsonObject().getAsJsonObject("player");
            List<String> mine = new ArrayList<String>();
            for (JsonElement e : me.getAsJsonArray("owned")) {
                Info info = info(e.getAsJsonObject());
                fresh.put(info.id, info); // owned items can be hidden from the public catalog
                mine.add(info.id);
            }
            catalog.putAll(fresh);
            catalog.keySet().retainAll(fresh.keySet());
            owned = Collections.unmodifiableList(mine);
            String uuid = normalize(string(me, "uuid"));
            JsonObject eq = me.getAsJsonObject("equippedIds");
            players.put(uuid, player(me, string(eq, "cape"), string(eq, "wings")));
            selfUuid = uuid;
            signedIn = true;
            status = null;
        } catch (AstraApiClient.ApiException e) {
            signedIn = false;
            status = e.status == 401 ? "Your Astra sign-in expired. Restart the game from the Astra app."
                    : e.status == 403 ? e.getMessage()
                    : e.getMessage() + ". Retrying in a few minutes.";
        } catch (Exception e) {
            status = "Couldn't load cosmetics: " + e.getMessage();
            System.err.println("[Astra] Cosmetics account load failed: " + e);
        }
    }

    /** Called every client tick: looks up the players around you. */
    public void tick() {
        if (api == null) {
            return;
        }
        ticks++;
        if (ticks % ACCOUNT_EVERY == 0) {
            refresh();
        }
        reportPresence();
        if (ticks % LOOKUP_EVERY != 0 || lookupBusy.get()) {
            return;
        }
        Platform p = Astra.platform();
        if (!p.isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        final List<String> wanted = new ArrayList<String>();
        for (String uuid : p.getWorldPlayerUuids()) {
            String id = normalize(uuid);
            if (id == null || id.equals(selfUuid) || wanted.contains(id)) {
                continue;
            }
            PlayerInfo known = players.get(id);
            if (known == null || now - known.fetched > LOOKUP_TTL) {
                wanted.add(id);
            }
            if (wanted.size() >= BATCH) {
                break;
            }
        }
        forgetOld(now);
        if (wanted.isEmpty() || !lookupBusy.compareAndSet(false, true)) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    lookup(wanted);
                } finally {
                    lookupBusy.set(false);
                }
            }
        });
    }

    /**
     * Tells the Astra server where you are (for the website's Players page): right away when it
     * changes, and every 30 seconds while in game so you show as online.
     */
    private void reportPresence() {
        if (!signedIn || ticks % 20 != 0) {
            return;
        }
        Platform p = Astra.platform();
        CosmeticsModule settings = settings();
        final String server = p.getServerAddress();
        final boolean hidden = settings != null && !settings.shareServer();
        final String version = server == null ? null : p.minecraftVersion();
        String now = server + "|" + hidden;
        boolean changed = !now.equals(reportedPresence);
        boolean due = server != null && ticks - lastPresenceTick >= 600;
        if (!changed && !due) {
            return;
        }
        reportedPresence = now;
        lastPresenceTick = ticks;
        net.execute(new Runnable() {
            @Override
            public void run() {
                JsonObject body = new JsonObject();
                body.addProperty("server", server);
                body.addProperty("version", version);
                body.addProperty("hidden", hidden);
                try {
                    api.postJson("/api/me/presence", body.toString());
                } catch (Exception e) {
                    reportedPresence = ""; // try again next time
                }
            }
        });
    }

    private void lookup(List<String> uuids) {
        StringBuilder q = new StringBuilder();
        for (String id : uuids) {
            q.append(q.length() == 0 ? "" : ",").append(id);
        }
        long now = System.currentTimeMillis();
        try {
            JsonObject found = api.getJson("/api/players?uuids=" + q).getAsJsonObject().getAsJsonObject("players");
            for (String id : uuids) {
                if (found.has(id)) {
                    JsonObject o = found.getAsJsonObject(id);
                    players.put(id, player(o, string(o, "cape"), string(o, "wings")));
                } else {
                    players.put(id, PlayerInfo.notAstra(now));
                }
            }
        } catch (Exception e) {
            // Server unreachable: ask about these players again in 30 seconds instead of every round.
            for (String id : uuids) {
                if (!players.containsKey(id)) {
                    players.put(id, PlayerInfo.notAstra(now - LOOKUP_TTL / 2));
                }
            }
        }
    }

    private void forgetOld(long now) {
        Iterator<Map.Entry<String, PlayerInfo>> it = players.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PlayerInfo> e = it.next();
            if (!e.getKey().equals(selfUuid) && now - e.getValue().fetched > FORGET_AFTER) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ the cosmetics page

    /** The ids of everything you can wear of this type (free ones plus what you were given), by name. */
    public List<String> list(Type type) {
        List<String> ids = new ArrayList<String>();
        for (String id : owned) {
            Info info = catalog.get(id);
            if (info != null && info.type == type) {
                ids.add(id);
            }
        }
        Collections.sort(ids, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return getName(a).compareToIgnoreCase(getName(b));
            }
        });
        return ids;
    }

    public String getName(String id) {
        Info info = catalog.get(id);
        return info == null ? id : info.name;
    }

    public String getRarity(String id) {
        Info info = catalog.get(id);
        return info == null ? "common" : info.rarity;
    }

    /** The id you're wearing of this type, or null. */
    public String getEquipped(Type type) {
        PlayerInfo me = selfUuid == null ? null : players.get(selfUuid);
        if (me == null) {
            return null;
        }
        return type == Type.CAPE ? me.cape : me.wings;
    }

    /** Wears (or, with null, takes off) a cosmetic. Shows straight away; saved on the server. */
    public void equip(final Type type, final String id) {
        if (api == null || selfUuid == null) {
            return;
        }
        final String uuid = selfUuid;
        final String before = getEquipped(type);
        PlayerInfo me = players.get(uuid);
        if (me != null) {
            players.put(uuid, me.withEquipped(type, id));
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                JsonObject body = new JsonObject();
                body.addProperty("type", type.id);
                body.addProperty("id", id);
                try {
                    api.postJson("/api/me/equip", body.toString());
                    status = null;
                } catch (Exception e) {
                    PlayerInfo now = players.get(uuid);
                    if (now != null) {
                        players.put(uuid, now.withEquipped(type, before));
                    }
                    status = "Couldn't save: " + e.getMessage();
                }
            }
        });
    }

    /** The processed texture for a cosmetic, or null while it downloads (or if it failed). */
    public Texture getTexture(Type type, String id) {
        if (id == null || api == null) {
            return null;
        }
        Texture tex = textures.get(id);
        if (tex == null) {
            download(id);
            return null;
        }
        if (tex.handle == null) {
            String name = "astra_cosmetic_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            tex.handle = Astra.platform().createTexture(name, tex.image);
        }
        return tex;
    }

    public String getError(Type type, String id) {
        return id == null ? null : failed.get(id);
    }

    private void download(final String id) {
        if (failed.containsKey(id) || loading.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }
        images.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Info info = catalog.get(id);
                    if (info == null) {
                        // Someone is wearing something we haven't seen yet: learn the new catalog.
                        reloadCatalog();
                        info = catalog.get(id);
                    }
                    byte[] data = api.getBytes("/api/cosmetics/" + id + "/image");
                    BufferedImage img = CosmeticImages.read(data);
                    Type type = info != null ? info.type
                            : CosmeticImages.isCapeTemplate(img) ? Type.CAPE : Type.WINGS;
                    img = type == Type.CAPE ? CosmeticImages.toCapeTexture(img) : CosmeticImages.toWingTexture(img);
                    textures.put(id, new Texture(img));
                } catch (AstraApiClient.ApiException e) {
                    if (e.status == 404) {
                        failed.put(id, "Removed from the server");
                    }
                    // Network trouble: it's tried again the next time it's drawn.
                } catch (Throwable e) {
                    failed.put(id, e.getMessage() == null ? e.toString() : e.getMessage());
                    System.err.println("[Astra] Couldn't load cosmetic " + id + ": " + e);
                } finally {
                    loading.remove(id);
                }
            }
        });
    }

    private void reloadCatalog() {
        try {
            for (JsonElement e : api.getJson("/api/cosmetics").getAsJsonObject().getAsJsonArray("cosmetics")) {
                Info info = info(e.getAsJsonObject());
                catalog.put(info.id, info);
            }
        } catch (Exception ignored) {
            // the type is guessed from the image instead
        }
    }

    // ------------------------------------------------------------------ rendering hooks (any player)

    private PlayerInfo visible(UUID uuid) {
        String id = key(uuid);
        if (id == null) {
            return null;
        }
        PlayerInfo info = players.get(id);
        if (info == null || !info.astra) {
            return null;
        }
        CosmeticsModule settings = settings();
        if (settings != null && !id.equals(selfUuid) && !settings.showOthers()) {
            return null;
        }
        return info;
    }

    /** The cape texture handle for a player, or null to let vanilla decide (used by the cape mixin). */
    public Object getCapeTexture(UUID uuid) {
        CosmeticsModule settings = settings();
        if (settings != null && !settings.showCape()) {
            return null;
        }
        PlayerInfo info = visible(uuid);
        if (info == null || info.cape == null) {
            return null;
        }
        Texture tex = getTexture(Type.CAPE, info.cape);
        return tex == null ? null : tex.handle;
    }

    /** "§c[Owner] " for ranked Astra players, or null. Drawn in front of their name tag. */
    public String getRankTag(UUID uuid) {
        CosmeticsModule settings = settings();
        if (settings != null && !settings.showRankTags()) {
            return null;
        }
        PlayerInfo info = visible(uuid);
        if (info == null || !info.ranked || info.rankName == null) {
            return null;
        }
        return "§" + info.rankColor + "[" + info.rankName + "]§r ";
    }

    /**
     * Draws a player's wings. Called from the player renderer while the GL matrix is in the
     * player model's space (1 unit = 1 block, y pointing down, +z = behind the player).
     */
    public void renderWings(UUID uuid, float ageTicks, boolean sneaking) {
        CosmeticsModule settings = settings();
        if (settings == null || !settings.showWings()) {
            return;
        }
        PlayerInfo info = visible(uuid);
        if (info == null || info.wings == null) {
            return;
        }
        Texture tex = getTexture(Type.WINGS, info.wings);
        if (tex == null) {
            return;
        }
        Platform p = Astra.platform();

        // Which part of the image is one wing, and which way it points.
        boolean pair = settings.wingMode().equals("Pair")
                || (settings.wingMode().equals("Auto") && tex.width > tex.height * 1.6f);
        boolean pointsLeft = settings.wingMode().equals("Single (points left)");
        float u0 = pair ? 0.5f : 0f;
        float u1 = 1f;
        if (pointsLeft) {
            u0 = 1f;
            u1 = 0f;
        }
        float imageW = tex.width * Math.abs(u1 - u0);
        float wingW = 0.8f * settings.wingSize();
        float wingH = Math.min(wingW * 1.4f, wingW * tex.height / Math.max(1f, imageW));

        // A slow, eased flap: wings rest mostly open and beat gently.
        float t = ageTicks * 0.09f * settings.flapSpeed();
        float flap = (float) Math.sin(t);
        flap = flap * Math.abs(flap); // ease in/out so they linger at the ends of each beat
        float spread = sneaking ? 18f : 34f + flap * 14f;   // swept back from the body
        float lift = sneaking ? 6f : 14f + flap * 10f;      // raised above the shoulders
        float tilt = sneaking ? 10f : 18f;                  // tips lean back

        GL11.glPushMatrix();
        if (sneaking) {
            GL11.glRotatef(28.6f, 1f, 0f, 0f); // match the body lean
            GL11.glTranslatef(0f, 0.05f, 0f);
        }
        // Root between the shoulder blades, just behind the back (and behind the cape).
        GL11.glTranslatef(0f, 0.22f, 0.16f);

        p.bindTexture(tex.handle);
        // Smooth filtering: wing art is drawn much smaller than the image, so nearest-pixel
        // sampling (Minecraft's default) makes jagged, shimmering edges.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        p.color(1f, 1f, 1f, 1f);
        p.blend(true);
        p.cull(false);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);

        for (int side = -1; side <= 1; side += 2) {
            GL11.glPushMatrix();
            GL11.glTranslatef(side * 0.06f, 0f, 0f);
            GL11.glRotatef(-side * spread, 0f, 1f, 0f);   // sweep the tip backwards
            GL11.glRotatef(-side * lift, 0f, 0f, 1f);     // raise the tip
            GL11.glRotatef(tilt, 1f, 0f, 0f);             // lean the top back
            float tip = side * wingW;
            // The wing's root sits at the top of the image third; most of it hangs below.
            float top = -wingH * 0.45f;
            float bottom = wingH * 0.55f;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glNormal3f(0f, 0f, 1f);
            GL11.glTexCoord2f(u0, 0f);
            GL11.glVertex3f(0f, top, 0f);
            GL11.glTexCoord2f(u0, 1f);
            GL11.glVertex3f(0f, bottom, 0f);
            GL11.glTexCoord2f(u1, 1f);
            GL11.glVertex3f(tip, bottom, 0f);
            GL11.glTexCoord2f(u1, 0f);
            GL11.glVertex3f(tip, top, 0f);
            GL11.glEnd();
            GL11.glPopMatrix();
        }

        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        p.cull(true);
        p.blend(false);
        GL11.glPopMatrix();
    }

    // ------------------------------------------------------------------ helpers

    private static Info info(JsonObject o) {
        return new Info(string(o, "id"), string(o, "name"), Type.of(string(o, "type")),
                o.has("rarity") ? string(o, "rarity") : "common");
    }

    private static PlayerInfo player(JsonObject o, String cape, String wings) {
        JsonObject rank = o.has("rank") && o.get("rank").isJsonObject() ? o.getAsJsonObject("rank") : new JsonObject();
        String rankId = string(rank, "id");
        String mc = string(rank, "mc");
        return new PlayerInfo(true, string(rank, "name"),
                mc == null || mc.length() != 1 ? "7" : mc,
                rankId != null && !"player".equals(rankId),
                cape, wings, System.currentTimeMillis());
    }

    /** The server's form of a UUID (32 hex characters), cached because it's needed every frame. */
    private String key(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String k = keys.get(uuid);
        if (k == null) {
            if (keys.size() > 2000) {
                keys.clear();
            }
            k = uuid.toString().replace("-", "");
            keys.put(uuid, k);
        }
        return k;
    }

    /** 32 lowercase hex characters, no dashes; null if it isn't a UUID. */
    static String normalize(String uuid) {
        if (uuid == null) {
            return null;
        }
        String s = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return s.matches("[0-9a-f]{32}") ? s : null;
    }

    private static String string(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return null;
        }
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static CosmeticsModule settings() {
        return Astra.get().getModules().get(CosmeticsModule.class);
    }

    private static ExecutorService executor(final String name) {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            }
        });
    }
}
