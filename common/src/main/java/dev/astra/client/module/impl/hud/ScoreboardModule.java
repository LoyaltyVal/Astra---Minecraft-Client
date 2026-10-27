package dev.astra.client.module.impl.hud;

import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.platform.HudData;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

/** Replaces the vanilla sidebar with a movable, resizable one. */
public final class ScoreboardModule extends HudModule {

    private static final int PAD = 3;
    private static final int LINE = 9;

    private final BooleanSetting showNumbers;
    private final BooleanSetting shadow;
    private final NumberSetting opacity;
    private final NumberSetting titleOpacity;

    public ScoreboardModule() {
        super("Scoreboard", "Move, resize and restyle the sidebar.", true, END, CENTER);
        showNumbers = setting(new BooleanSetting("Show Numbers", false));
        shadow = setting(new BooleanSetting("Text Shadow", true));
        opacity = setting(new NumberSetting("Background Opacity", 30, 0, 100, 1));
        titleOpacity = setting(new NumberSetting("Title Opacity", 40, 0, 100, 1));
    }

    private HudData.Sidebar sidebar(boolean editor) {
        HudData.Sidebar live = mc().isInGame() ? mc().getSidebar() : null;
        if (live != null || !editor) {
            return live;
        }
        HudData.Sidebar demo = new HudData.Sidebar("§e§lASTRA");
        demo.lines.add(new HudData.Line("§fKills: §a12", 5));
        demo.lines.add(new HudData.Line("§fDeaths: §c3", 4));
        demo.lines.add(new HudData.Line(" ", 3));
        demo.lines.add(new HudData.Line("§fMap: §bNether", 2));
        demo.lines.add(new HudData.Line("§eplay.example.net", 1));
        return demo;
    }

    private String number(HudData.Line line) {
        return "§c" + line.score;
    }

    @Override
    public int getWidth() {
        HudData.Sidebar sb = sidebar(true);
        if (sb == null) {
            return 0;
        }
        int w = Render.width(sb.title);
        for (HudData.Line line : sb.lines) {
            int lw = Render.width(line.text);
            if (showNumbers.isOn()) {
                lw += 8 + Render.width(number(line));
            }
            w = Math.max(w, lw);
        }
        return w + PAD * 2;
    }

    @Override
    public int getHeight() {
        HudData.Sidebar sb = sidebar(true);
        return sb == null ? 0 : (sb.lines.size() + 1) * LINE + PAD + 1;
    }

    @Override
    protected void render(boolean editor) {
        HudData.Sidebar sb = sidebar(editor);
        if (sb == null) {
            return;
        }
        int w = getWidth();
        int titleH = LINE + PAD;
        Render.rect(0, 0, w, titleH, Colors.withAlpha(0, (int) (titleOpacity.get() * 2.55)));
        Render.rect(0, titleH, w, getHeight() - titleH, Colors.withAlpha(0, (int) (opacity.get() * 2.55)));
        Render.textCentered(sb.title, w / 2f, 2, 0xFFFFFFFF, shadow.isOn());

        int y = titleH + 1;
        for (HudData.Line line : sb.lines) {
            Render.text(line.text, PAD, y, 0xFFFFFFFF, shadow.isOn());
            if (showNumbers.isOn()) {
                String n = number(line);
                Render.text(n, w - PAD - Render.width(n), y, 0xFFFF5555, shadow.isOn());
            }
            y += LINE;
        }
    }
}
