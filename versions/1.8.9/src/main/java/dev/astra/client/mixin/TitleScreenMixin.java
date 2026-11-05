package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Astra's title screen ({@link dev.astra.client.ui.TitleMenu}): account menu, big buttons and an
 * icon bar, drawn over the panorama (or Menu Background). Vanilla's buttons stay, hidden, so
 * Singleplayer, Options and the rest still run vanilla's own code. With "Astra Title Screen" off
 * it's the vanilla screen plus a row of Astra buttons. "Minecraft 1.8.9" becomes "Astra Client 1.8.9".
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private static final int QUICK_JOIN = 0x4153_0001;
    private static final int FRIENDS = 0x4153_0002;
    private static final int SCREENSHOTS = 0x4153_0003;
    private static final int ACCOUNTS = 0x4153_0004;

    @Shadow
    private void renderBackground(int mouseX, int mouseY, float tickDelta) {
    }

    /** Astra's screen: hide vanilla's buttons (they still work when clicked through TitleMenu). */
    @Inject(method = "init", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void astra$buttons(CallbackInfo ci) {
        if (dev.astra.client.ui.TitleMenu.enabled()) {
            for (Object b : this.buttons) {
                ((ButtonWidget) b).visible = false;
            }
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


    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void astra$render(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (!dev.astra.client.ui.TitleMenu.enabled()) {
            return;
        }
        renderBackground(mouseX, mouseY, tickDelta);
        dev.astra.client.ui.TitleMenu.draw(this.width, this.height, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void astra$mouse(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (!dev.astra.client.ui.TitleMenu.enabled()) {
            return;
        }
        ci.cancel();
        dev.astra.client.ui.TitleMenu.click(this.width, this.height, mouseX, mouseY, button, new dev.astra.client.ui.TitleMenu.Actions() {
            @Override
            public void singleplayer() {
                press(1);
            }

            @Override
            public void multiplayer() {
                press(2);
            }

            @Override
            public void options() {
                press(0);
            }

            @Override
            public void language() {
                press(5);
            }

            @Override
            public void quit() {
                press(4);
            }
        });
    }

    /** Runs a vanilla title button (by id) through vanilla's own buttonClicked. */
    private void press(int id) {
        for (Object o : this.buttons) {
            ButtonWidget b = (ButtonWidget) o;
            if (b.id == id) {
                try {
                    this.buttonClicked(b);
                } catch (Exception e) {
                    System.err.println("[Astra] Title button " + id + " failed: " + e);
                }
                return;
            }
        }
    }

    @ModifyConstant(method = "render", constant = @Constant(stringValue = "Minecraft 1.8.9"))
    private String astra$brand(String original) {
        return Astra.brand("1.8.9");
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
