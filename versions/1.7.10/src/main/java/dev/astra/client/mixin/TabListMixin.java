package dev.astra.client.mixin;

import dev.astra.client.Astra;
import dev.astra.client.ui.RankIcon;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/**
 * The tab list (1.7 draws it inside the HUD): ranked Astra players get their rank star in front of
 * their name. 1.7's tab list only knows names, so this works for you and for Astra players Astra
 * has already looked up (everyone you've been near).
 */
@Mixin(InGameHud.class)
public abstract class TabListMixin {

    private static final Map<String, Integer> ASTRA_STARS = new HashMap<String, Integer>();

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Ljava/lang/String;)Ljava/lang/String;"))
    private String astra$roomForStar(AbstractTeam team, String name) {
        String decorated = Team.decorateName(team, name);
        Astra astra = Astra.get();
        Integer color = astra == null || astra.getCosmetics() == null ? null : astra.getCosmetics().getRankColorByName(name);
        if (color == null || decorated == null) {
            return decorated;
        }
        String padded = RankIcon.NAME_PAD + decorated;
        if (ASTRA_STARS.size() > 512) {
            ASTRA_STARS.clear();
        }
        ASTRA_STARS.put(padded, color);
        return padded;
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;method_956(Ljava/lang/String;III)I"))
    private int astra$drawStar(TextRenderer font, String text, int x, int y, int color) {
        int result = font.method_956(text, x, y, color);
        Integer star = text == null ? null : ASTRA_STARS.get(text);
        if (star != null) {
            RankIcon.draw(x, y - 0.5f, 8f, star);
        }
        return result;
    }
}
