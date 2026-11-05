package dev.astra.client.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.astra.client.input.Keyboard;
import dev.astra.client.ui.UiScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.session.Session;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Minecraft 1.20.4 implementation (Yarn 1.20.4+build.3).
 *
 * Drawing goes through the DrawContext of the current frame ({@link RenderCtx}); input through
 * GLFW, translated to the LWJGL 2 key codes Astra uses everywhere ({@link Keyboard#fromGlfw}).
 */
public final class PlatformImpl implements Platform {

    private final Map<String, Identifier> textures = new HashMap<String, Identifier>();
    private int textureCount;

    /** Set by WorldRendererMixin every frame, for in-world markers (waypoints). */
    public static final FloatBuffer MODEL_VIEW = org.lwjgl.BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer PROJECTION = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static DrawContext ctx() {
        return RenderCtx.context();
    }

    @Override
    public String minecraftVersion() {
        return "1.20.4";
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
        return mc().options.getGuiScale().getValue();
    }

    @Override
    public File getGameDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        DrawContext c = ctx();
        if (c != null) {
            c.fill(x1, y1, x2, y2, argb);
        }
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow) {
        DrawContext c = ctx();
        if (c == null || text == null) {
            return;
        }
        TextRenderer tr = mc().textRenderer;
        tr.draw(text, x, y, argb, shadow, c.getMatrices().peek().getPositionMatrix(), c.getVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        c.draw();
    }

    @Override
    public int getTextWidth(String text) {
        return text == null ? 0 : mc().textRenderer.getWidth(text);
    }

    @Override
    public int getFontHeight() {
        return mc().textRenderer.fontHeight;
    }

    @Override
    public void drawItem(Object stack, int x, int y) {
        DrawContext c = ctx();
        if (c == null || !(stack instanceof ItemStack)) {
            return;
        }
        c.drawItem((ItemStack) stack, x, y);
        c.drawItemInSlot(mc().textRenderer, (ItemStack) stack, x, y);
    }

    /** Effect icons: {@code iconIndex} is the effect's registry id (see {@link #getEffects()}). */
    @Override
    public void drawEffectIcon(int iconIndex, int x, int y) {
        DrawContext c = ctx();
        StatusEffect effect = Registries.STATUS_EFFECT.get(iconIndex);
        if (c == null || effect == null) {
            return;
        }
        Sprite sprite = mc().getStatusEffectSpriteManager().getSprite(effect);
        c.drawSprite(x, y, 0, 18, 18, sprite);
    }

    /**
     * Old GUI texture sheets. 1.20.2 split icons.png into sprites, so the bits Astra uses (the
     * boss bar) are mapped to their sprites; anything else draws from the path as before.
     */
    @Override
    public void drawGuiTexture(String path, int x, int y, int u, int v, int width, int height) {
        DrawContext c = ctx();
        if (c == null) {
            return;
        }
        if (path.endsWith("textures/gui/icons.png") && (v == 74 || v == 79)) {
            Identifier sprite = new Identifier(v == 74 ? "boss_bar/pink_background" : "boss_bar/pink_progress");
            c.drawGuiTexture(sprite, 182, 5, u, 0, x, y, width, height);
            return;
        }
        c.drawTexture(texture(path), x, y, u, v, width, height);
    }

    private Identifier texture(String path) {
        Identifier id = textures.get(path);
        if (id == null) {
            id = new Identifier(path);
            textures.put(path, id);
        }
        return id;
    }

    // ------------------------------------------------------------------ matrices

    private static MatrixStack matrices() {
        return RenderCtx.matrices();
    }

    @Override
    public void pushMatrix() {
        matrices().push();
    }

    @Override
    public void popMatrix() {
        matrices().pop();
    }

    @Override
    public void translate(float x, float y, float z) {
        matrices().translate(x, y, z);
    }

    @Override
    public void scale(float x, float y, float z) {
        matrices().scale(x, y, z);
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        matrices().multiply(new Quaternionf().rotationAxis((float) Math.toRadians(angle), x, y, z));
    }

    // ------------------------------------------------------------------ textured / shaded quads

    private static void flush() {
        DrawContext c = ctx();
        if (c != null) {
            c.draw();
        }
    }

    @Override
    public void drawTexture(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
        if (!(handle instanceof Identifier)) {
            return;
        }
        flush();
        RenderSystem.setShaderTexture(0, (Identifier) handle);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        Matrix4f m = matrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(m, x, y, 0f).texture(u0, v0).color(r, g, b, a).next();
        buf.vertex(m, x, y + h, 0f).texture(u0, v1).color(r, g, b, a).next();
        buf.vertex(m, x + w, y + h, 0f).texture(u1, v1).color(r, g, b, a).next();
        buf.vertex(m, x + w, y, 0f).texture(u1, v0).color(r, g, b, a).next();
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    @Override
    public void fillTriangleStrip(float[] xy, int argb) {
        if (xy.length < 6) {
            return;
        }
        flush();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        Matrix4f m = matrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i + 1 < xy.length; i += 2) {
            buf.vertex(m, xy[i], xy[i + 1], 0f).color(r, g, b, a).next();
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    @Override
    public void drawWorldQuad(Object handle, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        if (!(handle instanceof Identifier)) {
            return;
        }
        RenderSystem.setShaderTexture(0, (Identifier) handle);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        Matrix4f m = matrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(m, x0, y0, 0f).texture(u0, v0).color(255, 255, 255, 255).next();
        buf.vertex(m, x0, y1, 0f).texture(u0, v1).color(255, 255, 255, 255).next();
        buf.vertex(m, x1, y1, 0f).texture(u1, v1).color(255, 255, 255, 255).next();
        buf.vertex(m, x1, y0, 0f).texture(u1, v0).color(255, 255, 255, 255).next();
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableCull();
    }

    @Override
    public void scissor(int x, int y, int w, int h) {
        DrawContext c = ctx();
        if (c != null) {
            c.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h));
        }
    }

    @Override
    public void endScissor() {
        DrawContext c = ctx();
        if (c != null) {
            c.disableScissor();
        }
    }

    // ------------------------------------------------------------------ input

    private static long window() {
        return mc().getWindow().getHandle();
    }

    @Override
    public boolean isKeyDown(int key) {
        int glfw = Keyboard.toGlfw(key);
        return glfw > 0 && InputUtil.isKeyPressed(window(), glfw);
    }

    @Override
    public String getKeyName(int key) {
        return Keyboard.nameOf(key);
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        return button >= 0 && button < 8 && GLFW.glfwGetMouseButton(window(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public int getMouseButtonCount() {
        return 8;
    }

    @Override
    public int consumeScroll() {
        int s = RenderCtx.scroll;
        RenderCtx.scroll = 0;
        return s;
    }

    // ------------------------------------------------------------------ window

    @Override
    public int getWindowWidth() {
        return mc().getWindow().getFramebufferWidth();
    }

    @Override
    public int getWindowHeight() {
        return mc().getWindow().getFramebufferHeight();
    }

    @Override
    public boolean isWindowFocused() {
        return mc().isWindowFocused();
    }

    @Override
    public void setWindowTitle(String title) {
        mc().getWindow().setTitle(title);
    }

    @Override
    public boolean setBorderless(boolean on) {
        return false; // 1.20.4's own fullscreen is already borderless-style; not needed yet
    }

    @Override
    public void resizeGame(int width, int height) {
        mc().onResolutionChanged();
    }

    // ------------------------------------------------------------------ textures & GL state

    @Override
    public Object createTexture(String name, BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        NativeImage native_ = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                // NativeImage stores ABGR.
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                native_.setColor(x, y, abgr);
            }
        }
        String prefix = ("astra_" + name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (prefix.length() > 60) {
            prefix = prefix.substring(0, 60);
        }
        return mc().getTextureManager().registerDynamicTexture(prefix + "_" + (textureCount++), new NativeImageBackedTexture(native_));
    }

    @Override
    public void bindTexture(Object handle) {
        if (handle instanceof Identifier) {
            RenderSystem.setShaderTexture(0, (Identifier) handle);
        }
    }

    @Override
    public void deleteTexture(Object handle) {
        if (handle instanceof Identifier) {
            mc().getTextureManager().destroyTexture((Identifier) handle);
        }
    }

    @Override
    public void color(float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
    }

    @Override
    public void blend(boolean enabled) {
        if (enabled) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void cull(boolean enabled) {
        if (enabled) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
    }

    @Override
    public void texture2d(boolean enabled) {
        // Shaders pick textured or not; nothing to switch.
    }

    // ------------------------------------------------------------------ player

    @Override
    public double getPlayerX() {
        return mc().player.getX();
    }

    @Override
    public double getPlayerY() {
        return mc().player.getY();
    }

    @Override
    public double getPlayerZ() {
        return mc().player.getZ();
    }

    @Override
    public float getPlayerYaw() {
        return mc().player.getYaw();
    }

    @Override
    public float getPlayerPitch() {
        return mc().player.getPitch();
    }

    @Override
    public int getPerspective() {
        return mc().options.getPerspective().ordinal();
    }

    @Override
    public void setPerspective(int perspective) {
        Perspective[] all = Perspective.values();
        mc().options.setPerspective(all[Math.max(0, Math.min(all.length - 1, perspective))]);
    }

    @Override
    public boolean isSprinting() {
        return isInGame() && mc().player.isSprinting();
    }

    @Override
    public int getSprintKeyCode() {
        InputUtil.Key key = ((dev.astra.client.mixin.KeyBindingAccessor) mc().options.sprintKey).astra$boundKey();
        return key != null && key.getCategory() == InputUtil.Type.KEYSYM ? Keyboard.fromGlfw(key.getCode()) : 0;
    }

    @Override
    public HudData.Item[] getArmor() {
        if (!isInGame()) {
            return new HudData.Item[0];
        }
        List<ItemStack> armor = mc().player.getInventory().armor;
        HudData.Item[] out = new HudData.Item[4];
        for (int i = 0; i < 4; i++) {
            out[i] = item(armor.get(3 - i)); // helmet first
        }
        return out;
    }

    @Override
    public HudData.Item getHeldItem() {
        return isInGame() ? item(mc().player.getMainHandStack()) : null;
    }

    private static HudData.Item item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        int max = stack.isDamageable() ? stack.getMaxDamage() : 0;
        return new HudData.Item(stack, stack.getDamage(), max, stack.getCount());
    }

    @Override
    public List<HudData.Effect> getEffects() {
        List<HudData.Effect> out = new ArrayList<HudData.Effect>();
        if (!isInGame()) {
            return out;
        }
        for (StatusEffectInstance inst : mc().player.getStatusEffects()) {
            StatusEffect effect = inst.getEffectType();
            out.add(new HudData.Effect(effect.getName().getString(), inst.getAmplifier(), inst.getDuration(),
                    Registries.STATUS_EFFECT.getRawId(effect)));
        }
        return out;
    }

    @Override
    public int getPing() {
        ClientPlayNetworkHandler handler = mc().getNetworkHandler();
        if (handler == null || !isInGame()) {
            return -1;
        }
        PlayerListEntry entry = handler.getPlayerListEntry(mc().player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    @Override
    public HudData.Sidebar getSidebar() {
        if (!isInGame()) {
            return null;
        }
        Scoreboard board = mc().world.getScoreboard();
        ScoreboardObjective objective = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            return null;
        }
        List<ScoreboardEntry> scores = new ArrayList<ScoreboardEntry>();
        for (ScoreboardEntry e : board.getScoreboardEntries(objective)) {
            if (!e.hidden()) {
                scores.add(e);
            }
        }
        Collections.sort(scores, new Comparator<ScoreboardEntry>() {
            @Override
            public int compare(ScoreboardEntry a, ScoreboardEntry b) {
                return b.value() != a.value() ? (b.value() > a.value() ? 1 : -1) : a.owner().compareToIgnoreCase(b.owner());
            }
        });
        HudData.Sidebar sidebar = new HudData.Sidebar(legacy(objective.getDisplayName()));
        for (int i = 0; i < scores.size() && i < 15; i++) {
            ScoreboardEntry e = scores.get(i);
            Team team = board.getScoreHolderTeam(e.owner());
            sidebar.lines.add(new HudData.Line(legacy(Team.decorateName(team, e.name())), e.value()));
        }
        return sidebar;
    }

    @Override
    public HudData.Boss getBossBar() {
        if (mc().inGameHud == null) {
            return null;
        }
        Map<UUID, ClientBossBar> bars = ((dev.astra.client.mixin.BossBarHudAccessor) mc().inGameHud.getBossBarHud()).astra$bossBars();
        for (ClientBossBar bar : bars.values()) {
            return new HudData.Boss(legacy(bar.getName()), bar.getPercent());
        }
        return null;
    }

    /** A Text as an old-style "§"-coded string (what Astra's HUD draws). */
    static String legacy(Text text) {
        if (text == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        text.visit((style, part) -> {
            if (!part.isEmpty()) {
                sb.append(codes(style)).append(part);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static String codes(Style style) {
        StringBuilder sb = new StringBuilder("§r");
        TextColor color = style.getColor();
        if (color != null) {
            Formatting best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Formatting f : Formatting.values()) {
                if (!f.isColor() || f.getColorValue() == null) {
                    continue;
                }
                int c = f.getColorValue();
                int d = sq(((c >> 16) & 255) - ((color.getRgb() >> 16) & 255))
                        + sq(((c >> 8) & 255) - ((color.getRgb() >> 8) & 255)) + sq((c & 255) - (color.getRgb() & 255));
                if (d < bestDistance) {
                    bestDistance = d;
                    best = f;
                }
            }
            if (best != null) {
                sb.append('§').append(best.getCode());
            }
        }
        if (style.isObfuscated()) {
            sb.append("§k");
        }
        if (style.isBold()) {
            sb.append("§l");
        }
        if (style.isStrikethrough()) {
            sb.append("§m");
        }
        if (style.isUnderlined()) {
            sb.append("§n");
        }
        if (style.isItalic()) {
            sb.append("§o");
        }
        return sb.toString();
    }

    private static int sq(int v) {
        return v * v;
    }

    @Override
    public String getServerAddress() {
        if (!isInGame()) {
            return null;
        }
        if (mc().isIntegratedServerRunning()) {
            return "singleplayer";
        }
        ServerInfo entry = mc().getCurrentServerEntry();
        return entry != null && entry.address != null ? entry.address : "unknown";
    }

    @Override
    public List<String> getWorldPlayerUuids() {
        List<String> out = new ArrayList<String>();
        if (mc().world == null) {
            return out;
        }
        for (PlayerEntity p : new ArrayList<PlayerEntity>(mc().world.getPlayers())) {
            out.add(p.getUuid().toString());
        }
        ClientPlayNetworkHandler handler = mc().getNetworkHandler();
        if (handler != null) {
            for (PlayerListEntry entry : new ArrayList<PlayerListEntry>(handler.getPlayerList())) {
                UUID id = entry.getProfile() == null ? null : entry.getProfile().getId();
                if (id != null && id.version() == 4 && !out.contains(id.toString())) {
                    out.add(id.toString());
                }
            }
        }
        return out;
    }

    /** kind 0 = healing splash potions, 1 = any healing potion, 2 = every potion. */
    @Override
    public int countPotions(int kind) {
        if (!isInGame()) {
            return 0;
        }
        int n = 0;
        for (ItemStack stack : mc().player.getInventory().main) {
            boolean splash = stack.isOf(Items.SPLASH_POTION);
            if (!splash && !stack.isOf(Items.POTION) && !stack.isOf(Items.LINGERING_POTION)) {
                continue;
            }
            Potion potion = PotionUtil.getPotion(stack);
            boolean healing = potion == Potions.HEALING || potion == Potions.STRONG_HEALING;
            if (kind == 2 || (kind == 1 && healing) || (kind == 0 && healing && splash)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    @Override
    public void swingIfUsingAndAttacking() {
        // 1.7 block-hit swinging isn't done on modern versions.
    }

    @Override
    public void sendChatMessage(String message) {
        ClientPlayNetworkHandler handler = mc().getNetworkHandler();
        if (handler == null || message == null || message.isEmpty()) {
            return;
        }
        if (message.startsWith("/")) {
            handler.sendChatCommand(message.substring(1));
        } else {
            handler.sendChatMessage(message);
        }
    }

    @Override
    public float getHealth() {
        return isInGame() ? mc().player.getHealth() : 0f;
    }

    @Override
    public int getDimension() {
        if (!isInGame()) {
            return 0;
        }
        if (mc().world.getRegistryKey() == World.NETHER) {
            return -1;
        }
        return mc().world.getRegistryKey() == World.END ? 1 : 0;
    }

    @Override
    public String getWorldName() {
        return mc().isIntegratedServerRunning() && mc().getServer() != null
                ? mc().getServer().getSaveProperties().getLevelName() : null;
    }

    @Override
    public double[] getRenderPos(float tickDelta) {
        Entity e = mc().getCameraEntity() != null ? mc().getCameraEntity() : mc().player;
        if (e == null) {
            return new double[3];
        }
        Vec3d pos = e.getLerpedPos(tickDelta);
        return new double[]{pos.x, pos.y, pos.z};
    }

    @Override
    public FloatBuffer getModelViewMatrix() {
        return MODEL_VIEW;
    }

    @Override
    public FloatBuffer getProjectionMatrix() {
        return PROJECTION;
    }

    // ------------------------------------------------------------------ session & servers

    @Override
    public void leaveWorld() {
        MinecraftClient mc = mc();
        if (mc.world != null) {
            mc.world.disconnect();
            mc.disconnect();
        }
    }

    @Override
    public String getSessionName() {
        return mc().getSession().getUsername();
    }

    @Override
    public void setSession(String name, String uuid, String accessToken) {
        UUID id;
        try {
            String dashed = uuid.length() == 32
                    ? uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5") : uuid;
            id = UUID.fromString(dashed);
        } catch (RuntimeException e) {
            id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        ((dev.astra.client.mixin.MinecraftClientAccessor) mc()).astra$setSession(
                new Session(name, id, accessToken, Optional.empty(), Optional.empty(), Session.AccountType.MSA));
    }

    @Override
    public void joinServer(String address) {
        MinecraftClient mc = mc();
        leaveWorld();
        ServerInfo info = new ServerInfo("Friend", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(address), info, false);
    }

    // ------------------------------------------------------------------ options

    @Override
    public int getMaxFps() {
        return mc().options.getMaxFps().getValue();
    }

    @Override
    public void setMaxFps(int fps) {
        mc().options.getMaxFps().setValue(fps);
    }

    @Override
    public int getParticles() {
        return mc().options.getParticles().getValue().ordinal();
    }

    @Override
    public void setParticles(int level) {
        ParticlesMode[] all = ParticlesMode.values();
        mc().options.getParticles().setValue(all[Math.max(0, Math.min(all.length - 1, level))]);
    }

    @Override
    public boolean getEntityShadows() {
        return mc().options.getEntityShadows().getValue();
    }

    @Override
    public void setEntityShadows(boolean on) {
        mc().options.getEntityShadows().setValue(on);
    }

    @Override
    public float getGamma() {
        return mc().options.getGamma().getValue().floatValue();
    }

    @Override
    public void setGamma(float gamma) {
        // The option only accepts 0..1; Fullbright needs more, so set the value directly.
        ((dev.astra.client.mixin.SimpleOptionAccessor) (Object) mc().options.getGamma()).astra$setValue((double) gamma);
    }

    @Override
    public float getSensitivity() {
        return mc().options.getMouseSensitivity().getValue().floatValue();
    }

    @Override
    public void setSensitivity(float sensitivity) {
        mc().options.getMouseSensitivity().setValue((double) sensitivity);
    }
}
