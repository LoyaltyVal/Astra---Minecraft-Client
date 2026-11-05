package dev.astra.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.astra.client.input.Keyboard;
import dev.astra.client.ui.UiScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
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
 * Minecraft 1.21.11 implementation, in Mojang's official names.
 *
 * Drawing goes through the GuiGraphics of the current frame ({@link RenderCtx}); input through
 * GLFW, translated to the LWJGL 2 key codes Astra uses everywhere ({@link Keyboard#fromGlfw}).
 */
public final class PlatformImpl implements Platform {

    private final Map<String, Identifier> textures = new HashMap<String, Identifier>();
    private int textureCount;

    /** Set by LevelRendererMixin every frame, for in-world markers (waypoints). */
    public static final FloatBuffer MODEL_VIEW = org.lwjgl.BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer PROJECTION = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static GuiGraphics ctx() {
        return RenderCtx.context();
    }

    @Override
    public String minecraftVersion() {
        return "1.21.11";
    }

    @Override
    public boolean isInGame() {
        return mc().player != null && mc().level != null;
    }

    @Override
    public boolean isScreenOpen() {
        return mc().screen != null;
    }

    @Override
    public UiScreen getOpenUi() {
        Screen screen = mc().screen;
        return screen instanceof AstraScreen ? ((AstraScreen) screen).getUi() : null;
    }

    @Override
    public void openUi(UiScreen ui) {
        mc().setScreen(ui == null ? null : new AstraScreen(ui));
    }

    @Override
    public int getGuiScale() {
        return mc().options.guiScale().get();
    }

    @Override
    public File getGameDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        GuiGraphics c = ctx();
        if (c != null) {
            c.fill(x1, y1, x2, y2, argb);
        }
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow) {
        GuiGraphics c = ctx();
        if (c == null || text == null) {
            return;
        }
        Font font = mc().font;
        if ((argb & 0xFC000000) == 0) {
            argb |= 0xFF000000; // like old Minecraft: no alpha given means opaque
        }
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        Matrix3x2fStack pose = c.pose();
        pose.pushMatrix();
        pose.translate(x - ix, y - iy);
        c.drawString(font, text, ix, iy, argb, shadow);
        pose.popMatrix();
    }

    @Override
    public int getTextWidth(String text) {
        return text == null ? 0 : mc().font.width(text);
    }

    @Override
    public int getFontHeight() {
        return mc().font.lineHeight;
    }

    @Override
    public void drawItem(Object stack, int x, int y) {
        GuiGraphics c = ctx();
        if (c == null || !(stack instanceof ItemStack)) {
            return;
        }
        c.renderItem((ItemStack) stack, x, y);
        c.renderItemDecorations(mc().font, (ItemStack) stack, x, y);
    }

    /** Effect icons: {@code iconIndex} is the effect's registry id (see {@link #getEffects()}). */
    @Override
    public void drawEffectIcon(int iconIndex, int x, int y) {
        GuiGraphics c = ctx();
        if (c == null) {
            return;
        }
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(iconIndex);
        if (!effect.isPresent()) {
            return;
        }
        c.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect.get()), x, y, 18, 18);
    }

    /**
     * Old GUI texture sheets. icons.png was split into sprites, so the bits Astra uses (the boss
     * bar) are mapped to their sprites; anything else draws from the path as before.
     */
    @Override
    public void drawGuiTexture(String path, int x, int y, int u, int v, int width, int height) {
        GuiGraphics c = ctx();
        if (c == null) {
            return;
        }
        if (path.endsWith("textures/gui/icons.png") && (v == 74 || v == 79)) {
            Identifier sprite = Identifier.withDefaultNamespace(v == 74 ? "boss_bar/pink_background" : "boss_bar/pink_progress");
            c.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 182, 5, u, 0, x, y, width, height);
            return;
        }
        c.blit(RenderPipelines.GUI_TEXTURED, texture(path), x, y, u, v, width, height, 256, 256);
    }

    private Identifier texture(String path) {
        Identifier id = textures.get(path);
        if (id == null) {
            id = Identifier.parse(path);
            textures.put(path, id);
        }
        return id;
    }

    // ------------------------------------------------------------------ matrices

    private static Matrix3x2fStack matrices() {
        return RenderCtx.matrices();
    }

    @Override
    public void pushMatrix() {
        if (RenderCtx.world != null) {
            RenderCtx.world.pushPose();
            return;
        }
        matrices().pushMatrix();
    }

    @Override
    public void popMatrix() {
        if (RenderCtx.world != null) {
            RenderCtx.world.popPose();
            return;
        }
        matrices().popMatrix();
    }

    @Override
    public void translate(float x, float y, float z) {
        if (RenderCtx.world != null) {
            RenderCtx.world.translate(x, y, z);
            return;
        }
        matrices().translate(x, y);
    }

    @Override
    public void scale(float x, float y, float z) {
        if (RenderCtx.world != null) {
            RenderCtx.world.scale(x, y, z);
            return;
        }
        matrices().scale(x, y);
    }

    /** The GUI is 2D since 1.21.6, so only turns around the Z axis apply. */
    @Override
    public void rotate(float angle, float x, float y, float z) {
        if (RenderCtx.world != null) {
            RenderCtx.world.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(angle), x, y, z));
            return;
        }
        if (z != 0f && x == 0f && y == 0f) {
            matrices().rotate((float) Math.toRadians(z > 0 ? angle : -angle));
        }
    }

    // ------------------------------------------------------------------ textured / shaded quads

    private static void submit(AstraGuiElement element) {
        GuiGraphics c = ctx();
        if (c != null) {
            ((dev.astra.client.mixin.GuiGraphicsAccessor) c).astra$renderState().submitGuiElement(element);
        }
    }

    private static int tinted(int argb) {
        int a = Math.round(((argb >>> 24) & 255) * RenderCtx.tintA);
        int r = Math.round(((argb >> 16) & 255) * RenderCtx.tintR);
        int g = Math.round(((argb >> 8) & 255) * RenderCtx.tintG);
        int b = Math.round((argb & 255) * RenderCtx.tintB);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void drawTexture(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
        if (!(handle instanceof Identifier) || ctx() == null) {
            return;
        }
        AbstractTexture texture = mc().getTextureManager().getTexture((Identifier) handle);
        float[] quad = {
                x, y, u0, v0,
                x, y + h, u0, v1,
                x + w, y + h, u1, v1,
                x + w, y, u1, v0};
        submit(new AstraGuiElement(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
                new Matrix3x2f(matrices()), quad, tinted(argb), RenderCtx.scissor()));
    }

    /** Each triangle of the strip becomes a quad with its last corner doubled (GUI shapes are quads). */
    @Override
    public void fillTriangleStrip(float[] xy, int argb) {
        if (xy.length < 6 || ctx() == null) {
            return;
        }
        int triangles = xy.length / 2 - 2;
        float[] quads = new float[triangles * 16];
        int o = 0;
        for (int t = 0; t < triangles; t++) {
            float ax = xy[t * 2], ay = xy[t * 2 + 1];
            float bx = xy[t * 2 + 2], by = xy[t * 2 + 3];
            float cx = xy[t * 2 + 4], cy = xy[t * 2 + 5];
            // Same turning direction as vanilla's rectangles, so nothing gets culled.
            if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) > 0) {
                float tx = bx, ty = by;
                bx = cx;
                by = cy;
                cx = tx;
                cy = ty;
            }
            float[] corners = {ax, ay, bx, by, cx, cy, cx, cy};
            for (int k = 0; k < 8; k += 2) {
                quads[o++] = corners[k];
                quads[o++] = corners[k + 1];
                quads[o++] = 0f;
                quads[o++] = 0f;
            }
        }
        submit(new AstraGuiElement(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(matrices()), quads, argb,
                RenderCtx.scissor()));
    }

    /** In-world quads (wings) only draw inside the player renderer, where RenderCtx.beginWorld was called. */
    @Override
    public void drawWorldQuad(Object handle, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        if (handle instanceof Identifier && RenderCtx.worldBuffers != null) {
            worldQuad((Identifier) handle, x0, y0, x1, y1, u0, v0, u1, v1);
        }
    }

    @Override
    public void scissor(int x, int y, int w, int h) {
        GuiGraphics c = ctx();
        if (c != null) {
            ScreenRectangle area = new ScreenRectangle(x, y, Math.max(0, w), Math.max(0, h)).transformAxisAligned(c.pose());
            ScreenRectangle outer = RenderCtx.scissor();
            if (outer != null) {
                ScreenRectangle both = outer.intersection(area);
                area = both != null ? both : new ScreenRectangle(area.left(), area.top(), 0, 0);
            }
            RenderCtx.SCISSORS.push(area);
            c.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h));
        }
    }

    @Override
    public void endScissor() {
        GuiGraphics c = ctx();
        if (c != null) {
            if (!RenderCtx.SCISSORS.isEmpty()) {
                RenderCtx.SCISSORS.pop();
            }
            c.disableScissor();
        }
    }

    /** A wing quad inside the player renderer: lit like the player, both sides showing. */
    private static void worldQuad(Identifier id, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = RenderCtx.world.last();
        com.mojang.blaze3d.vertex.VertexConsumer vc = RenderCtx.worldBuffers.apply(id);
        float[][] corners = {{x0, y0, u0, v0}, {x0, y1, u0, v1}, {x1, y1, u1, v1}, {x1, y0, u1, v0}};
        for (float[] c : corners) {
            vc.addVertex(pose, c[0], c[1], 0f).setColor(255, 255, 255, 255).setUv(c[2], c[3])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(RenderCtx.worldLight).setNormal(pose, 0f, 0f, 1f);
        }
    }

    /** The UUID of the player with this entity id (render states only carry the id). */
    public static java.util.UUID playerUuid(int entityId) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(entityId);
        return e instanceof Player ? e.getUUID() : null;
    }

    // ------------------------------------------------------------------ input

    private static long window() {
        return mc().getWindow().handle();
    }

    @Override
    public boolean isKeyDown(int key) {
        int glfw = Keyboard.toGlfw(key);
        return glfw > 0 && InputConstants.isKeyDown(mc().getWindow(), glfw);
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
        return mc().getWindow().getWidth();
    }

    @Override
    public int getWindowHeight() {
        return mc().getWindow().getHeight();
    }

    @Override
    public boolean isWindowFocused() {
        return mc().isWindowActive();
    }

    @Override
    public void setWindowTitle(String title) {
        mc().getWindow().setTitle(title);
    }

    @Override
    public boolean setBorderless(boolean on) {
        return false; // modern fullscreen is already borderless-style
    }

    @Override
    public void resizeGame(int width, int height) {
        mc().resizeDisplay();
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
                native_.setPixel(x, y, argb);
            }
        }
        String prefix = ("astra_" + name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (prefix.length() > 60) {
            prefix = prefix.substring(0, 60);
        }
        Identifier id = Identifier.fromNamespaceAndPath("astra", prefix + "_" + (textureCount++));
        final String label = prefix;
        mc().getTextureManager().register(id, new DynamicTexture(() -> label, native_));
        return id;
    }

    @Override
    public void bindTexture(Object handle) {
        // Textures are given with each quad on 1.21.6+.
    }

    @Override
    public void deleteTexture(Object handle) {
        if (handle instanceof Identifier) {
            mc().getTextureManager().release((Identifier) handle);
        }
    }

    @Override
    public void color(float r, float g, float b, float a) {
        RenderCtx.tintR = r;
        RenderCtx.tintG = g;
        RenderCtx.tintB = b;
        RenderCtx.tintA = a;
    }

    @Override
    public void blend(boolean enabled) {
        // GUI pipelines always blend on 1.21.6+.
    }

    @Override
    public void cull(boolean enabled) {
    }

    @Override
    public void texture2d(boolean enabled) {
        // Pipelines pick textured or not; nothing to switch.
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
        return mc().player.getYRot();
    }

    @Override
    public float getPlayerPitch() {
        return mc().player.getXRot();
    }

    @Override
    public int getPerspective() {
        return mc().options.getCameraType().ordinal();
    }

    @Override
    public void setPerspective(int perspective) {
        CameraType[] all = CameraType.values();
        mc().options.setCameraType(all[Math.max(0, Math.min(all.length - 1, perspective))]);
    }

    @Override
    public boolean isSprinting() {
        return isInGame() && mc().player.isSprinting();
    }

    @Override
    public int getSprintKeyCode() {
        InputConstants.Key key = ((dev.astra.client.mixin.KeyMappingAccessor) mc().options.keySprint).astra$key();
        return key != null && key.getType() == InputConstants.Type.KEYSYM ? Keyboard.fromGlfw(key.getValue()) : 0;
    }

    @Override
    public HudData.Item[] getArmor() {
        if (!isInGame()) {
            return new HudData.Item[0];
        }
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        HudData.Item[] out = new HudData.Item[4];
        for (int i = 0; i < 4; i++) {
            out[i] = item(mc().player.getItemBySlot(slots[i]));
        }
        return out;
    }

    @Override
    public HudData.Item getHeldItem() {
        return isInGame() ? item(mc().player.getMainHandItem()) : null;
    }

    private static HudData.Item item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        int max = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
        return new HudData.Item(stack, stack.getDamageValue(), max, stack.getCount());
    }

    @Override
    public List<HudData.Effect> getEffects() {
        List<HudData.Effect> out = new ArrayList<HudData.Effect>();
        if (!isInGame()) {
            return out;
        }
        for (MobEffectInstance inst : mc().player.getActiveEffects()) {
            MobEffect effect = inst.getEffect().value();
            out.add(new HudData.Effect(effect.getDisplayName().getString(), inst.getAmplifier(), inst.getDuration(),
                    BuiltInRegistries.MOB_EFFECT.getId(effect)));
        }
        return out;
    }

    @Override
    public int getPing() {
        ClientPacketListener handler = mc().getConnection();
        if (handler == null || !isInGame()) {
            return -1;
        }
        PlayerInfo entry = handler.getPlayerInfo(mc().player.getUUID());
        return entry == null ? -1 : entry.getLatency();
    }

    @Override
    public HudData.Sidebar getSidebar() {
        if (!isInGame()) {
            return null;
        }
        Scoreboard board = mc().level.getScoreboard();
        Objective objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return null;
        }
        List<PlayerScoreEntry> scores = new ArrayList<PlayerScoreEntry>();
        for (PlayerScoreEntry e : board.listPlayerScores(objective)) {
            if (!e.isHidden()) {
                scores.add(e);
            }
        }
        Collections.sort(scores, new Comparator<PlayerScoreEntry>() {
            @Override
            public int compare(PlayerScoreEntry a, PlayerScoreEntry b) {
                return b.value() != a.value() ? (b.value() > a.value() ? 1 : -1) : a.owner().compareToIgnoreCase(b.owner());
            }
        });
        HudData.Sidebar sidebar = new HudData.Sidebar(legacy(objective.getDisplayName()));
        for (int i = 0; i < scores.size() && i < 15; i++) {
            PlayerScoreEntry e = scores.get(i);
            PlayerTeam team = board.getPlayersTeam(e.owner());
            sidebar.lines.add(new HudData.Line(legacy(PlayerTeam.formatNameForTeam(team, e.ownerName())), e.value()));
        }
        return sidebar;
    }

    @Override
    public HudData.Boss getBossBar() {
        if (mc().gui == null) {
            return null;
        }
        Map<UUID, LerpingBossEvent> bars = ((dev.astra.client.mixin.BossHealthOverlayAccessor) mc().gui.getBossOverlay()).astra$events();
        for (LerpingBossEvent bar : bars.values()) {
            return new HudData.Boss(legacy(bar.getName()), bar.getProgress());
        }
        return null;
    }

    /** A Component as an old-style "§"-coded string (what Astra's HUD draws). */
    static String legacy(Component text) {
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
            int rgb = color.getValue();
            ChatFormatting best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (ChatFormatting f : ChatFormatting.values()) {
                if (!f.isColor() || f.getColor() == null) {
                    continue;
                }
                int c = f.getColor();
                int d = sq(((c >> 16) & 255) - ((rgb >> 16) & 255))
                        + sq(((c >> 8) & 255) - ((rgb >> 8) & 255)) + sq((c & 255) - (rgb & 255));
                if (d < bestDistance) {
                    bestDistance = d;
                    best = f;
                }
            }
            if (best != null) {
                sb.append('§').append(best.getChar());
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
        if (mc().isLocalServer()) {
            return "singleplayer";
        }
        ServerData entry = mc().getCurrentServer();
        return entry != null && entry.ip != null ? entry.ip : "unknown";
    }

    @Override
    public List<String> getWorldPlayerUuids() {
        List<String> out = new ArrayList<String>();
        if (mc().level == null) {
            return out;
        }
        for (Player p : new ArrayList<Player>(mc().level.players())) {
            out.add(p.getUUID().toString());
        }
        ClientPacketListener handler = mc().getConnection();
        if (handler != null) {
            for (PlayerInfo entry : new ArrayList<PlayerInfo>(handler.getOnlinePlayers())) {
                UUID id = entry.getProfile() == null ? null : entry.getProfile().id();
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
        for (ItemStack stack : mc().player.getInventory().getNonEquipmentItems()) {
            boolean splash = stack.is(Items.SPLASH_POTION);
            if (!splash && !stack.is(Items.POTION) && !stack.is(Items.LINGERING_POTION)) {
                continue;
            }
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            boolean healing = contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
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
        ClientPacketListener handler = mc().getConnection();
        if (handler == null || message == null || message.isEmpty()) {
            return;
        }
        if (message.startsWith("/")) {
            handler.sendCommand(message.substring(1));
        } else {
            handler.sendChat(message);
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
        if (mc().level.dimension() == Level.NETHER) {
            return -1;
        }
        return mc().level.dimension() == Level.END ? 1 : 0;
    }

    @Override
    public String getWorldName() {
        return mc().isLocalServer() && mc().getSingleplayerServer() != null
                ? mc().getSingleplayerServer().getWorldData().getLevelName() : null;
    }

    @Override
    public double[] getRenderPos(float tickDelta) {
        Entity e = mc().getCameraEntity() != null ? mc().getCameraEntity() : mc().player;
        if (e == null) {
            return new double[3];
        }
        Vec3 pos = e.getPosition(tickDelta);
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
        Minecraft mc = mc();
        if (mc.level != null) {
            mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
    }

    @Override
    public String getSessionName() {
        return mc().getUser().getName();
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
        ((dev.astra.client.mixin.MinecraftAccessor) mc()).astra$setUser(
                new User(name, id, accessToken, Optional.empty(), Optional.empty()));
    }

    @Override
    public void joinServer(String address) {
        Minecraft mc = mc();
        leaveWorld();
        ServerData info = new ServerData("Friend", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), info, false, null);
    }

    // ------------------------------------------------------------------ options

    @Override
    public int getMaxFps() {
        return mc().options.framerateLimit().get();
    }

    @Override
    public void setMaxFps(int fps) {
        mc().options.framerateLimit().set(fps);
    }

    @Override
    public int getParticles() {
        return mc().options.particles().get().ordinal();
    }

    @Override
    public void setParticles(int level) {
        ParticleStatus[] all = ParticleStatus.values();
        mc().options.particles().set(all[Math.max(0, Math.min(all.length - 1, level))]);
    }

    @Override
    public boolean getEntityShadows() {
        return mc().options.entityShadows().get();
    }

    @Override
    public void setEntityShadows(boolean on) {
        mc().options.entityShadows().set(on);
    }

    @Override
    public float getGamma() {
        return mc().options.gamma().get().floatValue();
    }

    @Override
    public void setGamma(float gamma) {
        // The option only accepts 0..1; Fullbright needs more, so set the value directly.
        ((dev.astra.client.mixin.OptionInstanceAccessor) (Object) mc().options.gamma()).astra$setValue((double) gamma);
    }

    @Override
    public float getSensitivity() {
        return mc().options.sensitivity().get().floatValue();
    }

    @Override
    public void setSensitivity(float sensitivity) {
        mc().options.sensitivity().set((double) sensitivity);
    }
}
