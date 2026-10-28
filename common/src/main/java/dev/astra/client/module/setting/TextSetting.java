package dev.astra.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A line of text the player types in (e.g. an Auto Text message). */
public final class TextSetting extends Setting<String> {

    private final int maxLength;
    private final String placeholder;

    public TextSetting(String name, String defaultValue, int maxLength, String placeholder) {
        super(name, defaultValue);
        this.maxLength = maxLength;
        this.placeholder = placeholder;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public boolean isEmpty() {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public void set(String value) {
        String v = value == null ? "" : value;
        super.set(v.length() > maxLength ? v.substring(0, maxLength) : v);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        try {
            set(json.getAsString());
        } catch (RuntimeException e) {
            reset();
        }
    }
}
