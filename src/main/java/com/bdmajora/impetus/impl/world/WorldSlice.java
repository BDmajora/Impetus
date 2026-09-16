package com.bdmajora.impetus.impl.world;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.fml.common.Optional;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import com.bdmajora.fulgor.FulgorRenderBridge;
import com.bdmajora.fulgor.lighting.FaceLightRules;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.world.biome.BiomeColorCache;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.bdmajora.impetus.impl.world.cloned.ChunkRenderContext;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSection;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSectionCache;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Copies a slice of world state (blocks, biomes, light) off-thread so chunk build tasks see a consistent snapshot; not shareable across threads, and pooled since the backing arrays are large
public class WorldSlice implements ImpetusBlockAccess {
    // The number of blocks on each axis in a section.
    private static final int SECTION_BLOCK_LENGTH = 16;

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;

    // The radius of blocks around the origin chunk that should be copied.
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // The radius of chunks around the origin chunk that should be copied.
    private static final int NEIGHBOR_CHUNK_RADIUS = MathHelper.roundUp(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // The number of sections on each axis of this slice.
    private static final int SECTION_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // Lookup table size for mapping values to coordinate pairs, always a power of two so hot-path multiplications become shifts
    private static final int TABLE_LENGTH = MathHelper.smallestEncompassingPowerOfTwo(SECTION_LENGTH);

    // The number of bits needed for each X/Y/Z component in a lookup table.
    private static final int TABLE_BITS = Integer.bitCount(TABLE_LENGTH - 1);

    // The default block state used for out-of-bounds access
    private static final IBlockState EMPTY_BLOCK_STATE = Blocks.AIR.getDefaultState();

    // The array size for the section lookup table.
    private static final int SECTION_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH * TABLE_LENGTH;

    // The world this slice has copied data from
    private final World world;
    private final WorldType worldType;
    private final int defaultSkyLightValue;

    // Fulgor's face-aware neighbour brightness, snapshotted per slice since the option needs a restart anyway
    private final boolean fixRenderLighting = FulgorRenderBridge.fixRenderLighting();


    // Local Section->BlockState table.
    private final IBlockState[][] blockStatesArrays;

    // Local Section->FluidState table.
    private final Object[][] fluidStatesArrays;

    // Local section copies. Read-only.
    private ClonedChunkSection[] sections;

    // Biome caches for each chunk section
    private final Biome[][] biomeCaches;

    // The biome blend caches for each color resolver type
    private final BiomeColorCache biomeColorCache;

    // The starting point from which this slice captures blocks
    private int baseX, baseY, baseZ;

    // The chunk origin of this slice
    private SectionPos origin;

    // The volume that this slice contains
    private StructureBoundingBox volume;

    // A fallback BlockPos object to use when retrieving data from the level directly
    private final BlockPos.MutableBlockPos fallbackPos = new BlockPos.MutableBlockPos();

    // Extra cloned chunk sections that the slice needed
    private final Long2ReferenceMap<ClonedChunkSection> extraClonedSections = new Long2ReferenceOpenHashMap<>();

    // Clones the 3x3x3 sections around a render section on the main thread, so the builder thread never touches the world
    public static ChunkRenderContext prepare(World world, SectionPos origin, ClonedChunkSectionCache sectionCache) {
        // Fulgor defers light propagation until something reads light, and the copies below read section arrays directly rather than via Chunk#getLightFor, so resolve what is pending or the mesh bakes in an earlier tick's light
        FulgorRenderBridge.flushPendingLightUpdates(world);

        Chunk chunk = world.getChunk(origin.x(), origin.z());
        ExtendedBlockStorage section = chunk.getBlockStorageArray()[origin.y()];

        // An absent or empty section will never have anything to render, so signal that no build task should be created; this greatly accelerates world load
        if (section == null || FulgorRenderBridge.isEmptyOfBlocks(section)) {
            return null;
        }

        StructureBoundingBox volume = new StructureBoundingBox(origin.minX() - NEIGHBOR_BLOCK_RADIUS,
                origin.minY() - NEIGHBOR_BLOCK_RADIUS,
                origin.minZ() - NEIGHBOR_BLOCK_RADIUS,
                origin.maxX() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxY() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxZ() + NEIGHBOR_BLOCK_RADIUS);

        // The min/max bounds of the chunks copied by this slice
        final int minChunkX = origin.x() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkY = origin.y() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkZ = origin.z() - NEIGHBOR_CHUNK_RADIUS;

        final int maxChunkX = origin.x() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkY = origin.y() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkZ = origin.z() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_TABLE_ARRAY_SIZE];

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections[getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ)] =
                            sectionCache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        return new ChunkRenderContext(origin, sections, volume);
    }

    // Nether and End have no skylight; skipping the lookup there saves a branch per vertex
    private boolean hasSkyLight() {
        return this.world.provider.hasSkyLight();
    }

    public WorldSlice(World world) {
        this.world = world;
        this.worldType = world.getWorldType();
        this.defaultSkyLightValue = this.hasSkyLight() ? EnumSkyBlock.SKY.defaultLightValue : 0;

        this.sections = new ClonedChunkSection[SECTION_TABLE_ARRAY_SIZE];
        this.blockStatesArrays = new IBlockState[SECTION_TABLE_ARRAY_SIZE][];
        this.biomeCaches = new Biome[SECTION_TABLE_ARRAY_SIZE][16 * 16];
        this.biomeColorCache = new BiomeColorCache(this, ImpetusVintage.options().quality.legacyBiomeBlendRadius);
        if(!FluidloggedCompat.IS_LOADED) this.fluidStatesArrays = null;
        else this.fluidStatesArrays = new Object[SECTION_TABLE_ARRAY_SIZE][];

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    int i = getLocalSectionIndex(x, y, z);

                    this.blockStatesArrays[i] = new IBlockState[SECTION_BLOCK_COUNT];
                    Arrays.fill(this.blockStatesArrays[i], EMPTY_BLOCK_STATE);

                    if (FluidloggedCompat.IS_LOADED) {
                        this.fluidStatesArrays[i] = new Object[SECTION_BLOCK_COUNT];
                        Arrays.fill(this.fluidStatesArrays[i], FluidloggedCompat.getEmptyFluidState());
                    }
                }
            }
        }
    }

    // Unpacks the cloned sections into flat arrays for the builder thread
    public void copyData(ChunkRenderContext context) {
        this.origin = context.getOrigin();
        this.sections = context.getSections();
        this.volume = context.getVolume();


        this.biomeColorCache.update(context.getOrigin());

        this.baseX = (this.origin.x() - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.baseY = (this.origin.y() - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.baseZ = (this.origin.z() - NEIGHBOR_CHUNK_RADIUS) << 4;

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    int idx = getLocalSectionIndex(x, y, z);

                    ClonedChunkSection section = this.sections[idx];

                    this.biomeCaches[idx] = section.getBiomeData();

                    this.unpackBlockData(this.blockStatesArrays[idx], section, context.getVolume());

                    if (FluidloggedCompat.IS_LOADED) {
                        this.unpackFluidData(this.fluidStatesArrays[idx], section, context.getVolume());
                    }
                }
            }
        }
    }

    // Drops references so a pooled slice does not pin chunk data between builds
    public void reset() {
        this.extraClonedSections.clear();
    }

    // Chooses the fastest copy shape for a section depending on how much of it the box covers
    private void unpackBlockData(IBlockState[] states, ClonedChunkSection section, StructureBoundingBox box) {
        if (this.origin.equals(section.getPosition()))  {
            this.unpackBlockDataZ(states, section);
        } else {
            this.unpackBlockDataR(states, section, box);
        }
    }

    // Fluidlogged API's second state layer, copied alongside blocks when that mod is present
    private void unpackFluidData(Object[] states, ClonedChunkSection section, StructureBoundingBox box) {
        var storage = section.getFluidData();
        if (storage.isEmpty()) {
            Arrays.fill(states, FluidloggedCompat.getEmptyFluidState());
            return;
        }
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    states[getLocalBlockIndex(x, y, z)] = storage.get(x, y, z);
                }
            }
        }
    }

    // Bounded copy for a partially covered section
    private static void copyBlocks(IBlockState[] blocks, ClonedChunkSection section, int minBlockY, int maxBlockY, int minBlockZ, int maxBlockZ, int minBlockX, int maxBlockX) {
        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    final int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    blocks[blockIdx] = section.getBlockState(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    // Row-by-row copy when the box spans whole rows
    private void unpackBlockDataR(IBlockState[] states, ClonedChunkSection section, StructureBoundingBox box) {
        SectionPos pos = section.getPosition();

        int minBlockX = Math.max(box.minX, pos.minX());
        int maxBlockX = Math.min(box.maxX, pos.maxX());

        int minBlockY = Math.max(box.minY, pos.minY());
        int maxBlockY = Math.min(box.maxY, pos.maxY());

        int minBlockZ = Math.max(box.minZ, pos.minZ());
        int maxBlockZ = Math.min(box.maxZ, pos.maxZ());

        copyBlocks(states, section, minBlockY, maxBlockY, minBlockZ, maxBlockZ, minBlockX, maxBlockX);
    }

    // Whole-section copy, the common case for the centre section
    private void unpackBlockDataZ(IBlockState[] states, ClonedChunkSection section) {
        // TODO: Look into a faster copy for this?
        final SectionPos pos = section.getPosition();

        final int minBlockX = pos.minX();
        final int maxBlockX = pos.maxX();

        final int minBlockY = pos.minY();
        final int maxBlockY = pos.maxY();

        final int minBlockZ = pos.minZ();
        final int maxBlockZ = pos.maxZ();

        // TODO: Can this be optimized?
        copyBlocks(states, section, minBlockY, maxBlockY, minBlockZ, maxBlockZ, minBlockX, maxBlockX);
    }

    // Inclusive bounds test
    private static boolean blockBoxContains(StructureBoundingBox box, int x, int y, int z) {
        return x >= box.minX &&
                x <= box.maxX &&
                y >= box.minY &&
                y <= box.maxY &&
                z >= box.minZ &&
                z <= box.maxZ;
    }

    // IBlockAccess entry point; unpacks to the int overload
    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    // IBlockAccess entry point
    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState state = this.getBlockState(pos);
        return state.getBlock().isAir(state, this, pos);
    }

    // Reads from the flat arrays; positions outside the slice fall back to the cloned section cache
    public IBlockState getBlockState(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return this.getBlockStateFallback(x, y, z);
        }

        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        return this.blockStatesArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                [getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
    }

    // Slice-relative read, the hot path for the block renderer
    public IBlockState getBlockStateRelative(int x, int y, int z) {
        // NOTE: Not bounds checked. We assume ChunkRenderRebuildTask is the only function using this
        return this.blockStatesArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)]
                [getLocalBlockIndex(x & 15, y & 15, z & 15)];
    }

    // IBlockAccess entry point
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return this.getBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    // From the cloned section's tile entity map, never the live world
    public TileEntity getBlockEntity(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return null;
        }

        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        return this.sections[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                .getBlockEntity(relX & 15, relY & 15, relZ & 15);
    }

    // Packed sky and block light as vanilla's lightmap expects, from the cloned light arrays
    @Override
    public int getCombinedLight(BlockPos pos, int ambientLight) {
        if (!blockBoxContains(this.volume, pos.getX(), pos.getY(), pos.getZ())) {
            return (this.defaultSkyLightValue << 20) | (ambientLight << 4);
        }

        int i = this.getLightFromNeighborsFor(EnumSkyBlock.SKY, pos);
        int j = this.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, pos);

        if (j < ambientLight)
        {
            j = ambientLight;
        }

        return i << 20 | j << 4;
    }

    // Single-type light read from the cloned arrays
    private int getLightFor(EnumSkyBlock type, int relX, int relY, int relZ) {
        ClonedChunkSection section = this.sections[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)];

        return section.getLightLevel(relX & 15, relY & 15, relZ & 15, type);
    }

    // Vanilla's neighbour-max rule for translucent blocks, over cloned data; with Fulgor's render fix a block is lit through its open faces only
    private int getLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos) {
        if(!this.hasSkyLight() && type == EnumSkyBlock.SKY) {
            return this.defaultSkyLightValue;
        }

        int relX = pos.getX() - this.baseX;
        int relY = pos.getY() - this.baseY;
        int relZ = pos.getZ() - this.baseZ;

        IBlockState state = this.getBlockStateRelative(relX, relY, relZ);

        if (this.fixRenderLighting) {
            int faces = FaceLightRules.openFaces(state);
            int level = getLightFor(type, relX, relY, relZ);
            if (faces == 0) {
                return level;
            }
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (level >= 15) {
                    break;
                }
                if ((faces & (1 << facing.ordinal())) != 0) {
                    level = FaceLightRules.fold(level, getLightFor(type, relX + facing.getXOffset(), relY + facing.getYOffset(), relZ + facing.getZOffset()), type);
                }
            }
            return level;
        }

        if(!state.useNeighborBrightness()) {
            return getLightFor(type, relX, relY, relZ);
        } else {
            int west = getLightFor(type, relX - 1, relY, relZ);
            int east = getLightFor(type, relX + 1, relY, relZ);
            int up = getLightFor(type, relX, relY + 1, relZ);
            int down = getLightFor(type, relX, relY - 1, relZ);
            int north = getLightFor(type, relX, relY, relZ + 1);
            int south = getLightFor(type, relX, relY, relZ - 1);

            if(east > west) {
                west = east;
            }

            if(up > west) {
                west = up;
            }

            if(down > west) {
                west = down;
            }

            if(north > west) {
                west = north;
            }

            if(south > west) {
                west = south;
            }

            return west;
        }
    }

    // IBlockAccess entry point
    @Override
    public Biome getBiome(BlockPos pos) {
        int x2 = (pos.getX() - this.baseX) >> 4;
        int z2 = (pos.getZ() - this.baseZ) >> 4;

        ClonedChunkSection section = this.sections[getLocalChunkIndex(x2, z2)];

        if (section != null) {
            return section.getBiomeForNoiseGen(pos.getX() & 15, pos.getZ() & 15);
        }

        return Biomes.PLAINS;
    }

    // Biome colour through the slice's own biome data, so smooth blending stays off-thread
    @Override
    public int getBlockTint(BlockPos pos, BiomeColorHelper.ColorResolver resolver) {
        if(!blockBoxContains(this.volume, pos.getX(), pos.getY(), pos.getZ())) {
            return resolver.getColorAtPos(Biomes.PLAINS, pos);
        }

        return this.biomeColorCache.getColor(resolver, pos.getX(), pos.getY(), pos.getZ());
    }

    // Redstone power is never needed for rendering; always zero
    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        IBlockState state = this.getBlockState(pos);
        return state.getBlock().getStrongPower(state, this, pos, direction);
    }

    // Forwarded from the world captured at prepare time
    @Override
    public WorldType getWorldType() {
        return this.worldType;
    }

    // Asks the block state directly against this slice
    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    // From the cloned biome array; y is ignored on 1.12.2
    public Biome getBiome(int x, int y, int z) {
        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        int idx = getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4);

        if (idx < 0 || idx >= this.biomeCaches.length) {
            return Biomes.PLAINS;
        }

        return this.biomeCaches[idx][((z & 15) << 4) | (x & 15)];
    }

    // The render section this slice was prepared for
    public SectionPos getOrigin() {
        return this.origin;
    }

    // Vanilla's per-face diffuse factors
    public float getBrightness(EnumFacing direction, boolean shaded) {
        if (!shaded) {
            return !hasSkyLight() ? 0.9f : 1.0f;
        }
        return LightUtil.diffuseLight(direction);
    }

    // For reads just outside the slice, e.g. a neighbour needed for culling; goes through the cache
    @Nullable
    private ClonedChunkSection fetchFallbackSectionForPos(int x, int y, int z) {
        int sX = PositionUtil.posToSectionCoord(x);
        int sY = PositionUtil.posToSectionCoord(y);
        int sZ = PositionUtil.posToSectionCoord(z);
        long key = PositionUtil.packSection(sX, sY, sZ);
        var section = this.extraClonedSections.get(key);
        if (section != null) {
            return section;
        }
        var renderer = ImpetusWorldRenderer.instanceNullable();
        if (renderer == null) {
            return null;
        }
        var manager = renderer.getRenderSectionManager();
        if (manager == null) {
            return null;
        }
        var sectionFuture = CompletableFuture.supplyAsync(() -> {
            return manager.getSectionCache().acquire(sX, sY, sZ);
        }, manager::scheduleAsyncTask);
        // The game will discard the future if the player disconnects, so we need to check that they are still connected.
        while (Minecraft.getMinecraft().world == this.world) {
            try {
                section = sectionFuture.get(500, TimeUnit.MILLISECONDS);
                break;
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to fetch fallback section", e);
            } catch (InterruptedException | TimeoutException ignored) {
            }
        }
        if (section != null) {
            this.extraClonedSections.put(key, section);
        }
        return section;
    }

    // Reads the block state safely off the main thread by cloning the needed section
    private IBlockState getBlockStateFallback(int x, int y, int z) {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            this.fallbackPos.setPos(x, y, z);
            return this.world.getBlockState(this.fallbackPos);
        } else {
            ClonedChunkSection sectionSnapshot = this.fetchFallbackSectionForPos(x, y, z);
            if (sectionSnapshot != null) {
                return sectionSnapshot.getBlockState(x & 15, y & 15, z & 15);
            } else {
                return EMPTY_BLOCK_STATE;
            }
        }
    }

    // Fluidlogged compat

    @Override
    @Optional.Method(modid = FluidloggedCompat.MODID)
    public FluidState getFluidState(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return FluidloggedCompat.getEmptyFluidState();
        }

        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        return (FluidState)this.fluidStatesArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                [getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
    }

    // The live world, for callers that need it; never read from the builder thread
    @Override
    @Optional.Method(modid = FluidloggedCompat.MODID)
    public World getWorld() {
        return world;
    }

    // [VanillaCopy] PalettedContainer#toIndex
    public static int getLocalBlockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    // Index into the 3x3x3 section array
    public static int getLocalSectionIndex(int x, int y, int z) {
        return y << TABLE_BITS << TABLE_BITS | z << TABLE_BITS | x;
    }

    // Index into the 3x3 chunk array
    public static int getLocalChunkIndex(int x, int z) {
        return z << TABLE_BITS | x;
    }
}

