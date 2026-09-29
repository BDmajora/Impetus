package com.bdmajora.impetus.engine.impl.biome;

import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BiomeColorCacheTest {
    // Colour is the biome id itself, so a checkerboard of biomes blurs while a uniform one stays flat
    private static final class Cache extends BiomeColorCache<Integer, String> {
        final AtomicInteger resolves = new AtomicInteger();

        Cache(int radius, boolean checkerboard) {
            super((x, y, z) -> checkerboard ? ((x + z) & 1) * 0xFF : 0x80, radius);
        }

        @Override
        protected int resolveColor(String resolver, Integer biome, int worldX, int worldY, int worldZ) {
            resolves.incrementAndGet();
            return 0xFF000000 | biome;
        }
    }

    @Test
    void uniformSlicesSkipTheBlurAndAreCached() {
        Cache cache = new Cache(2, false);
        cache.update(new SectionPos(0, 0, 0));
        assertEquals(0xFF000080, cache.getColor("grass", 5, 5, 5));
        int after = cache.resolves.get();
        assertEquals(0xFF000080, cache.getColor("grass", 6, 5, 6));
        assertEquals(after, cache.resolves.get());
        assertEquals(0xFF000080, cache.getColor("grass", -100, 500, 100));
        cache.update(new SectionPos(1, 1, 1));
        cache.getColor("grass", 20, 20, 20);
        assertTrue(cache.resolves.get() > after);
    }

    @Test
    void checkerboardsBlurWhenARadiusIsSet() {
        Cache blurred = new Cache(1, true);
        blurred.update(new SectionPos(0, 0, 0));
        int colour = blurred.getColor("water", 8, 8, 8);
        assertTrue((colour & 0xFF) > 0 && (colour & 0xFF) < 0xFF);
        Cache sharp = new Cache(0, true);
        sharp.update(new SectionPos(0, 0, 0));
        int raw = sharp.getColor("water", 8, 8, 8) & 0xFF;
        assertTrue(raw == 0 || raw == 0xFF);
        new Cache(50, true).update(new SectionPos(0, 0, 0));
    }
}
