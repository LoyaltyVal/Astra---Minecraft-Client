package dev.astra.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.astra.client.module.HudModule;
import dev.astra.client.module.Module;
import dev.astra.client.module.ModuleManager;
import dev.astra.client.module.setting.Setting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;

/** Saves module state, HUD positions and settings to .minecraft/config/astra.json. */
public final class ConfigManager {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final ModuleManager modules;

    public ConfigManager(File file, ModuleManager modules) {
        this.file = file;
        this.modules = modules;
    }

    public void load() {
        if (!file.isFile()) {
            save(); // write defaults so the file exists for users to inspect
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF8);
            // Instance parse() is used on purpose: 1.7.10 ships an old Gson without JsonParser.parseReader.
            @SuppressWarnings("deprecation")
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("modules")) {
                return;
            }
            JsonObject saved = root.getAsJsonObject().getAsJsonObject("modules");
            for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
                Module module = modules.getByName(entry.getKey());
                if (module != null && entry.getValue().isJsonObject()) {
                    loadModule(module, entry.getValue().getAsJsonObject());
                }
            }
        } catch (Exception e) {
            System.err.println("[Astra] Could not read " + file + ", using defaults: " + e);
        } finally {
            closeQuietly(reader);
        }
    }

    private void loadModule(Module module, JsonObject json) {
        if (json.has("enabled")) {
            module.loadEnabled(json.get("enabled").getAsBoolean());
        }
        if (module instanceof HudModule && json.has("fx") && json.has("fy")) {
            ((HudModule) module).setFraction(json.get("fx").getAsFloat(), json.get("fy").getAsFloat());
        } else if (module instanceof HudModule && json.has("x") && json.has("y")) {
            ((HudModule) module).setPosition(json.get("x").getAsFloat(), json.get("y").getAsFloat());
        }
        if (json.has("settings") && json.get("settings").isJsonObject()) {
            JsonObject settings = json.getAsJsonObject("settings");
            for (Setting<?> setting : module.getSettings()) {
                if (settings.has(setting.getName())) {
                    setting.load(settings.get(setting.getName()));
                }
            }
        }
    }

    public synchronized void save() {
        JsonObject saved = new JsonObject();
        for (Module module : modules.getModules()) {
            JsonObject json = new JsonObject();
            json.addProperty("enabled", module.isEnabled());
            if (module instanceof HudModule && ((HudModule) module).isPlaced()) {
                json.addProperty("x", ((HudModule) module).getX());
                json.addProperty("y", ((HudModule) module).getY());
                if (((HudModule) module).getFractionX() >= 0) {
                    json.addProperty("fx", ((HudModule) module).getFractionX());
                    json.addProperty("fy", ((HudModule) module).getFractionY());
                }
            }
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settings.add(setting.getName(), setting.save());
            }
            json.add("settings", settings);
            saved.add(module.getName(), json);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("modules", saved);

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            System.err.println("[Astra] Could not create " + parent);
            return;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), UTF8);
            writer.write(GSON.toJson(root));
        } catch (IOException e) {
            System.err.println("[Astra] Could not save " + file + ": " + e);
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
