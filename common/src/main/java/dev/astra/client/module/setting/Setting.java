package dev.astra.client.module.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {

    private final String name;
    private final T defaultValue;
    protected T value;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        set(defaultValue);
    }

    public abstract JsonElement save();

    /** Reads a saved value; bad or missing data falls back to the default. */
    public abstract void load(JsonElement json);
}
