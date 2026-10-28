package dev.astra.client.module.impl;

import dev.astra.client.module.Category;
import dev.astra.client.module.Module;
import dev.astra.client.module.setting.BooleanSetting;

/**
 * 1.7 animations on 1.8.9: block-hitting (the sword swings while blocking), swinging while
 * eating/drinking and drawing a bow, red armor when hit, and smooth sneaking. Visual only: nothing
 * extra is sent to the server. (The mixins live in versions/1.8.9; on 1.7.10 this is how it already looks.)
 */
public final class OldAnimationsModule extends Module {

    private final BooleanSetting blockHit;
    private final BooleanSetting swingWhileUsing;
    private final BooleanSetting eatSwing;
    private final BooleanSetting bowSwing;
    private final BooleanSetting redArmor;
    private final BooleanSetting smoothSneak;

    private float sneakOffset;
    private long lastFrame = System.nanoTime();

    public OldAnimationsModule() {
        super("1.7 Animations", "Block-hitting, red armor, smooth sneak and more from 1.7.", Category.VISUAL, true);
        blockHit = setting(new BooleanSetting("Block-hit Animation", true));
        swingWhileUsing = setting(new BooleanSetting("Swing While Using Items", true));
        eatSwing = setting(new BooleanSetting("1.7 Eating & Drinking", true));
        bowSwing = setting(new BooleanSetting("1.7 Bow", true));
        redArmor = setting(new BooleanSetting("Red Armor When Hit", true));
        smoothSneak = setting(new BooleanSetting("Smooth Sneaking", true));
    }

    private boolean on(BooleanSetting s) {
        return isEnabled() && s.isOn() && mc().minecraftVersion().startsWith("1.8");
    }

    public boolean blockHit() {
        return on(blockHit);
    }

    public boolean eatSwing() {
        return on(eatSwing);
    }

    public boolean bowSwing() {
        return on(bowSwing);
    }

    public boolean redArmor() {
        return on(redArmor);
    }

    @Override
    public void onTick() {
        if (on(swingWhileUsing) && mc().isInGame() && !mc().isScreenOpen()) {
            mc().swingIfUsingAndAttacking();
        }
    }

    /**
     * The camera height while sneaking, eased over a few frames like 1.7 instead of snapping.
     * Called from the camera code with the real eye height.
     */
    public float smoothEyeHeight(float eyeHeight, float standingEyeHeight) {
        if (!on(smoothSneak)) {
            sneakOffset = 0f;
            return eyeHeight;
        }
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float target = standingEyeHeight - eyeHeight;
        sneakOffset += (target - sneakOffset) * (1f - (float) Math.exp(-seconds * 14f));
        if (Math.abs(target - sneakOffset) < 0.001f) {
            sneakOffset = target;
        }
        return standingEyeHeight - sneakOffset;
    }

    @Override
    public String getPreviewText() {
        return "Block-hit";
    }
}
