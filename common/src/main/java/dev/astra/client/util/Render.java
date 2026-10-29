package dev.astra.client.util;

import dev.astra.client.Astra;
import dev.astra.client.platform.Platform;

/** Small 2D drawing helpers on top of the platform's fill and text calls. */
public final class Render {

    private Render() {
    }

    private static Platform p() {
        return Astra.platform();
    }

    public static void rect(int x, int y, int w, int h, int argb) {
        if (w > 0 && h > 0) {
            p().fill(x, y, x + w, y + h, argb);
        }
    }

    /**
     * A rectangle with rounded corners. The pieces never overlap, so translucent colours stay even.
     */
    public static void round(int x, int y, int w, int h, int radius, int argb) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            rect(x, y, w, h, argb);
            return;
        }
        rect(x + r, y, w - 2 * r, h, argb);         // centre column
        rect(x, y + r, r, h - 2 * r, argb);         // left
        rect(x + w - r, y + r, r, h - 2 * r, argb); // right
        for (int i = 0; i < r; i++) {
            // How far this row of the corner is inset from the edge.
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            int span = r - inset;
            rect(x + inset, y + i, span, 1, argb);                 // top-left
            rect(x + w - r, y + i, span, 1, argb);                 // top-right
            rect(x + inset, y + h - 1 - i, span, 1, argb);         // bottom-left
            rect(x + w - r, y + h - 1 - i, span, 1, argb);         // bottom-right
        }
    }

    /** Draws a textured quad from a platform texture handle; UVs are 0..1. */
    public static void texture(Object handle, float x, float y, float w, float h,
                               float u0, float v0, float u1, float v1) {
        p().drawTexture(handle, x, y, w, h, u0, v0, u1, v1);
    }

    public static void outline(int x, int y, int w, int h, int argb) {
        rect(x, y, w, 1, argb);
        rect(x, y + h - 1, w, 1, argb);
        rect(x, y + 1, 1, h - 2, argb);
        rect(x + w - 1, y + 1, 1, h - 2, argb);
    }

    public static void text(String s, float x, float y, int argb, boolean shadow) {
        p().drawText(s, x, y, argb, shadow);
    }

    public static void textCentered(String s, float centerX, float y, int argb, boolean shadow) {
        p().drawText(s, centerX - width(s) / 2f, y, argb, shadow);
    }

    /** Text drawn at a size multiplier, centred on centerX. */
    public static void textCenteredScaled(String s, float centerX, float y, float scale, int argb, boolean shadow) {
        Astra.platform().pushMatrix();
        Astra.platform().translate(centerX, y, 0f);
        Astra.platform().scale(scale, scale, 1f);
        p().drawText(s, -width(s) / 2f, 0, argb, shadow);
        Astra.platform().popMatrix();
    }

    public static int width(String s) {
        return p().getTextWidth(s);
    }

    public static int fontHeight() {
        return p().getFontHeight();
    }

    /** Cuts a string down with "..." so it fits in maxWidth. */
    public static String trim(String s, int maxWidth) {
        if (width(s) <= maxWidth) {
            return s;
        }
        String dots = "...";
        int end = s.length();
        while (end > 0 && width(s.substring(0, end) + dots) > maxWidth) {
            end--;
        }
        return s.substring(0, end) + dots;
    }

    /** Clips drawing to a rectangle in scaled GUI coordinates. Always pair with {@link #endScissor()}. */
    public static void scissor(int x, int y, int w, int h) {
        p().scissor(x, y, w, h);
    }

    public static void endScissor() {
        p().endScissor();
    }

    public static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
