package dev.astra.client.module;

import dev.astra.client.module.impl.ClientSettingsModule;
import dev.astra.client.module.impl.CosmeticsModule;
import dev.astra.client.module.impl.FullbrightModule;
import dev.astra.client.module.impl.AutoTextModule;
import dev.astra.client.module.impl.EmotesModule;
import dev.astra.client.module.impl.AutoGGModule;
import dev.astra.client.module.impl.WaypointsModule;
import dev.astra.client.module.impl.ScreenshotsModule;
import dev.astra.client.module.impl.BorderlessModule;
import dev.astra.client.module.impl.ChatModule;
import dev.astra.client.module.impl.FreelookModule;
import dev.astra.client.module.impl.HurtcamModule;
import dev.astra.client.module.impl.MenuBackgroundModule;
import dev.astra.client.module.impl.NoGlintModule;
import dev.astra.client.module.impl.OldAnimationsModule;
import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.module.impl.hud.PotCounterModule;
import dev.astra.client.module.impl.hud.NowPlayingModule;
import dev.astra.client.module.impl.ZoomModule;
import dev.astra.client.module.impl.hud.ArmorStatusModule;
import dev.astra.client.module.impl.hud.BossBarModule;
import dev.astra.client.module.impl.hud.ClockModule;
import dev.astra.client.module.impl.hud.CoordinatesModule;
import dev.astra.client.module.impl.hud.CpsModule;
import dev.astra.client.module.impl.hud.DirectionModule;
import dev.astra.client.module.impl.hud.FpsModule;
import dev.astra.client.module.impl.hud.KeystrokesModule;
import dev.astra.client.module.impl.hud.MemoryModule;
import dev.astra.client.module.impl.hud.PingModule;
import dev.astra.client.module.impl.hud.PotionEffectsModule;
import dev.astra.client.module.impl.hud.ScoreboardModule;
import dev.astra.client.module.impl.hud.SpeedModule;
import dev.astra.client.module.impl.hud.ToggleSprintModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {

    private final List<Module> modules = new ArrayList<Module>();
    private final List<HudModule> hudModules = new ArrayList<HudModule>();

    /** Add new modules here. */
    public void registerDefaults() {
        // Client-wide settings (not shown as cards).
        register(new ClientSettingsModule());
        register(new CosmeticsModule());
        // HUD
        register(new FpsModule());
        register(new CpsModule());
        register(new KeystrokesModule());
        register(new ScoreboardModule());
        register(new ArmorStatusModule());
        register(new PotionEffectsModule());
        register(new DirectionModule());
        register(new BossBarModule());
        register(new CoordinatesModule());
        register(new PingModule());
        register(new SpeedModule());
        register(new ClockModule());
        register(new MemoryModule());
        register(new PotCounterModule());
        register(new NowPlayingModule());
        // Mechanics & visuals
        register(new ToggleSprintModule());
        register(new ZoomModule());
        register(new FreelookModule());
        register(new FullbrightModule());
        register(new OldAnimationsModule());
        register(new HurtcamModule());
        register(new NoGlintModule());
        register(new ChatModule());
        register(new AutoTextModule());
        register(new EmotesModule());
        register(new AutoGGModule());
        register(new WaypointsModule());
        register(new ScreenshotsModule());
        register(new PerformanceModule());
        register(new BorderlessModule());
        register(new MenuBackgroundModule());
    }

    public void register(Module module) {
        modules.add(module);
        if (module instanceof HudModule) {
            hudModules.add((HudModule) module);
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<HudModule> getHudModules() {
        return Collections.unmodifiableList(hudModules);
    }

    public List<Module> getModules(Category category) {
        if (category == null) {
            return getModules();
        }
        List<Module> result = new ArrayList<Module>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }

    public Module getByName(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public void onShutdown() {
        for (Module module : modules) {
            try {
                module.onShutdown();
            } catch (RuntimeException ignored) {
                // the game is closing; never block it
            }
        }
    }
}
