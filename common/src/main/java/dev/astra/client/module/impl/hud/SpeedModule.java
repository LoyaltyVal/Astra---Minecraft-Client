package dev.astra.client.module.impl.hud;

import dev.astra.client.module.TextHudModule;

import java.util.Locale;

/** Horizontal speed in blocks per second. */
public final class SpeedModule extends TextHudModule {

    private static final int SAMPLES = 5;

    private final double[] history = new double[SAMPLES];
    private int index;
    private double lastX = Double.NaN;
    private double lastZ;
    private double bps;

    public SpeedModule() {
        super("Speed", "How fast you're moving, in blocks per second.", false, 136, 46);
    }

    @Override
    public void onTick() {
        if (!mc().isInGame()) {
            lastX = Double.NaN;
            return;
        }
        double x = mc().getPlayerX();
        double z = mc().getPlayerZ();
        if (!Double.isNaN(lastX)) {
            double dist = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));
            history[index++ % SAMPLES] = dist > 10 ? 0 : dist * 20; // ignore teleports
            double sum = 0;
            for (double v : history) {
                sum += v;
            }
            bps = sum / SAMPLES;
        }
        lastX = x;
        lastZ = z;
    }

    @Override
    protected String getText() {
        return String.format(Locale.ROOT, "%.2f m/s", mc().isInGame() ? bps : 5.61);
    }
}
