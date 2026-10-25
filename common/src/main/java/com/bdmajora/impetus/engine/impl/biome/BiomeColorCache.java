package com.bdmajora.impetus.engine.impl.biome;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.util.color.BoxBlur;
import com.bdmajora.impetus.engine.impl.util.color.BoxBlur.ColorBuffer;
import com.bdmajora.impetus.engine.impl.util.position.PositionalSupplier;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;

import java.util.Arrays;
import java.util.Objects;

// Caches biome colours for the region around a section with optional blending between neighbouring blocks
public abstract class BiomeColorCache<BIOME, RESOLVER> {
    // Extra blocks around the section and the largest blend radius
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int MAX_BLEND_RADIUS = 14;

    // Source of the biome at each block
    private final PositionalSupplier<BIOME> biomeData;

    // Slices for each resolver with one slice per layer
    private final Reference2ReferenceOpenHashMap<RESOLVER, Slice[]> slices;
    // Resolver and slices of the most recent lookup
    private RESOLVER lastResolver;
    private Slice[] lastSlices;

    // Counter that advances each time the region moves to a new section
    private long populateStamp;

    // Radius of the blend in blocks
    private final int blendRadius;

    // Scratch buffer for the blur or null when the radius is zero
    private final ColorBuffer tempColorBuffer;

    // Lowest block coordinates of the region as longs
    private long minX, minY, minZ;

    // Width of the region on X and Z and its height on Y
    private final int sizeXZ, sizeY;

    // Creates a cache for the biome source and blend radius centred on section zero
    public BiomeColorCache(PositionalSupplier<BIOME> biomeData, int blendRadius) {
        this.biomeData = Objects.requireNonNull(biomeData, "biomeData");
        // Clamps the blend radius between zero and the maximum
        this.blendRadius = Math.clamp(blendRadius, 0, MAX_BLEND_RADIUS);

        this.sizeXZ = 16 + ((NEIGHBOR_BLOCK_RADIUS + this.blendRadius) * 2);
        this.sizeY = 16 + (NEIGHBOR_BLOCK_RADIUS * 2);

        this.slices = new Reference2ReferenceOpenHashMap<>();

        this.tempColorBuffer = this.blendRadius > 0 ? new ColorBuffer(this.sizeXZ, this.sizeXZ) : null;

        // Centres the region on section zero
        this.recentre(0, 0, 0);
    }

    // Moves the region to the given section and marks every slice as stale
    public void update(SectionPos origin) {
        this.recentre(origin.minX(), origin.minY(), origin.minZ());
    }

    // Sets the region corner from the section origin and advances the populate stamp
    private void recentre(int originX, int originY, int originZ) {
        this.minX = (long) originX - NEIGHBOR_BLOCK_RADIUS - this.blendRadius;
        this.minY = (long) originY - NEIGHBOR_BLOCK_RADIUS;
        this.minZ = (long) originZ - NEIGHBOR_BLOCK_RADIUS - this.blendRadius;

        this.populateStamp++;
    }

    // Returns the colour at a block clamped into the region and resolves one cell when unblended or a whole layer when blended
    public int getColor(RESOLVER resolver, int blockX, int blockY, int blockZ) {
        int relX = Math.clamp(blockX - this.minX, 0, this.sizeXZ - 1);
        int relY = Math.clamp(blockY - this.minY, 0, this.sizeY - 1);
        int relZ = Math.clamp(blockZ - this.minZ, 0, this.sizeXZ - 1);

        var slice = this.sliceFor(resolver, relY);

        if (this.blendRadius == 0) {
            return this.cellColor(resolver, slice, relX, relY, relZ);
        }

        if (slice.lastPopulateStamp != this.populateStamp) {
            this.updateColorBuffers(relY, resolver, slice);
        }

        return slice.buffer.get(relX, relZ);
    }

    // Returns the slice for the resolver and layer and creates it on first use
    private Slice sliceFor(RESOLVER resolver, int relY) {
        var layers = this.lastSlices;

        if (layers == null || resolver != this.lastResolver) {
            layers = this.slices.get(resolver);

            if (layers == null) {
                layers = new Slice[this.sizeY];
                this.slices.put(resolver, layers);
            }

            this.lastResolver = resolver;
            this.lastSlices = layers;
        }

        var slice = layers[relY];

        if (slice == null) {
            slice = layers[relY] = new Slice(this.sizeXZ, this.blendRadius == 0);
        }

        return slice;
    }

    // Returns the colour of the biome at the given world position
    protected abstract int resolveColor(RESOLVER resolver, BIOME biome, int worldX, int worldY, int worldZ);

    // Resolves the colour at a position relative to the region
    private int resolveAt(RESOLVER resolver, int relX, int relY, int relZ) {
        int worldX = (int) (this.minX + relX);
        int worldY = (int) (this.minY + relY);
        int worldZ = (int) (this.minZ + relZ);

        return this.resolveColor(resolver, this.biomeData.getAt(worldX, worldY, worldZ), worldX, worldY, worldZ);
    }

    // Returns the colour of one cell and resolves it only when it has not been resolved since the region last moved
    private int cellColor(RESOLVER resolver, Slice slice, int relX, int relY, int relZ) {
        var resolved = slice.resolved;

        if (slice.lastPopulateStamp != this.populateStamp) {
            Arrays.fill(resolved, 0L);
            slice.lastPopulateStamp = this.populateStamp;
        }

        int index = ColorBuffer.getIndex(relX, relZ, this.sizeXZ);
        long bit = 1L << index;

        if ((resolved[index >>> 6] & bit) == 0) {
            slice.buffer.set(relX, relZ, this.resolveAt(resolver, relX, relY, relZ));
            // Marks the cell as resolved after its colour is stored
            resolved[index >>> 6] |= bit;
        }

        return slice.buffer.get(relX, relZ);
    }

    // Resolves every colour in the layer and blurs the layer by the blend radius
    private void updateColorBuffers(int relY, RESOLVER resolver, Slice slice) {
        var buffer = slice.buffer;

        for (int relZ = 0; relZ < this.sizeXZ; relZ++) {
            for (int relX = 0; relX < this.sizeXZ; relX++) {
                buffer.set(relX, relZ, this.resolveAt(resolver, relX, relY, relZ));
            }
        }

        // Blurs the layer in place
        BoxBlur.blur(buffer, this.tempColorBuffer, this.blendRadius);

        slice.lastPopulateStamp = this.populateStamp;
    }

    // Colours of one layer for one resolver
    private static final class Slice {
        private final ColorBuffer buffer;
        // One bit per cell resolved since the region last moved or null for a blended slice
        private final long[] resolved;
        // Populate stamp the slice was last filled for
        private long lastPopulateStamp;

        // Creates the colour buffer and the resolved bits for an unblended slice
        private Slice(int size, boolean perCell) {
            this.buffer = new ColorBuffer(size, size);
            this.resolved = perCell ? new long[((size * size) + 63) >>> 6] : null;
        }
    }
}