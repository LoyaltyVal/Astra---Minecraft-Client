package dev.astra.client.module;

import dev.astra.client.Astra;
import dev.astra.client.module.setting.Setting;
import dev.astra.client.platform.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private boolean enabled;

    protected Module(String name, String description, Category category, boolean enabledByDefault) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
    }

    /** Registers a setting so it appears in the menu and is saved to the config. */
    protected final <S extends Setting<?>> S setting(S setting) {
        settings.add(setting);
        return setting;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** Used by the config loader: sets the flag without running enable/disable logic. */
    public void loadEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Runs every client tick while enabled. */
    public void onTick() {
    }

    /** Big text shown on this module's card in the menu (HUD modules show a live preview instead). */
    public String getPreviewText() {
        return getName();
    }

    /** Runs once when the game closes, so modules can undo option changes (gamma, sensitivity). */
    public void onShutdown() {
    }

    protected static Platform mc() {
        return Astra.platform();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }
}
