package dev.astra.client.platform;

import dev.astra.client.Astra;
import dev.astra.client.module.impl.PerformanceModule;
import dev.astra.client.platform.legacy.Occlusion;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CullingCameraView;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/** Astra's rendering optimizations on 1.7.10 (see {@link PerformanceModule}); the perf mixins call in here. */
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

    /** Named mobs keep their name tag showing, so they're never hidden. */
    public static boolean named(Entity e) {
        return e instanceof MobEntity && ((MobEntity) e).method_5392();
    }

    /** Works out what's hidden behind walls (called each tick while Performance is on). */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PerformanceModule pm = m();
        final World world = mc.world;
        Entity camera = mc.field_6279;
        if (pm == null || !pm.entityCulling() || world == null || camera == null) {
            OCCLUSION.clear();
            return;
        }
        // Camera.field_887-889 (ActiveRenderInfo.objectX/Y/Z): where the camera sits relative to the
        // entity it follows, third person and freelook included.
        double cx = camera.x + Camera.field_887;
        double cy = camera.y + Camera.field_888;
        double cz = camera.z + Camera.field_889;
        OCCLUSION.begin((x, y, z) -> y >= 0 && y < 256 && world.getBlock(x, y, z).hasTransparency(), cx, cy, cz);

        List<?> entities = world.loadedEntities;
        int n = entities.size();
        int start = OCCLUSION.startList(0, n);
        for (int i = 0; i < n; i++) {
            Entity e = (Entity) entities.get((start + i) % n);
            if (e == camera || e == mc.field_3805 || e instanceof PlayerEntity || named(e)
                    || e.rider == camera || e.vehicle == camera || camera.vehicle == e) {
                OCCLUSION.exempt(e);
                continue;
            }
            Box b = e.boundingBox;
            // Half a block of margin, so nothing pops in late when you step round a corner.
            OCCLUSION.check(e, b.minX - 0.5, b.minY - 0.5, b.minZ - 0.5, b.maxX + 0.5, b.maxY + 0.5, b.maxZ + 0.5);
        }

        List<?> blockEntities = world.blockEntities;
        n = blockEntities.size();
        start = OCCLUSION.startList(1, n);
        for (int i = 0; i < n; i++) {
            BlockEntity be = (BlockEntity) blockEntities.get((start + i) % n);
            if (be instanceof BeaconBlockEntity) {
                OCCLUSION.exempt(be); // its beam is seen over walls
                continue;
            }
            OCCLUSION.check(be, be.x - 0.5, be.y - 0.5, be.z - 0.5, be.x + 1.5, be.y + 2, be.z + 1.5);
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
        return view == null || view.isBoxInFrustum(particle.boundingBox.expand(2, 2, 2));
    }
}
