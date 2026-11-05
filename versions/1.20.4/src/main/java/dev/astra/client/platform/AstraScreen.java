package dev.astra.client.platform;

import dev.astra.client.input.Keyboard;
import dev.astra.client.ui.UiScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Shows an Astra {@link UiScreen} as a Minecraft screen (1.20.4). */
public final class AstraScreen extends Screen {

    private final UiScreen ui;

    public AstraScreen(UiScreen ui) {
        super(Text.literal("Astra"));
        this.ui = ui;
    }

    public UiScreen getUi() {
        return ui;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderCtx.begin(context);
        try {
            ui.frame(this.width, this.height, mouseX, mouseY, delta);
        } finally {
            RenderCtx.end();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The Astra screens draw their own backdrop.
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Keys that don't type a character (Escape, Backspace, Enter, Tab, arrows...) go to the screen
     * here; letters come through {@link #charTyped}. Astra's screens get both like on 1.8.9.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = Keyboard.fromGlfw(keyCode);
        boolean escapeUsed = ui.usesEscape();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_DELETE
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            ui.charTyped('\0', key);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !escapeUsed) {
            close();
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        ui.charTyped(chr, 0);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        RenderCtx.addScroll(vertical);
        return true;
    }
}
