package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.testing.Mc;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelLookupCacheTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void aModelIsLoadedOnceAndThenReadFromTheCache() {
        AtomicInteger loads = new AtomicInteger();
        IBakedModel model = mock(IBakedModel.class);
        ModelLookupCache<String> cache = new ModelLookupCache<>(key -> {
            loads.incrementAndGet();
            return model;
        }, false);

        assertSame(model, cache.get("minecraft:stone"));
        assertSame(model, cache.get("minecraft:stone"));
        assertEquals(1, loads.get());

        // Clearing sends the next lookup back to the loader
        cache.clear();
        assertSame(model, cache.get("minecraft:stone"));
        assertEquals(2, loads.get());
    }

    @Test
    void aNullAnswerIsOnlyRememberedWhenItIsARealOne() {
        AtomicInteger loads = new AtomicInteger();
        ModelLookupCache<String> remembers = new ModelLookupCache<>(key -> {
            loads.incrementAndGet();
            return null;
        }, true);
        assertNull(remembers.get("minecraft:missing"));
        assertNull(remembers.get("minecraft:missing"));
        // The item mesher answers null for an unregistered meta, so that is an answer worth keeping
        assertEquals(1, loads.get());

        AtomicInteger retries = new AtomicInteger();
        ModelLookupCache<String> retriesEveryTime = new ModelLookupCache<>(key -> {
            retries.incrementAndGet();
            return null;
        }, false);
        assertNull(retriesEveryTime.get("minecraft:missing"));
        assertNull(retriesEveryTime.get("minecraft:missing"));
        assertEquals(2, retries.get());
    }

    @Test
    void theOldestEntryIsDroppedOnceTheCacheIsFull() {
        AtomicInteger loads = new AtomicInteger();
        IBakedModel model = mock(IBakedModel.class);
        ModelLookupCache<Object> cache = new ModelLookupCache<>(key -> {
            loads.incrementAndGet();
            return model;
        }, false);

        // Keys are the per-item location objects, which are unique per registration, so they are held by reference
        Object[] keys = new Object[1001];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Object();
            cache.get(keys[i]);
        }
        assertEquals(1001, loads.get());
        // The most recent thousand are still there; the first one had to go
        cache.get(keys[1000]);
        assertEquals(1001, loads.get());
        cache.get(keys[0]);
        assertEquals(1002, loads.get());
    }

    @Test
    void aBlocksStateTableIsBuiltOnceAndKept() {
        BlockStateMapper mapper = mock(BlockStateMapper.class);
        IBlockState stone = Blocks.STONE.getDefaultState();
        ModelResourceLocation location = new ModelResourceLocation("minecraft:stone", "normal");
        Map<IBlockState, ModelResourceLocation> variants = new HashMap<>();
        variants.put(stone, location);
        when(mapper.getVariants(Blocks.STONE)).thenReturn(variants);

        StateModelLocations locations = new StateModelLocations(mapper);
        assertSame(location, locations.locationFor(stone));
        assertSame(location, locations.locationFor(stone));
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(1)).getVariants(Blocks.STONE);

        // A mapper that fails is reported with its own cause rather than Guava's wrapper
        BlockStateMapper broken = mock(BlockStateMapper.class);
        when(broken.getVariants(Blocks.DIRT)).thenThrow(new IllegalStateException("no variants"));
        StateModelLocations failing = new StateModelLocations(broken);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> failing.locationFor(Blocks.DIRT.getDefaultState()));
        assertInstanceOf(IllegalStateException.class, thrown.getCause());
    }
}
