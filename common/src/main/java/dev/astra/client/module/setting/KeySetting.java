package dev.astra.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.astra.client.util.Keys;

/** A key or mouse button (see {@link Keys} for the code format). */
public final class KeySetting extends Setting<Integer> {

    public KeySetting(String name, int defaultCode) {
        super(name, defaultCode);
    }

    public int getCode() {
        return value;
    }

    public boolean isDown() {
        return Keys.isDown(value);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        try {
            value = json.getAsInt();
        } catch (RuntimeException e) {
            reset();
        }
    }
}
