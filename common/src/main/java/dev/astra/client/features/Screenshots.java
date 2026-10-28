package dev.astra.client.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astra.client.Astra;
import dev.astra.client.util.DataFiles;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The screenshots folder for the Screenshot Manager: listing, starred files, and images scaled down
 * on a background thread. Textures are only created on the render thread ({@link #texture}) and
 * freed when no longer shown, so a big folder never fills video memory.
 */
public final class Screenshots {

    /** A loaded image ready for the GPU (or already on it). */
    public static final class Picture {
        public final int width;
        public final int height;
        BufferedImage pending;
        Object handle;

        Picture(BufferedImage image) {
            this.pending = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
        }
    }

    private static final int THUMB_W = 256;
    private static final int FULL_W = 1600;
    private static final int MAX_THUMBS = 64;
    private static Screenshots instance;

    private final ExecutorService loader = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Astra screenshots");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    });
    /** Most recently used last; the oldest textures are freed past MAX_THUMBS. */
    private final LinkedHashMap<String, Picture> thumbs = new LinkedHashMap<String, Picture>(16, 0.75f, true);
    private final Map<String, Boolean> loading = new ConcurrentHashMap<String, Boolean>();
    private final Map<String, Picture> ready = new ConcurrentHashMap<String, Picture>();
    private final Set<String> starred = new HashSet<String>();
    private Picture full;
    private String fullName;
    private int counter;

    public static synchronized Screenshots get() {
        if (instance == null) {
            instance = new Screenshots();
            instance.loadStars();
        }
        return instance;
    }

    public static File folder() {
        File dir = new File(Astra.platform().getGameDir(), "screenshots");
        dir.mkdirs();
        return dir;
    }

    /** Images in the folder, newest first. */
    public List<File> list(boolean starredOnly) {
        File[] files = folder().listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                String n = f.getName().toLowerCase();
                return f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"));
            }
        });
        List<File> out = new ArrayList<File>();
        if (files == null) {
            return out;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (File f : files) {
            if (!starredOnly || starred.contains(f.getName())) {
                out.add(f);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ stars

    public boolean isStarred(File f) {
        return starred.contains(f.getName());
    }

    public void toggleStar(File f) {
        if (!starred.remove(f.getName())) {
            starred.add(f.getName());
        }
        saveStars();
    }

    private void loadStars() {
        JsonElement json = DataFiles.read("screenshots.json");
        if (json != null && json.isJsonObject() && json.getAsJsonObject().has("starred")) {
            for (JsonElement e : json.getAsJsonObject().getAsJsonArray("starred")) {
                starred.add(e.getAsString());
            }
        }
    }

    private void saveStars() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String s : starred) {
            arr.add(new com.google.gson.JsonPrimitive(s));
        }
        root.add("starred", arr);
        DataFiles.write("screenshots.json", root);
    }

    // ------------------------------------------------------------------ files

    /** Renames a screenshot (keeps the extension). Returns an error message or null. */
    public String rename(File f, String newName) {
        String clean = newName.trim().replaceAll("[\\\\/:*?\"<>|]", "");
        if (clean.isEmpty()) {
            return "Type a name";
        }
        String name = f.getName();
        String ext = name.substring(name.lastIndexOf('.'));
        File to = new File(f.getParentFile(), clean + ext);
        if (to.exists()) {
            return "A screenshot with that name already exists";
        }
        if (!f.renameTo(to)) {
            return "Couldn't rename it (is it open somewhere?)";
        }
        if (starred.remove(name)) {
            starred.add(to.getName());
            saveStars();
        }
        forget(name);
        return null;
    }

    public boolean delete(File f) {
        boolean ok = f.delete();
        if (ok) {
            if (starred.remove(f.getName())) {
                saveStars();
            }
            forget(f.getName());
        }
        return ok;
    }

    // ------------------------------------------------------------------ images

    /** The thumbnail's texture, or null while it loads (call from the render thread only). */
    public Picture thumb(final File f) {
        final String key = f.getName() + "|" + f.lastModified();
        Picture p = thumbs.get(key);
        if (p == null) {
            p = ready.remove(key);
            if (p != null) {
                thumbs.put(key, p);
                trim();
            }
        }
        if (p != null) {
            return upload(p, "astra_shot_thumb");
        }
        if (loading.putIfAbsent(key, Boolean.TRUE) == null) {
            loader.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedImage img = scaled(f, THUMB_W);
                        if (img != null) {
                            ready.put(key, new Picture(img));
                        }
                    } finally {
                        loading.remove(key);
                    }
                }
            });
        }
        return null;
    }

    /** The big version for the viewer (one at a time), or null while it loads. */
    public Picture full(final File f) {
        final String key = "full|" + f.getName() + "|" + f.lastModified();
        if (key.equals(fullName) && full != null) {
            return upload(full, "astra_shot_full");
        }
        Picture done = ready.remove(key);
        if (done != null) {
            freeFull();
            full = done;
            fullName = key;
            return upload(full, "astra_shot_full");
        }
        if (loading.putIfAbsent(key, Boolean.TRUE) == null) {
            loader.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedImage img = scaled(f, FULL_W);
                        if (img != null) {
                            ready.put(key, new Picture(img));
                        }
                    } finally {
                        loading.remove(key);
                    }
                }
            });
        }
        return null;
    }

    private Picture upload(Picture p, String name) {
        if (p.handle == null && p.pending != null) {
            p.handle = Astra.platform().createTexture(name + "_" + (counter++), p.pending);
            p.pending = null;
        }
        return p;
    }

    private void trim() {
        while (thumbs.size() > MAX_THUMBS) {
            String oldest = thumbs.keySet().iterator().next();
            free(thumbs.remove(oldest));
        }
    }

    private void forget(String fileName) {
        for (String k : new ArrayList<String>(thumbs.keySet())) {
            if (k.startsWith(fileName + "|")) {
                free(thumbs.remove(k));
            }
        }
        if (fullName != null && fullName.startsWith("full|" + fileName + "|")) {
            freeFull();
        }
    }

    private void freeFull() {
        free(full);
        full = null;
        fullName = null;
    }

    /** Frees every texture (when the screen closes). Render thread only. */
    public void releaseAll() {
        for (Picture p : thumbs.values()) {
            free(p);
        }
        thumbs.clear();
        ready.clear();
        freeFull();
    }

    private static void free(Picture p) {
        if (p != null && p.handle != null) {
            Astra.platform().deleteTexture(p.handle);
            p.handle = null;
        }
    }

    /** Reads an image and scales it down to at most maxWidth (keeping its shape). */
    static BufferedImage scaled(File f, int maxWidth) {
        try {
            BufferedImage src = ImageIO.read(f);
            if (src == null) {
                return null;
            }
            int w = Math.min(maxWidth, src.getWidth());
            int h = Math.max(1, Math.round(src.getHeight() * (w / (float) src.getWidth())));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // Big shrinks look better done in halves.
            BufferedImage step = src;
            while (step.getWidth() / 2 >= w * 1.5) {
                BufferedImage half = new BufferedImage(step.getWidth() / 2, step.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
                Graphics2D hg = half.createGraphics();
                hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                hg.drawImage(step, 0, 0, half.getWidth(), half.getHeight(), null);
                hg.dispose();
                step = half;
            }
            g.drawImage(step, 0, 0, w, h, null);
            g.dispose();
            return out;
        } catch (Exception e) {
            System.err.println("[Astra] Couldn't read screenshot " + f + ": " + e);
            return null;
        }
    }

    /** Copies the full-size image to the clipboard. Returns false if the system wouldn't allow it. */
    public static boolean copy(File f) {
        try {
            final BufferedImage img = ImageIO.read(f);
            if (img == null) {
                return false;
            }
            java.awt.datatransfer.Transferable t = new java.awt.datatransfer.Transferable() {
                @Override
                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                    return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                    return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                    return img;
                }
            };
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
            return true;
        } catch (Throwable e) {
            System.err.println("[Astra] Couldn't copy screenshot: " + e);
            return false;
        }
    }
}
