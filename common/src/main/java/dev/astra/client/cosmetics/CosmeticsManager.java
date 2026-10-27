package dev.astra.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.astra.client.Astra;
import dev.astra.client.module.impl.CosmeticsModule;
import dev.astra.client.platform.Platform;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Capes and wings made from your own images.
 *
 * Folder layout (shared with the Astra Launcher, which passes -Dastra.cosmetics=...):
 *   cosmetics/capes/*.png     any picture, or a standard 64x32 cape template
 *   cosmetics/wings/*.png     a single wing (root on the left, tip on the right) or a pair
 *   cosmetics/equipped.json   {"cape": "name.png", "wings": "name.png"}
 *
 * Images are decoded on a background thread and uploaded to the GPU on the render thread.
 * Cosmetics are drawn on your own player; other players see them only once a cosmetics server
 * shares them (see README).
 */
public final class CosmeticsManager {

    public enum Type {
        CAPE("capes"),
        WINGS("wings");

        public final String folder;

        Type(String folder) {
            this.folder = folder;
        }
    }

    /** A processed image, uploaded or waiting to be. */
    public static final class Texture {
        public final int width;
        public final int height;
        final long modified;
        final BufferedImage image;
        Object handle;

        Texture(BufferedImage image, long modified) {
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.modified = modified;
        }

        public Object getHandle() {
            return handle;
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final List<String> EXTENSIONS = Arrays.asList(".png", ".jpg", ".jpeg");

    private final File root;
    private final Map<String, Texture> textures = new ConcurrentHashMap<String, Texture>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<String, Boolean>();
    private final Map<String, String> failed = new ConcurrentHashMap<String, String>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Astra cosmetics loader");
            t.setDaemon(true);
            return t;
        }
    });

    private String cape;
    private String wings;
    private long equippedModified = -1;
    private int ticks;

    public CosmeticsManager(File root) {
        this.root = root;
        for (Type type : Type.values()) {
            new File(root, type.folder).mkdirs();
        }
        readEquipped();
    }

    public File getRoot() {
        return root;
    }

    public File getFolder(Type type) {
        return new File(root, type.folder);
    }

    // ------------------------------------------------------------------ listing & equipping

    public List<String> list(Type type) {
        List<String> names = new ArrayList<String>();
        File[] files = getFolder(type).listFiles();
        if (files != null) {
            for (File f : files) {
                String lower = f.getName().toLowerCase(Locale.ROOT);
                for (String ext : EXTENSIONS) {
                    if (f.isFile() && lower.endsWith(ext)) {
                        names.add(f.getName());
                        break;
                    }
                }
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public String getEquipped(Type type) {
        return type == Type.CAPE ? cape : wings;
    }

    public void equip(Type type, String name) {
        if (type == Type.CAPE) {
            cape = name;
        } else {
            wings = name;
        }
        writeEquipped();
    }

    public String getError(Type type, String name) {
        return failed.get(key(type, name));
    }

    /** Called every client tick: picks up changes the launcher makes to equipped.json. */
    public void tick() {
        if (++ticks % 40 == 0) {
            File file = equippedFile();
            if (file.lastModified() != equippedModified) {
                readEquipped();
            }
        }
    }

    // ------------------------------------------------------------------ textures

    /** The processed texture for an image, or null while it's still loading (or if it failed). */
    public Texture getTexture(Type type, String name) {
        if (name == null) {
            return null;
        }
        final String key = key(type, name);
        final File file = new File(getFolder(type), name);
        Texture tex = textures.get(key);
        if (tex != null && tex.modified != file.lastModified()) {
            textures.remove(key);
            failed.remove(key);
            tex = null;
        }
        if (tex == null) {
            if (!failed.containsKey(key) && loading.putIfAbsent(key, Boolean.TRUE) == null) {
                final Type t = type;
                loader.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            BufferedImage img = CosmeticImages.read(file);
                            img = t == Type.CAPE ? CosmeticImages.toCapeTexture(img) : CosmeticImages.toWingTexture(img);
                            textures.put(key, new Texture(img, file.lastModified()));
                        } catch (Throwable e) {
                            failed.put(key, e.getMessage() == null ? e.toString() : e.getMessage());
                            System.err.println("[Astra] Couldn't load cosmetic " + file + ": " + e);
                        } finally {
                            loading.remove(key);
                        }
                    }
                });
            }
            return null;
        }
        if (tex.handle == null) {
            tex.handle = Astra.platform().createTexture("astra_" + key.replaceAll("[^a-z0-9_]", "_"), tex.image);
        }
        return tex;
    }

    /** The equipped cape's texture handle, or null for no cape (used by the cape mixin). */
    public Object getCapeTexture() {
        CosmeticsModule settings = settings();
        if (cape == null || (settings != null && !settings.showCape())) {
            return null;
        }
        Texture tex = getTexture(Type.CAPE, cape);
        return tex == null ? null : tex.handle;
    }

    // ------------------------------------------------------------------ wings

    /**
     * Draws the equipped wings. Called from the player renderer while the GL matrix is in the
     * player model's space (1 unit = 1 block, y pointing down, +z = behind the player).
     */
    public void renderWings(float ageTicks, boolean sneaking) {
        CosmeticsModule settings = settings();
        if (wings == null || settings == null || !settings.showWings()) {
            return;
        }
        Texture tex = getTexture(Type.WINGS, wings);
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
        float wingW = 0.9f * settings.wingSize();
        float wingH = wingW * tex.height / Math.max(1f, imageW);

        float flap = (float) Math.sin(ageTicks * 0.12f * settings.flapSpeed());
        float spread = sneaking ? 15f : 22f + flap * 18f;
        float lift = sneaking ? 4f : 8f + flap * 6f;

        GL11.glPushMatrix();
        if (sneaking) {
            GL11.glRotatef(28.6f, 1f, 0f, 0f); // match the body lean
        }
        GL11.glTranslatef(0f, 0.2f, 0.14f);

        p.bindTexture(tex.handle);
        p.color(1f, 1f, 1f, 1f);
        p.blend(true);
        p.cull(false);

        for (int side = -1; side <= 1; side += 2) {
            GL11.glPushMatrix();
            GL11.glTranslatef(side * 0.04f, 0f, 0f);
            GL11.glRotatef(-side * spread, 0f, 1f, 0f);   // sweep the tip backwards
            GL11.glRotatef(-side * lift, 0f, 0f, 1f);     // and slightly up
            float tip = side * wingW;
            float top = -wingH * 0.6f;
            float bottom = wingH * 0.4f;
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

        p.cull(true);
        p.blend(false);
        GL11.glPopMatrix();
    }

    // ------------------------------------------------------------------ equipped.json

    private File equippedFile() {
        return new File(root, "equipped.json");
    }

    private void readEquipped() {
        File file = equippedFile();
        equippedModified = file.lastModified();
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF8);
            @SuppressWarnings("deprecation")
            JsonElement root = new JsonParser().parse(reader);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                cape = string(obj, "cape");
                wings = string(obj, "wings");
            }
        } catch (Exception e) {
            System.err.println("[Astra] Couldn't read " + file + ": " + e);
        } finally {
            close(reader);
        }
    }

    private void writeEquipped() {
        JsonObject obj = new JsonObject();
        obj.addProperty("cape", cape);
        obj.addProperty("wings", wings);
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(equippedFile()), UTF8);
            writer.write(obj.toString());
        } catch (IOException e) {
            System.err.println("[Astra] Couldn't save equipped cosmetics: " + e);
        } finally {
            close(writer);
            equippedModified = equippedFile().lastModified();
        }
    }

    private static String string(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private static String key(Type type, String name) {
        return type.folder + "/" + name.toLowerCase(Locale.ROOT);
    }

    private static CosmeticsModule settings() {
        return Astra.get().getModules().get(CosmeticsModule.class);
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}
