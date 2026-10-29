package dev.astra.client.module.impl;

import dev.astra.client.Astra;
import dev.astra.client.features.Projection;
import dev.astra.client.features.Waypoints;
import dev.astra.client.features.WaypointsScreen;
import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.social.Notifications;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Keys;
import dev.astra.client.util.Render;
import dev.astra.client.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Waypoints: saved locations shown as markers on screen with their distance. M opens the list,
 * N drops one where you stand, and dying leaves a "Death" marker.
 */
public final class WaypointsModule extends Module {

    private final KeySetting menuKey = setting(new KeySetting("Open Waypoints", Keyboard.KEY_M));
    private final KeySetting addKey = setting(new KeySetting("Add Waypoint Here", Keyboard.KEY_N));
    private final BooleanSetting showNames = setting(new BooleanSetting("Show Names", true));
    private final BooleanSetting showDistance = setting(new BooleanSetting("Show Distance", true));
    private final BooleanSetting edges = setting(new BooleanSetting("Point to Off-Screen Waypoints", true));
    private final BooleanSetting deathPoint = setting(new BooleanSetting("Death Waypoint", true));
    private final NumberSetting maxDistance = setting(new NumberSetting("Max Distance (blocks)", 5000, 100, 20000, 100));
    private final NumberSetting size = setting(new NumberSetting("Marker Size", 1.0, 0.6, 2.0, 0.1));

    private boolean menuWasDown;
    private boolean addWasDown;
    private boolean wasAlive = true;

    public WaypointsModule() {
        super("Waypoints", "Save locations and see them on screen.", Category.VISUAL, true);
    }

    @Override
    public void onTick() {
        boolean free = mc().isInGame() && !mc().isScreenOpen();
        boolean menu = menuKey.getCode() != Keys.NONE && menuKey.isDown();
        if (menu && !menuWasDown && free) {
            mc().openUi(new WaypointsScreen());
        }
        menuWasDown = menu;
        boolean add = addKey.getCode() != Keys.NONE && addKey.isDown();
        if (add && !addWasDown && free) {
            Waypoints wp = Waypoints.get();
            Waypoints.Waypoint w = wp.addHere(wp.nextName(), wp.nextColor());
            if (w != null) {
                Notifications.push("Waypoint added", w.name + " at " + w.x + ", " + w.y + ", " + w.z, w.color);
            }
        }
        addWasDown = add;

        if (mc().isInGame()) {
            boolean alive = mc().getHealth() > 0f;
            if (!alive && wasAlive && deathPoint.isOn()) {
                int x = (int) Math.floor(mc().getPlayerX());
                int y = (int) Math.floor(mc().getPlayerY());
                int z = (int) Math.floor(mc().getPlayerZ());
                Waypoints.get().setDeath(x, y, z, mc().getDimension());
                Notifications.push("Death waypoint saved", x + ", " + y + ", " + z, 0xFFFF5C7A);
            }
            wasAlive = alive;
        } else {
            wasAlive = true;
        }
    }

    @Override
    public void onRenderHud(float tickDelta) {
        List<Waypoints.Waypoint> list = Waypoints.get().here();
        if (list.isEmpty()) {
            return;
        }
        int dim = mc().getDimension();
        double[] cam = mc().getRenderPos(tickDelta);
        int[] res = Astra.get().getScaledResolution();
        double max = maxDistance.get();

        // Far ones first so near markers draw on top.
        List<Object[]> shown = new ArrayList<Object[]>();
        for (Waypoints.Waypoint w : list) {
            if (!w.visible || w.dimension != dim) {
                continue;
            }
            double dx = w.x + 0.5 - cam[0];
            double dy = w.y + 1.0 - cam[1];
            double dz = w.z + 0.5 - cam[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > max) {
                continue;
            }
            float[] p = Projection.project(mc().getModelViewMatrix(), mc().getProjectionMatrix(), dx, dy, dz, res[0], res[1], 12);
            if (p == null || (p[2] == 0f && !edges.isOn())) {
                continue;
            }
            shown.add(new Object[]{w, p, dist});
        }
        Collections.sort(shown, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[2], (Double) a[2]);
            }
        });
        for (Object[] s : shown) {
            draw((Waypoints.Waypoint) s[0], (float[]) s[1], (Double) s[2], res);
        }
        mc().color(1f, 1f, 1f, 1f);
    }

    private void draw(Waypoints.Waypoint w, float[] p, double dist, int[] res) {
        float scale = size.get().floatValue();
        boolean onScreen = p[2] == 1f;
        if (!onScreen) {
            scale *= 0.85f;
        }
        int x = Math.round(p[0]);
        int y = Math.round(p[1]);
        int r = Math.max(2, Math.round(3 * scale));
        // Dot with a dark ring.
        Render.round(x - r - 1, y - r - 1, 2 * r + 2, 2 * r + 2, r + 1, 0xB0000000);
        Render.round(x - r, y - r, 2 * r, 2 * r, r, w.color);

        String distance = dist < 1000 ? Math.round(dist) + "m" : String.format("%.1fkm", dist / 1000);
        String label;
        if (showNames.isOn() && showDistance.isOn()) {
            label = w.name + "  " + distance;
        } else if (showNames.isOn()) {
            label = w.name;
        } else {
            label = showDistance.isOn() ? distance : null;
        }
        if (label == null) {
            return;
        }
        if (!onScreen && label.length() > 24) {
            label = Render.trim(label, 110); // edge markers stay compact
        }
        int tw = Render.width(label);
        int lx = x - tw / 2;
        int ly = y - r - 13;
        if (ly < 2) {
            ly = y + r + 3;
        }
        lx = Math.max(2, Math.min(res[0] - tw - 2, lx));
        Render.round(lx - 4, ly - 2, tw + 8, 12, 4, 0x90000000);
        Render.rect(lx - 4, ly + 9, tw + 8, 1, Colors.withAlpha(w.color, 0xC0));
        Render.text(label, lx, ly, 0xFFFFFFFF, false);
    }

    @Override
    public dev.astra.client.ui.UiScreen createScreen() {
        return new WaypointsScreen();
    }

    @Override
    public String getPreviewText() {
        int n = Waypoints.get().here().size();
        return n == 0 ? "Waypoints" : n + " here";
    }
}
