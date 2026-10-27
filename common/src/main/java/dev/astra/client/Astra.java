package dev.astra.client;

import dev.astra.client.config.ConfigManager;
import dev.astra.client.cosmetics.CosmeticsManager;
import dev.astra.client.module.HudModule;
import dev.astra.client.module.Module;
import dev.astra.client.module.ModuleManager;
import dev.astra.client.module.impl.ZoomModule;
import dev.astra.client.module.impl.hud.BossBarModule;
import dev.astra.client.module.impl.hud.ScoreboardModule;
import dev.astra.client.platform.Platform;
import dev.astra.client.platform.PlatformImpl;
import dev.astra.client.ui.ModMenuScreen;
import dev.astra.client.util.ClickCounter;
import dev.astra.client.util.FrameCounter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Astra client entrypoint. The version-specific mixins call {@link #onTick()},
 * {@link #onRenderHud()}, {@link #modifyFov(float)} and the cosmetics hooks; everything else
 * happens in /common.
 */
public final class Astra implements ClientModInitializer {

    public static final String NAME = "Astra";
    public static final String VERSION = "0.2.0";
    public static final int MENU_KEY = Keyboard.KEY_RSHIFT;

    private static Astra instance;

    private final Platform platform = new PlatformImpl();
    private final ModuleManager modules = new ModuleManager();
    private final FrameCounter frames = new FrameCounter();
    private final ClickCounter clicks = new ClickCounter();
    /** Modules that already threw once; logged once so a broken module can't spam or crash the game. */
    private final Set<String> reportedErrors = new HashSet<String>();
    private ConfigManager config;
    private CosmeticsManager cosmetics;
    private boolean menuKeyWasDown;

    @Override
    public void onInitializeClient() {
        instance = this;
        modules.registerDefaults();

        File file = FabricLoader.getInstance().getConfigDir().resolve("astra.json").toFile();
        config = new ConfigManager(file, modules);
        config.load();

        // The Astra app passes the cosmetics server and your session token when it starts the game.
        cosmetics = new CosmeticsManager(System.getProperty("astra.api"), System.getProperty("astra.token"));

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                modules.onShutdown();
                config.save();
            }
        }, "Astra config save"));

        System.out.println("[Astra] " + VERSION + " loaded " + modules.getModules().size()
                + " modules for Minecraft " + platform.minecraftVersion());
    }

    public static Astra get() {
        return instance;
    }

    public static Platform platform() {
        return instance.platform;
    }

    // ------------------------------------------------------------------ hooks

    /** Called at the start of every client tick (20 per second). */
    public void onTick() {
        boolean menuDown = Keyboard.isKeyDown(MENU_KEY);
        if (menuDown && !menuKeyWasDown && platform.isInGame() && !platform.isScreenOpen()) {
            platform.openUi(new ModMenuScreen());
        }
        menuKeyWasDown = menuDown;

        cosmetics.tick();
        for (Module module : modules.getModules()) {
            if (module.isEnabled()) {
                try {
                    module.onTick();
                } catch (Throwable t) {
                    report(module, t);
                }
            }
        }
    }

    /** Called every frame after the vanilla HUD has been drawn. */
    public void onRenderHud() {
        frames.onFrame();
        if (!platform.isScreenOpen()) {
            clicks.update();
        }

        // Any Astra screen hides the live HUD: the HUD editor draws the modules itself (with outlines
        // and dragging), and the mod menu shows them as card previews, so the HUD would only clutter it.
        if (platform.getOpenUi() != null) {
            return;
        }

        int[] res = getScaledResolution();
        for (HudModule hud : modules.getHudModules()) {
            if (hud.isShownOnHud()) {
                try {
                    hud.clampToScreen(res[0], res[1]);
                    hud.draw(false);
                } catch (Throwable t) {
                    report(hud, t);
                }
            }
        }
        platform.color(1f, 1f, 1f, 1f);
    }

    /** Called from the FOV calculation. Returns the FOV to use (zoom divides it). */
    public float modifyFov(float fov) {
        ZoomModule zoom = modules.get(ZoomModule.class);
        return zoom == null ? fov : zoom.applyFov(fov);
    }

    /** True while Astra draws the sidebar itself (vanilla's is skipped). */
    public boolean hidesVanillaScoreboard() {
        ScoreboardModule m = modules.get(ScoreboardModule.class);
        return m != null && m.isEnabled();
    }

    /** True while Astra draws the boss bar itself (vanilla's is skipped). */
    public boolean hidesVanillaBossBar() {
        BossBarModule m = modules.get(BossBarModule.class);
        return m != null && m.isEnabled();
    }

    private void report(Module module, Throwable t) {
        if (reportedErrors.add(module.getName())) {
            System.err.println("[Astra] " + module.getName() + " hit an error (shown once):");
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** {scaledWidth, scaledHeight, scaleFactor}, computed exactly like vanilla's ScaledResolution. */
    public int[] getScaledResolution() {
        int width = Display.getWidth();
        int height = Display.getHeight();
        int guiScale = platform.getGuiScale();
        if (guiScale == 0) {
            guiScale = 1000;
        }
        int factor = 1;
        while (factor < guiScale && width / (factor + 1) >= 320 && height / (factor + 1) >= 240) {
            factor++;
        }
        return new int[] {
                (int) Math.ceil(width / (double) factor),
                (int) Math.ceil(height / (double) factor),
                factor
        };
    }

    public void saveConfig() {
        if (config != null) {
            config.save();
        }
    }

    public ModuleManager getModules() {
        return modules;
    }

    public CosmeticsManager getCosmetics() {
        return cosmetics;
    }

    public FrameCounter getFrames() {
        return frames;
    }

    public ClickCounter getClicks() {
        return clicks;
    }
}
