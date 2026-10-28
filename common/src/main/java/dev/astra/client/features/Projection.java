package dev.astra.client.features;

import java.nio.FloatBuffer;

/** World position -> HUD position, with the game's own camera matrices (like gluProject). */
public final class Projection {

    private Projection() {
    }

    /**
     * Projects a point given relative to the camera entity's render position. Returns
     * {x, y, onScreen (1/0), behind (1/0)} in scaled GUI pixels, or null if the matrices aren't ready.
     * Points off screen (or behind you) are pushed to the screen's edge, so markers can point at them.
     */
    public static float[] project(FloatBuffer mv, FloatBuffer proj, double rx, double ry, double rz,
                                  int screenW, int screenH, int margin) {
        if (mv == null || proj == null || mv.capacity() < 16 || proj.capacity() < 16) {
            return null;
        }
        float[] eye = mul(mv, (float) rx, (float) ry, (float) rz, 1f);
        float[] clip = mul(proj, eye[0], eye[1], eye[2], eye[3]);
        float w = clip[3];
        if (w == 0f || Float.isNaN(w)) {
            return null;
        }
        boolean behind = w < 0f;
        float nx = clip[0] / w;
        float ny = clip[1] / w;
        if (behind) {
            // Mirror so the edge marker sits on the side you'd turn towards.
            nx = -nx;
            ny = -ny;
        }
        boolean on = !behind && nx >= -1f && nx <= 1f && ny >= -1f && ny <= 1f;
        if (!on) {
            // Push out to the edge along the direction from the centre.
            float m = Math.max(Math.abs(nx), Math.abs(ny));
            if (m < 1e-4f) {
                ny = -1f;
                m = 1f;
            }
            nx /= m;
            ny /= m;
        }
        float sx = (nx * 0.5f + 0.5f) * screenW;
        float sy = (1f - (ny * 0.5f + 0.5f)) * screenH;
        if (!on) {
            sx = Math.max(margin, Math.min(screenW - margin, sx));
            sy = Math.max(margin, Math.min(screenH - margin, sy));
        }
        return new float[]{sx, sy, on ? 1f : 0f, behind ? 1f : 0f};
    }

    /** Column-major 4x4 matrix times (x, y, z, w). */
    static float[] mul(FloatBuffer m, float x, float y, float z, float w) {
        float[] out = new float[4];
        for (int r = 0; r < 4; r++) {
            out[r] = m.get(r) * x + m.get(4 + r) * y + m.get(8 + r) * z + m.get(12 + r) * w;
        }
        return out;
    }
}
