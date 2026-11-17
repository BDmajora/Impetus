package com.bdmajora.impetus.impl.world;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
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
import com.bdmajora.fulgor.FulgorRenderBridge;
import com.bdmajora.fulgor.lighting.FaceLightRules;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggingInference;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.world.biome.BiomeColorCache;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.bdmajora.impetus.impl.world.cloned.ChunkRenderContext;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSection;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSectionCache;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Block access over cloned sections around a section.
public class WorldSlice implements ImpetusBlockAccess {
    // Blocks along a section edge.
    private static final int SECTION_BLOCK_LENGTH = 16;

    // Blocks in a section.
    private static final int SECTION_BLOCK_COUNT = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;

    // Blocks read beyond the section on each side.
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // Neighbor chunk radius.
    private static final int NEIGHBOR_CHUNK_RADIUS = MathHelper.roundUp(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // Sections along a slice edge.
    private static final int SECTION_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // Table length.
    private static final int TABLE_LENGTH = MathHelper.smallestEncompassingPowerOfTwo(SECTION_LENGTH);

    // Table bits.
    private static final int TABLE_BITS = Integer.bitCount(TABLE_LENGTH - 1);

    // Empty block state.
    private static final IBlockState EMPTY_BLOCK_STATE = Blocks.AIR.getDefaultState();

    // Entries of the section tables.
    private static final int SECTION_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH * TABLE_LENGTH;

    // World the slice copies from.
    private final World world;
    // World type.
    private final WorldType worldType;
    // Whether the dimension has sky light.
    private final boolean hasSkyLight;
    // Default sky light value.
    private final int defaultSkyLightValue;

    // Flag set when Fulgor render lighting fixes are on.
    private final boolean fixRenderLighting = FulgorRenderBridge.fixRenderLighting();

    // Block states of each section.
    private final IBlockState[][] blockStatesArrays;

    // Fluid states of each section.
    private final Object[][] fluidStatesArrays;

    // Cloned sections of the slice.
    private ClonedChunkSection[] sections;

    // Biomes of each section.
    private final Biome[][] biomeCaches;

    // Biome color cache.
    private final BiomeColorCache biomeColorCache;

    // Origin of the slice in blocks.
    private int baseX, baseY, baseZ;

    // Section at the center of the slice.
    private SectionPos origin;

    // Block volume the slice covers.
    private StructureBoundingBox volume;

    // Reusable position for fallback reads.
    private final BlockPos.MutableBlockPos fallbackPos = new BlockPos.MutableBlockPos();

    // Sections cloned on demand outside the slice.
    private final Long2ReferenceMap<ClonedChunkSection> extraClonedSections = new Long2ReferenceOpenHashMap<>();

    // Block entities made for blocks that have none stored.
    private final Long2ReferenceMap<TileEntity> standInBlockEntities = new Long2ReferenceOpenHashMap<>();

    // Clones the sections around a section and returns null for an empty section.
    public static ChunkRenderContext prepare(World world, SectionPos origin, ClonedChunkSectionCache sectionCache) {
        FulgorRenderBridge.flushPendingLightUpdates(world);

        if (FluidloggedCompat.IS_LOADED) {
            FluidloggingInference.refresh();
        }

        Chunk chunk = world.getChunk(origin.x(), origin.z());
        ExtendedBlockStorage section = chunk.getBlockStorageArray()[origin.y()];

        if (section == null || FulgorRenderBridge.isEmptyOfBlocks(section)) {
            return null;
        }

        StructureBoundingBox volume = new StructureBoundingBox(origin.minX() - NEIGHBOR_BLOCK_RADIUS,
                origin.minY() - NEIGHBOR_BLOCK_RADIUS,
                origin.minZ() - NEIGHBOR_BLOCK_RADIUS,
                origin.maxX() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxY() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxZ() + NEIGHBOR_BLOCK_RADIUS);

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

    // Creates a world slice from its world.
    public WorldSlice(World world) {
        this.world = world;
        this.worldType = world.getWorldType();
        this.hasSkyLight = world.provider.hasSkyLight();
        this.defaultSkyLightValue = this.hasSkyLight ? EnumSkyBlock.SKY.defaultLightValue : 0;

        this.sections = new ClonedChunkSection[SECTION_TABLE_ARRAY_SIZE];
        this.blockStatesArrays = new IBlockState[SECTION_TABLE_ARRAY_SIZE][];
        this.biomeCaches = new Biome[SECTION_TABLE_ARRAY_SIZE][];
        this.biomeColorCache = new BiomeColorCache(this, ImpetusVintage.options().quality.legacyBiomeBlendRadius);
        this.fluidStatesArrays = FluidloggedCompat.IS_LOADED ? new Object[SECTION_TABLE_ARRAY_SIZE][] : null;

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

    // Copies the data.
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

                    unpackBlockData(this.blockStatesArrays[idx], section, context.getVolume());

                    if (FluidloggedCompat.IS_LOADED) {
                        this.unpackFluidData(this.fluidStatesArrays[idx], section, context.getVolume());
                    }
                }
            }
        }

        if (FluidloggedCompat.IS_LOADED && FluidloggingInference.isActive()) {
            FluidloggingInference.apply(this, this.baseX, this.baseY, this.baseZ,
                    this.volume.minX - this.baseX, this.volume.minY - this.baseY, this.volume.minZ - this.baseZ,
                    this.volume.maxX - this.baseX, this.volume.maxY - this.baseY, this.volume.maxZ - this.baseZ);
        }
    }

    // Returns the fluid state at slice coordinates.
    public Object getFluidStateRelative(int x, int y, int z) {
        return this.fluidStatesArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)][getLocalBlockIndex(x & 15, y & 15, z & 15)];
    }

    // Stores the fluid state relative.
    public void setFluidStateRelative(int x, int y, int z, Object state) {
        this.fluidStatesArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)][getLocalBlockIndex(x & 15, y & 15, z & 15)] = state;
    }

    // Forgets the extra sections and the stand in block entities.
    public void reset() {
        this.extraClonedSections.clear();
        this.standInBlockEntities.clear();
    }

    // Copies the block states of a cloned section.
    private static void unpackBlockData(IBlockState[] states, ClonedChunkSection section, StructureBoundingBox box) {
        SectionPos pos = section.getPosition();

        int minBlockX = Math.max(box.minX, pos.minX());
        int maxBlockX = Math.min(box.maxX, pos.maxX());

        int minBlockY = Math.max(box.minY, pos.minY());
        int maxBlockY = Math.min(box.maxY, pos.maxY());

        int minBlockZ = Math.max(box.minZ, pos.minZ());
        int maxBlockZ = Math.min(box.maxZ, pos.maxZ());

        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    states[getLocalBlockIndex(x & 15, y & 15, z & 15)] = section.getBlockState(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    // Copies the fluid states of a cloned section.
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

    // Returns whether a box holds a position.
    private static boolean blockBoxContains(StructureBoundingBox box, int x, int y, int z) {
        return x >= box.minX &&
                x <= box.maxX &&
                y >= box.minY &&
                y <= box.maxY &&
                z >= box.minZ &&
                z <= box.maxZ;
    }

    // Returns the block state.
    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    // Returns whether the copied block at a position is air.
    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState state = this.getBlockState(pos);
        return state.getBlock().isAir(state, this, pos);
    }

    // Returns the block state.
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

    // Returns the block state at slice coordinates.
    public IBlockState getBlockStateRelative(int x, int y, int z) {
        return this.blockStatesArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)]
                [getLocalBlockIndex(x & 15, y & 15, z & 15)];
    }

    // Returns the tile entity.
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return this.getBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    // Returns the block entity.
    public TileEntity getBlockEntity(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return null;
        }

        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        int sectionIdx = getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4);
        TileEntity entity = this.sections[sectionIdx].getBlockEntity(relX & 15, relY & 15, relZ & 15);
        if (entity != null) {
            return entity;
        }

        IBlockState state = this.blockStatesArrays[sectionIdx][getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
        if (!state.getBlock().hasTileEntity(state)) {
            return null;
        }
        return this.standInBlockEntity(x, y, z, state);
    }

    // Returns a block entity made for a block or null.
    private TileEntity standInBlockEntity(int x, int y, int z, IBlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        long key = pos.toLong();
        TileEntity standIn = this.standInBlockEntities.get(key);
        if (standIn != null || this.standInBlockEntities.containsKey(key)) {
            return standIn;
        }
        try {
            standIn = state.getBlock().createTileEntity(this.world, state);
        } catch (RuntimeException e) {
            standIn = null;
        }
        if (standIn != null) {
            standIn.setWorld(this.world);
            standIn.setPos(pos);
        }
        this.standInBlockEntities.put(key, standIn);

        World world = this.world;
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().world != world || !world.isBlockLoaded(pos)) {
                return;
            }
            IBlockState liveState = world.getBlockState(pos);
            Chunk chunk = world.getChunk(pos);
            if (!liveState.getBlock().hasTileEntity(liveState) || chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) != null) {
                return;
            }
            if (world.getTileEntity(pos) != null) {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        });
        return standIn;
    }

    // Returns the combined light.
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

    // Returns the light of one type at slice coordinates.
    private int getLightFor(EnumSkyBlock type, int relX, int relY, int relZ) {
        ClonedChunkSection section = this.sections[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)];

        return section.getLightLevel(relX & 15, relY & 15, relZ & 15, type);
    }

    // Returns the light of a block from its brightest neighbour.
    private int getLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos) {
        if(!this.hasSkyLight && type == EnumSkyBlock.SKY) {
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

        if (!state.useNeighborBrightness()) {
            return getLightFor(type, relX, relY, relZ);
        }
        int level = getLightFor(type, relX - 1, relY, relZ);
        level = Math.max(level, getLightFor(type, relX + 1, relY, relZ));
        level = Math.max(level, getLightFor(type, relX, relY + 1, relZ));
        level = Math.max(level, getLightFor(type, relX, relY - 1, relZ));
        level = Math.max(level, getLightFor(type, relX, relY, relZ + 1));
        return Math.max(level, getLightFor(type, relX, relY, relZ - 1));
    }

    // Returns the biome.
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

    // Returns the tint of a block from the biome color cache.
    @Override
    public int getBlockTint(BlockPos pos, BiomeColorHelper.ColorResolver resolver) {
        if(!blockBoxContains(this.volume, pos.getX(), pos.getY(), pos.getZ())) {
            return resolver.getColorAtPos(Biomes.PLAINS, pos);
        }

        return this.biomeColorCache.getColor(resolver, pos.getX(), pos.getY(), pos.getZ());
    }

    // Returns the strong power.
    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        IBlockState state = this.getBlockState(pos);
        return state.getBlock().getStrongPower(state, this, pos, direction);
    }

    // Returns the world type.
    @Override
    public WorldType getWorldType() {
        return this.worldType;
    }

    // Returns whether a side of the copied block is solid or the default outside the slice.
    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    // Returns the biome.
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

    // Returns the origin.
    public SectionPos getOrigin() {
        return this.origin;
    }

    // Returns the face shade of a direction.
    public float getBrightness(EnumFacing direction, boolean shaded) {
        if (!shaded) {
            return this.hasSkyLight ? 1.0f : 0.9f;
        }
        return LightUtil.diffuseLight(direction);
    }

    // Returns a cloned section for a position outside the slice.
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
        var sectionFuture = CompletableFuture.supplyAsync(() -> manager.getSectionCache().acquire(sX, sY, sZ), manager::scheduleAsyncTask);
        while (Minecraft.getMinecraft().world == this.world) {
            try {
                section = sectionFuture.get(500, TimeUnit.MILLISECONDS);
                break;
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to fetch fallback section", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sectionFuture.cancel(true);
                return null;
            } catch (TimeoutException ignored) {
            }
        }
        if (section != null) {
            this.extraClonedSections.put(key, section);
        }
        return section;
    }

    // Returns a block state outside the slice.
    private IBlockState getBlockStateFallback(int x, int y, int z) {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            this.fallbackPos.setPos(x, y, z);
            return this.world.getBlockState(this.fallbackPos);
        }
        ClonedChunkSection sectionSnapshot = this.fetchFallbackSectionForPos(x, y, z);
        return sectionSnapshot != null ? sectionSnapshot.getBlockState(x & 15, y & 15, z & 15) : EMPTY_BLOCK_STATE;
    }

    // Returns the fluid state.
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

    // Returns the world.
    @Override
    @Optional.Method(modid = FluidloggedCompat.MODID)
    public World getWorld() {
        return world;
    }

    // Returns the chunk.
    @Override
    @Optional.Method(modid = FluidloggedCompat.MODID)
    public Chunk getChunk(int chunkX, int chunkZ) {
        if (this.origin == null) {
            return null;
        }
        int relX = chunkX - (this.origin.x() - NEIGHBOR_CHUNK_RADIUS);
        int relZ = chunkZ - (this.origin.z() - NEIGHBOR_CHUNK_RADIUS);
        if (relX < 0 || relX >= SECTION_LENGTH || relZ < 0 || relZ >= SECTION_LENGTH) {
            return null;
        }
        ClonedChunkSection section = this.sections[getLocalSectionIndex(relX, NEIGHBOR_CHUNK_RADIUS, relZ)];
        return section != null ? section.getChunk() : null;
    }

    // Returns the index of a block in a section.
    public static int getLocalBlockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    // Returns the index of a section in the slice.
    public static int getLocalSectionIndex(int x, int y, int z) {
        return y << TABLE_BITS << TABLE_BITS | z << TABLE_BITS | x;
    }

    // Returns the index of a chunk in the slice.
    public static int getLocalChunkIndex(int x, int z) {
        return z << TABLE_BITS | x;
    }
}

