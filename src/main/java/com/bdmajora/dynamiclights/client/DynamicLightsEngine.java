package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsMode;
import com.bdmajora.dynamiclights.client.item.ItemLightSources;
import com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

// Tracks every moving light source and computes the light it adds.
public final class DynamicLightsEngine {
    // Single instance.
    private static final DynamicLightsEngine INSTANCE = new DynamicLightsEngine();

    // Distance at which a light fades out.
    private static final double MAX_RADIUS = 7.75;
    // Square of the fade distance.
    private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;

    // Largest light level of an item.
    private static final int MAX_LUMINANCE = 14;

    // Tracked sources.
    private final Set<DynamicLightSource> dynamicLightSources = new HashSet<>();
    // Lock that guards the sources.
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Number of tracked sources.
    private volatile int sourceCount;

    // Time of the last full update.
    private long lastUpdate = System.currentTimeMillis();
    // Number of sources changed by the last update.
    private int lastUpdateCount;

    // Prevents instantiation
    private DynamicLightsEngine() {
    }

    // Returns the single engine.
    public static DynamicLightsEngine get() {
        return INSTANCE;
    }

    // Updates every source at most every fifty milliseconds.
    public void updateAll(RenderGlobal renderer) {
        if (!DynamicLights.options().mode.isEnabled() || this.sourceCount == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= this.lastUpdate && now < this.lastUpdate + 50L) {
            return;
        }

        this.lastUpdate = now;
        int updated = 0;

        this.lock.readLock().lock();
        try {
            for (DynamicLightSource source : this.dynamicLightSources) {
                if (source.impetus$updateDynamicLight(renderer)) {
                    updated++;
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }

        this.lastUpdateCount = updated;
    }

    // Returns the number of sources changed by the last update.
    public int getLastUpdateCount() {
        return this.lastUpdateCount;
    }

    // Returns the number of tracked sources.
    public int getLightSourcesCount() {
        return this.sourceCount;
    }

    // Returns the light map value raised by the lights at the position.
    public int getLightmapWithDynamicLight(BlockPos pos, int lightmap) {
        return this.getLightmapWithDynamicLight(this.getDynamicLightLevel(pos), lightmap);
    }

    // Returns the light map value raised by the lights at the entity.
    public int getLightmapWithDynamicLight(Entity entity, int lightmap) {
        if (this.sourceCount == 0) {
            return lightmap;
        }

        int atPosition = (int) this.getDynamicLightLevel(new BlockPos(entity.posX, entity.posY, entity.posZ));
        int ownLuminance = ((DynamicLightSource) entity).impetus$getLuminance();

        return this.getLightmapWithDynamicLight(Math.max(atPosition, ownLuminance), lightmap);
    }

    // Returns the light map value raised to the light level.
    public int getLightmapWithDynamicLight(double dynamicLightLevel, int lightmap) {
        if (dynamicLightLevel <= 0) {
            return lightmap;
        }

        int blockLight = (lightmap >> 4) & 0xF;
        int skyLight = (lightmap >> 20) & 0xF;

        if (dynamicLightLevel > blockLight) {
            blockLight = (int) dynamicLightLevel;
        }

        return (skyLight << 20) | (blockLight << 4);
    }

    // Returns the strongest light level at the position.
    public double getDynamicLightLevel(BlockPos pos) {
        return getDynamicLightLevel(pos.getX(), pos.getY(), pos.getZ());
    }

    // Returns the strongest light level at the coordinates.
    public double getDynamicLightLevel(int x, int y, int z) {
        if (this.sourceCount == 0) {
            return 0.0D;
        }

        double result = 0.0D;

        this.lock.readLock().lock();
        try {
            for (DynamicLightSource source : this.dynamicLightSources) {
                result = maxDynamicLightLevel(x, y, z, source, result);
            }
        } finally {
            this.lock.readLock().unlock();
        }

        return result < 0.0D ? 0.0D : Math.min(result, 15.0D);
    }

    // Returns the larger of the current level and the level the source adds.
    public static double maxDynamicLightLevel(int x, int y, int z, DynamicLightSource lightSource,
                                              double currentLightLevel) {
        int luminance = lightSource.impetus$getLuminance();
        if (luminance <= 0) {
            return currentLightLevel;
        }

        double dx = (x + 0.5D) - lightSource.impetus$getDynamicLightX();
        double dy = (y + 0.5D) - lightSource.impetus$getDynamicLightY();
        double dz = (z + 0.5D) - lightSource.impetus$getDynamicLightZ();

        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > MAX_RADIUS_SQUARED) {
            return currentLightLevel;
        }

        double lightLevel = (1.0D - Math.sqrt(distanceSquared) / MAX_RADIUS) * luminance;
        return lightLevel > currentLightLevel ? lightLevel : currentLightLevel;
    }

    // Starts tracking a source of the client world.
    public void addLightSource(DynamicLightSource lightSource) {
        World world = lightSource.impetus$getDynamicLightWorld();
        if (world == null || !world.isRemote) {
            return;
        }
        if (!DynamicLights.options().mode.isEnabled() || this.containsLightSource(lightSource)) {
            return;
        }

        this.lock.writeLock().lock();
        try {
            if (this.dynamicLightSources.add(lightSource)) {
                this.sourceCount = this.dynamicLightSources.size();
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    // Returns whether the source is tracked.
    public boolean containsLightSource(DynamicLightSource lightSource) {
        World world = lightSource.impetus$getDynamicLightWorld();
        if (world == null || !world.isRemote || this.sourceCount == 0) {
            return false;
        }

        this.lock.readLock().lock();
        try {
            return this.dynamicLightSources.contains(lightSource);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    // Stops tracking the source and rebuilds its chunks.
    public void removeLightSource(DynamicLightSource lightSource) {
        this.lock.writeLock().lock();
        try {
            if (this.dynamicLightSources.remove(lightSource)) {
                this.sourceCount = this.dynamicLightSources.size();
                lightSource.impetus$scheduleTrackedChunksRebuild(Minecraft.getMinecraft().renderGlobal);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    // Stops tracking every source and rebuilds their chunks.
    public void clearLightSources() {
        this.lock.writeLock().lock();
        try {
            RenderGlobal renderer = Minecraft.getMinecraft().renderGlobal;

            for (Iterator<DynamicLightSource> it = this.dynamicLightSources.iterator(); it.hasNext(); ) {
                DynamicLightSource source = it.next();
                it.remove();

                if (source.impetus$getLuminance() > 0) {
                    source.impetus$resetDynamicLight();
                }
                source.impetus$scheduleTrackedChunksRebuild(renderer);
            }

            this.sourceCount = 0;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    // Stops tracking the sources that match the filter.
    public void removeLightSources(Predicate<DynamicLightSource> filter) {
        this.lock.writeLock().lock();
        try {
            RenderGlobal renderer = Minecraft.getMinecraft().renderGlobal;
            boolean changed = false;

            for (Iterator<DynamicLightSource> it = this.dynamicLightSources.iterator(); it.hasNext(); ) {
                DynamicLightSource source = it.next();
                if (!filter.test(source)) {
                    continue;
                }

                it.remove();
                changed = true;

                if (source.impetus$getLuminance() > 0) {
                    source.impetus$resetDynamicLight();
                }
                source.impetus$scheduleTrackedChunksRebuild(renderer);
            }

            if (changed) {
                this.sourceCount = this.dynamicLightSources.size();
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    // Stops tracking entities other than players.
    public void removeEntitiesLightSource() {
        this.removeLightSources(source -> source instanceof Entity && !(source instanceof EntityPlayer));
    }

    // Stops tracking creepers.
    public void removeCreeperLightSources() {
        this.removeLightSources(source -> source instanceof EntityCreeper);
    }

    // Stops tracking primed explosives.
    public void removeTntLightSources() {
        this.removeLightSources(source -> source instanceof EntityTNTPrimed);
    }

    // Stops tracking block entities.
    public void removeBlockEntitiesLightSource() {
        this.removeLightSources(source -> source instanceof TileEntity);
    }

    // Starts or stops tracking to match the light level of the source.
    public static void updateTracking(DynamicLightSource lightSource) {
        boolean enabled = lightSource.impetus$isDynamicLightEnabled();
        int luminance = lightSource.impetus$getLuminance();

        if (!enabled && luminance > 0) {
            lightSource.impetus$setDynamicLightEnabled(true);
        } else if (enabled && luminance < 1) {
            lightSource.impetus$setDynamicLightEnabled(false);
        }
    }

    // Marks the section at the position for a rebuild.
    public static void scheduleChunkRebuild(RenderGlobal renderer, BlockPos chunkPos) {
        scheduleChunkRebuild(renderer, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
    }

    // Marks the packed section for a rebuild.
    public static void scheduleChunkRebuild(RenderGlobal renderer, long packedChunkPos) {
        scheduleChunkRebuild(renderer, unpackX(packedChunkPos), unpackY(packedChunkPos), unpackZ(packedChunkPos));
    }

    // Marks the section for a rebuild.
    public static void scheduleChunkRebuild(RenderGlobal renderer, int x, int y, int z) {
        if (Minecraft.getMinecraft().world == null) {
            return;
        }

        int minX = x << 4;
        int minY = y << 4;
        int minZ = z << 4;

        ((RenderGlobalRebuildAccessor) renderer)
                .impetus$markBlocksForUpdate(minX, minY, minZ, minX + 15, minY + 15, minZ + 15, false);
    }

    // Returns the new update time or minus one when the update is not due.
    public static long nextUpdateStamp(long lastUpdate) {
        DynamicLightsMode mode = DynamicLights.options().mode;
        if (!mode.isEnabled()) {
            return -1L;
        }
        if (!mode.hasDelay()) {
            return lastUpdate;
        }
        long now = System.currentTimeMillis();
        return now >= lastUpdate && now < lastUpdate + mode.getDelay() ? -1L : now;
    }

    // Visits the eight sections nearest to a point and tracks them.
    public static void walkLitSections(RenderGlobal renderer, double x, double y, double z, LongOpenHashSet old, LongOpenHashSet lit) {
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);
        BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos(blockX >> 4, blockY >> 4, blockZ >> 4);
        EnumFacing directionX = (blockX & 15) >= 8 ? EnumFacing.EAST : EnumFacing.WEST;
        EnumFacing directionY = (blockY & 15) >= 8 ? EnumFacing.UP : EnumFacing.DOWN;
        EnumFacing directionZ = (blockZ & 15) >= 8 ? EnumFacing.SOUTH : EnumFacing.NORTH;

        visitLitSection(renderer, chunkPos, old, lit);
        for (int i = 0; i < 7; i++) {
            switch (i & 3) {
                case 0 -> chunkPos.move(directionX);
                case 1 -> chunkPos.move(directionZ);
                case 2 -> chunkPos.move(directionX.getOpposite());
                default -> {
                    chunkPos.move(directionZ.getOpposite());
                    chunkPos.move(directionY);
                }
            }
            visitLitSection(renderer, chunkPos, old, lit);
        }
    }

    // Marks the section for a rebuild and updates the tracked sets.
    private static void visitLitSection(RenderGlobal renderer, BlockPos chunkPos, LongOpenHashSet old, LongOpenHashSet lit) {
        if (renderer != null) {
            scheduleChunkRebuild(renderer, chunkPos);
        }
        updateTrackedChunks(chunkPos, old, lit);
    }

    // Moves a section from the old set to the new set.
    public static void updateTrackedChunks(BlockPos chunkPos, LongOpenHashSet old, LongOpenHashSet newPos) {
        if (old == null && newPos == null) {
            return;
        }

        long packed = packChunkPos(chunkPos);
        if (old != null) {
            old.remove(packed);
        }
        if (newPos != null) {
            newPos.add(packed);
        }
    }

    // Returns the section position packed into a long.
    public static long packChunkPos(BlockPos pos) {
        return (((long) pos.getX() & 0x3FFFFFFL) << 38)
                | (((long) pos.getY() & 0xFFFL) << 26)
                | ((long) pos.getZ() & 0x3FFFFFFL);
    }

    // Returns the x part of a packed section.
    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    // Returns the y part of a packed section.
    public static int unpackY(long packed) {
        return (int) ((packed >> 26) & 0xFFFL);
    }

    // Returns the z part of a packed section.
    public static int unpackZ(long packed) {
        return (int) (packed << 38 >> 38);
    }

    // Returns whether the eyes of the entity are in a fluid and the check is enabled.
    public static boolean isEyeSubmergedInFluid(EntityLivingBase entity) {
        return DynamicLights.options().waterSensitiveCheck && FluidHandler.isFluid(entity);
    }

    // Returns the largest light level of the held and worn items.
    public static int getLivingEntityLuminanceFromItems(EntityLivingBase entity) {
        int luminance = 0;
        int submerged = SUBMERSION_UNKNOWN;

        for (ItemStack equipped : entity.getHeldEquipment()) {
            if (equipped.isEmpty()) {
                continue;
            }
            if (submerged == SUBMERSION_UNKNOWN) {
                submerged = isEyeSubmergedInFluid(entity) ? 1 : 0;
            }
            luminance = Math.max(luminance, getLuminanceFromItemStack(equipped, submerged == 1));
        }

        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (armor.isEmpty()) {
                continue;
            }
            if (submerged == SUBMERSION_UNKNOWN) {
                submerged = isEyeSubmergedInFluid(entity) ? 1 : 0;
            }
            luminance = Math.max(luminance, getLuminanceFromItemStack(armor, submerged == 1));
        }

        return luminance;
    }

    // Marker for an unchecked submersion state.
    private static final int SUBMERSION_UNKNOWN = -1;

    // Returns the light level of the stack limited to the maximum.
    public static int getLuminanceFromItemStack(ItemStack stack, boolean submergedInWater) {
        int luminance = ItemLightSources.getLuminance(stack, submergedInWater);
        return Math.min(luminance, MAX_LUMINANCE);
    }
}
