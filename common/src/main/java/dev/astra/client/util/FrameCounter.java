package dev.astra.client.util;

/** Counts rendered frames per second without touching Minecraft's own counter. */
public final class FrameCounter {

    private int frames;
    private int fps;
    private long windowStart = System.currentTimeMillis();

    public void onFrame() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000L) {
            fps = Math.round(frames * 1000f / (now - windowStart));
            frames = 0;
            windowStart = now;
        }
    }

    public int getFps() {
        return fps;
    }
}
