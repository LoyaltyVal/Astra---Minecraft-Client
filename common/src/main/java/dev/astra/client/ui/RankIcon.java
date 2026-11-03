package dev.astra.client.ui;

import dev.astra.client.Astra;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * The Astra rank star (the A with the swoosh and the four-point star), tinted with the rank's
 * colour: red for Owner, orange for Admin, and so on. Shown instead of "[Owner]"-style text on
 * name tags and in menus. The image is grey with shading, so tinting keeps the highlights.
 */
public final class RankIcon {

    /** Space put in front of a name so the icon has room (3 spaces = 12 px in Minecraft's font). */
    public static final String NAME_PAD = "   ";

    private static Object texture;
    private static boolean failed;

    private RankIcon() {
    }

    private static Object texture() {
        if (texture == null && !failed) {
            InputStream in = RankIcon.class.getResourceAsStream("/assets/astra/rank-star.png");
            try {
                BufferedImage img = in == null ? null : ImageIO.read(in);
                if (img == null) {
                    failed = true;
                    return null;
                }
                texture = Astra.platform().createTexture("astra_rank_star", img);
            } catch (Exception e) {
                failed = true;
                System.err.println("[Astra] Couldn't load the rank icon: " + e);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Exception ignored) {
                    // closed
                }
            }
        }
        return texture;
    }

    /**
     * Draws the icon with its top-left at (x, y), `size` units square, in the rank colour
     * (0xRRGGBB or 0xAARRGGBB; alpha 0 counts as opaque).
     */
    public static void draw(float x, float y, float size, int color) {
        Object t = texture();
        if (t == null) {
            return;
        }
        int argb = (color >>> 24) == 0 ? color | 0xFF000000 : color;
        Astra.platform().drawTexture(t, x, y, size, size, 0f, 0f, 1f, 1f, argb);
    }
}
