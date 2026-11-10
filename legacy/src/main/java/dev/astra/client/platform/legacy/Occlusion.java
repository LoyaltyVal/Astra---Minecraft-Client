package dev.astra.client.platform.legacy;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Entity culling for 1.8.9 / 1.7.10: works out which entities and block entities are completely
 * hidden behind solid blocks, so they can be skipped when drawing (the expensive part of crowded
 * lobbies, mob farms and chest rooms). Vanilla only skips what's off screen, not what's behind a wall.
 *
 * Each tick the version code calls {@link #check} for things near the camera. A thing is visible if
 * any straight line from the camera to its centre or one of its corners gets there without passing
 * through a solid, opaque block. The work per tick has a time budget; what isn't rechecked keeps its
 * last result, and anything new counts as visible until it has been checked.
 */
public final class Occlusion {

    /** Whether the block at a position is a solid, opaque cube (blocks all sight). */
    public interface Blocks {
        boolean opaque(int x, int y, int z);
    }

    private static final class Entry {
        boolean hidden;
        int seen;
    }

    /** Nothing past this is checked (left to vanilla): the rays get long and the gain small. */
    private static final double MAX_DISTANCE = 96;
    /** Time allowed per tick, so a huge lobby can't make a tick slow. */
    private static final long BUDGET_NANOS = 1_500_000L;

    private final Map<Object, Entry> results = new IdentityHashMap<Object, Entry>();
    private int tick;
    private boolean overBudget;
    private long deadline;

    /** Where each list's checks start this tick, so a list too long for one tick is covered over several. */
    private final int[] cursor = new int[2];
    private final int[] checked = new int[2];
    private int list;

    private Blocks blocks;
    private double camX, camY, camZ;
    private boolean cameraInWall;

    /** Starts a tick's checks from the camera's position. */
    public void begin(Blocks blocks, double x, double y, double z) {
        this.blocks = blocks;
        this.camX = x;
        this.camY = y;
        this.camZ = z;
        this.tick++;
        this.overBudget = false;
        this.deadline = System.nanoTime() + BUDGET_NANOS;
        // Inside a block (spectator, a replay camera flying through walls): every ray would start
        // blocked, so cull nothing.
        this.cameraInWall = blocks.opaque(floor(x), floor(y), floor(z));
    }

    /**
     * Starts going through a list (0 = entities, 1 = block entities) of {@code size} things; returns
     * the index to start from. Iterate {@code (start + i) % size}.
     */
    public int startList(int which, int size) {
        if (size <= 0) {
            return 0;
        }
        cursor[which] = (cursor[which] + checked[which]) % size;
        checked[which] = 0;
        list = which;
        return cursor[which];
    }

    /** Ends the tick: forgets things that are gone. */
    public void end() {
        if ((tick & 63) == 0) {
            for (Iterator<Entry> it = results.values().iterator(); it.hasNext(); ) {
                if (tick - it.next().seen > 100) {
                    it.remove();
                }
            }
        }
        blocks = null;
    }

    public void clear() {
        results.clear();
    }

    /** Whether the drawing code should skip this entity / block entity. */
    public boolean isHidden(Object thing) {
        Entry e = results.get(thing);
        return e != null && e.hidden;
    }

    /**
     * Works out whether a thing with this box is hidden. {@code key} is the entity or block entity
     * itself. Things already checked recently are only rechecked when there's time left.
     */
    public void check(Object key, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Entry e = results.get(key);
        if (e == null) {
            e = new Entry();
            results.put(key, e);
        } else if (overBudget) {
            e.seen = tick;
            return;
        }
        e.seen = tick;
        if (cameraInWall) {
            e.hidden = false;
            return;
        }
        if (System.nanoTime() > deadline) {
            overBudget = true;
            return;
        }
        e.hidden = !visible(minX, minY, minZ, maxX, maxY, maxZ);
        checked[list]++;
    }

    /** Marks a thing as always drawn (players, named mobs, what you ride...). */
    public void exempt(Object key) {
        Entry e = results.get(key);
        if (e != null) {
            e.hidden = false;
            e.seen = tick;
        }
    }

    private boolean visible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
        double dx = cx - camX, dy = cy - camY, dz = cz - camZ;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
            return true;
        }
        // The camera inside or right next to it: always visible.
        if (camX > minX - 1 && camX < maxX + 1 && camY > minY - 1 && camY < maxY + 1 && camZ > minZ - 1 && camZ < maxZ + 1) {
            return true;
        }
        if (ray(cx, cy, cz, minX, minY, minZ, maxX, maxY, maxZ)) {
            return true;
        }
        // Corners, pulled in a little so they're inside the box rather than on a neighbouring block.
        double ix = Math.min(0.05, (maxX - minX) / 4), iy = Math.min(0.05, (maxY - minY) / 4), iz = Math.min(0.05, (maxZ - minZ) / 4);
        double x0 = minX + ix, x1 = maxX - ix, y0 = minY + iy, y1 = maxY - iy, z0 = minZ + iz, z1 = maxZ - iz;
        return ray(x0, y0, z0, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x1, y0, z0, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x0, y1, z0, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x1, y1, z0, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x0, y0, z1, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x1, y0, z1, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x0, y1, z1, minX, minY, minZ, maxX, maxY, maxZ)
                || ray(x1, y1, z1, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Walks the blocks on the line from the camera to (tx, ty, tz) (Amanatides & Woo). True if it
     * reaches a block that touches the target box before hitting an opaque one.
     */
    private boolean ray(double tx, double ty, double tz,
                        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = tx - camX, dy = ty - camY, dz = tz - camZ;
        int x = floor(camX), y = floor(camY), z = floor(camZ);
        int ex = floor(tx), ey = floor(ty), ez = floor(tz);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tmx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? (x + 1 - camX) : (camX - x)) * tdx;
        double tmy = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? (y + 1 - camY) : (camY - y)) * tdy;
        double tmz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? (z + 1 - camZ) : (camZ - z)) * tdz;
        int steps = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z) + 1;
        for (int i = 0; i < steps; i++) {
            if (tmx < tmy && tmx < tmz) {
                x += sx;
                tmx += tdx;
            } else if (tmy < tmz) {
                y += sy;
                tmy += tdy;
            } else {
                z += sz;
                tmz += tdz;
            }
            // Reached a block the target is in (or touches): nothing solid on the way.
            if (x + 1 > minX && x < maxX && y + 1 > minY && y < maxY && z + 1 > minZ && z < maxZ) {
                return true;
            }
            if (blocks.opaque(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
