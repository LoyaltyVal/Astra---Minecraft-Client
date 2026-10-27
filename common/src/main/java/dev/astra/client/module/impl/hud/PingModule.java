package dev.astra.client.module.impl.hud;

import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.BooleanSetting;

/** Your ping to the server. */
public final class PingModule extends TextHudModule {

    private final BooleanSetting colored;

    public PingModule() {
        super("Ping", "Your connection latency to the server.", false, 136, 14);
        colored = setting(new BooleanSetting("Color By Ping", true));
    }

    @Override
    protected String getText() {
        int ping = mc().isInGame() ? mc().getPing() : 42;
        if (ping < 0) {
            return "Ping: --";
        }
        String color = !colored.isOn() ? "" : ping < 80 ? "§a" : ping < 150 ? "§e" : "§c";
        return "Ping: " + color + ping + "ms";
    }
}
