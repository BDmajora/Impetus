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

// Tracked light sources and the lightmap arithmetic over them, read from client, render and chunk-builder threads under a read/write lock; sourceCount is a lock-free fast path for the "nothing is glowing" case
public final class DynamicLightsEngine {
    private static final DynamicLightsEngine INSTANCE = new DynamicLightsEngine();

    // How far a source's light reaches, in blocks; short of vanilla's 15 since every extra block is another ring of chunk sections to rebuild on move
    private static final double MAX_RADIUS = 7.75;
    private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;

    // Clamped here rather than at 15 so a source never reads as a full-bright block
    private static final int MAX_LUMINANCE = 14;

    private final Set<DynamicLightSource> dynamicLightSources = new HashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Mirrors dynamicLightSources.size() for readers that must not take the lock
    private volatile int sourceCount;

    private long lastUpdate = System.currentTimeMillis();
    private int lastUpdateCount;

    private DynamicLightsEngine() {
    }

    // Single client-wide instance
    public static DynamicLightsEngine get() {
        return INSTANCE;
    }

    // Per-frame update

    // Gives every tracked source the chance to re-light the chunks around it; rate-limited to once per tick (50ms) since sources can't move faster
    public void updateAll(RenderGlobal renderer) {
        if (!DynamicLights.options().mode.isEnabled() || this.sourceCount == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.lastUpdate + 50L) {
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

    // How many sources scheduled a rebuild on the last update pass; for the F3 overlay
    public int getLastUpdateCount() {
        return this.lastUpdateCount;
    }

    // How many sources are currently tracked; for the F3 overlay
    public int getLightSourcesCount() {
        return this.sourceCount;
    }

    // Lightmap arithmetic

    // Folds the dynamic light at pos into a packed vanilla lightmap coordinate
    public int getLightmapWithDynamicLight(BlockPos pos, int lightmap) {
        return this.getLightmapWithDynamicLight(this.getDynamicLightLevel(pos), lightmap);
    }

    // Folds the light at the entity's feet and its own luminance into lightmap; once per rendered entity per frame, so the empty case returns before allocating a BlockPos
    public int getLightmapWithDynamicLight(Entity entity, int lightmap) {
        if (this.sourceCount == 0) {
            return lightmap;
        }

        int atPosition = (int) this.getDynamicLightLevel(new BlockPos(entity.posX, entity.posY, entity.posZ));
        int ownLuminance = ((DynamicLightSource) entity).impetus$getLuminance();

        return this.getLightmapWithDynamicLight(Math.max(atPosition, ownLuminance), lightmap);
    }

    // Raises the block-light half of lightmap to dynamicLightLevel if brighter; sky light is carried through untouched, a torch shouldn't fake daytime
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

    // Brightest dynamic light reaching pos on the 0-15 scale; hot path (once per block per section compile), so the empty-set check short-circuits before the lock
    public double getDynamicLightLevel(BlockPos pos) {
        return getDynamicLightLevel(pos.getX(), pos.getY(), pos.getZ());
    }

    // Block-coordinate form, so a per-frame caller (particles) need not allocate a BlockPos
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

    // currentLightLevel, or this source's contribution at the block if brighter; linear falloff rather than per-block subtraction since there is no grid to step and a ramp avoids banding on a moving source
    public static double maxDynamicLightLevel(int x, int y, int z, DynamicLightSource lightSource,
                                              double currentLightLevel) {
        int luminance = lightSource.impetus$getLuminance();
        if (luminance <= 0) {
            return currentLightLevel;
        }

        // Not Entity#getDistanceSq: the source's Y is its eye height, not its feet.
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

    // The tracked set

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

    // Lock-free negative answers for the common cases before taking the read lock
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

    // Drops a source and rebuilds whatever it was lighting, so its glow does not linger
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

    // Drops every source and re-lights whatever they were lighting
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

    // Drops every source matching filter; upstream breaks after the first match, but removing every match is what the callers mean
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

    // Drops every non-player entity source, for the entities toggle
    public void removeEntitiesLightSource() {
        this.removeLightSources(source -> source instanceof Entity && !(source instanceof EntityPlayer));
    }

    // Drops creeper sources, for the creeper mode switch
    public void removeCreeperLightSources() {
        this.removeLightSources(source -> source instanceof EntityCreeper);
    }

    // Drops primed TNT sources, for the TNT mode switch
    public void removeTntLightSources() {
        this.removeLightSources(source -> source instanceof EntityTNTPrimed);
    }

    // Drops block entity sources, for the block entities toggle
    public void removeBlockEntitiesLightSource() {
        this.removeLightSources(source -> source instanceof TileEntity);
    }

    // Tracking and chunk rebuilds

    // Starts tracking a source that has become lit, or stops tracking one that has gone dark
    public static void updateTracking(DynamicLightSource lightSource) {
        boolean enabled = lightSource.impetus$isDynamicLightEnabled();
        int luminance = lightSource.impetus$getLuminance();

        if (!enabled && luminance > 0) {
            lightSource.impetus$setDynamicLightEnabled(true);
        } else if (enabled && luminance < 1) {
            lightSource.impetus$setDynamicLightEnabled(false);
        }
    }

    // BlockPos overload of the section rebuild
    public static void scheduleChunkRebuild(RenderGlobal renderer, BlockPos chunkPos) {
        scheduleChunkRebuild(renderer, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
    }

    // Packed-long overload, the form the tracked-section sets store
    public static void scheduleChunkRebuild(RenderGlobal renderer, long packedChunkPos) {
        scheduleChunkRebuild(renderer, unpackX(packedChunkPos), unpackY(packedChunkPos), unpackZ(packedChunkPos));
    }

    // Queues a rebuild of the section at chunk coordinates (x, y, z) via markBlocksForUpdate, which Impetus overwrites to route into its own renderer
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

    // Whether a source may re-light its surroundings yet under the configured rate: returns the stamp to store, or -1 when the delay since lastUpdate has not elapsed (or the mode is off)
    public static long nextUpdateStamp(long lastUpdate) {
        DynamicLightsMode mode = DynamicLights.options().mode;
        if (!mode.isEnabled()) {
            return -1L;
        }
        if (!mode.hasDelay()) {
            return lastUpdate;
        }
        long now = System.currentTimeMillis();
        return now < lastUpdate + mode.getDelay() ? -1L : now;
    }

    // The eight sections a source at (x, y, z) reaches: its own, the neighbour on the near side of each axis and the diagonals between them, since a 7.75-block reach spills that far; each is moved from old into lit (either may be null) and rebuilt when a renderer is given
    public static void walkLitSections(RenderGlobal renderer, double x, double y, double z, LongOpenHashSet old, LongOpenHashSet lit) {
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);
        BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos(blockX >> 4, blockY >> 4, blockZ >> 4);
        EnumFacing directionX = (blockX & 15) >= 8 ? EnumFacing.EAST : EnumFacing.WEST;
        EnumFacing directionY = (blockY & 15) >= 8 ? EnumFacing.UP : EnumFacing.DOWN;
        EnumFacing directionZ = (blockZ & 15) >= 8 ? EnumFacing.SOUTH : EnumFacing.NORTH;

        visitLitSection(renderer, chunkPos, old, lit);
        // Walks the four sections of the near layer in a ring, then steps up (or down) and walks the ring again
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

    private static void visitLitSection(RenderGlobal renderer, BlockPos chunkPos, LongOpenHashSet old, LongOpenHashSet lit) {
        if (renderer != null) {
            scheduleChunkRebuild(renderer, chunkPos);
        }
        updateTrackedChunks(chunkPos, old, lit);
    }

    // Moves chunkPos from the old tracked set into newPos
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

    // Packs signed 26/12/26-bit chunk coordinates into one long
    public static long packChunkPos(BlockPos pos) {
        return (((long) pos.getX() & 0x3FFFFFFL) << 38)
                | (((long) pos.getY() & 0xFFFL) << 26)
                | ((long) pos.getZ() & 0x3FFFFFFL);
    }

    // Top 26 bits, sign-extended by the arithmetic shift
    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    // Middle 12 bits
    public static int unpackY(long packed) {
        return (int) ((packed >> 26) & 0xFFFL);
    }

    // Bottom 26 bits, sign-extended by shifting up then down
    public static int unpackZ(long packed) {
        return (int) (packed << 38 >> 38);
    }

    // Item luminance

    // True when the entity's eyes are inside a fluid and the water-sensitivity check is on
    public static boolean isEyeSubmergedInFluid(EntityLivingBase entity) {
        return DynamicLights.options().waterSensitiveCheck && FluidHandler.isFluid(entity);
    }

    // Brightest item the entity holds or wears; runs every tick per living entity, so the submersion block lookup is deferred until a non-empty stack, with a tri-state to stay allocation-free
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

    // Sentinel for "the submersion test has not been run yet"
    private static final int SUBMERSION_UNKNOWN = -1;

    // Luminance of an item stack, capped at MAX_LUMINANCE so held light tops out one step below full-bright (which would defeat distance falloff)
    public static int getLuminanceFromItemStack(ItemStack stack, boolean submergedInWater) {
        int luminance = ItemLightSources.getLuminance(stack, submergedInWater);
        return Math.min(luminance, MAX_LUMINANCE);
    }
}
