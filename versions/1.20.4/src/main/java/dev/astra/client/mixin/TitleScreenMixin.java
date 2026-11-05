package dev.astra.client.mixin;

import dev.astra.client.platform.RenderCtx;
import dev.astra.client.ui.TitleMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Astra's title screen ({@link TitleMenu}) over the panorama, when "Astra Title Screen" is on. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Shadow
    @Final
    private RotatingCubeMapRenderer backgroundRenderer;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void astra$hideButtons(CallbackInfo ci) {
        if (TitleMenu.enabled()) {
            this.clearChildren();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void astra$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!TitleMenu.enabled()) {
            return;
        }
        ci.cancel();
        dev.astra.client.module.impl.MenuBackgroundModule bg = dev.astra.client.Astra.get().getModules()
                .get(dev.astra.client.module.impl.MenuBackgroundModule.class);
        RenderCtx.begin(context);
        try {
            if (bg == null || !bg.draw(this.width, this.height)) {
                backgroundRenderer.render(delta, 1f);
            }
            TitleMenu.draw(this.width, this.height, mouseX, mouseY);
        } finally {
            RenderCtx.end();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void astra$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!TitleMenu.enabled()) {
            return;
        }
        final Screen self = this;
        final MinecraftClient mc = MinecraftClient.getInstance();
        cir.setReturnValue(TitleMenu.click(this.width, this.height, (int) mouseX, (int) mouseY, button, new TitleMenu.Actions() {
            @Override
            public void singleplayer() {
                mc.setScreen(new SelectWorldScreen(self));
            }

            @Override
            public void multiplayer() {
                mc.setScreen(new MultiplayerScreen(self));
            }

            @Override
            public void options() {
                mc.setScreen(new OptionsScreen(self, mc.options));
            }

            @Override
            public void language() {
                mc.setScreen(new LanguageOptionsScreen(self, mc.options, mc.getLanguageManager()));
            }

            @Override
            public void quit() {
                mc.scheduleStop();
            }
        }));
    }
}
