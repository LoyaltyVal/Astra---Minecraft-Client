package dev.astra.client.platform;

import java.util.ArrayList;
import java.util.List;

/** Plain snapshots of game state that HUD modules draw. Filled in by each version's PlatformImpl. */
public final class HudData {

    private HudData() {
    }

    /** The sidebar scoreboard: a title and up to 15 lines, top line first. */
    public static final class Sidebar {
        public final String title;
        public final List<Line> lines = new ArrayList<Line>();

        public Sidebar(String title) {
            this.title = title;
        }
    }

    public static final class Line {
        /** Player/entry name, already decorated with team prefix/suffix (may contain § colour codes). */
        public final String text;
        public final int score;

        public Line(String text, int score) {
            this.text = text;
            this.score = score;
        }
    }

    /** An item stack to draw (armor piece or held item). */
    public static final class Item {
        /** The game's own ItemStack, handed back to {@link Platform#drawItem}. */
        public final Object stack;
        public final int damage;
        public final int maxDamage;
        public final int count;

        public Item(Object stack, int damage, int maxDamage, int count) {
            this.stack = stack;
            this.damage = damage;
            this.maxDamage = maxDamage;
            this.count = count;
        }

        public boolean isDamageable() {
            return maxDamage > 0;
        }

        public int remaining() {
            return maxDamage - damage;
        }
    }

    /** An active potion effect. */
    public static final class Effect {
        public final String name;
        public final int amplifier;
        public final int durationTicks;
        /** Index into the inventory texture's effect icons, or -1 for none. */
        public final int iconIndex;

        public Effect(String name, int amplifier, int durationTicks, int iconIndex) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.iconIndex = iconIndex;
        }
    }

    /** The boss health bar (wither / ender dragon). */
    public static final class Boss {
        public final String name;
        public final float percent;

        public Boss(String name, float percent) {
            this.name = name;
            this.percent = percent;
        }
    }
}
