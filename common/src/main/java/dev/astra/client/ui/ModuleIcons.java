package dev.astra.client.ui;

import dev.astra.client.module.Module;
import dev.astra.client.util.Render;

import java.util.HashMap;
import java.util.Map;

/**
 * Pixel-art pictures (16 x 16, Minecraft style) for the mods that have no live HUD preview,
 * shown on their cards in the mod menu. Each row is a string; each letter is a colour from PALETTE
 * and '.' is see-through.
 */
public final class ModuleIcons {

    private static final Map<Character, Integer> PALETTE = new HashMap<Character, Integer>();
    private static final Map<String, String[]> ICONS = new HashMap<String, String[]>();

    static {
        PALETTE.put('k', 0xFF1B1A26);
        PALETTE.put('K', 0xFF1B1A26);
        PALETTE.put('w', 0xFFFFFFFF);
        PALETTE.put('g', 0xFFC9C9D6);
        PALETTE.put('G', 0xFF7D7C92);
        PALETTE.put('D', 0xFF4A4960);
        PALETTE.put('r', 0xFFFF5C6E);
        PALETTE.put('R', 0xFFB0283E);
        PALETTE.put('y', 0xFFFFD166);
        PALETTE.put('o', 0xFFFF9F43);
        PALETTE.put('O', 0xFFC96A14);
        PALETTE.put('b', 0xFF5AC8FA);
        PALETTE.put('B', 0xFF2C6FD1);
        PALETTE.put('p', 0xFFB79BFF);
        PALETTE.put('P', 0xFF7B5CF0);
        PALETTE.put('n', 0xFF5BD68A);
        PALETTE.put('N', 0xFF2E8F55);
        PALETTE.put('c', 0xFF9BE8FF);
        PALETTE.put('s', 0xFFF2C9A0);
        PALETTE.put('m', 0xFF9A6B45);
        PALETTE.put('M', 0xFF6B4528);

        ICONS.put("ClientSettingsModule", new String[]{
                "................",
                "......kkkk......",
                "..kk..kggk..kk..",
                ".kgGk.kggk.kGgk.",
                ".kGggkkggkkggGk.",
                "..kggggggggggk..",
                "...kgggGGgggk...",
                "kkkkggGkkGggkkkk",
                "kggggGk..kGggggk",
                "kkkkggGkkGggkkkk",
                "...kgggGGgggk...",
                "..kggggggggggk..",
                ".kGggkkggkkggGk.",
                ".kgGk.kggk.kGgk.",
                "..kk..kggk..kk..",
                "......kkkk......",
        });
        ICONS.put("CosmeticsModule", new String[]{
                "................",
                "...kkkkkkkkkk...",
                "..kPPPPPPPPPPk..",
                "..kPppppppppPk..",
                "..kPpPPPPPPpPk..",
                "..kPpPppppPpPk..",
                "..kPpPpyypPpPk..",
                "..kPpPpyypPpPk..",
                "..kPpPppppPpPk..",
                "..kPpPPPPPPpPk..",
                "..kPppppppppPk..",
                "..kPPPPPPPPPPk..",
                "..kPPkPPPPkPPk..",
                "..kPk.kPPk.kPk..",
                "..kk...kk...kk..",
                "................",
        });
        ICONS.put("ZoomModule", new String[]{
                "................",
                "....kkkkk.......",
                "...kcccccK......",
                "..kcwwcccck.....",
                ".kcwccccccck....",
                ".kccccccccck....",
                ".kccccccccck....",
                ".kccccccccck....",
                "..kcccccccK.....",
                "...kcccccKk.....",
                "....kkkkkGGk....",
                "..........kGGk..",
                "...........kGGk.",
                "............kGGk",
                ".............kk.",
                "................",
        });
        ICONS.put("FreelookModule", new String[]{
                "................",
                "................",
                "................",
                ".....kkkkkk.....",
                "...kkwwwwwwkk...",
                "..kwwwwkkwwwwk..",
                ".kwwwwkbbkwwwwk.",
                "kwwwwkbBBbkwwwwk",
                "kwwwwkbBkbkwwwwk",
                ".kwwwwkbbkwwwwk.",
                "..kwwwwkkwwwwk..",
                "...kkwwwwwwkk...",
                ".....kkkkkk.....",
                "................",
                "................",
                "................",
        });
        ICONS.put("FullbrightModule", new String[]{
                "................",
                ".......yy.......",
                "..y....yy....y..",
                "...y........y...",
                ".......kk.......",
                ".....kkyykk.....",
                "....kyyyyyyk....",
                "yy..kyywyyyk..yy",
                "yy..kyyyyyyk..yy",
                "....kyyyyyok....",
                ".....kkyokk.....",
                ".......kk.......",
                "...y........y...",
                "..y....yy....y..",
                ".......yy.......",
                "................",
        });
        ICONS.put("OldAnimationsModule", new String[]{
                "................",
                ".............kk.",
                "............kck.",
                "...........kcck.",
                "..........kcck..",
                ".........kcck...",
                "..g.....kcck....",
                "..gg...kcck.....",
                "...gg.kcck......",
                "..kk.kcck.......",
                "..kMkkck........",
                "...kMMk.........",
                "..kMkkMk........",
                ".kMk..kMk.......",
                ".kk....kk.......",
                "................",
        });
        ICONS.put("HurtcamModule", new String[]{
                "................",
                "................",
                "...kkk....kkk...",
                "..krrrk..krrrk..",
                ".krwrrrkkrrrrRk.",
                ".krwrrrrrrrrrRk.",
                "G.krrrrrrrrrRk.G",
                "GG.krrrrrrrRk.GG",
                "G...krrrrrRk...G",
                ".....krrrRk.....",
                "......krRk......",
                ".......kk.......",
                "................",
                "................",
                "................",
                "................",
        });
        ICONS.put("NoGlintModule", new String[]{
                "................",
                "..p..........kk.",
                ".ppp........kck.",
                "..p........kcck.",
                "..........kcck..",
                "rr.......kcck.rr",
                ".rr.....kcck.rr.",
                "..rr...kcck.rr..",
                "...rr.kcck.rr...",
                "..kkrrkck.rr....",
                "..kMkrrk.rr.....",
                "...kMMrrrr......",
                "..kMkrrrr.......",
                ".kMk.rrrrr......",
                ".kkrrr..rrr.....",
                "..rr......rr..p.",
        });
        ICONS.put("ChatModule", new String[]{
                "................",
                ".kkkkkkkkkkk....",
                "kGGGGGGGGGGGk...",
                "kGwwwwwwGGGGk...",
                "kGGGGGGGGGGGk...",
                "kGwwwwGGGkkkkkkk",
                "kGGGGGGGkbbbbbbk",
                ".kkGkkkkkbwwwwbk",
                "...kk...kbbbbbbk",
                "........kbwwwbbk",
                "........kbbbbbbk",
                ".........kkkkbkk",
                "............kk..",
                "................",
                "................",
                "................",
        });
        ICONS.put("AutoTextModule", new String[]{
                "................",
                "................",
                "..kkkkkkkk......",
                ".kggggggggk.....",
                ".kgwwwwwwgk.....",
                ".kgwkkkkwgk...y.",
                ".kgwwkkwwgk..yy.",
                ".kgwwkkwwgk.yyy.",
                ".kgwwkkwwgk..yy.",
                ".kgwwwwwwgk...y.",
                ".kggggggggk.....",
                ".kGGGGGGGGk.....",
                "..kkkkkkkk......",
                "................",
                "................",
                "................",
        });
        ICONS.put("EmotesModule", new String[]{
                "................",
                ".......kkkk.....",
                "......kssssk....",
                "......kskskk....",
                "......kssssk.k..",
                ".......kkkk.ksk.",
                "......kppppkksk.",
                ".....kppppppkk..",
                "....kspppppk....",
                "....ks.kpppk....",
                ".......kpppk....",
                "......kBBkBBk...",
                "......kBk.kBk...",
                "......kBk.kBk...",
                ".....kkkk.kkkk..",
                "................",
        });
        ICONS.put("AutoGGModule", new String[]{
                "................",
                "..kkkkkkkkkkkk..",
                ".knnnnnnnnnnnnk.",
                ".knwwwwnnwwwwnk.",
                ".knwnnnnnwnnnnk.",
                ".knwnwwnnwnwwnk.",
                ".knwnnwnnwnnwnk.",
                ".knwwwwnnwwwwnk.",
                ".knnnnnnnnnnnnk.",
                "..kkkknnkkkkkk..",
                "....knnk........",
                "....knk.........",
                "....kk..........",
                "................",
                "................",
                "................",
        });
        ICONS.put("WaypointsModule", new String[]{
                "................",
                ".....kkkkkk.....",
                "....krrrrrrk....",
                "...krrwwrrrRk...",
                "...krwwwwrrRk...",
                "...krrwwrrrRk...",
                "...krrrrrrrRk...",
                "....krrrrrRk....",
                ".....krrrRk.....",
                "......krRk......",
                "......krRk......",
                ".......kk.......",
                "....NNNNNNNN....",
                "..NNnnnnnnnnNN..",
                "....NNNNNNNN....",
                "................",
        });
        ICONS.put("ScreenshotsModule", new String[]{
                "................",
                "................",
                ".....kkkkk......",
                "....kGGGGGk.....",
                "kkkkkkkkkkkkkkk.",
                "kGGGGGkkkkkGGGk.",
                "kGGGGkcccckGyGk.",
                "kGGGkccwccckGGk.",
                "kGGGkcwcccckGGk.",
                "kGGGkcccccckGGk.",
                "kGGGGkcccckGGGk.",
                "kGGGGGkkkkGGGGk.",
                "kkkkkkkkkkkkkkk.",
                "................",
                "................",
                "................",
        });
        ICONS.put("PerformanceModule", new String[]{
                "................",
                ".........kkkk...",
                "........kyyyk...",
                ".......kyyyk....",
                "......kyyyk.....",
                ".....kyyyk......",
                "....kyyyykkkk...",
                "...kyyyyyyyyk...",
                "...kkkkyyyyk....",
                ".......kyyk.....",
                "......kyyk......",
                ".....kyyk.......",
                "....kyyk........",
                "....kyk.........",
                "....kk..........",
                "................",
        });
        ICONS.put("BorderlessModule", new String[]{
                "................",
                "kkkkk......kkkkk",
                "kwwwk......kwwwk",
                "kwwk........kwwk",
                "kwk.kkkkkkkk.kwk",
                "kk..kbbbbbbk..kk",
                "....kbBBBBbk....",
                "....kbBBBBbk....",
                "....kbBBBBbk....",
                "....kbBBBBbk....",
                "kk..kbbbbbbk..kk",
                "kwk.kkkkkkkk.kwk",
                "kwwk........kwwk",
                "kwwwk......kwwwk",
                "kkkkk......kkkkk",
                "................",
        });
        ICONS.put("MenuBackgroundModule", new String[]{
                "................",
                "kkkkkkkkkkkkkkkk",
                "kmmmmmmmmmmmmmmk",
                "kmbbbbbbbbbbbbmk",
                "kmbbbbbbbbbyybmk",
                "kmbbbbbbbbbyybmk",
                "kmbbbbbgbbbbbbmk",
                "kmbbbbgggbbbbbmk",
                "kmbbbgggggbbbbmk",
                "kmbbgggggggbnnmk",
                "kmbgggggggggnnmk",
                "kmnnnnnnnnnnnnmk",
                "kmNNNNNNNNNNNNmk",
                "kmmmmmmmmmmmmmmk",
                "kkkkkkkkkkkkkkkk",
                "................",
        });
    }

    private ModuleIcons() {
    }

    public static boolean has(Module m) {
        return ICONS.containsKey(m.getClass().getSimpleName());
    }

    /** Draws the module's picture centred on (cx, cy), each pixel `scale` GUI pixels wide. */
    public static void draw(Module m, float cx, float cy, int scale) {
        String[] rows = ICONS.get(m.getClass().getSimpleName());
        if (rows == null) {
            return;
        }
        int x0 = Math.round(cx - 8 * scale);
        int y0 = Math.round(cy - 8 * scale);
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            int x = 0;
            while (x < row.length()) {
                char ch = row.charAt(x);
                int run = 1;
                while (x + run < row.length() && row.charAt(x + run) == ch) {
                    run++;
                }
                Integer color = PALETTE.get(ch);
                if (color != null) {
                    // One rectangle per run of same-coloured pixels keeps it to a few dozen draws.
                    Render.rect(x0 + x * scale, y0 + y * scale, run * scale, scale, color);
                }
                x += run;
            }
        }
    }
}
