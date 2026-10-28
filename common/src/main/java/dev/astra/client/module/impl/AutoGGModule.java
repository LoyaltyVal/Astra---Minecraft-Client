package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;
import dev.astra.client.module.setting.NumberSetting;
import dev.astra.client.module.setting.TextSetting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Says "gg" when a game ends. Watches chat for the end-of-game lines servers print (Hypixel,
 * Minemen, PvP Land, BlocksMC and most minigame servers use one of these), ignoring anything a
 * player typed, then sends the message after a short delay.
 */
public final class AutoGGModule extends Module {

    /** Lines that only appear when a game is over. Compared in lower case. */
    private static final String[] END_LINES = {
            "reward summary",            // Hypixel, after every game
            "1st killer -",              // Hypixel
            "winner:", "winners:", "winning team",
            "1st place -",
            "you won! want to play again",
            "you died! want to play again",
            "victory!", "game over!",
            "won the game", "has won the game",
            "match results", "match complete", "match over", "duel ended",
            "winner -", "game ended"
    };

    /** "Name: text", "[RANK] Name: text", "Guild > ...": things players say, never triggers. */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:\\[[^\\]]{1,24}\\] ?)*(?:[A-Za-z0-9_]{1,16})(?: \\[[^\\]]{1,24}\\])*: .*"
                    + "|^(?:guild|party|officer|co-op|from|to) ?>.*|^(?:from|to) .{1,40}: .*",
            Pattern.CASE_INSENSITIVE);

    private final TextSetting message = setting(new TextSetting("Message", "gg", 100, "e.g. gg or gg wp"));
    private final NumberSetting delay = setting(new NumberSetting("Delay (seconds)", 1.0, 0.0, 5.0, 0.5));
    private final BooleanSetting secondLine = setting(new BooleanSetting("Also Say a Second Message", false));
    private final TextSetting second = setting(new TextSetting("Second Message", "Astra Client", 100, "Sent right after the first"));
    private final TextSetting extraTriggers = setting(new TextSetting("Extra Triggers", "", 200,
            "More end-of-game lines, separated by |"));

    private long sendAt;
    private long lastSent;

    public AutoGGModule() {
        super("Auto GG", "Says gg in chat when a game ends.", Category.MECHANIC, false);
    }

    @Override
    public void onChat(String raw) {
        if (sendAt != 0 || System.currentTimeMillis() - lastSent < 10_000) {
            return; // one gg per game
        }
        String line = strip(raw);
        // Players' chat starts at the left; servers centre their end-of-game lines with spaces.
        if (!line.startsWith(" ") && PLAYER_CHAT.matcher(line).matches()) {
            return;
        }
        String lower = line.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty() || lower.length() > 120) {
            return;
        }
        if (matches(lower)) {
            sendAt = System.currentTimeMillis() + (long) (delay.get() * 1000);
        }
    }

    private boolean matches(String lower) {
        for (String end : END_LINES) {
            if (lower.contains(end)) {
                return true;
            }
        }
        for (String extra : extraTriggers.get().split("\\|")) {
            String e = extra.trim().toLowerCase(Locale.ROOT);
            if (!e.isEmpty() && lower.contains(e)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick() {
        if (sendAt == 0 || System.currentTimeMillis() < sendAt) {
            return;
        }
        sendAt = 0;
        lastSent = System.currentTimeMillis();
        if (!mc().isInGame()) {
            return;
        }
        String text = message.get().trim();
        if (!text.isEmpty()) {
            mc().sendChatMessage(text);
        }
        if (secondLine.isOn() && !second.get().trim().isEmpty()) {
            mc().sendChatMessage(second.get().trim());
        }
    }

    /** Removes colour codes (§x). */
    private static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    @Override
    public String getPreviewText() {
        return message.isEmpty() ? "gg" : message.get();
    }
}
