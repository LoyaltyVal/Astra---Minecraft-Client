package dev.astra.client.social;

/**
 * Emote poses. Each emote is a function of time that returns target angles (radians) for the
 * right arm, left arm and head as {x, y, z} triples; NaN means "leave as vanilla". The player
 * model mixins blend from the normal pose into these and back out at the end.
 *
 * Model angles: x tilts forward/back (negative raises an arm forwards and up), y twists, z swings
 * an arm out to the side (right arm: positive = out, left arm: negative = out).
 */
public final class Emotes {

    public static final String[] IDS = {"wave", "cheer", "dab", "tpose", "facepalm", "point", "clap", "salute", "shrug", "bow"};
    public static final String[] NAMES = {"Wave", "Cheer", "Dab", "T-Pose", "Facepalm", "Point", "Clap", "Salute", "Shrug", "Bow"};

    /** How long an emote plays, in seconds (matches the server's 4 s). */
    public static final float LENGTH = 3.6f;
    private static final float FADE = 0.25f;
    private static final float N = Float.NaN;

    private Emotes() {
    }

    public static String name(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return NAMES[i];
            }
        }
        return id;
    }

    /** 0..1: how strongly the pose applies at time t (fades in and out). */
    public static float weight(float t) {
        if (t < 0 || t > LENGTH) {
            return 0f;
        }
        float w = Math.min(1f, Math.min(t / FADE, (LENGTH - t) / FADE));
        return w * w * (3 - 2 * w); // smoothstep
    }

    /** {rightArm x,y,z, leftArm x,y,z, head x,y,z} at time t, or null for an unknown emote. */
    public static float[] pose(String id, float t) {
        float s = (float) Math.sin(t * 9.0);
        float bounce = (float) Math.abs(Math.sin(t * 7.0));
        if ("wave".equals(id)) {
            return new float[]{-2.7f, 0f, 0.15f + 0.35f * s, N, N, N, N, N, N};
        } else if ("cheer".equals(id)) {
            return new float[]{-2.9f, 0f, 0.35f + 0.15f * bounce, -2.9f, 0f, -0.35f - 0.15f * bounce, -0.3f, N, N};
        } else if ("dab".equals(id)) {
            return new float[]{-1.9f, -1.0f, 0f, -2.2f, 0f, -1.0f, 0.55f, -0.6f, 0f};
        } else if ("tpose".equals(id)) {
            return new float[]{0f, 0f, 1.5708f, 0f, 0f, -1.5708f, 0f, N, N};
        } else if ("facepalm".equals(id)) {
            return new float[]{-2.15f, -0.55f, 0f, N, N, N, 0.35f, 0f, 0f};
        } else if ("point".equals(id)) {
            return new float[]{-1.55f, 0f, 0f, N, N, N, N, N, N};
        } else if ("clap".equals(id)) {
            float in = 0.35f + 0.3f * bounce;
            return new float[]{-1.25f, -in, 0f, -1.25f, in, 0f, N, N, N};
        } else if ("salute".equals(id)) {
            return new float[]{-2.25f, -0.75f, 0.2f, N, N, N, N, N, N};
        } else if ("shrug".equals(id)) {
            float up = 0.25f * bounce;
            return new float[]{-0.6f, 0.4f, 0.55f + up, -0.6f, -0.4f, -0.55f - up, N, N, 0.18f * s};
        } else if ("bow".equals(id)) {
            return new float[]{0.35f, 0f, 0f, 0.35f, 0f, 0f, 0.75f, 0f, 0f};
        }
        return null;
    }

    /**
     * The pose to apply to a player right now: 9 angles (see {@link #pose}) then the blend weight,
     * or null when they aren't emoting (or emotes are switched off).
     */
    public static float[] forPlayer(java.util.UUID uuid) {
        dev.astra.client.Astra astra = dev.astra.client.Astra.get();
        if (astra == null || astra.getCosmetics() == null || uuid == null) {
            return null;
        }
        dev.astra.client.module.impl.EmotesModule module = astra.getModules().get(dev.astra.client.module.impl.EmotesModule.class);
        if (module == null || !module.isEnabled()) {
            return null;
        }
        Object[] e = astra.getCosmetics().getEmote(uuid);
        if (e == null) {
            return null;
        }
        String self = astra.getCosmetics().getSelfUuid();
        if (!module.showOthers() && (self == null || !uuid.toString().replace("-", "").equals(self))) {
            return null;
        }
        float t = (Float) e[1];
        float w = weight(t);
        float[] p = w <= 0f ? null : pose((String) e[0], t);
        if (p == null) {
            return null;
        }
        float[] out = new float[10];
        System.arraycopy(p, 0, out, 0, 9);
        out[9] = w;
        return out;
    }

    /** Blends one angle from vanilla towards the pose. */
    public static float blend(float vanilla, float target, float w) {
        return Float.isNaN(target) ? vanilla : vanilla + (target - vanilla) * w;
    }
}
