package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsMode;
import com.bdmajora.dynamiclights.client.item.ItemLightSources;
import com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.TerrainDynamicLights;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// Tracks every moving light source and computes the light it adds.
public final class DynamicLightsEngine {
    // Single instance.
    private static final DynamicLightsEngine INSTANCE = new DynamicLightsEngine();

    // Distance at which a light fades out.
    private static final double MAX_RADIUS = 7.75;
    // Square of the fade distance.
    private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;
    // Widening on every reach test so rounding can add a section but never drop a lit one.
    private static final double REACH_SLACK = 1.0E-6D;
    // Radius the snapshot indexes lights by, the full fade distance so lookups stay exact.
    static final double LOOKUP_RADIUS = MAX_RADIUS + REACH_SLACK;
    // Blocks past a section's edge that its mesh reads light from, since smooth lighting samples one block out.
    private static final int MESH_MARGIN = 1;
    // Most sections a reach can cover: at most three per axis even with the mesh margin.
    static final int MAX_REACHED_SECTIONS = 27;

    // Largest light level of an item.
    private static final int MAX_LUMINANCE = 14;

    // Tracked sources, only touched while holding their monitor.
    private final Set<DynamicLightSource> dynamicLightSources = new HashSet<>();
    // Frozen lights every lookup reads without locking.
    private volatile LightSnapshot snapshot = LightSnapshot.EMPTY;
    // Set when the tracked sources changed after the snapshot was taken.
    private volatile boolean snapshotStale;

    // Number of tracked sources.
    private volatile int sourceCount;

    // Scratch list of the lights handed to the terrain shaders, x, y, z and luminance each.
    private final double[] terrainLights = new double[TerrainDynamicLights.MAX_LIGHTS * TerrainDynamicLights.STRIDE];
    // Squared camera distance of each light in the scratch list.
    private final double[] terrainLightDistances = new double[TerrainDynamicLights.MAX_LIGHTS];

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

        // The snapshot's own array, so a source may add or remove itself mid-walk
        for (DynamicLightSource source : this.snapshot().sources) {
            if (source.impetus$updateDynamicLight(renderer)) {
                updated++;
            }
        }

        // Rebuilds were only queued above, so the meshes they produce read these positions
        if (updated > 0) {
            this.recapture();
        }

        this.lastUpdateCount = updated;
    }

    // Hands the terrain shaders the nearest lit sources, at their drawn positions, for this frame.
    public void publishTerrainLights(float partialTicks) {
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (!TerrainDynamicLights.isShaderApplied() || !DynamicLights.options().mode.isEnabled() || this.sourceCount == 0
                || viewer == null) {
            if (TerrainDynamicLights.lightCount() != 0) {
                TerrainDynamicLights.setLights(this.terrainLights, 0);
            }
            return;
        }

        double cameraX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double cameraY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double cameraZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
        double[] lights = this.terrainLights;
        double[] distances = this.terrainLightDistances;
        int kept = 0;

        for (DynamicLightSource source : this.snapshot().sources) {
            int luminance = source.impetus$getLuminance();
            if (luminance <= 0) {
                continue;
            }
            double x = source.impetus$getDynamicLightX(partialTicks);
            double y = source.impetus$getDynamicLightY(partialTicks);
            double z = source.impetus$getDynamicLightZ(partialTicks);
            double dx = x - cameraX;
            double dy = y - cameraY;
            double dz = z - cameraZ;
            double distance = dx * dx + dy * dy + dz * dz;
            // Not a number never ranks, so a broken position cannot take a slot
            if (!(distance < Double.POSITIVE_INFINITY)) {
                continue;
            }
            int slot = kept < TerrainDynamicLights.MAX_LIGHTS ? kept++ : -1;
            if (slot < 0) {
                // Full: the light replaces the farthest kept one when it is nearer
                int farthest = 0;
                for (int i = 1; i < kept; i++) {
                    if (distances[i] > distances[farthest]) {
                        farthest = i;
                    }
                }
                if (distance >= distances[farthest]) {
                    continue;
                }
                slot = farthest;
            }
            distances[slot] = distance;
            int offset = slot * TerrainDynamicLights.STRIDE;
            lights[offset] = x;
            lights[offset + 1] = y;
            lights[offset + 2] = z;
            lights[offset + 3] = luminance;
        }

        TerrainDynamicLights.setLights(lights, kept);
    }

    // Retakes the snapshot from the tracked sources.
    private void recapture() {
        synchronized (this.dynamicLightSources) {
            this.snapshot = LightSnapshot.capture(this.dynamicLightSources);
            // Cleared after the write so a reader that sees it cleared also sees the new snapshot
            this.snapshotStale = false;
        }
    }

    // Returns the current snapshot, retaking it first when the tracked set changed.
    private LightSnapshot snapshot() {
        if (this.snapshotStale) {
            synchronized (this.dynamicLightSources) {
                if (this.snapshotStale) {
                    this.snapshot = LightSnapshot.capture(this.dynamicLightSources);
                    this.snapshotStale = false;
                }
            }
        }
        return this.snapshot;
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

        int atPosition = (int) this.getDynamicLightLevel(MathHelper.floor(entity.posX), MathHelper.floor(entity.posY),
                MathHelper.floor(entity.posZ));
        int ownLuminance = ((DynamicLightSource) entity).impetus$getLuminance();

        return this.getLightmapWithDynamicLight(Math.max(atPosition, ownLuminance), lightmap);
    }

    // Returns the light map value raised to the light level.
    public int getLightmapWithDynamicLight(double dynamicLightLevel, int lightmap) {
        int level = (int) dynamicLightLevel;
        int blockLight = (lightmap >> 4) & 0xF;
        // Untouched unless the whole level is brighter, so a fraction never repacks the value
        if (level <= blockLight) {
            return lightmap;
        }

        int skyLight = (lightmap >> 20) & 0xF;
        return (skyLight << 20) | (Math.min(level, 15) << 4);
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
        return this.snapshot().levelAt(x, y, z);
    }

    // Returns the larger of the current level and the level the source adds.
    public static double maxDynamicLightLevel(int x, int y, int z, DynamicLightSource lightSource,
                                              double currentLightLevel) {
        return lightLevel(x, y, z, lightSource.impetus$getDynamicLightX(), lightSource.impetus$getDynamicLightY(),
                lightSource.impetus$getDynamicLightZ(), lightSource.impetus$getLuminance(), currentLightLevel);
    }

    // Returns the larger of the current level and the level a light at the point adds to the block.
    static double lightLevel(int x, int y, int z, double lightX, double lightY, double lightZ, int luminance,
                             double currentLightLevel) {
        if (luminance <= 0) {
            return currentLightLevel;
        }

        double dx = (x + 0.5D) - lightX;
        double dy = (y + 0.5D) - lightY;
        double dz = (z + 0.5D) - lightZ;

        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > MAX_RADIUS_SQUARED) {
            return currentLightLevel;
        }

        double lightLevel = (1.0D - Math.sqrt(distanceSquared) / MAX_RADIUS) * luminance;
        return lightLevel > currentLightLevel ? lightLevel : currentLightLevel;
    }

    // Returns the distance inside which the light raises a block by at least one whole level.
    static double meshReach(int luminance) {
        // (1 - d / R) * luminance >= 1 solved for d; the lightmap only takes whole levels
        return MAX_RADIUS * (1.0D - 1.0D / luminance) + REACH_SLACK;
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

        synchronized (this.dynamicLightSources) {
            if (this.dynamicLightSources.add(lightSource)) {
                this.sourceCount = this.dynamicLightSources.size();
                this.snapshotStale = true;
            }
        }
    }

    // Returns whether the source is tracked.
    public boolean containsLightSource(DynamicLightSource lightSource) {
        World world = lightSource.impetus$getDynamicLightWorld();
        if (world == null || !world.isRemote || this.sourceCount == 0) {
            return false;
        }

        synchronized (this.dynamicLightSources) {
            return this.dynamicLightSources.contains(lightSource);
        }
    }

    // Stops tracking the source and rebuilds its chunks.
    public void removeLightSource(DynamicLightSource lightSource) {
        boolean removed;
        synchronized (this.dynamicLightSources) {
            removed = this.dynamicLightSources.remove(lightSource);
            if (removed) {
                this.sourceCount = this.dynamicLightSources.size();
                this.snapshotStale = true;
            }
        }

        // The stale flag makes the rebuild's first lookup retake the snapshot without this source
        if (removed) {
            lightSource.impetus$scheduleTrackedChunksRebuild(Minecraft.getMinecraft().renderGlobal);
        }
    }

    // Stops tracking every source and rebuilds their chunks.
    public void clearLightSources() {
        this.removeLightSources(source -> true);
    }

    // Stops tracking the sources that match the filter.
    public void removeLightSources(Predicate<DynamicLightSource> filter) {
        List<DynamicLightSource> removed = new ArrayList<>();
        synchronized (this.dynamicLightSources) {
            for (Iterator<DynamicLightSource> it = this.dynamicLightSources.iterator(); it.hasNext(); ) {
                DynamicLightSource source = it.next();
                if (filter.test(source)) {
                    it.remove();
                    removed.add(source);
                }
            }

            if (!removed.isEmpty()) {
                this.sourceCount = this.dynamicLightSources.size();
                this.snapshotStale = true;
            }
        }

        // Outside the monitor, so a source's rebuild hook can never wait on a reader retaking the snapshot
        RenderGlobal renderer = removed.isEmpty() ? null : Minecraft.getMinecraft().renderGlobal;
        for (DynamicLightSource source : removed) {
            if (source.impetus$getLuminance() > 0) {
                source.impetus$resetDynamicLight();
            }
            source.impetus$scheduleTrackedChunksRebuild(renderer);
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

    // Queues the sections the light left or now reaches and returns the ones it reaches, null when dark.
    public static LongOpenHashSet relightSections(RenderGlobal renderer, World world, LongOpenHashSet previous,
                                                  double x, double y, double z, int luminance) {
        // Meshes hold no moving light while the terrain shaders apply it, so there is nothing to rebuild or track
        if (TerrainDynamicLights.isShaderApplied()) {
            return null;
        }
        LongOpenHashSet reached = null;
        if (luminance > 0) {
            reached = new LongOpenHashSet(8);
            collectLitSections(x, y, z, luminance, reached);
        }

        // A light from a world that is no longer shown has nothing on screen to rebuild
        if (renderer == null || Minecraft.getMinecraft().world != world) {
            return reached;
        }

        if (reached != null) {
            for (LongIterator it = reached.iterator(); it.hasNext(); ) {
                scheduleChunkRebuild(renderer, it.nextLong());
            }
        }
        if (previous != null) {
            for (LongIterator it = previous.iterator(); it.hasNext(); ) {
                long packed = it.nextLong();
                if (reached == null || !reached.contains(packed)) {
                    scheduleChunkRebuild(renderer, packed);
                }
            }
        }
        return reached;
    }

    // Adds every section whose mesh can show at least one level of the light.
    public static void collectLitSections(double x, double y, double z, int luminance, LongSet out) {
        if (luminance <= 0) {
            return;
        }

        long[] reached = new long[MAX_REACHED_SECTIONS];
        int count = reachedSections(x, y, z, meshReach(luminance), MESH_MARGIN, reached);
        for (int i = 0; i < count; i++) {
            out.add(reached[i]);
        }
    }

    // Writes the sections, each widened by margin blocks, holding a block centre within the radius, and returns how many.
    static int reachedSections(double x, double y, double z, double radius, int margin, long[] out) {
        double radiusSquared = radius * radius;
        int minX = (MathHelper.ceil(x - 0.5D - radius) - margin) >> 4;
        int maxX = (MathHelper.floor(x - 0.5D + radius) + margin) >> 4;
        int minY = (MathHelper.ceil(y - 0.5D - radius) - margin) >> 4;
        int maxY = (MathHelper.floor(y - 0.5D + radius) + margin) >> 4;
        int minZ = (MathHelper.ceil(z - 0.5D - radius) - margin) >> 4;
        int maxZ = (MathHelper.floor(z - 0.5D + radius) + margin) >> 4;
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);
        int count = 0;

        // Box corners already cover the axis ranges, the sphere test drops the far corners
        for (int sectionX = minX; sectionX <= maxX; sectionX++) {
            double dx = nearestBlock(blockX, sectionX, margin) + 0.5D - x;
            for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                double dy = nearestBlock(blockY, sectionY, margin) + 0.5D - y;
                for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                    double dz = nearestBlock(blockZ, sectionZ, margin) + 0.5D - z;
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        out[count++] = packSection(sectionX, sectionY, sectionZ);
                    }
                }
            }
        }
        return count;
    }

    // Returns the block of the widened section closest to the given block on one axis.
    private static int nearestBlock(int block, int section, int margin) {
        int min = (section << 4) - margin;
        int max = (section << 4) + 15 + margin;
        return block < min ? min : Math.min(block, max);
    }

    // Returns the section position packed into a long.
    public static long packChunkPos(BlockPos pos) {
        return packSection(pos.getX(), pos.getY(), pos.getZ());
    }

    // Returns the section coordinates packed into a long.
    public static long packSection(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38)
                | (((long) y & 0xFFFL) << 26)
                | ((long) z & 0x3FFFFFFL);
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
