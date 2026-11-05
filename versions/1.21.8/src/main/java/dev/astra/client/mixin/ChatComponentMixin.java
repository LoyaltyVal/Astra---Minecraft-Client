package dev.astra.client.mixin;

import dev.astra.client.Astra;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat lines go to modules like Auto GG. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void astra$onChat(Component text, CallbackInfo ci) {
        if (text != null && Astra.get() != null) {
            Astra.get().onChat(text.getString());
        }
    }
}
