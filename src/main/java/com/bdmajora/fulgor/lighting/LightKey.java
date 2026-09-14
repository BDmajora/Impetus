package com.bdmajora.fulgor.lighting;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.Vec3i;

// The packed position key shared by the engine's queues and every propagator: [light(4)][y(8)][x(26)][z(26)], x/z biased so a neighbour offset is a plain addition
final class LightKey {
    static final int MAX_LIGHT = 15;

    // Layout parameters: length of each bit segment.
    static final int L_X = 26;
    static final int L_Y = 8;
    static final int L_Z = 26;
    static final int L_L = 4;

    // Bit segment shifts.
    static final int S_Z = 0;
    static final int S_X = S_Z + L_Z;
    static final int S_Y = S_X + L_X;
    static final int S_L = S_Y + L_Y;

    // Bit segment masks.
    static final long M_X = (1L << L_X) - 1;
    static final long M_Y = (1L << L_Y) - 1;
    static final long M_Z = (1L << L_Z) - 1;
    static final long M_L = (1L << L_L) - 1;
    static final long M_POS = (M_Y << S_Y) | (M_X << S_X) | (M_Z << S_Z);

    // Set when a neighbour offset carried out of the y field, i.e. stepped outside the world
    static final long Y_CHECK = 1L << (S_Y + L_Y);

    // Isolates the chunk a position belongs to, so the chunk lookup can be skipped when it repeats
    static final long M_CHUNK = ((M_X >> 4) << (4 + S_X)) | ((M_Z >> 4) << (4 + S_Z));

    // Encoded offsets for the six neighbours, added directly to an encoded position
    static final long[] NEIGHBOR_SHIFTS = new long[6];

    static {
        for (int i = 0; i < 6; i++) {
            Vec3i offset = EnumFacing.VALUES[i].getDirectionVec();
            NEIGHBOR_SHIFTS[i] = ((long) offset.getY() << S_Y)
                    | ((long) offset.getX() << S_X)
                    | ((long) offset.getZ() << S_Z);
        }
    }

    private LightKey() {
    }

    // Unpacks a queue key back to a position, undoing the x/z bias
    static MutableBlockPos decode(MutableBlockPos pos, long key) {
        return pos.setPos(
                (int) (key >> S_X & M_X) - (1 << L_X - 1),
                (int) (key >> S_Y & M_Y),
                (int) (key >> S_Z & M_Z) - (1 << L_Z - 1));
    }

    // Packs a position into a queue key; the bias keeps negative x/z from touching the sign bit
    static long encode(BlockPos pos) {
        return ((long) pos.getY() << S_Y)
                | ((long) pos.getX() + (1 << L_X - 1) << S_X)
                | ((long) pos.getZ() + (1 << L_Z - 1) << S_Z);
    }

    // Chunk x of a key's position
    static int chunkX(long key) {
        return ((int) (key >> S_X & M_X) - (1 << L_X - 1)) >> 4;
    }

    // Chunk z of a key's position
    static int chunkZ(long key) {
        return ((int) (key >> S_Z & M_Z) - (1 << L_Z - 1)) >> 4;
    }
}
