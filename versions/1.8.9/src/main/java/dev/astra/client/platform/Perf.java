package dev.astra.client.platform;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.legacy.Occlusion;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CullingCameraView;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Astra's rendering optimizations on 1.8.9 (see {@link PerformanceModule}); the perf mixins call in here. */
public final class Perf {

    public static final Occlusion OCCLUSION = new Occlusion();
    private static PerformanceModule module;
    private static CullingCameraView particleView;

    private Perf() {
    }

    /** The Performance module, or null before Astra has started. */
    public static PerformanceModule m() {
        if (module == null && Astra.get() != null && Astra.get().getModules() != null) {
            module = Astra.get().getModules().get(PerformanceModule.class);
        }
        return module;
    }

    /** Works out what's hidden behind walls (called each tick while Performance is on). */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PerformanceModule pm = m();
        final World world = mc.world;
        Entity camera = mc.getCameraEntity();
        if (pm == null || !pm.entityCulling() || world == null || camera == null) {
            OCCLUSION.clear();
            return;
        }
        // Camera.getPosition() is where the camera sits relative to the entity it follows
        // (eye height, third person and freelook included).
        Vec3d offset = Camera.getPosition();
        double cx = camera.x + (offset != null ? offset.x : 0);
        double cy = camera.y + (offset != null ? offset.y : camera.getEyeHeight());
        double cz = camera.z + (offset != null ? offset.z : 0);
        final BlockPos.Mutable pos = new BlockPos.Mutable();
        OCCLUSION.begin((x, y, z) -> y >= 0 && y < 256
                && world.getBlockState(pos.setPosition(x, y, z)).getBlock().hasTransparency(), cx, cy, cz);

        List<Entity> entities = world.loadedEntities;
        int n = entities.size();
        int start = OCCLUSION.startList(0, n);
        for (int i = 0; i < n; i++) {
            Entity e = entities.get((start + i) % n);
            if (e == camera || e == mc.player || e instanceof PlayerEntity
                    || (e.hasCustomName() && e.isCustomNameVisible())
                    || e.rider == camera || e.vehicle == camera || camera.vehicle == e) {
                OCCLUSION.exempt(e);
                continue;
            }
            Box b = e.getBoundingBox();
            // Half a block of margin, so nothing pops in late when you step round a corner.
            OCCLUSION.check(e, b.minX - 0.5, b.minY - 0.5, b.minZ - 0.5, b.maxX + 0.5, b.maxY + 0.5, b.maxZ + 0.5);
        }

        List<BlockEntity> blockEntities = world.blockEntities;
        n = blockEntities.size();
        start = OCCLUSION.startList(1, n);
        for (int i = 0; i < n; i++) {
            BlockEntity be = blockEntities.get((start + i) % n);
            if (be instanceof BeaconBlockEntity) {
                OCCLUSION.exempt(be); // its beam is seen over walls
                continue;
            }
            BlockPos p = be.getPos();
            // A little bigger than the block: banners are two tall, pistons move things out.
            double down = be instanceof BannerBlockEntity ? 1.5 : 0.5;
            OCCLUSION.check(be, p.getX() - 0.5, p.getY() - down, p.getZ() - 0.5, p.getX() + 1.5, p.getY() + 2, p.getZ() + 1.5);
        }
        OCCLUSION.end();
    }

    /** Starts a frame's particles: the view to test them against, or null to draw them all. */
    public static void beginParticles() {
        PerformanceModule pm = m();
        if (pm != null && pm.cullParticles()) {
            CullingCameraView view = new CullingCameraView();
            view.setPos(BlockEntityRenderDispatcher.CAMERA_X, BlockEntityRenderDispatcher.CAMERA_Y, BlockEntityRenderDispatcher.CAMERA_Z);
            particleView = view;
        } else {
            particleView = null;
        }
    }

    /** Whether a particle is on screen (with a margin: big particles reach past their box). */
    public static boolean particleVisible(Entity particle) {
        CullingCameraView view = particleView;
        return view == null || view.isBoxInFrustum(particle.getBoundingBox().expand(2, 2, 2));
    }
}
