package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.KeySetting;
import dev.astra.client.module.setting.TextSetting;
import dev.astra.client.util.Keys;

/**
 * Hotkeys that send a chat message or command, e.g. F6 = "/play duels_classic_duel" or "gg".
 * Each slot has a key and a message; press the key in game to send it.
 */
public final class AutoTextModule extends Module {

    private static final int SLOTS = 6;
    private static final long COOLDOWN_MS = 1000;

    private final KeySetting[] keys = new KeySetting[SLOTS];
    private final TextSetting[] messages = new TextSetting[SLOTS];
    private final boolean[] wasDown = new boolean[SLOTS];
    private final long[] lastSent = new long[SLOTS];

    public AutoTextModule() {
        super("Auto Text", "Hotkeys that send a message or command.", Category.MECHANIC, true);
        String[] examples = {"gg", "/play duels_classic_duel", "/lobby", "", "", ""};
        for (int i = 0; i < SLOTS; i++) {
            keys[i] = setting(new KeySetting("Hotkey " + (i + 1), Keys.NONE));
            messages[i] = setting(new TextSetting("Message " + (i + 1), "", 100,
                    examples[i].isEmpty() ? "Type a message or /command" : "e.g. " + examples[i]));
        }
    }

    @Override
    public void onTick() {
        boolean canSend = mc().isInGame() && !mc().isScreenOpen();
        long now = System.currentTimeMillis();
        for (int i = 0; i < SLOTS; i++) {
            boolean down = canSend && keys[i].getCode() != Keys.NONE && keys[i].isDown();
            if (down && !wasDown[i] && !messages[i].isEmpty() && now - lastSent[i] > COOLDOWN_MS) {
                mc().sendChatMessage(messages[i].get().trim());
                lastSent[i] = now;
            }
            wasDown[i] = down;
        }
    }

    @Override
    public String getPreviewText() {
        for (int i = 0; i < SLOTS; i++) {
            if (keys[i].getCode() != Keys.NONE && !messages[i].isEmpty()) {
                return Keys.name(keys[i].getCode()) + ": " + messages[i].get();
            }
        }
        return "Set a hotkey";
    }
}
