package com.bdmajora.extras.client.culling;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Opacity bits of the loaded blocks that the worker thread ray-casts against.
public final class OcclusionGrid {
    // Bits of a section that holds no opaque block.
    static final long[] CLEAR = new long[0];
    // Number of longs in the bits of one section.
    static final int SECTION_LONGS = 64;
    // Number of sections in a column.
    static final int SECTIONS = 16;

    // Columns of loaded chunks by chunk key.
    private final Long2ObjectOpenHashMap<Column> columns = new Long2ObjectOpenHashMap<>();
    // Key of the column found by the last lookup.
    private long lastKey = Long.MIN_VALUE;
    // Column found by the last lookup or null.
    private Column lastColumn;
    // Number of sections built since the grid was made.
    private long sectionsBuilt;

    // Loaded chunk and the lazily built bits of its sections.
    static final class Column {
        // Chunk that holds the blocks.
        final Chunk chunk;
        // Bits of each section or null until a ray needs them.
        final long[][] sections = new long[SECTIONS][];

        // Creates an unbuilt column of the chunk.
        Column(Chunk chunk) {
            this.chunk = chunk;
        }

        // Forgets the bits of the sections in the mask.
        void invalidate(int mask) {
            for (int sy = 0; sy < SECTIONS; sy++) {
                if ((mask & (1 << sy)) != 0) {
                    this.sections[sy] = null;
                }
            }
        }
    }

    // Returns the index of the brick long that holds the block.
    static int brickIndex(int x, int y, int z) {
        return ((x >> 2) & 3) | (((z >> 2) & 3) << 2) | (((y >> 2) & 3) << 4);
    }

    // Returns the bit of the block inside its brick long.
    static int bitIndex(int x, int y, int z) {
        return (x & 3) | ((z & 3) << 2) | ((y & 3) << 4);
    }

    // Builds the opacity bits of a block storage section.
    static long[] build(ExtendedBlockStorage storage) {
        if (storage == null || storage.isEmpty()) {
            return CLEAR;
        }
        long[] bits = null;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (storage.get(x, y, z).isOpaqueCube()) {
                        if (bits == null) {
                            bits = new long[SECTION_LONGS];
                        }
                        bits[brickIndex(x, y, z)] |= 1L << bitIndex(x, y, z);
                    }
                }
            }
        }
        return bits == null ? CLEAR : bits;
    }

    // Returns the column of the chunk or null when it is not loaded.
    private Column column(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (key != this.lastKey) {
            this.lastColumn = this.columns.get(key);
            this.lastKey = key;
        }
        return this.lastColumn;
    }

    // Returns the bits of a section or null when its chunk is not loaded.
    public long[] sectionBits(int sectionX, int sectionY, int sectionZ) {
        if (sectionY < 0 || sectionY >= SECTIONS) {
            return CLEAR;
        }
        Column column = this.column(sectionX, sectionZ);
        if (column == null) {
            return null;
        }
        long[] bits = column.sections[sectionY];
        if (bits == null) {
            ExtendedBlockStorage[] storages = column.chunk.getBlockStorageArray();
            bits = build(sectionY < storages.length ? storages[sectionY] : null);
            column.sections[sectionY] = bits;
            this.sectionsBuilt++;
        }
        return bits;
    }

    // Returns whether the block is an opaque cube.
    public boolean isOpaque(int x, int y, int z) {
        if (y < 0 || y >= SECTIONS * 16) {
            return false;
        }
        long[] bits = this.sectionBits(x >> 4, y >> 4, z >> 4);
        if (bits == null || bits.length == 0) {
            return false;
        }
        return (bits[brickIndex(x, y, z)] >>> bitIndex(x, y, z) & 1L) != 0L;
    }

    // Applies the changes the main thread queued.
    public void apply(OcclusionInbox.Batch batch) {
        if (batch.reset() != null) {
            this.clear();
            for (Long2ObjectMap.Entry<Chunk> entry : batch.reset().long2ObjectEntrySet()) {
                this.columns.put(entry.getLongKey(), new Column(entry.getValue()));
            }
        }
        for (Long2ObjectMap.Entry<Chunk> entry : batch.chunks().long2ObjectEntrySet()) {
            Chunk chunk = entry.getValue();
            long key = entry.getLongKey();
            Column known = this.columns.get(key);
            if (chunk == null) {
                this.columns.remove(key);
            } else if (known != null && known.chunk == chunk) {
                known.invalidate(-1);
            } else {
                this.columns.put(key, new Column(chunk));
            }
        }
        for (Long2IntMap.Entry entry : batch.sections().long2IntEntrySet()) {
            Column known = this.columns.get(entry.getLongKey());
            if (known != null) {
                known.invalidate(entry.getIntValue());
            }
        }
        this.lastKey = Long.MIN_VALUE;
        this.lastColumn = null;
    }

    // Drops every column.
    public void clear() {
        this.columns.clear();
        this.lastKey = Long.MIN_VALUE;
        this.lastColumn = null;
    }

    // Returns the number of known columns.
    public int columnCount() {
        return this.columns.size();
    }

    // Returns the number of sections built so far.
    public long sectionsBuilt() {
        return this.sectionsBuilt;
    }
}
