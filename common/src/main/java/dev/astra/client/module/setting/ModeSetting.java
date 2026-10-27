package dev.astra.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;

/** One choice out of a fixed list; clicking cycles through them. */
public final class ModeSetting extends Setting<String> {

    private final String[] modes;

    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name, defaultMode);
        this.modes = modes;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public void cycle() {
        int index = Arrays.asList(modes).indexOf(value);
        value = modes[(index + 1) % modes.length];
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        try {
            String s = json.getAsString();
            if (Arrays.asList(modes).contains(s)) {
                value = s;
                return;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        reset();
    }
}
