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

// Thread that tests which entities and block entities are hidden.
public final class OcclusionCullingThread extends Thread {
    // Time between passes.
    private static final long PASS_INTERVAL_NANOS = 10_000_000L;
    // Squared distance beyond which nothing is tested.
    private static final double MAX_DISTANCE_SQ = 96.0D * 96.0D;
    // Radius of the cell cache.
    private static final int CACHE_RADIUS = 48;
    // Cosine below which a target is behind the camera.
    private static final double BEHIND_COSINE = -0.5D;

    // Running thread or null.
    private static volatile OcclusionCullingThread instance;

    // Visibility of the cells around the camera.
    private final CellVisibilityCache cache = new CellVisibilityCache(CACHE_RADIUS);
    // Ray tester of the current world.
    private OcclusionRaycaster raycaster;
    // World of the ray tester.
    private WorldClient raycasterWorld;

    // East position of the camera.
    private double camX;
    // Height of the camera.
    private double camY;
    // South position of the camera.
    private double camZ;
    // East part of the view direction.
    private double lookX;
    // Vertical part of the view direction.
    private double lookY;
    // South part of the view direction.
    private double lookZ;
    // East block of the camera.
    private int camBlockX;
    // Block height of the camera.
    private int camBlockY;
    // South block of the camera.
    private int camBlockZ;

    // Duration of the last pass.
    public static volatile long lastPassNanos;
    // Entities hidden by the last pass.
    public static volatile int lastEntitiesCulled;
    // Block entities hidden by the last pass.
    public static volatile int lastBlockEntitiesCulled;

    // Creates the thread.
    private OcclusionCullingThread() {
        super("Impetus Occlusion Culling");
        this.setDaemon(true);
        this.setPriority(Thread.MIN_PRIORITY + 1);
    }

    // Starts the thread when it is not running.
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

    // Runs a pass every interval.
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

    // Tests every entity and block entity of the world.
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

    // Returns whether the entity is visible.
    private boolean isEntityVisible(Entity entity, ExtrasConfig.OcclusionSettings settings, double slack) {
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

    // Returns whether the block entity is visible.
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

    // Returns whether the target is behind the camera.
    private boolean isBehindCamera(double dx, double dy, double dz, double distanceSq) {
        double inv = 1.0D / Math.sqrt(distanceSq);
        double cosine = (dx * this.lookX + dy * this.lookY + dz * this.lookZ) * inv;
        return cosine < BEHIND_COSINE;
    }

    // Returns whether any ray from the camera reaches the box.
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

    // Returns whether any cell of the face is visible.
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

    // Returns whether the cell is visible.
    private boolean cellVisible(int x, int y, int z, double slack) {
        return this.cache.isVisible(x - this.camBlockX, y - this.camBlockY, z - this.camBlockZ,
                () -> this.raycaster.isClear(this.camX, this.camY, this.camZ, x, y, z, slack));
    }
}
