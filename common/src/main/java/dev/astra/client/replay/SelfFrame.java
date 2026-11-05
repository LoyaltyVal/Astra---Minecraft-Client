package dev.astra.client.replay;

/** One tick of the recording player's own movement (the server never sends you your own). */
public final class SelfFrame {

    public static final int ON_GROUND = 1;
    public static final int SNEAKING = 2;
    public static final int SPRINTING = 4;
    public static final int SWING = 8;
    public static final int INVISIBLE = 16;

    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final float headYaw;
    public final int flags;

    public SelfFrame(double x, double y, double z, float yaw, float pitch, float headYaw, int flags) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.headYaw = headYaw;
        this.flags = flags;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}
