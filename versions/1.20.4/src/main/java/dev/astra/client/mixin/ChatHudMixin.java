package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat lines go to modules like Auto GG. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void astra$onChat(Text text, CallbackInfo ci) {
        if (text != null && Astra.get() != null) {
            Astra.get().onChat(text.getString());
        }
    }
}
