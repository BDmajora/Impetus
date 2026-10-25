package com.bdmajora.impetus.impl.world.biome;

import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import com.bdmajora.impetus.impl.world.WorldSlice;
import com.bdmajora.testing.Mc;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeColorHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BiomeColorCacheTest {
    private record Seen(Biome biome, BlockPos pos, int x, int y, int z) {}

    @BeforeAll
    static void bootstrap() {
        // WorldSlice reads Blocks as it initialises
        Mc.bootstrap();
    }

    private static WorldSlice forestAt37() {
        WorldSlice slice = mock(WorldSlice.class);
        when(slice.getBiome(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int) call.getArgument(0) == 37 ? Biomes.FOREST : Biomes.PLAINS);
        return slice;
    }

    // Coordinates copied at call time, since the cursor is reused
    private static BiomeColorHelper.ColorResolver recording(List<Seen> seen) {
        return (biome, pos) -> {
            seen.add(new Seen(biome, pos, pos.getX(), pos.getY(), pos.getZ()));
            return biome == Biomes.FOREST ? 0x336699 : 0x112233;
        };
    }

    @Test
    void resolvesTheSliceBiomeAtTheWorldPosition() {
        List<Seen> seen = new ArrayList<>();
        BiomeColorHelper.ColorResolver resolver = recording(seen);
        BiomeColorCache cache = new BiomeColorCache(forestAt37(), 0);
        cache.update(new SectionPos(2, 4, -1));
        assertEquals(0x336699, cache.getColor(resolver, 37, 70, -9));
        assertEquals(0x112233, cache.getColor(resolver, 38, 70, -9));
        assertEquals(0x336699, cache.getColor(resolver, 37, 70, -9));
        assertEquals(2, seen.size());
        assertEquals(new Seen(Biomes.FOREST, seen.get(0).pos(), 37, 70, -9), seen.get(0));
        assertEquals(new Seen(Biomes.PLAINS, seen.get(0).pos(), 38, 70, -9), seen.get(1));
    }

    @Test
    void aBlendedLayerReusesOneCursor() {
        List<Seen> seen = new ArrayList<>();
        BiomeColorCache cache = new BiomeColorCache(forestAt37(), 1);
        cache.update(new SectionPos(2, 4, -1));
        cache.getColor(recording(seen), 37, 70, -9);
        // 16 + (2 + 1) * 2 cells a side, all at the asked y
        assertEquals(22 * 22, seen.size());
        assertTrue(seen.stream().allMatch(s -> s.pos() == seen.get(0).pos() && s.y() == 70));
        assertEquals(29, seen.stream().mapToInt(Seen::x).min().orElseThrow());
        assertEquals(50, seen.stream().mapToInt(Seen::x).max().orElseThrow());
        assertEquals(-19, seen.stream().mapToInt(Seen::z).min().orElseThrow());
        assertEquals(2, seen.stream().mapToInt(Seen::z).max().orElseThrow());
    }
}
