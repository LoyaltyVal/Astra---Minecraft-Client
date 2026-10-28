package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.ChatModule;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat module: a much longer history, and repeated messages stacked into one line ("gg (x3)").
 * Stacking gives every normal message its own id, so the previous copy can be removed and
 * replaced with the counted one.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    private static final int ASTRA_ID_BASE = 0x41530000;

    private String astra$lastText;
    private int astra$lastId;
    private int astra$count;
    private int astra$nextId;

    @Shadow
    public abstract void addMessage(Text text, int id);

    @Shadow
    public abstract void removeMessage(int id);

    private static ChatModule astra$chat() {
        Astra astra = Astra.get();
        return astra == null ? null : astra.getModules().get(ChatModule.class);
    }

    /** Vanilla drops lines past 100. */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int astra$history(int vanilla) {
        ChatModule chat = astra$chat();
        return chat == null ? vanilla : chat.historyLimit(vanilla);
    }

    /** Every chat line from the server (and the game) passes here; Auto GG and others listen. */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void astra$onChat(Text text, CallbackInfo ci) {
        if (text != null && Astra.get() != null) {
            Astra.get().onChat(text.asUnformattedString());
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;I)V", at = @At("HEAD"), cancellable = true)
    private void astra$stack(Text text, int id, CallbackInfo ci) {
        ChatModule chat = astra$chat();
        if (chat == null || !chat.stacks() || id != 0 || text == null) {
            return; // messages with their own id (and our re-adds) go through untouched
        }
        ci.cancel();
        String plain = text.asFormattedString();
        if (plain.equals(astra$lastText)) {
            astra$count++;
            removeMessage(astra$lastId);
            addMessage(text.copy().append(" \u00a77(x" + astra$count + ")"), astra$lastId);
        } else {
            astra$lastText = plain;
            astra$count = 1;
            astra$nextId = (astra$nextId + 1) & 0xFFFF;
            astra$lastId = ASTRA_ID_BASE + astra$nextId;
            addMessage(text, astra$lastId);
        }
    }
}
