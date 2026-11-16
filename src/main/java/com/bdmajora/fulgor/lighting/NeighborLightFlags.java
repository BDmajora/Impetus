package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.ChunkLightingData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;

// Saved flags of pending neighbor light checks.
public final class NeighborLightFlags {
    // Key of the flags in the chunk tag.
    public static final String NBT_KEY = "NeighborLightChecks";

    // Prevents instantiation
    private NeighborLightFlags() {
    }

    // Choices of the boundary facing setting.
    public enum BoundaryFacing {
        // Value that selects in.
        IN,
        // Value that selects out.
        OUT;

        // Returns the opposite.
        public BoundaryFacing getOpposite() {
            return this == IN ? OUT : IN;
        }
    }

    // Returns the flag index of a boundary by chunk offset.
    public static int index(EnumSkyBlock lightType, int xOffset, int zOffset,
                           // Axis direction and boundary facing.
                           EnumFacing.AxisDirection axisDirection, BoundaryFacing boundaryFacing) {
        return (lightType == EnumSkyBlock.BLOCK ? 0 : 16)
                | ((xOffset + 1) << 2)
                | ((zOffset + 1) << 1)
                | (axisDirection.getOffset() + 1)
                | boundaryFacing.ordinal();
    }

    // Returns the flag index of a boundary by facing.
    public static int index(EnumSkyBlock lightType, EnumFacing dir,
                            // Axis direction and boundary facing.
                            EnumFacing.AxisDirection axisDirection, BoundaryFacing boundaryFacing) {
        return index(lightType, dir.getXOffset(), dir.getZOffset(), axisDirection, boundaryFacing);
    }

    // Returns the axis direction of a facing at a position.
    public static EnumFacing.AxisDirection axisDirection(EnumFacing dir, int x, int z) {
        return ((dir.getAxis() == EnumFacing.Axis.X ? z : x) & 15) < 8
                ? EnumFacing.AxisDirection.NEGATIVE
                : EnumFacing.AxisDirection.POSITIVE;
    }

    // Sets the flags of a boundary for the given sections.
    public static void flagBoundary(Chunk chunk, short sectionMask, EnumSkyBlock lightType, EnumFacing dir,
                                    // Axis direction and boundary facing.
                                    EnumFacing.AxisDirection axisDirection, BoundaryFacing boundaryFacing) {
        ChunkLightingData data = (ChunkLightingData) chunk;

        data.fulgor$initNeighborLightChecks();
        data.fulgor$getNeighborLightChecks()[index(lightType, dir, axisDirection, boundaryFacing)] |= sectionMask;

        chunk.markDirty();
    }

    // Writes the flags of a chunk into its tag.
    public static void write(Chunk chunk, NBTTagCompound compound) {
        short[] flags = ((ChunkLightingData) chunk).fulgor$getNeighborLightChecks();

        if (flags == null) {
            return;
        }

        boolean empty = true;
        NBTTagList list = new NBTTagList();

        for (short mask : flags) {
            list.appendTag(new NBTTagShort(mask));

            if (mask != 0) {
                empty = false;
            }
        }

        if (!empty) {
            compound.setTag(NBT_KEY, list);
        }
    }

    // Reads the flags of a chunk from its tag.
    public static void read(Chunk chunk, NBTTagCompound compound) {
        if (!compound.hasKey(NBT_KEY, 9)) {
            return;
        }

        NBTTagList list = compound.getTagList(NBT_KEY, 2);

        if (list.tagCount() != Fulgor.BOUNDARY_FLAG_COUNT) {
            Fulgor.LOGGER.warn("Chunk field {} had invalid length, ignoring it (chunk coordinates: {} {})",
                    NBT_KEY, chunk.x, chunk.z);
            return;
        }

        ChunkLightingData data = (ChunkLightingData) chunk;
        data.fulgor$initNeighborLightChecks();

        short[] flags = data.fulgor$getNeighborLightChecks();

        for (int i = 0; i < Fulgor.BOUNDARY_FLAG_COUNT; i++) {
            flags[i] = ((NBTTagShort) list.get(i)).getShort();
        }
    }
}
