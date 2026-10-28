package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;

/**
 * Chat tweaks: keep far more history than vanilla's 100 lines (scroll up as far as you like) and
 * stack repeated messages into one line with a counter, "gg (x3)". See ChatHudMixin.
 */
public final class ChatModule extends Module {

    public static final int LONG_HISTORY = 5000;

    private final BooleanSetting infiniteHistory;
    private final BooleanSetting stack;

    public ChatModule() {
        super("Chat", "Longer chat history and stacked repeats.", Category.VISUAL, true);
        infiniteHistory = setting(new BooleanSetting("Long History", true));
        stack = setting(new BooleanSetting("Stack Repeated Messages", true));
    }

    public int historyLimit(int vanilla) {
        return isEnabled() && infiniteHistory.isOn() ? LONG_HISTORY : vanilla;
    }

    public boolean stacks() {
        return isEnabled() && stack.isOn();
    }

    @Override
    public String getPreviewText() {
        return "gg (x3)";
    }
}
