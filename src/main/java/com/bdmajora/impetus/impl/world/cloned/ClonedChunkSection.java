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

// Immutable snapshot of a chunk section's blocks/biomes/light, taken off-thread so render code isn't touching live chunk data
public class ClonedChunkSection {
    private static final ExtendedBlockStorage EMPTY_SECTION = new ExtendedBlockStorage(0, false);

    private final Short2ObjectMap<TileEntity> blockEntities;

    private final ExtendedBlockStorage data;
    @Getter
    private final FluidStateStorage fluidData;

    private final Biome[] biomeData;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    private final SectionPos sectionPos;

    // The live chunk this copy came from, for callers that must reach it (Fluidlogged resolves tile entities through the chunk, not the block access)
    private final Chunk chunk;

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

    // Resolves the chunk's biome array so biome colour never touches the live chunk; through the chunk in hand rather than World#getBiome, which re-looks the chunk up per column
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

    // Section-local read
    public IBlockState getBlockState(int x, int y, int z) {
        return data.get(x, y, z);
    }

    // Column-local biome read
    public Biome getBiomeForNoiseGen(int x, int z) {
        return this.biomeData[x | z << 4];
    }

    // The copied biome array
    public Biome[] getBiomeData() {
        return this.biomeData;
    }

    // From the copied tile entity map
    public TileEntity getBlockEntity(int x, int y, int z) {
        return this.blockEntities.get(packLocal(x, y, z));
    }

    // Which section this is a copy of
    public SectionPos getPosition() {
        return this.sectionPos;
    }

    // The live chunk behind the copy; only its identity is safe to rely on off the main thread
    public Chunk getChunk() {
        return this.chunk;
    }

    // From the copied light arrays
    public int getLightLevel(int x, int y, int z, EnumSkyBlock type) {
        NibbleArray lightArray = type == EnumSkyBlock.BLOCK ? this.data.getBlockLight() : this.data.getSkyLight();
        return lightArray != null ? lightArray.get(x, y, z) : type.defaultLightValue;
    }

    // Null for an empty section, which callers treat as all air
    private static ExtendedBlockStorage getChunkSection(Chunk chunk, int y) {
        ExtendedBlockStorage section = null;

        var storageArray = chunk.getBlockStorageArray();

        if (y >= 0 && y < storageArray.length) {
            section = storageArray[y];
        }

        return section;
    }

    // For the cache's eviction
    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    // Touched on every acquire
    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }

    // Packs local (0-15) x/y/z into one short to key the block entity map
    private static short packLocal(int x, int y, int z) {
        return (short) (x << 8 | z << 4 | y);
    }
}
