package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

// The worker behind occlusion culling, after tr7zw's and Meldexun's Entity Culling: every ~10 ms it walks the loaded entities and tile entities, ray-casts from the camera toward each one's box, and stamps a verdict on it that the render hooks read on the next frame. It reads world state without locks, exactly as those mods do; a torn read only ever produces a wrong verdict for one pass, and every exception ends the pass rather than the thread
public final class OcclusionCullingThread extends Thread {
    private static final long PASS_INTERVAL_NANOS = 10_000_000L;
    // Beyond this the verdict is ignored by the hooks, so nothing past it is worth casting to
    private static final double MAX_DISTANCE_SQ = 96.0D * 96.0D;
    private static final int CACHE_RADIUS = 48;
    // A crude view cone: anything more than this far behind the camera is left visible (never cast), since the frustum will drop it anyway
    private static final double BEHIND_COSINE = -0.5D;

    private static volatile OcclusionCullingThread instance;

    private final CellVisibilityCache cache = new CellVisibilityCache(CACHE_RADIUS);
    private OcclusionRaycaster raycaster;
    private WorldClient raycasterWorld;

    private double camX;
    private double camY;
    private double camZ;
    private double lookX;
    private double lookY;
    private double lookZ;
    private int camBlockX;
    private int camBlockY;
    private int camBlockZ;

    // Timing shared with the debug overlay
    public static volatile long lastPassNanos;
    public static volatile int lastEntitiesCulled;
    public static volatile int lastBlockEntitiesCulled;

    private OcclusionCullingThread() {
        super("Impetus Occlusion Culling");
        this.setDaemon(true);
        this.setPriority(Thread.MIN_PRIORITY + 1);
    }

    // Started on first use from the render thread; a daemon, so it never keeps the game alive
    public static void ensureRunning() {
        if (instance == null) {
            synchronized (OcclusionCullingThread.class) {
                if (instance == null) {
                    OcclusionCullingThread thread = new OcclusionCullingThread();
                    thread.start();
                    instance = thread;
                }
            }
        }
    }

    @Override
    public void run() {
        Minecraft mc = Minecraft.getMinecraft();
        while (true) {
            long start = System.nanoTime();
            try {
                ExtrasConfig.OcclusionSettings settings = Extras.options().occlusion;
                WorldClient world = mc.world;
                Entity view = mc.getRenderViewEntity();
                if (settings.enabled && world != null && view != null) {
                    this.pass(world, view, settings);
                }
            } catch (Throwable ignored) {
                // A pass torn by the client thread mutating a list or a chunk mid-read; the next pass starts clean
            }
            long elapsed = System.nanoTime() - start;
            lastPassNanos = elapsed;
            long sleep = PASS_INTERVAL_NANOS - elapsed;
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void pass(WorldClient world, Entity view, ExtrasConfig.OcclusionSettings settings) {
        if (this.raycasterWorld != world) {
            this.raycaster = new OcclusionRaycaster(world);
            this.raycasterWorld = world;
        }
        this.camX = view.posX;
        this.camY = view.posY + view.getEyeHeight();
        this.camZ = view.posZ;
        Vec3d look = view.getLook(1.0F);
        this.lookX = look.x;
        this.lookY = look.y;
        this.lookZ = look.z;
        this.camBlockX = MathHelper.floor(this.camX);
        this.camBlockY = MathHelper.floor(this.camY);
        this.camBlockZ = MathHelper.floor(this.camZ);
        this.cache.clear();
        long stamp = System.nanoTime();
        int culledEntities = 0;
        int culledBlockEntities = 0;
        double slack = settings.raycastSlack / 100.0D;
        if (settings.entities) {
            List<Entity> entities = world.loadedEntityList;
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity entity = entities.get(i);
                if (entity == null || entity == view) {
                    continue;
                }
                boolean occluded = !this.isEntityVisible(entity, settings, slack);
                ((Cullable) entity).impetus$setOccluded(occluded, stamp);
                if (occluded) {
                    culledEntities++;
                }
            }
        }
        if (settings.blockEntities) {
            List<TileEntity> blockEntities = world.loadedTileEntityList;
            for (int i = 0, n = blockEntities.size(); i < n; i++) {
                TileEntity blockEntity = blockEntities.get(i);
                if (blockEntity == null) {
                    continue;
                }
                boolean occluded = !this.isBlockEntityVisible(blockEntity, settings, slack);
                ((Cullable) blockEntity).impetus$setOccluded(occluded, stamp);
                if (occluded) {
                    culledBlockEntities++;
                }
            }
        }
        lastEntitiesCulled = culledEntities;
        lastBlockEntitiesCulled = culledBlockEntities;
    }

    private boolean isEntityVisible(Entity entity, ExtrasConfig.OcclusionSettings settings, double slack) {
        // Bosses, glowing and name-tagged entities are drawn through walls by vanilla's own rules; anything vanilla would not frustum-cull is left alone too
        if (!entity.isNonBoss() || entity.ignoreFrustumCheck || entity.isGlowing() || entity.getAlwaysRenderNameTagForRender()) {
            return true;
        }
        if (entity.width > settings.maxEntitySize || entity.height > settings.maxEntitySize) {
            return true;
        }
        double dx = entity.posX - this.camX;
        double dy = entity.posY - this.camY;
        double dz = entity.posZ - this.camZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq > MAX_DISTANCE_SQ || distanceSq < 4.0D) {
            return true;
        }
        if (this.isBehindCamera(dx, dy, dz, distanceSq)) {
            return true;
        }
        AxisAlignedBB box = entity.getRenderBoundingBox();
        if (box == null || Double.isNaN(box.minX)) {
            box = entity.getEntityBoundingBox();
        }
        return this.isBoxVisible(box.minX - 0.5D, box.minY - 0.5D, box.minZ - 0.5D, box.maxX + 0.5D, box.maxY + 0.5D, box.maxZ + 0.5D, slack);
    }

    private boolean isBlockEntityVisible(TileEntity blockEntity, ExtrasConfig.OcclusionSettings settings, double slack) {
        if (blockEntity.isInvalid()) {
            return true;
        }
        BlockPos pos = blockEntity.getPos();
        double dx = pos.getX() + 0.5D - this.camX;
        double dy = pos.getY() + 0.5D - this.camY;
        double dz = pos.getZ() + 0.5D - this.camZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq > MAX_DISTANCE_SQ || distanceSq < 4.0D) {
            return true;
        }
        if (this.isBehindCamera(dx, dy, dz, distanceSq)) {
            return true;
        }
        AxisAlignedBB box = blockEntity.getRenderBoundingBox();
        if (box == null || box == TileEntity.INFINITE_EXTENT_AABB) {
            return true;
        }
        if (box.maxX - box.minX > settings.maxBlockEntitySize || box.maxY - box.minY > settings.maxBlockEntitySize || box.maxZ - box.minZ > settings.maxBlockEntitySize) {
            return true;
        }
        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, slack);
    }

    private boolean isBehindCamera(double dx, double dy, double dz, double distanceSq) {
        double inv = 1.0D / Math.sqrt(distanceSq);
        double cosine = (dx * this.lookX + dy * this.lookY + dz * this.lookZ) * inv;
        return cosine < BEHIND_COSINE;
    }

    // Centre first, since it answers the common fully-visible case with one ray, then every block cell on the faces of the box that face the camera; a box the camera is inside is visible by definition
    private boolean isBoxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double slack) {
        if (this.camX >= minX && this.camX <= maxX && this.camY >= minY && this.camY <= maxY && this.camZ >= minZ && this.camZ <= maxZ) {
            return true;
        }
        if (this.raycaster.isClear(this.camX, this.camY, this.camZ, (minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D, slack)) {
            return true;
        }
        int startX = MathHelper.floor(minX);
        int startY = MathHelper.floor(minY);
        int startZ = MathHelper.floor(minZ);
        int endX = MathHelper.ceil(maxX);
        int endY = MathHelper.ceil(maxY);
        int endZ = MathHelper.ceil(maxZ);
        if (this.camX < startX) {
            if (this.faceVisible(startX, startX, startY, endY, startZ, endZ, slack)) return true;
        } else if (this.camX > endX) {
            if (this.faceVisible(endX, endX, startY, endY, startZ, endZ, slack)) return true;
        }
        if (this.camY < startY) {
            if (this.faceVisible(startX, endX, startY, startY, startZ, endZ, slack)) return true;
        } else if (this.camY > endY) {
            if (this.faceVisible(startX, endX, endY, endY, startZ, endZ, slack)) return true;
        }
        if (this.camZ < startZ) {
            if (this.faceVisible(startX, endX, startY, endY, startZ, startZ, slack)) return true;
        } else if (this.camZ > endZ) {
            if (this.faceVisible(startX, endX, startY, endY, endZ, endZ, slack)) return true;
        }
        return false;
    }

    private boolean faceVisible(int x0, int x1, int y0, int y1, int z0, int z1, double slack) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (this.cellVisible(x, y, z, slack)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean cellVisible(int x, int y, int z, double slack) {
        return this.cache.isVisible(x - this.camBlockX, y - this.camBlockY, z - this.camBlockZ,
                () -> this.raycaster.isClear(this.camX, this.camY, this.camZ, x, y, z, slack));
    }
}
