package dev.astra.client.module.impl.hud;

import dev.astra.client.module.HudModule;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.ModeSetting;
import dev.astra.client.platform.HudData;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.ArrayList;
import java.util.List;

/** Armor pieces (and optionally the held item) with durability. */
public final class ArmorStatusModule extends HudModule {

    private static final int ICON = 16;
    private static final int GAP = 2;
    private static final String[] PLACEHOLDER = {"364", "529", "496", "430"};

    private final ModeSetting durability;
    private final BooleanSetting heldItem;
    private final BooleanSetting horizontal;
    private final BooleanSetting textOnLeft;

    public ArmorStatusModule() {
        super("Armor Status", "Your armor and its durability.", true, END, END);
        durability = setting(new ModeSetting("Durability", "Value", "Value", "Percent", "Hidden"));
        heldItem = setting(new BooleanSetting("Show Held Item", true));
        horizontal = setting(new BooleanSetting("Horizontal", false));
        textOnLeft = setting(new BooleanSetting("Text On Left", true));
    }

    private List<HudData.Item> items() {
        List<HudData.Item> list = new ArrayList<HudData.Item>();
        if (!mc().isInGame()) {
            return list;
        }
        for (HudData.Item item : mc().getArmor()) {
            if (item != null) {
                list.add(item);
            }
        }
        if (heldItem.isOn()) {
            HudData.Item held = mc().getHeldItem();
            if (held != null) {
                list.add(held);
            }
        }
        return list;
    }

    private String label(HudData.Item item) {
        if (durability.is("Hidden") || !item.isDamageable()) {
            return "";
        }
        if (durability.is("Percent")) {
            return Math.round(item.remaining() * 100f / item.maxDamage) + "%";
        }
        return String.valueOf(item.remaining());
    }

    private static int durabilityColor(HudData.Item item) {
        if (!item.isDamageable()) {
            return 0xFFFFFFFF;
        }
        float f = item.remaining() / (float) item.maxDamage;
        return f > 0.5f ? Colors.lerp(0xFFFFFF55, 0xFF55FF55, (f - 0.5f) * 2f)
                : Colors.lerp(0xFFFF5555, 0xFFFFFF55, f * 2f);
    }

    private int textWidth(List<HudData.Item> list, boolean editor) {
        int max = 0;
        if (list.isEmpty() && editor && !durability.is("Hidden")) {
            for (String s : PLACEHOLDER) {
                max = Math.max(max, Render.width(s));
            }
        }
        for (HudData.Item item : list) {
            max = Math.max(max, Render.width(label(item)));
        }
        return max;
    }

    private int rows(List<HudData.Item> list, boolean editor) {
        return list.isEmpty() && editor ? 4 : list.size();
    }

    @Override
    public int getWidth() {
        List<HudData.Item> list = items();
        int n = rows(list, true);
        int tw = textWidth(list, true);
        int cell = ICON + (tw > 0 ? tw + 3 : 0);
        return horizontal.isOn() ? n * cell + Math.max(0, n - 1) * 4 : cell;
    }

    @Override
    public int getHeight() {
        int n = rows(items(), true);
        return horizontal.isOn() ? ICON : n * ICON + Math.max(0, n - 1) * GAP;
    }

    @Override
    protected void render(boolean editor) {
        List<HudData.Item> list = items();
        int n = rows(list, editor);
        if (n == 0) {
            return;
        }
        int tw = textWidth(list, editor);
        int cell = ICON + (tw > 0 ? tw + 3 : 0);
        for (int i = 0; i < n; i++) {
            int x = horizontal.isOn() ? i * (cell + 4) : 0;
            int y = horizontal.isOn() ? 0 : i * (ICON + GAP);
            boolean left = textOnLeft.isOn() && !horizontal.isOn();
            int iconX = left ? x + cell - ICON : x;
            int textX = left ? x + cell - ICON - 3 : x + ICON + 3;

            String text;
            int color;
            if (list.isEmpty()) {
                // Editor placeholder: an empty slot outline with sample durability.
                Render.outline(iconX + 1, y + 1, ICON - 2, ICON - 2, 0x80FFFFFF);
                text = durability.is("Hidden") ? "" : PLACEHOLDER[i];
                color = 0xFF55FF55;
            } else {
                HudData.Item item = list.get(i);
                mc().drawItem(item.stack, iconX, y);
                text = label(item);
                color = durabilityColor(item);
            }
            if (!text.isEmpty()) {
                float ty = y + (ICON - Render.fontHeight()) / 2f + 1;
                Render.text(text, left ? textX - Render.width(text) : textX, ty, color, true);
            }
        }
    }
}
