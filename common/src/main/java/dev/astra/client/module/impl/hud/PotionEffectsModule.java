package dev.astra.client.module.impl.hud;

import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ColorSetting;
import dev.astra.client.platform.HudData;
import dev.astra.client.util.Render;

import java.util.ArrayList;
import java.util.List;

/** Active potion effects with icons and time left. */
public final class PotionEffectsModule extends HudModule {

    private static final int ROW = 20;
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    private final BooleanSetting icons;
    private final BooleanSetting blink;
    private final ColorSetting nameColor;
    private final ColorSetting timeColor;

    public PotionEffectsModule() {
        super("Potion Effects", "Active effects with time left.", true, 4, END);
        icons = setting(new BooleanSetting("Show Icons", true));
        blink = setting(new BooleanSetting("Blink When Ending", true));
        nameColor = setting(new ColorSetting("Name Color", 0xFFFFFFFF));
        timeColor = setting(new ColorSetting("Time Color", 0xFFB8B8C8));
    }

    private List<HudData.Effect> effects(boolean editor) {
        List<HudData.Effect> list = mc().isInGame() ? mc().getEffects() : new ArrayList<HudData.Effect>();
        if (list.isEmpty() && editor) {
            list.add(new HudData.Effect("Speed", 1, 20 * 62, 0));
            list.add(new HudData.Effect("Strength", 0, 20 * 185, 4));
        }
        return list;
    }

    private static String name(HudData.Effect e) {
        return e.name + (e.amplifier > 0 && e.amplifier < ROMAN.length ? ROMAN[e.amplifier] : e.amplifier > 0 ? " " + (e.amplifier + 1) : "");
    }

    private static String time(HudData.Effect e) {
        if (e.durationTicks > 32766) {
            return "**:**";
        }
        int seconds = e.durationTicks / 20;
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    private int textX() {
        return icons.isOn() ? 21 : 0;
    }

    @Override
    public int getWidth() {
        int w = 0;
        for (HudData.Effect e : effects(true)) {
            w = Math.max(w, Math.max(Render.width(name(e)), Render.width(time(e))));
        }
        return w + textX();
    }

    @Override
    public int getHeight() {
        int n = effects(true).size();
        return n == 0 ? 0 : n * ROW - 2;
    }

    @Override
    protected void render(boolean editor) {
        int y = 0;
        for (HudData.Effect e : effects(editor)) {
            if (icons.isOn() && e.iconIndex >= 0) {
                mc().drawEffectIcon(e.iconIndex, 0, y);
            }
            boolean ending = e.durationTicks < 200 && e.durationTicks <= 32766;
            boolean hidden = blink.isOn() && ending && (System.currentTimeMillis() / 400) % 2 == 0;
            Render.text(name(e), textX(), y + 1, nameColor.getArgb(), true);
            if (!hidden) {
                Render.text(time(e), textX(), y + 10, ending ? 0xFFFF5555 : timeColor.getArgb(), true);
            }
            y += ROW;
        }
    }
}
