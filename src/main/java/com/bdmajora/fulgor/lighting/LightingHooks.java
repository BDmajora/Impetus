package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.fulgor.lighting.NeighborLightFlags.BoundaryFacing;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Hooks that schedule light checks at chunk edges.
public final class LightingHooks {
    // Light types.
    private static final EnumSkyBlock[] LIGHT_TYPES = EnumSkyBlock.values();
    // Axis directions.
    private static final EnumFacing.AxisDirection[] AXIS_DIRECTIONS = EnumFacing.AxisDirection.values();

    // Prevents instantiation
    private LightingHooks() {
    }

    // Relights the sky light of one column range.
    public static void relightSkylightColumn(World world, Chunk chunk, int x, int z, int height1, int height2) {
        int yMin = Math.min(height1, height2);
        int yMax = Math.max(height1, height2) - 1;

        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

        int xBase = (chunk.x << 4) + x;
        int zBase = (chunk.z << 4) + z;

        scheduleRelightChecksForColumn(world, EnumSkyBlock.SKY, xBase, zBase, yMin, yMax);

        if (yMin < 0 || yMax > 255) {
            yMin = Math.max(0, yMin);
            yMax = Math.min(255, yMax);
        }

        if (yMin >> 4 < sections.length && sections[yMin >> 4] == Chunk.NULL_BLOCK_STORAGE && yMin > 0) {
            world.checkLightFor(EnumSkyBlock.SKY, new BlockPos(xBase, yMin - 1, zBase));
        }

        short emptySections = 0;

        for (int section = yMax >> 4; section >= yMin >> 4; section--) {
            if (sections[section] == Chunk.NULL_BLOCK_STORAGE) {
                emptySections |= (short) (1 << section);
            }
        }

        if (emptySections == 0) {
            return;
        }

        for (EnumFacing dir : EnumFacing.HORIZONTALS) {
            int xOffset = dir.getXOffset();
            int zOffset = dir.getZOffset();

            boolean neighborColumnExists = (((x + xOffset) | (z + zOffset)) & 16) == 0
                    || world.getChunkProvider().getLoadedChunk(chunk.x + xOffset, chunk.z + zOffset) != null;

            if (neighborColumnExists) {
                for (int section = yMax >> 4; section >= yMin >> 4; section--) {
                    if ((emptySections & (1 << section)) != 0) {
                        scheduleRelightChecksForColumn(world, EnumSkyBlock.SKY, xBase + xOffset, zBase + zOffset,
                                section << 4, (section << 4) + 15);
                    }
                }
            } else {
                NeighborLightFlags.flagBoundary(chunk, emptySections, EnumSkyBlock.SKY, dir,
                        NeighborLightFlags.axisDirection(dir, x, z), BoundaryFacing.OUT);
            }
        }
    }

    // Schedules light checks over a box.
    public static void scheduleRelightChecksForArea(World world, EnumSkyBlock lightType,
                                                    // Lowest corner of the box.
                                                    int xMin, int yMin, int zMin,
                                                    // Highest corner of the box.
                                                    int xMax, int yMax, int zMax) {
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                scheduleRelightChecksForColumn(world, lightType, x, z, yMin, yMax);
            }
        }
    }

    // Schedules light checks over a column range.
    private static void scheduleRelightChecksForColumn(World world, EnumSkyBlock lightType,
                                                       // Column and height range.
                                                       int x, int z, int yMin, int yMax) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = yMin; y <= yMax; y++) {
            world.checkLightFor(lightType, pos.setPos(x, y, z));
        }
    }

    // Schedules the flagged checks at the edges of a chunk.
    public static void scheduleRelightChecksForChunkBoundaries(World world, Chunk chunk) {
        for (EnumFacing dir : EnumFacing.HORIZONTALS) {
            int xOffset = dir.getXOffset();
            int zOffset = dir.getZOffset();

            Chunk neighbor = world.getChunkProvider().getLoadedChunk(chunk.x + xOffset, chunk.z + zOffset);

            if (neighbor == null) {
                continue;
            }

            for (EnumSkyBlock lightType : LIGHT_TYPES) {
                for (EnumFacing.AxisDirection axisDir : AXIS_DIRECTIONS) {
                    mergeFlags(lightType, chunk, neighbor, dir, axisDir);
                    mergeFlags(lightType, neighbor, chunk, dir.getOpposite(), axisDir);

                    scheduleRelightChecksForBoundary(world, chunk, neighbor, null, lightType, xOffset, zOffset, axisDir);
                    scheduleRelightChecksForBoundary(world, neighbor, chunk, null, lightType, -xOffset, -zOffset, axisDir);

                    scheduleRelightChecksForBoundary(world, neighbor, null, chunk, lightType,
                            zOffset != 0 ? axisDir.getOffset() : 0,
                            xOffset != 0 ? axisDir.getOffset() : 0,
                            dir.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE
                                    ? EnumFacing.AxisDirection.NEGATIVE
                                    : EnumFacing.AxisDirection.POSITIVE);
                }
            }
        }
    }

    // Moves flags of a boundary between two chunks.
    private static void mergeFlags(EnumSkyBlock lightType, Chunk inChunk, Chunk outChunk,
                                   // Facing of the edge.
                                   EnumFacing dir, EnumFacing.AxisDirection axisDir) {
        ChunkLightingData outData = (ChunkLightingData) outChunk;

        if (outData.fulgor$getNeighborLightChecks() == null) {
            return;
        }

        ChunkLightingData inData = (ChunkLightingData) inChunk;
        inData.fulgor$initNeighborLightChecks();

        int inIndex = NeighborLightFlags.index(lightType, dir, axisDir, BoundaryFacing.IN);
        int outIndex = NeighborLightFlags.index(lightType, dir.getOpposite(), axisDir, BoundaryFacing.OUT);

        inData.fulgor$getNeighborLightChecks()[inIndex] |= outData.fulgor$getNeighborLightChecks()[outIndex];
    }

    // Schedules light checks along one chunk boundary.
    private static void scheduleRelightChecksForBoundary(World world, Chunk chunk, Chunk neighbor, Chunk diagonal,
                                                         // Light type and chunk offset.
                                                         EnumSkyBlock lightType, int xOffset, int zOffset,
                                                         EnumFacing.AxisDirection axisDir) {
        ChunkLightingData data = (ChunkLightingData) chunk;

        if (data.fulgor$getNeighborLightChecks() == null) {
            return;
        }

        int flagIndex = NeighborLightFlags.index(lightType, xOffset, zOffset, axisDir, BoundaryFacing.IN);
        short flags = data.fulgor$getNeighborLightChecks()[flagIndex];

        if (flags == 0) {
            return;
        }

        if (neighbor == null) {
            neighbor = world.getChunkProvider().getLoadedChunk(chunk.x + xOffset, chunk.z + zOffset);

            if (neighbor == null) {
                return;
            }
        }

        if (diagonal == null) {
            diagonal = world.getChunkProvider().getLoadedChunk(
                    chunk.x + (zOffset != 0 ? axisDir.getOffset() : 0),
                    chunk.z + (xOffset != 0 ? axisDir.getOffset() : 0));

            if (diagonal == null) {
                return;
            }
        }

        int reverseIndex = NeighborLightFlags.index(lightType, -xOffset, -zOffset, axisDir, BoundaryFacing.OUT);

        data.fulgor$getNeighborLightChecks()[flagIndex] = 0;

        ChunkLightingData neighborData = (ChunkLightingData) neighbor;

        if (neighborData.fulgor$getNeighborLightChecks() != null) {
            neighborData.fulgor$getNeighborLightChecks()[reverseIndex] = 0;
        }

        chunk.markDirty();
        neighbor.markDirty();

        int xMin = chunk.x << 4;
        int zMin = chunk.z << 4;

        if ((xOffset | zOffset) > 0) {
            xMin += 15 * xOffset;
            zMin += 15 * zOffset;
        }

        if (axisDir == EnumFacing.AxisDirection.POSITIVE) {
            xMin += 8 * (zOffset & 1);
            zMin += 8 * (xOffset & 1);
        }

        int xMax = xMin + 7 * (zOffset & 1);
        int zMax = zMin + 7 * (xOffset & 1);

        for (int section = 0; section < 16; section++) {
            if ((flags & (1 << section)) != 0) {
                scheduleRelightChecksForArea(world, lightType, xMin, section << 4, zMin,
                        xMax, (section << 4) + 15, zMax);
            }
        }
    }

    // Initializes the chunk lighting.
    public static void initChunkLighting(World world, Chunk chunk) {
        int xBase = chunk.x << 4;
        int zBase = chunk.z << 4;

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(xBase, 0, zBase);

        try {
            if (!world.isAreaLoaded(pos.add(-16, 0, -16), pos.add(31, 255, 31), false)) {
                return;
            }

            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

            for (int i = 0; i < sections.length; i++) {
                ExtendedBlockStorage section = sections[i];

                if (section == Chunk.NULL_BLOCK_STORAGE) {
                    continue;
                }

                int yBase = i << 4;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            pos.setPos(xBase + x, yBase + y, zBase + z);

                            if (LightUtil.getLightValue(section.getData().get(x, y, z), world, pos, chunk) > 0) {
                                world.checkLightFor(EnumSkyBlock.BLOCK, pos);
                            }
                        }
                    }
                }
            }

            if (world.provider.hasSkyLight()) {
                ((ChunkLightingData) chunk).fulgor$setSkylightUpdated();
            }

            ((ChunkLightingData) chunk).fulgor$setLightInitialized(true);
        } finally {
            pos.release();
        }
    }

    // Lights a chunk and its boundaries.
    public static void checkChunkLighting(World world, Chunk chunk) {
        if (!((ChunkLightingData) chunk).fulgor$isLightInitialized()) {
            initChunkLighting(world, chunk);
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                Chunk neighbor = world.getChunkProvider().getLoadedChunk(chunk.x + x, chunk.z + z);

                if (neighbor == null || !((ChunkLightingData) neighbor).fulgor$isLightInitialized()) {
                    return;
                }
            }
        }

        chunk.setLightPopulated(true);
    }

    // Initializes the skylight for section.
    public static void initSkylightForSection(World world, Chunk chunk, ExtendedBlockStorage section) {
        if (!world.provider.hasSkyLight()) {
            return;
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunk.getHeightValue(x, z) > section.getYLocation()) {
                    continue;
                }

                for (int y = 0; y < 16; y++) {
                    section.setSkyLight(x, y, z, EnumSkyBlock.SKY.defaultLightValue);
                }
            }
        }
    }
}
