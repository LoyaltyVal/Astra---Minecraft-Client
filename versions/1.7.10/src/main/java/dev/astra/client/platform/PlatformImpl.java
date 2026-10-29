package dev.astra.client.platform;

import dev.astra.client.ui.UiScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.class_482;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 1.7.10 implementation (Legacy Yarn build 604 mappings).
 *
 * Every Minecraft name used by Astra on 1.7.10 lives in this file, AstraScreen and the mixin/
 * folder. If a mappings update renames something, run {@code gradlew :versions:1.7.10:genSources}
 * and look up the new name in the decompiled sources.
 */
public final class PlatformImpl extends dev.astra.client.platform.legacy.LegacyPlatform {

    private static final Identifier INVENTORY = new Identifier("textures/gui/container/inventory.png");

    private final DrawableHelper drawer = new DrawableHelper();
    private ItemRenderer itemRenderer;
    private final Map<String, Identifier> guiTextures = new HashMap<String, Identifier>();

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /** Legacy Yarn doesn't name this field on 1.7.10 (MCP: Minecraft#thePlayer). */
    private static net.minecraft.entity.player.ControllablePlayerEntity player() {
        return mc().field_3805;
    }

    @Override
    public String minecraftVersion() {
        return "1.7.10";
    }

    @Override
    public boolean isInGame() {
        return player() != null && mc().world != null;
    }

    @Override
    public boolean isScreenOpen() {
        return mc().currentScreen != null;
    }

    @Override
    public UiScreen getOpenUi() {
        Screen screen = mc().currentScreen;
        return screen instanceof AstraScreen ? ((AstraScreen) screen).getUi() : null;
    }

    @Override
    public void openUi(UiScreen ui) {
        mc().setScreen(ui == null ? null : new AstraScreen(ui));
    }

    @Override
    public int getGuiScale() {
        return mc().options.guiScale;
    }

    @Override
    public File getGameDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        DrawableHelper.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow) {
        // 1.7.10's font renderer only takes whole-pixel coordinates.
        mc().textRenderer.draw(text, Math.round(x), Math.round(y), argb, shadow);
    }

    @Override
    public int getTextWidth(String text) {
        return mc().textRenderer.getStringWidth(text);
    }

    @Override
    public int getFontHeight() {
        return mc().textRenderer.fontHeight;
    }

    @Override
    public void drawItem(Object stack, int x, int y) {
        ItemStack item = (ItemStack) stack;
        if (itemRenderer == null) {
            itemRenderer = new ItemRenderer();
        }
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(770, 771);
        DiffuseLighting.enable();
        // method_6920 = renderItemAndEffectIntoGUI, method_1549 = renderItemOverlayIntoGUI (unnamed in 1.7.10 yarn).
        itemRenderer.method_6920(mc().textRenderer, mc().getTextureManager(), item, x, y);
        itemRenderer.method_1549(mc().textRenderer, mc().getTextureManager(), item, x, y);
        DiffuseLighting.disable();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    @Override
    public void drawEffectIcon(int iconIndex, int x, int y) {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_BLEND);
        mc().getTextureManager().bindTexture(INVENTORY);
        drawer.drawTexture(x, y, iconIndex % 8 * 18, 198 + iconIndex / 8 * 18, 18, 18);
    }

    @Override
    public void drawGuiTexture(String path, int x, int y, int u, int v, int width, int height) {
        Identifier id = guiTextures.get(path);
        if (id == null) {
            id = new Identifier(path);
            guiTextures.put(path, id);
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_BLEND);
        mc().getTextureManager().bindTexture(id);
        drawer.drawTexture(x, y, u, v, width, height);
    }

    // ------------------------------------------------------------------ textures & GL

    @Override
    public Object createTexture(String name, BufferedImage image) {
        return mc().getTextureManager().registerDynamicTexture(name, new NativeImageBackedTexture(image));
    }

    @Override
    public void bindTexture(Object handle) {
        mc().getTextureManager().bindTexture((Identifier) handle);
    }

    @Override
    public void color(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    @Override
    public void blend(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(770, 771);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    @Override
    public void cull(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Override
    public void texture2d(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
    }

    // ------------------------------------------------------------------ player

    @Override
    public double getPlayerX() {
        return player().x;
    }

    @Override
    public double getPlayerY() {
        return player().y;
    }

    @Override
    public double getPlayerZ() {
        return player().z;
    }

    @Override
    public float getPlayerYaw() {
        return player().yaw;
    }

    @Override
    public boolean isSprinting() {
        return isInGame() && player().isSprinting();
    }

    @Override
    public HudData.Item[] getArmor() {
        if (!isInGame()) {
            return new HudData.Item[0];
        }
        ItemStack[] armor = player().inventory.armor;
        HudData.Item[] out = new HudData.Item[4];
        for (int i = 0; i < 4; i++) {
            out[i] = item(armor[3 - i]); // helmet first
        }
        return out;
    }

    @Override
    public HudData.Item getHeldItem() {
        if (!isInGame()) {
            return null;
        }
        return item(player().inventory.main[player().inventory.selectedSlot]);
    }

    private static HudData.Item item(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        int max = stack.isDamageable() ? stack.getMaxDamage() : 0;
        return new HudData.Item(stack, stack.getDamage(), max, stack.count);
    }

    @Override
    public List<HudData.Effect> getEffects() {
        List<HudData.Effect> out = new ArrayList<HudData.Effect>();
        if (!isInGame()) {
            return out;
        }
        for (Object o : player().getStatusEffectInstances()) {
            StatusEffectInstance inst = (StatusEffectInstance) o;
            StatusEffect effect = StatusEffect.STATUS_EFFECTS[inst.getEffectId()];
            if (effect == null) {
                continue;
            }
            int icon = effect.hasIcon() ? effect.getIconLevel() : -1;
            out.add(new HudData.Effect(I18n.translate(effect.getTranslationKey()), inst.getAmplifier(),
                    inst.getDuration(), icon));
        }
        return out;
    }

    @Override
    public int getPing() {
        ClientPlayNetworkHandler handler = mc().getNetworkHandler();
        if (handler == null || !isInGame()) {
            return -1;
        }
        // field_7911 = the tab list (MCP: playerInfoList); class_482 = GuiPlayerInfo(name, responseTime).
        String name = player().getGameProfile().getName();
        for (Object o : handler.field_7911) {
            class_482 info = (class_482) o;
            if (name.equals(info.field_1679)) {
                return info.field_1680;
            }
        }
        return -1;
    }

    @Override
    public HudData.Sidebar getSidebar() {
        if (!isInGame()) {
            return null;
        }
        Scoreboard board = mc().world.getScoreboard();
        ScoreboardObjective objective = board.getObjectiveForSlot(1);
        if (objective == null) {
            return null;
        }
        List<ScoreboardPlayerScore> scores = new ArrayList<ScoreboardPlayerScore>();
        for (Object o : board.getAllPlayerScores(objective)) {
            ScoreboardPlayerScore score = (ScoreboardPlayerScore) o;
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                scores.add(score);
            }
        }
        // Highest score at the top, at most 15 lines (like vanilla).
        Collections.sort(scores, new Comparator<ScoreboardPlayerScore>() {
            @Override
            public int compare(ScoreboardPlayerScore a, ScoreboardPlayerScore b) {
                return b.getScore() != a.getScore()
                        ? (b.getScore() > a.getScore() ? 1 : -1)
                        : a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
            }
        });
        HudData.Sidebar sidebar = new HudData.Sidebar(objective.getDisplayName());
        for (int i = 0; i < scores.size() && i < 15; i++) {
            ScoreboardPlayerScore score = scores.get(i);
            Team team = board.getPlayerTeam(score.getPlayerName());
            sidebar.lines.add(new HudData.Line(Team.decorateName(team, score.getPlayerName()), score.getScore()));
        }
        return sidebar;
    }

    @Override
    public HudData.Boss getBossBar() {
        if (BossBar.name == null || BossBar.framesToLive <= 0) {
            return null;
        }
        return new HudData.Boss(BossBar.name, BossBar.percent);
    }

    // ------------------------------------------------------------------ options

    @Override
    public float getGamma() {
        return mc().options.gamma;
    }

    @Override
    public void setGamma(float gamma) {
        mc().options.gamma = gamma;
    }

    @Override
    public float getSensitivity() {
        return mc().options.sensitivity;
    }

    @Override
    public void setSensitivity(float sensitivity) {
        mc().options.sensitivity = sensitivity;
    }

    @Override
    public List<String> getWorldPlayerUuids() {
        List<String> out = new ArrayList<String>();
        if (mc().world == null) {
            return out;
        }
        for (Object o : new ArrayList<Object>(mc().world.playerEntities)) {
            if (o instanceof net.minecraft.entity.Entity) {
                out.add(((net.minecraft.entity.Entity) o).getUuid().toString());
            }
        }
        return out;
    }

    @Override
    public float getPlayerPitch() {
        return player().pitch;
    }

    @Override
    public int getPerspective() {
        return mc().options.perspective;
    }

    @Override
    public void setPerspective(int perspective) {
        mc().options.perspective = perspective;
    }

    @Override
    public String getServerAddress() {
        if (!isInGame()) {
            return null;
        }
        if (mc().isIntegratedServerRunning()) {
            return "singleplayer";
        }
        net.minecraft.client.network.ServerInfo entry = mc().getCurrentServerEntry();
        return entry != null && entry.address != null ? entry.address : "unknown";
    }

    @Override
    public int getSprintKeyCode() {
        return mc().options.sprintKey.getCode();
    }

    @Override
    public int countPotions(int kind) {
        if (!isInGame()) {
            return 0;
        }
        int n = 0;
        for (ItemStack stack : player().inventory.main) {
            if (stack == null || net.minecraft.item.Item.getRawId(stack.getItem()) != 373) {
                continue;
            }
            int meta = stack.getData();
            boolean healing = (meta & 15) == 5;
            boolean splash = (meta & 16384) != 0;
            if (kind == 2 || (kind == 1 && healing) || (kind == 0 && healing && splash)) {
                n += stack.count;
            }
        }
        return n;
    }

    @Override
    public void sendChatMessage(String message) {
        if (isInGame()) {
            player().method_1262(message);
        }
    }

    @Override
    public void deleteTexture(Object handle) {
        if (handle instanceof Identifier) {
            mc().getTextureManager().close((Identifier) handle);
        }
    }

    @Override
    public float getHealth() {
        return isInGame() ? player().getHealth() : 0f;
    }

    @Override
    public int getDimension() {
        return isInGame() ? player().dimension : 0;
    }

    @Override
    public String getWorldName() {
        return mc().isIntegratedServerRunning() && mc().getServer() != null ? mc().getServer().getLevelName() : null;
    }

    @Override
    public double[] getRenderPos(float tickDelta) {
        net.minecraft.entity.Entity e = mc().field_6279 != null ? mc().field_6279 : player();
        if (e == null) {
            return new double[3];
        }
        return new double[]{
                e.prevTickX + (e.x - e.prevTickX) * tickDelta,
                e.prevTickY + (e.y - e.prevTickY) * tickDelta,
                e.prevTickZ + (e.z - e.prevTickZ) * tickDelta
        };
    }

    @Override
    public java.nio.FloatBuffer getModelViewMatrix() {
        return dev.astra.client.mixin.CameraAccessor.astra$model();
    }

    @Override
    public java.nio.FloatBuffer getProjectionMatrix() {
        return dev.astra.client.mixin.CameraAccessor.astra$projection();
    }

    @Override
    public void leaveWorld() {
        MinecraftClient mc = mc();
        if (mc.world != null) {
            mc.world.disconnect();
            mc.connect((net.minecraft.client.world.ClientWorld) null);
        }
    }

    @Override
    public String getSessionName() {
        return mc().getSession().getUsername();
    }

    @Override
    public void setSession(String name, String uuid, String accessToken) {
        MinecraftClient mc = mc();
        ((dev.astra.client.mixin.MinecraftClientAccessor) mc).astra$setSession(
                new net.minecraft.client.util.Session(name, uuid, accessToken, "msa"));
    }

    @Override
    public void joinServer(String address) {
        MinecraftClient mc = mc();
        leaveWorld();
        net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo("Friend", address, false);
        mc.setScreen(new net.minecraft.client.gui.screen.ConnectScreen(new net.minecraft.client.gui.screen.TitleScreen(), mc, info));
    }

    @Override
    public void resizeGame(int width, int height) {
        ((dev.astra.client.mixin.MinecraftClientAccessor) mc()).astra$resize(width, height);
    }

    @Override
    public int getMaxFps() {
        return mc().options.maxFramerate;
    }

    @Override
    public void setMaxFps(int fps) {
        mc().options.maxFramerate = fps;
    }

    @Override
    public int getParticles() {
        return mc().options.particle;
    }

    @Override
    public void setParticles(int level) {
        mc().options.particle = level;
    }

    @Override
    public boolean getEntityShadows() {
        return true; // 1.7.10 has no entity shadow option
    }

    @Override
    public void setEntityShadows(boolean on) {
        // 1.7.10 has no entity shadow option
    }

    @Override
    public void swingIfUsingAndAttacking() {
        // 1.7.10 already swings while using items.
    }
}
