package dev.astra.client.module.impl.hud;

import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.ModeSetting;

public final class MemoryModule extends TextHudModule {

    private final ModeSetting mode;

    public MemoryModule() {
        super("Memory", "Shows how much RAM the game is using.", false, 70, 46);
        mode = setting(new ModeSetting("Display", "Percent", "Percent", "Megabytes"));
    }

    @Override
    protected String getText() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        if (mode.is("Megabytes")) {
            return "Mem: " + (used / 1048576L) + "/" + (max / 1048576L) + "MB";
        }
        return "Mem: " + (used * 100L / max) + "%";
    }
}
