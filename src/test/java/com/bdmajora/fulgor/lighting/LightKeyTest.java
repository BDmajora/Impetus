package com.bdmajora.fulgor.lighting;

import com.bdmajora.testing.Mc;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightKeyTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void positionsSurviveTheRoundTripIncludingNegativeCoordinates() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockPos pos : new BlockPos[] {
                new BlockPos(0, 0, 0),
                new BlockPos(1, 255, 2),
                new BlockPos(-1000, 64, -2000),
                new BlockPos(29999999, 128, -29999999)}) {
            long key = LightKey.encode(pos);
            assertEquals(pos, LightKey.decode(cursor, key).toImmutable());
            assertEquals(pos.getX() >> 4, LightKey.chunkX(key));
            assertEquals(pos.getZ() >> 4, LightKey.chunkZ(key));
        }
    }

    @Test
    void neighbourOffsetsAreAPlainAddition() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos pos = new BlockPos(-33, 70, 17);
        long key = LightKey.encode(pos);
        for (EnumFacing facing : EnumFacing.VALUES) {
            long neighbour = key + LightKey.NEIGHBOR_SHIFTS[facing.ordinal()];
            assertEquals(pos.offset(facing), LightKey.decode(cursor, neighbour).toImmutable());
        }
        // Stepping below the world carries out of the y field, which is what the engine tests for
        long bottom = LightKey.encode(new BlockPos(0, 0, 0)) + LightKey.NEIGHBOR_SHIFTS[EnumFacing.DOWN.ordinal()];
        assertTrue((bottom & LightKey.Y_CHECK) != 0);
        long top = LightKey.encode(new BlockPos(0, 255, 0)) + LightKey.NEIGHBOR_SHIFTS[EnumFacing.UP.ordinal()];
        assertTrue((top & LightKey.Y_CHECK) != 0);
        long inside = LightKey.encode(new BlockPos(0, 128, 0)) + LightKey.NEIGHBOR_SHIFTS[EnumFacing.UP.ordinal()];
        assertEquals(0, inside & LightKey.Y_CHECK);
    }

    @Test
    void theChunkMaskIsolatesTheColumn() {
        long inside = LightKey.encode(new BlockPos(17, 64, 33));
        long alsoInside = LightKey.encode(new BlockPos(31, 200, 47));
        long elsewhere = LightKey.encode(new BlockPos(48, 64, 33));
        assertEquals(inside & LightKey.M_CHUNK, alsoInside & LightKey.M_CHUNK);
        assertNotEquals(inside & LightKey.M_CHUNK, elsewhere & LightKey.M_CHUNK);
        assertEquals(15, LightKey.MAX_LIGHT);
        // The light field sits above the position, so a level never disturbs the coordinates
        long withLight = inside | (7L << LightKey.S_L);
        assertEquals(inside, withLight & LightKey.M_POS);
        assertEquals(7L, (withLight >> LightKey.S_L) & LightKey.M_L);
    }
}
