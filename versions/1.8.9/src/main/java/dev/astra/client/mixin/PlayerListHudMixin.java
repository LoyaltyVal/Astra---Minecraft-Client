package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.ui.RankIcon;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * The tab list: ranked Astra players get their rank star in front of their name. Their name gets
 * a little space in front (so column widths account for it), and the star is drawn into that
 * space right after the name.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    /** Padded name → rank colour, for the names handed out this frame. */
    private static final Map<String, Integer> ASTRA_STARS = new HashMap<String, Integer>();

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void astra$roomForStar(PlayerListEntry entry, CallbackInfoReturnable<String> cir) {
        Astra astra = Astra.get();
        if (astra == null || astra.getCosmetics() == null || entry == null || entry.getProfile() == null
                || entry.getProfile().getId() == null || cir.getReturnValue() == null) {
            return;
        }
        Integer color = astra.getCosmetics().getRankColor(entry.getProfile().getId());
        if (color == null) {
            return;
        }
        String padded = RankIcon.NAME_PAD + cir.getReturnValue();
        if (ASTRA_STARS.size() > 512) {
            ASTRA_STARS.clear();
        }
        ASTRA_STARS.put(padded, color);
        cir.setReturnValue(padded);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I"))
    private int astra$drawStar(TextRenderer font, String text, float x, float y, int color) {
        int result = font.drawWithShadow(text, x, y, color);
        Integer star = text == null ? null : ASTRA_STARS.get(text);
        if (star != null) {
            RankIcon.draw(x, y - 0.5f, 8f, star);
        }
        return result;
    }
}
