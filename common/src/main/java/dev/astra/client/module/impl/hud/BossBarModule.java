package dev.astra.client.module.impl.hud;

import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.platform.HudData;
import dev.astra.client.util.Render;

/** The wither/dragon health bar, movable and resizable. */
public final class BossBarModule extends HudModule {

    private static final String ICONS = "textures/gui/icons.png";
    private static final int BAR_W = 182;

    private final BooleanSetting showName;
    private final BooleanSetting showBar;

    public BossBarModule() {
        super("Boss Bar", "Move or hide the boss health bar.", true, CENTER, 12);
        showName = setting(new BooleanSetting("Show Name", true));
        showBar = setting(new BooleanSetting("Show Bar", true));
    }

    private HudData.Boss boss(boolean editor) {
        HudData.Boss live = mc().getBossBar();
        return live != null || !editor ? live : new HudData.Boss("Wither", 0.62f);
    }

    @Override
    public int getWidth() {
        return BAR_W;
    }

    @Override
    public int getHeight() {
        return (showName.isOn() ? 10 : 0) + (showBar.isOn() ? 5 : 0);
    }

    @Override
    protected void render(boolean editor) {
        HudData.Boss boss = boss(editor);
        if (boss == null) {
            return;
        }
        int y = 0;
        if (showName.isOn()) {
            Render.textCentered(boss.name, BAR_W / 2f, y, 0xFFFF55FF, true);
            y += 10;
        }
        if (showBar.isOn()) {
            mc().drawGuiTexture(ICONS, 0, y, 0, 74, BAR_W, 5);
            int filled = (int) (boss.percent * (BAR_W + 1));
            if (filled > 0) {
                mc().drawGuiTexture(ICONS, 0, y, 0, 79, filled, 5);
            }
        }
    }
}
