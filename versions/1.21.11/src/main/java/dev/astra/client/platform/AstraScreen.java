package dev.astra.client.platform;

import dev.astra.client.input.Keyboard;
import dev.astra.client.ui.UiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shows an Astra {@link UiScreen} as a Minecraft screen (1.21.x, Mojang names). */
public final class AstraScreen extends Screen {

    private final UiScreen ui;

    public AstraScreen(UiScreen ui) {
        super(Component.literal("Astra"));
        this.ui = ui;
    }

    public UiScreen getUi() {
        return ui;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderCtx.begin(context);
        try {
            ui.frame(this.width, this.height, mouseX, mouseY, delta);
        } finally {
            RenderCtx.end();
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // The Astra screens draw their own backdrop.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Keys that don't type a character (Escape, Backspace, Enter, Tab, arrows...) go to the screen
     * here; letters come through {@link #charTyped}. Astra's screens get both like on 1.8.9.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int key = Keyboard.fromGlfw(keyCode);
        boolean escapeUsed = ui.usesEscape();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_DELETE
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            ui.charTyped('\0', key);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !escapeUsed) {
            onClose();
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (char chr : Character.toChars(event.codepoint())) {
            ui.charTyped(chr, 0);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        RenderCtx.addScroll(vertical);
        return true;
    }
}
