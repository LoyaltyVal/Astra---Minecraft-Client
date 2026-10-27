package dev.astra.client.module.impl.hud;

import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoordinatesModule extends HudModule {

    private static final String[] DIRECTIONS = {"South (+Z)", "West (-X)", "North (-Z)", "East (+X)"};
    private static final int PADDING = 5;
    private static final int LINE_GAP = 2;

    private final BooleanSetting showFacing;
    private final BooleanSetting decimals;
    private final BooleanSetting background;
    private final NumberSetting backgroundOpacity;
    private final ColorSetting labelColor;
    private final ColorSetting valueColor;

    public CoordinatesModule() {
        super("Coordinates", "Shows your XYZ position and facing.", false, 4, 174);
        showFacing = setting(new BooleanSetting("Show Facing", true));
        decimals = setting(new BooleanSetting("Decimals", false));
        background = setting(new BooleanSetting("Background", true));
        backgroundOpacity = setting(new NumberSetting("Background Opacity", 45, 0, 100, 1));
        labelColor = setting(new ColorSetting("Label Color", 0xFF9B7BFF));
        valueColor = setting(new ColorSetting("Value Color", 0xFFFFFFFF));
    }

    private List<String[]> lines() {
        List<String[]> lines = new ArrayList<String[]>();
        boolean live = mc().isInGame();
        lines.add(new String[] {"X: ", live ? number(mc().getPlayerX()) : "0"});
        lines.add(new String[] {"Y: ", live ? number(mc().getPlayerY()) : "64"});
        lines.add(new String[] {"Z: ", live ? number(mc().getPlayerZ()) : "0"});
        if (showFacing.isOn()) {
            lines.add(new String[] {"Facing: ", live ? facing(mc().getPlayerYaw()) : DIRECTIONS[2]});
        }
        return lines;
    }

    private String number(double v) {
        return decimals.isOn() ? String.format(Locale.ROOT, "%.1f", v) : String.valueOf((int) Math.floor(v));
    }

    private static String facing(float yaw) {
        int index = (int) Math.floor(yaw * 4.0 / 360.0 + 0.5) & 3;
        return DIRECTIONS[index];
    }

    private int pad() {
        return background.isOn() ? PADDING : 0;
    }

    @Override
    public int getWidth() {
        int max = 0;
        for (String[] line : lines()) {
            max = Math.max(max, Render.width(line[0] + line[1]));
        }
        return max + pad() * 2;
    }

    @Override
    public int getHeight() {
        int n = lines().size();
        return n * Render.fontHeight() + (n - 1) * LINE_GAP + pad() * 2;
    }

    @Override
    protected void render(boolean editor) {
        if (background.isOn()) {
            int alpha = (int) (backgroundOpacity.get() / 100.0 * 255);
            Render.rect(0, 0, getWidth(), getHeight(), Colors.withAlpha(0x000000, alpha));
        }
        int y = pad();
        for (String[] line : lines()) {
            Render.text(line[0], pad(), y, labelColor.getArgb(), true);
            Render.text(line[1], pad() + Render.width(line[0]), y, valueColor.getArgb(), true);
            y += Render.fontHeight() + LINE_GAP;
        }
    }
}
