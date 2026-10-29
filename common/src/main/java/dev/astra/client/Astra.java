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
import dev.astra.client.ui.HudEditorScreen;
import dev.astra.client.ui.ModMenuScreen;
import dev.astra.client.util.ClickCounter;
import dev.astra.client.util.FrameCounter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import dev.astra.client.input.Keyboard;
import dev.astra.client.input.Window;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Astra client entrypoint. The version-specific mixins call {@link #onTick()},
 * {@link #onRenderHud(float)}, {@link #onChat(String)}, {@link #modifyFov(float)} and the cosmetics hooks; everything else
 * happens in /common.
 */
public final class Astra implements ClientModInitializer {

    public static final String NAME = "Astra";
    public static final String VERSION = "1.0.0";
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
    private final dev.astra.client.social.SocialManager social = new dev.astra.client.social.SocialManager();
    private boolean friendsKeyWasDown;
    private dev.astra.client.ui.UiScreen lastUi;
    private boolean menuKeyWasDown;
    private boolean titleSet;
    private float partialTicks = 1f;

    @Override
    public void onInitializeClient() {
        instance = this;
        modules.registerDefaults();

        File file = FabricLoader.getInstance().getConfigDir().resolve("astra.json").toFile();
        config = new ConfigManager(file, modules);
        config.load();

        // The Astra app passes the cosmetics server and your session token when it starts the game.
        cosmetics = new CosmeticsManager(System.getProperty("astra.api"), System.getProperty("astra.token"));

        try {
            dev.astra.client.features.Radio.get().startUp();
        } catch (Throwable t) {
            System.err.println("[Astra] Radio couldn't start: " + t);
        }

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
        if (!titleSet) {
            titleSet = true;
            try {
                dev.astra.client.input.Window.setTitle(brand(platform.minecraftVersion()));
            } catch (Throwable ignored) {
                // no window yet
            }
        }
        boolean menuDown = Keyboard.isKeyDown(MENU_KEY);
        if (menuDown && !menuKeyWasDown && platform.isInGame() && !platform.isScreenOpen()) {
            platform.openUi(new HudEditorScreen());
        }
        menuKeyWasDown = menuDown;

        // Shift + Tab opens the friends menu.
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean friendsDown = shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
        if (friendsDown && !friendsKeyWasDown && platform.isInGame() && !platform.isScreenOpen()) {
            platform.openUi(new dev.astra.client.social.FriendsScreen());
        }
        friendsKeyWasDown = friendsDown;
        dev.astra.client.ui.UiScreen ui = platform.getOpenUi();
        if (lastUi != ui && lastUi != null) {
            try {
                lastUi.onClosed();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        lastUi = ui;

        cosmetics.tick();
        social.tick();
        dev.astra.client.features.Accounts.get().tick();
        trackServer();
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

    /** A chat line was added (from the server or the game); passed to modules such as Auto GG. */
    public void onChat(String message) {
        if (message == null) {
            return;
        }
        for (Module module : modules.getModules()) {
            if (module.isEnabled()) {
                try {
                    module.onChat(message);
                } catch (Throwable t) {
                    report(module, t);
                }
            }
        }
    }

    private String lastServer;

    /** Remembers multiplayer servers you join, for Quick Join's "Recent" list. */
    private void trackServer() {
        String server = platform.isInGame() ? platform.getServerAddress() : null;
        if (server != null && !server.equals(lastServer) && !"singleplayer".equals(server)) {
            try {
                dev.astra.client.features.Servers.get().played(server);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        lastServer = server;
    }

    /** How far between ticks this frame is (0..1), for smooth in-world positions on the HUD. */
    public float getPartialTicks() {
        return partialTicks;
    }

    /** Called every frame after the vanilla HUD has been drawn. */
    public void onRenderHud(float tickDelta) {
        partialTicks = tickDelta;
        frames.onFrame();
        if (!platform.isScreenOpen()) {
            clicks.update();
        }

        dev.astra.client.social.Notifications.render(getScaledResolution()[0]);

        // Any Astra screen hides the live HUD: the HUD editor draws the modules itself (with outlines
        // and dragging), and the mod menu shows them as card previews, so the HUD would only clutter it.
        if (platform.getOpenUi() != null) {
            return;
        }

        if (platform.isInGame() && !platform.isScreenOpen()) {
            for (Module module : modules.getModules()) {
                if (module.isEnabled()) {
                    try {
                        module.onRenderHud(tickDelta);
                    } catch (Throwable t) {
                        report(module, t);
                    }
                }
            }
            platform.color(1f, 1f, 1f, 1f);
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

    /** Client-wide options and small game tweaks (used by several mixins). */
    public dev.astra.client.module.impl.ClientSettingsModule tweaks() {
        return modules.get(dev.astra.client.module.impl.ClientSettingsModule.class);
    }

    /** True when your own name tag should be drawn (third person + the setting, or freelook). */
    public boolean showOwnNametag() {
        if (platform.getPerspective() == 0) {
            return false;
        }
        dev.astra.client.module.impl.FreelookModule f = freelook();
        if (f != null) {
            return f.showOwnNametag();
        }
        dev.astra.client.module.impl.ClientSettingsModule t = tweaks();
        return t != null && t.ownNametag();
    }

    /** "Astra Client 1.8.9": the window title and the title screen's version text. */
    public static String brand(String minecraftVersion) {
        return NAME + " Client " + minecraftVersion;
    }

    /** The freelook module when it's moving the camera, else null (used by the camera mixins). */
    public dev.astra.client.module.impl.FreelookModule freelook() {
        dev.astra.client.module.impl.FreelookModule f = modules.get(dev.astra.client.module.impl.FreelookModule.class);
        return f != null && f.isLooking() ? f : null;
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
        int width = Window.getWidth();
        int height = Window.getHeight();
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

    public dev.astra.client.social.SocialManager getSocial() {
        return social;
    }

    public FrameCounter getFrames() {
        return frames;
    }

    public ClickCounter getClicks() {
        return clicks;
    }
}
