package com.bdmajora.impetus.impl.world.cloned;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidStateStorage;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;

import java.util.Map;

// Copy of one chunk section for meshing off thread.
public class ClonedChunkSection {
    // Storage used for a missing section.
    private static final ExtendedBlockStorage EMPTY_SECTION = new ExtendedBlockStorage(0, false);

    // Block entities by packed local position.
    private final Short2ObjectMap<TileEntity> blockEntities;

    // Copied block storage.
    private final ExtendedBlockStorage data;
    // Fluid states or null.
    @Getter
    private final FluidStateStorage fluidData;

    // Biomes of the columns.
    private final Biome[] biomeData;

    // Time of the last use.
    private long lastUsedTimestamp = Long.MAX_VALUE;

    // Position of the section.
    private final SectionPos sectionPos;

    // Chunk of the section.
    private final Chunk chunk;

    // Creates a cloned chunk section from its world and chunk and x and y and z.
    ClonedChunkSection(World world, Chunk chunk, int x, int y, int z) {
        this.blockEntities = new Short2ObjectOpenHashMap<>();
        this.sectionPos = new SectionPos(x, y, z);
        this.chunk = chunk;

        ExtendedBlockStorage section = getChunkSection(chunk, y);

        if (section == Chunk.NULL_BLOCK_STORAGE) {
            section = EMPTY_SECTION;
        }

        this.data = section;
        if (FluidloggedCompat.IS_LOADED) {
            this.fluidData = new FluidStateStorage(chunk, y << 4);
        } else {
            this.fluidData = null;
        }
        this.biomeData = new Biome[chunk.getBiomeArray().length];

        if (!chunk.getTileEntityMap().isEmpty()) {
            StructureBoundingBox box = new StructureBoundingBox(this.sectionPos.minX(), this.sectionPos.minY(), this.sectionPos.minZ(),
                    this.sectionPos.maxX(), this.sectionPos.maxY(), this.sectionPos.maxZ());

            for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
                BlockPos entityPos = entry.getKey();

                if (box.isVecInside(entityPos)) {
                    this.blockEntities.put(packLocal(entityPos.getX() & 15, entityPos.getY() & 15, entityPos.getZ() & 15), entry.getValue());
                }
            }
        }

        populateBiomeData(chunk, x, z, world);
    }

    // Copies the biomes of a chunk.
    private void populateBiomeData(Chunk chunk, int chunkX, int chunkZ, World world) {
        BlockPos.MutableBlockPos biomePos = new BlockPos.MutableBlockPos();
        var provider = world.getBiomeProvider();

        chunkX <<= 4;
        chunkZ <<= 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                biomePos.setPos(chunkX + x, 100, chunkZ + z);
                this.biomeData[z << 4 | x] = chunk.getBiome(biomePos, provider);
            }
        }
    }

    // Returns the block state.
    public IBlockState getBlockState(int x, int y, int z) {
        return data.get(x, y, z);
    }

    // Returns the biome of a column.
    public Biome getBiomeForNoiseGen(int x, int z) {
        return this.biomeData[x | z << 4];
    }

    // Returns the biome data.
    public Biome[] getBiomeData() {
        return this.biomeData;
    }

    // Returns the block entity.
    public TileEntity getBlockEntity(int x, int y, int z) {
        return this.blockEntities.get(packLocal(x, y, z));
    }

    // Returns the position.
    public SectionPos getPosition() {
        return this.sectionPos;
    }

    // Returns the chunk.
    public Chunk getChunk() {
        return this.chunk;
    }

    // Returns the light level.
    public int getLightLevel(int x, int y, int z, EnumSkyBlock type) {
        NibbleArray lightArray = type == EnumSkyBlock.BLOCK ? this.data.getBlockLight() : this.data.getSkyLight();
        return lightArray != null ? lightArray.get(x, y, z) : type.defaultLightValue;
    }

    // Returns a section of a chunk or the empty storage.
    private static ExtendedBlockStorage getChunkSection(Chunk chunk, int y) {
        ExtendedBlockStorage section = null;

        var storageArray = chunk.getBlockStorageArray();

        if (y >= 0 && y < storageArray.length) {
            section = storageArray[y];
        }

        return section;
    }

    // Returns the last used timestamp.
    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    // Stores the last used timestamp.
    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }

    // Packs a local position into a short.
    private static short packLocal(int x, int y, int z) {
        return (short) (x << 8 | z << 4 | y);
    }
}
