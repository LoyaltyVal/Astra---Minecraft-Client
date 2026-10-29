package dev.astra.client.platform;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.astra.client.ui.UiScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.resource.language.I18n;
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 1.8.9 implementation (Legacy Yarn build 604 mappings).
 *
 * Every Minecraft name used by Astra on 1.8.9 lives in this file, AstraScreen and the mixin/
 * folder. If a mappings update renames something, run {@code gradlew :versions:1.8.9:genSources}
 * and look up the new name in the decompiled sources.
 */
public final class PlatformImpl extends dev.astra.client.platform.legacy.LegacyPlatform {

    private static final Identifier INVENTORY = new Identifier("textures/gui/container/inventory.png");

    private final DrawableHelper drawer = new DrawableHelper();
    private final Map<String, Identifier> guiTextures = new HashMap<String, Identifier>();

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    @Override
    public String minecraftVersion() {
        return "1.8.9";
    }

    @Override
    public boolean isInGame() {
        return mc().player != null && mc().world != null;
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
        mc().textRenderer.draw(text, x, y, argb, shadow);
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
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        DiffuseLighting.enable();
        mc().getItemRenderer().renderInGuiWithOverrides(item, x, y);
        mc().getItemRenderer().renderGuiItemOverlay(mc().textRenderer, item, x, y);
        DiffuseLighting.disable();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void drawEffectIcon(int iconIndex, int x, int y) {
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
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
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
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
        GlStateManager.color(r, g, b, a);
    }

    @Override
    public void blend(boolean enabled) {
        if (enabled) {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        } else {
            GlStateManager.disableBlend();
        }
    }

    @Override
    public void cull(boolean enabled) {
        if (enabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
    }

    @Override
    public void texture2d(boolean enabled) {
        if (enabled) {
            GlStateManager.enableTexture();
        } else {
            GlStateManager.disableTexture();
        }
    }

    // ------------------------------------------------------------------ player

    @Override
    public double getPlayerX() {
        return mc().player.x;
    }

    @Override
    public double getPlayerY() {
        return mc().player.y;
    }

    @Override
    public double getPlayerZ() {
        return mc().player.z;
    }

    @Override
    public float getPlayerYaw() {
        return mc().player.yaw;
    }

    @Override
    public boolean isSprinting() {
        return isInGame() && mc().player.isSprinting();
    }

    @Override
    public HudData.Item[] getArmor() {
        if (!isInGame()) {
            return new HudData.Item[0];
        }
        ItemStack[] armor = mc().player.inventory.armor;
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
        return item(mc().player.inventory.main[mc().player.inventory.selectedSlot]);
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
        for (Object o : mc().player.getStatusEffectInstances()) {
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
        PlayerListEntry entry = handler.getPlayerListEntry(mc().player.getGameProfile().getName());
        return entry == null ? -1 : entry.getLatency();
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
        return mc().player.pitch;
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
        for (ItemStack stack : mc().player.inventory.main) {
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
            mc().player.sendChatMessage(message);
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
        return isInGame() ? mc().player.getHealth() : 0f;
    }

    @Override
    public int getDimension() {
        return isInGame() ? mc().player.dimension : 0;
    }

    @Override
    public String getWorldName() {
        return mc().isIntegratedServerRunning() && mc().getServer() != null ? mc().getServer().getLevelName() : null;
    }

    @Override
    public double[] getRenderPos(float tickDelta) {
        net.minecraft.entity.Entity e = mc().getCameraEntity() != null ? mc().getCameraEntity() : mc().player;
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
        mc.getSessionProperties().clear(); // refetched for the new account (skin and cape)
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
        return mc().options.entityShadows;
    }

    @Override
    public void setEntityShadows(boolean on) {
        mc().options.entityShadows = on;
    }

    @Override
    public void swingIfUsingAndAttacking() {
        net.minecraft.entity.player.ClientPlayerEntity p = mc().player;
        if (p == null || !p.isUsingItem() || !mc().options.attackKey.isPressed()) {
            return;
        }
        // Same as LivingEntity#swingHand without the packet: restart the swing once it's half done.
        if (!p.handSwinging || p.handSwingTicks >= 3 || p.handSwingTicks < 0) {
            p.handSwingTicks = -1;
            p.handSwinging = true;
        }
    }
}
