package dev.astra.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void set(Double v) {
        double clamped = Math.max(min, Math.min(max, v));
        if (step > 0) {
            clamped = min + Math.round((clamped - min) / step) * step;
            clamped = Math.max(min, Math.min(max, clamped));
        }
        value = clamped;
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    /** 0..1 position of the value between min and max (for sliders). */
    public double getPercent() {
        return (value - min) / (max - min);
    }

    public void setPercent(double percent) {
        set(min + (max - min) * Math.max(0, Math.min(1, percent)));
    }

    public String format() {
        if (step >= 1) {
            return String.valueOf(getInt());
        }
        return String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        try {
            set(json.getAsDouble());
        } catch (RuntimeException e) {
            reset();
        }
    }
}
