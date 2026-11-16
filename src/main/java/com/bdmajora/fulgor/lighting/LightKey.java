package com.bdmajora.fulgor.lighting;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.Vec3i;

// Packed position and light type of a light update.
final class LightKey {
    // Brightest light level.
    static final int MAX_LIGHT = 15;

    // Bits of x.
    static final int L_X = 26;
    // Bits of y.
    static final int L_Y = 8;
    // Bits of z.
    static final int L_Z = 26;
    // Bits of the light level.
    static final int L_L = 4;

    // Shift of z.
    static final int S_Z = 0;
    // Shift of x.
    static final int S_X = S_Z + L_Z;
    // Shift of y.
    static final int S_Y = S_X + L_X;
    // Shift of the light level.
    static final int S_L = S_Y + L_Y;

    // Mask of x.
    static final long M_X = (1L << L_X) - 1;
    // Mask of y.
    static final long M_Y = (1L << L_Y) - 1;
    // Mask of z.
    static final long M_Z = (1L << L_Z) - 1;
    // Mask of the light level.
    static final long M_L = (1L << L_L) - 1;
    // Mask of the position bits.
    static final long M_POS = (M_Y << S_Y) | (M_X << S_X) | (M_Z << S_Z);

    // Bit that flags a y outside the world.
    static final long Y_CHECK = 1L << (S_Y + L_Y);

    // Mask of the chunk bits.
    static final long M_CHUNK = ((M_X >> 4) << (4 + S_X)) | ((M_Z >> 4) << (4 + S_Z));

    // Key offsets of the six neighbours.
    static final long[] NEIGHBOR_SHIFTS = new long[6];

    static {
        for (int i = 0; i < 6; i++) {
            Vec3i offset = EnumFacing.VALUES[i].getDirectionVec();
            NEIGHBOR_SHIFTS[i] = ((long) offset.getY() << S_Y)
                    | ((long) offset.getX() << S_X)
                    | ((long) offset.getZ() << S_Z);
        }
    }

    // Prevents instantiation
    private LightKey() {
    }

    // Writes the position of a key into a mutable position.
    static MutableBlockPos decode(MutableBlockPos pos, long key) {
        return pos.setPos(
                (int) (key >> S_X & M_X) - (1 << L_X - 1),
                (int) (key >> S_Y & M_Y),
                (int) (key >> S_Z & M_Z) - (1 << L_Z - 1));
    }

    // Packs a position into a key.
    static long encode(BlockPos pos) {
        return ((long) pos.getY() << S_Y)
                | ((long) pos.getX() + (1 << L_X - 1) << S_X)
                | ((long) pos.getZ() + (1 << L_Z - 1) << S_Z);
    }

    // Returns the chunk x of a key.
    static int chunkX(long key) {
        return ((int) (key >> S_X & M_X) - (1 << L_X - 1)) >> 4;
    }

    // Returns the chunk z of a key.
    static int chunkZ(long key) {
        return ((int) (key >> S_Z & M_Z) - (1 << L_Z - 1)) >> 4;
    }
}
