package dev.astra.client.module.impl;

import dev.astra.client.Astra;
import dev.astra.client.cosmetics.CosmeticImages;
import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.ModeSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.util.Render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Replaces the title screen's spinning panorama with Astra's background, or your own picture:
 * put a PNG or JPG in %APPDATA%\.astra\backgrounds and choose "Custom Image".
 */
public final class MenuBackgroundModule extends Module {

    private final ModeSetting source;
    private final NumberSetting darken;

    private Object texture;
    private int texW;
    private int texH;
    private String loadedKey = "";
    private long lastCheck;
    private String currentKey = "";

    public MenuBackgroundModule() {
        super("Menu Background", "Your own picture behind the title screen.", Category.VISUAL, true);
        source = setting(new ModeSetting("Picture", "Astra", "Astra", "Custom Image"));
        darken = setting(new NumberSetting("Darken", 20, 0, 80, 5));
    }

    /** %APPDATA%\.astra\backgrounds (next to the instances folder). */
    public static File folder() {
        File game = Astra.platform().getGameDir();
        File root = game.getParentFile() != null && game.getParentFile().getName().equals("instances")
                ? game.getParentFile().getParentFile() : game;
        File dir = new File(root, "backgrounds");
        dir.mkdirs();
        return dir;
    }

    /** Draws the background; returns false to let vanilla draw its panorama instead. */
    public boolean draw(int width, int height) {
        if (!isEnabled()) {
            return false;
        }
        refresh();
        if (texture == null) {
            return false;
        }
        // Cover the screen like CSS "object-fit: cover".
        float screen = width / (float) height;
        float image = texW / (float) texH;
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (image > screen) {
            float keep = screen / image;
            u0 = (1 - keep) / 2;
            u1 = 1 - u0;
        } else {
            float keep = image / screen;
            v0 = (1 - keep) / 2;
            v1 = 1 - v0;
        }
        Render.texture(texture, 0, 0, width, height, u0, v0, u1, v1);
        int a = (int) (darken.getFloat() / 100f * 255);
        if (a > 0) {
            Render.rect(0, 0, width, height, a << 24);
        }
        return true;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheck > 2000) {
            lastCheck = now;
            currentKey = source.is("Custom Image") ? customKey() : "astra";
        }
        if (currentKey.equals(loadedKey)) {
            return;
        }
        loadedKey = currentKey;
        texture = null;
        try {
            BufferedImage img = currentKey.equals("astra") ? builtIn() : CosmeticImages.read(customFile());
            if (img == null) {
                return;
            }
            texW = img.getWidth();
            texH = img.getHeight();
            texture = Astra.platform().createTexture("astra_menu_bg_" + Math.abs(currentKey.hashCode()), img);
        } catch (Exception e) {
            System.err.println("[Astra] Couldn't load the menu background: " + e);
        }
    }

    private static BufferedImage builtIn() throws Exception {
        InputStream in = MenuBackgroundModule.class.getResourceAsStream("/assets/astra/background.png");
        if (in == null) {
            return null;
        }
        try {
            return ImageIO.read(in);
        } finally {
            in.close();
        }
    }

    private static File customFile() {
        File[] files = folder().listFiles();
        if (files == null) {
            return null;
        }
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
                return f;
            }
        }
        return null;
    }

    /** Changes when a different picture is chosen or the file is replaced. */
    private static String customKey() {
        File f = customFile();
        return f == null ? "astra" : f.getAbsolutePath() + "@" + f.lastModified();
    }

    @Override
    public String getPreviewText() {
        return source.get();
    }
}
