package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Minecraft 1.7.10" in the corner of the title screen becomes "Astra Client 1.7.10". */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private static final int QUICK_JOIN = 0x4153_0001;
    private static final int FRIENDS = 0x4153_0002;
    private static final int SCREENSHOTS = 0x4153_0003;
    private static final int ACCOUNTS = 0x4153_0004;

    /** A row of Astra buttons under Options / Quit Game. */
    @Inject(method = "init", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void astra$buttons(CallbackInfo ci) {
        dev.astra.client.module.impl.ClientSettingsModule settings = Astra.get().tweaks();
        if (settings != null && !settings.titleButtons()) {
            return;
        }
        int y = this.height / 4 + 48 + 72 + 12 + 24;
        int x = this.width / 2 - 100;
        this.buttons.add(new ButtonWidget(QUICK_JOIN, x, y, 66, 20, "Quick Join"));
        this.buttons.add(new ButtonWidget(FRIENDS, x + 67, y, 66, 20, "Friends"));
        this.buttons.add(new ButtonWidget(SCREENSHOTS, x + 134, y, 66, 20, "Screenshots"));
        // Top-left: who you're playing as; opens the account switcher.
        String name = Astra.platform().getSessionName();
        int w = Math.min(150, this.textRenderer.getStringWidth(name) + 30);
        this.buttons.add(new ButtonWidget(ACCOUNTS, 4, 4, w, 20, name + " \u25be"));
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void astra$click(ButtonWidget button, CallbackInfo ci) {
        if (button.id == QUICK_JOIN) {
            dev.astra.client.features.QuickJoinScreen.open();
        } else if (button.id == FRIENDS) {
            Astra.platform().openUi(new dev.astra.client.social.FriendsScreen());
        } else if (button.id == ACCOUNTS) {
            Astra.platform().openUi(new dev.astra.client.features.AccountsScreen());
        } else if (button.id == SCREENSHOTS) {
            Astra.platform().openUi(new dev.astra.client.features.ScreenshotsScreen());
        } else {
            return;
        }
        ci.cancel();
    }


    @ModifyConstant(method = "render", constant = @Constant(stringValue = "Minecraft 1.7.10"))
    private String astra$brand(String original) {
        return Astra.brand("1.7.10");
    }

    /** TitleScreen#renderBackground (MCP: renderSkybox) draws the panorama; Menu Background replaces it. */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void astra$background(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        dev.astra.client.module.impl.MenuBackgroundModule bg =
                Astra.get().getModules().get(dev.astra.client.module.impl.MenuBackgroundModule.class);
        int[] size = Astra.get().getScaledResolution();
        if (bg != null && bg.draw(size[0], size[1])) {
            ci.cancel();
        }
    }
}
