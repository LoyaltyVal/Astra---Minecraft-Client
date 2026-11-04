package dev.astra.client.cosmetics;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Turns any picture into something Minecraft can wear, so cape and wing art can come straight from
 * an image generator:
 *
 *  - Capes: a 2:1 image (64x32, 128x64, ...) is used as a normal cape template. Any other picture is
 *    treated as artwork: it's cropped to the cape's 10:16 shape and wrapped onto the cape model.
 *  - Wings: if the picture has no transparency, the flat background is removed (flood fill from the
 *    edges), then the image is trimmed to the wing itself.
 */
public final class CosmeticImages {

    private static final int MAX_SIZE = 1024;

    private CosmeticImages() {
    }

    public static BufferedImage read(File file) throws IOException {
        BufferedImage raw = ImageIO.read(file);
        if (raw == null) {
            throw new IOException("Not an image: " + file.getName());
        }
        return limit(toArgb(raw), MAX_SIZE);
    }

    /** Decodes a downloaded PNG or JPEG. */
    public static BufferedImage read(byte[] data) throws IOException {
        BufferedImage raw = ImageIO.read(new java.io.ByteArrayInputStream(data));
        if (raw == null) {
            throw new IOException("The server sent something that isn't an image");
        }
        return limit(toArgb(raw), MAX_SIZE);
    }

    // ------------------------------------------------------------------ animation

    /** An animated cosmetic: frames (already full pictures, not GIF patches) and how long each shows. */
    public static final class Animation {
        public final java.util.List<BufferedImage> frames;
        public final int[] delaysMs;

        Animation(java.util.List<BufferedImage> frames, int[] delaysMs) {
            this.frames = frames;
            this.delaysMs = delaysMs;
        }
    }

    /** Animated capes keep at most this many frames, each at most this wide (memory and upload time). */
    static final int MAX_FRAMES = 60;
    static final int MAX_ANIMATED_WIDTH = 256;

    /**
     * Reads an animated cosmetic, or returns null when the data is a still picture:
     *  - an animated GIF (frame timing from the GIF), or
     *  - a cape "strip": a PNG with several 2:1 cape templates stacked top to bottom
     *    (the common animated-cape format, 10 frames a second).
     */
    public static Animation readAnimation(byte[] data, boolean cape) throws IOException {
        if (data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return readGif(data);
        }
        if (!cape) {
            return null;
        }
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(data));
        if (img == null) {
            return null;
        }
        int w = img.getWidth();
        int frameH = w / 2;
        if (w % 64 != 0 || frameH == 0 || img.getHeight() % frameH != 0 || img.getHeight() / frameH < 2) {
            return null;
        }
        int count = Math.min(MAX_FRAMES, img.getHeight() / frameH);
        java.util.List<BufferedImage> frames = new java.util.ArrayList<BufferedImage>();
        int[] delays = new int[count];
        for (int i = 0; i < count; i++) {
            frames.add(toArgb(img.getSubimage(0, i * frameH, w, frameH)));
            delays[i] = 100;
        }
        return new Animation(frames, delays);
    }

    /** Decodes every frame of a GIF into a full picture (applying offsets and disposal), or null if it has one frame. */
    private static Animation readGif(byte[] data) throws IOException {
        javax.imageio.ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data));
        try {
            reader.setInput(in, false);
            int count = reader.getNumImages(true);
            if (count < 2) {
                return null;
            }
            int width = -1;
            int height = -1;
            javax.imageio.metadata.IIOMetadata stream = reader.getStreamMetadata();
            if (stream != null) {
                org.w3c.dom.Node screen = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                if (screen != null) {
                    width = intAttr(screen, "logicalScreenWidth", -1);
                    height = intAttr(screen, "logicalScreenHeight", -1);
                }
            }
            BufferedImage first = reader.read(0);
            if (width <= 0 || height <= 0) {
                width = first.getWidth();
                height = first.getHeight();
            }
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            java.util.List<BufferedImage> frames = new java.util.ArrayList<BufferedImage>();
            java.util.List<Integer> delays = new java.util.ArrayList<Integer>();
            // Keep every n-th frame when there are too many, adding up their time so the speed stays right.
            int step = Math.max(1, (count + MAX_FRAMES - 1) / MAX_FRAMES);
            int pendingDelay = 0;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = i == 0 ? first : reader.read(i);
                org.w3c.dom.Node meta = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                org.w3c.dom.Node desc = child(meta, "ImageDescriptor");
                org.w3c.dom.Node gce = child(meta, "GraphicControlExtension");
                int x = desc == null ? 0 : intAttr(desc, "imageLeftPosition", 0);
                int y = desc == null ? 0 : intAttr(desc, "imageTopPosition", 0);
                int delay = gce == null ? 10 : intAttr(gce, "delayTime", 10) * 10;
                if (delay < 20) {
                    delay = 100; // browsers treat 0-10 ms the same way
                }
                String disposal = gce == null ? "none" : String.valueOf(((org.w3c.dom.Element) gce).getAttribute("disposalMethod"));

                BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, x, y, null);
                g.dispose();
                pendingDelay += delay;
                if (i % step == 0 || i == count - 1) {
                    frames.add(copy(canvas));
                    delays.add(pendingDelay);
                    pendingDelay = 0;
                }
                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(java.awt.AlphaComposite.Clear);
                    clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    clear.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }
            int[] d = new int[delays.size()];
            for (int i = 0; i < d.length; i++) {
                d[i] = delays.get(i);
            }
            return new Animation(frames, d);
        } finally {
            reader.dispose();
            in.close();
        }
    }

    private static org.w3c.dom.Node child(org.w3c.dom.Node parent, String name) {
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) {
                return n;
            }
        }
        return null;
    }

    private static int intAttr(org.w3c.dom.Node node, String name, int fallback) {
        try {
            return Integer.parseInt(((org.w3c.dom.Element) node).getAttribute(name));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Cape textures for every frame of an animated cape, all the same size. Artwork frames are
     * fitted without the per-picture crop (which could differ frame to frame and make it jitter).
     */
    public static java.util.List<BufferedImage> toCapeFrames(Animation anim) {
        java.util.List<BufferedImage> out = new java.util.ArrayList<BufferedImage>();
        for (BufferedImage f : anim.frames) {
            BufferedImage tex = isCapeTemplate(f) ? f : capeFromArt(f);
            out.add(limit(tex, MAX_ANIMATED_WIDTH));
        }
        return out;
    }

    // ------------------------------------------------------------------ capes

    public static boolean isCapeTemplate(BufferedImage img) {
        return img.getWidth() == img.getHeight() * 2 && img.getWidth() % 64 == 0;
    }

    /** Returns a cape texture with the standard 64x32 layout (at a higher resolution if the art allows). */
    public static BufferedImage toCapeTexture(BufferedImage img) {
        if (isCapeTemplate(img)) {
            return img;
        }
        // Art from image generators often has the cape floating on a plain backdrop: crop to it.
        return capeFromArt(cropToContent(img));
    }

    /** Wraps a picture onto the cape template (front, back, edges). */
    private static BufferedImage capeFromArt(BufferedImage img) {
        // One "cape pixel" = unit texture pixels; the cape face is 10 x 16 cape pixels.
        int unit = Math.max(1, Math.min(16, Math.min(img.getWidth() / 10, img.getHeight() / 16)));
        int faceW = 10 * unit;
        int faceH = 16 * unit;
        BufferedImage face = cover(img, faceW, faceH);

        BufferedImage tex = new BufferedImage(64 * unit, 32 * unit, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tex.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Outer face (seen from behind the player) and inner face (mirrored).
        g.drawImage(face, unit, unit, null);
        g.drawImage(face, 12 * unit + faceW, unit, 12 * unit, unit + faceH, 0, 0, faceW, faceH, null);
        // Edges: stretch the outermost rows/columns of the art so the sides match.
        g.drawImage(face, 0, unit, unit, unit + faceH, 0, 0, 1, faceH, null);                      // left side
        g.drawImage(face, 11 * unit, unit, 12 * unit, unit + faceH, faceW - 1, 0, faceW, faceH, null); // right side
        g.drawImage(face, unit, 0, unit + faceW, unit, 0, 0, faceW, 1, null);                       // top
        g.drawImage(face, 11 * unit, 0, 11 * unit + faceW, unit, 0, faceH - 1, faceW, faceH, null); // bottom
        g.dispose();
        return tex;
    }

    /**
     * Crops away a plain border (any colour) around the artwork. Leaves the image alone when there's
     * no clear border, or when cropping would leave almost nothing.
     */
    static BufferedImage cropToContent(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int bg = borderColor(img);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                if ((rgb >>> 24) > 16 && distance(rgb, bg) > 40) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) {
            return img;
        }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        if (cw < w / 8 || ch < h / 8 || (cw > w * 0.95 && ch > h * 0.95)) {
            return img;
        }
        return img.getSubimage(minX, minY, cw, ch);
    }

    // ------------------------------------------------------------------ wings

    public static BufferedImage toWingTexture(BufferedImage img) {
        BufferedImage out = hasTransparency(img) ? img : feather(removeBackground(img), borderColor(img));
        return shrink(trim(out), 512);
    }

    /**
     * Softens the cut-out edge: pixels next to the removed background that are still close to the
     * background colour become partly transparent, so there's no hard white (or black) halo.
     */
    private static BufferedImage feather(BufferedImage img, int bg) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        int[] out = px.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((px[i] >>> 24) == 0) {
                    continue;
                }
                boolean edge = (x > 0 && (px[i - 1] >>> 24) == 0) || (x < w - 1 && (px[i + 1] >>> 24) == 0)
                        || (y > 0 && (px[i - w] >>> 24) == 0) || (y < h - 1 && (px[i + w] >>> 24) == 0);
                if (!edge) {
                    continue;
                }
                // 48..160 colour distance from the background maps to 0..255 alpha.
                int d = distance(px[i], bg);
                int a = Math.max(0, Math.min(255, (d - 48) * 255 / 112));
                out[i] = (a << 24) | (px[i] & 0xFFFFFF);
            }
        }
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        res.setRGB(0, 0, w, h, out, 0, w);
        return res;
    }

    /**
     * Scales down in halving steps with premultiplied alpha, which keeps edges smooth and avoids the
     * dark fringe a plain bilinear resize of a transparent image leaves behind.
     */
    static BufferedImage shrink(BufferedImage img, int max) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= max && h <= max) {
            return img;
        }
        double scale = max / (double) Math.max(w, h);
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage cur = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g0 = cur.createGraphics();
        g0.drawImage(img, 0, 0, null);
        g0.dispose();
        while (w > tw || h > th) {
            w = Math.max(tw, w / 2);
            h = Math.max(th, h / 2);
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, w, h, null);
            g.dispose();
            cur = next;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(cur, 0, 0, null);
        g.dispose();
        return out;
    }

    private static boolean hasTransparency(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int step = Math.max(1, Math.min(w, h) / 64);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                if ((img.getRGB(x, y) >>> 24) < 250) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Clears every pixel connected to the image border that is close to the border's average colour. */
    private static BufferedImage removeBackground(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage img = copy(src);
        int bg = borderColor(img);
        boolean[] seen = new boolean[w * h];
        Deque<Integer> queue = new ArrayDeque<Integer>();
        for (int x = 0; x < w; x++) {
            queue.add(x);
            queue.add((h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            queue.add(y * w);
            queue.add(y * w + w - 1);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            if (i < 0 || i >= w * h || seen[i]) {
                continue;
            }
            seen[i] = true;
            int x = i % w;
            int y = i / w;
            int rgb = img.getRGB(x, y);
            if (distance(rgb, bg) > 48) {
                continue;
            }
            img.setRGB(x, y, 0);
            if (x > 0) queue.add(i - 1);
            if (x < w - 1) queue.add(i + 1);
            if (y > 0) queue.add(i - w);
            if (y < h - 1) queue.add(i + w);
        }
        return img;
    }

    private static int borderColor(BufferedImage img) {
        long r = 0, g = 0, b = 0, n = 0;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int x = 0; x < w; x += Math.max(1, w / 32)) {
            int[] edge = {img.getRGB(x, 0), img.getRGB(x, h - 1)};
            for (int c : edge) {
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        return (int) (0xFF000000L | (r / n) << 16 | (g / n) << 8 | (b / n));
    }

    private static int distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** Crops away fully transparent margins (keeps a 2px border). */
    private static BufferedImage trim(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) > 16) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return img; // fully transparent; leave as is
        }
        minX = Math.max(0, minX - 2);
        minY = Math.max(0, minY - 2);
        maxX = Math.min(w - 1, maxX + 2);
        maxY = Math.min(h - 1, maxY + 2);
        return copy(img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1));
    }

    // ------------------------------------------------------------------ helpers

    /** Scales and centre-crops to exactly w x h (like CSS object-fit: cover). */
    static BufferedImage cover(BufferedImage img, int w, int h) {
        double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int sw = (int) Math.ceil(img.getWidth() * scale);
        int sh = (int) Math.ceil(img.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage limit(BufferedImage img, int max) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= max && h <= max) {
            return img;
        }
        double scale = max / (double) Math.max(w, h);
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toArgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return img;
        }
        return copy(img);
    }

    private static BufferedImage copy(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }
}
