package dev.astra.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB colour picked from a palette of swatches. */
public final class ColorSetting extends Setting<Integer> {

    public static final int[] PALETTE = {
            0xFFFFFFFF, // white
            0xFFB8B8C8, // silver
            0xFF9B7BFF, // astra violet
            0xFF4FC3F7, // sky
            0xFF3DDC97, // mint
            0xFF2FA84F, // green
            0xFFFFD166, // gold
            0xFFFF8A4C, // orange
            0xFFFF5C7A, // rose
    };

    public ColorSetting(String name, int defaultArgb) {
        super(name, defaultArgb);
    }

    public int getArgb() {
        return value;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(String.format("#%08X", value));
    }

    @Override
    public void load(JsonElement json) {
        try {
            String s = json.getAsString().replace("#", "");
            value = (int) Long.parseLong(s, 16);
        } catch (RuntimeException e) {
            reset();
        }
    }
}
