package com.bdmajora.equilibrium.common;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import com.bdmajora.equilibrium.common.advancements.StackSizeThresholds;
import com.bdmajora.equilibrium.common.ai.NavigationChunkGuard;
import com.bdmajora.equilibrium.common.collections.BlockPosLongMap;
import com.bdmajora.equilibrium.common.crafting.CraftMatrixKey;
import com.bdmajora.equilibrium.common.crafting.CraftingCache;
import com.bdmajora.equilibrium.common.crafting.FurnaceRecipeIndex;
import com.bdmajora.equilibrium.common.util.math.CompactSineLUT;
import com.bdmajora.equilibrium.common.world.IntCachePool;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquilibriumCommonTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    // A real stack carrying the previous-count field the mixin adds, spliced in for the test run
    private static ItemStack counted(int count, int previous) {
        ItemStack stack = new ItemStack(Items.APPLE, count);
        ((PreviousStackSize) (Object) stack).equilibrium$setPreviousCount(previous);
        return stack;
    }

    @Test
    void thresholdsAnswerWhetherACountCrossedOne() {
        StackSizeThresholds.clear();
        StackSizeThresholds.add(16);
        StackSizeThresholds.add(64);
        assertTrue(StackSizeThresholds.crossesAny(counted(1, 0)));
        assertFalse(StackSizeThresholds.crossesAny(counted(5, 3)));
        assertTrue(StackSizeThresholds.crossesAny(counted(16, 15)));
        assertFalse(StackSizeThresholds.crossesAny(counted(20, 16)));
        assertTrue(StackSizeThresholds.crossesAny(counted(64, 63)));
        assertFalse(StackSizeThresholds.crossesAny(counted(70, 64)));
        StackSizeThresholds.clear();
        assertFalse(StackSizeThresholds.crossesAny(counted(70, 64)));
        assertTrue(StackSizeThresholds.crossesAny(counted(70, 0)));

        SlotChangeContext.SlotCounts counts = new SlotChangeContext.SlotCounts(2, 3, 4);
        assertTrue(counts.matches(new MinMaxBounds(2f, 2f), MinMaxBounds.UNBOUNDED, new MinMaxBounds(1f, null)));
        assertFalse(counts.matches(MinMaxBounds.UNBOUNDED, new MinMaxBounds(null, 1f), MinMaxBounds.UNBOUNDED));
        assertEquals(2, counts.full);
        assertEquals(3, counts.empty);
        assertEquals(4, counts.occupied);
    }

    @Test
    void navigationGuardIsPerThread() throws Exception {
        assertFalse(NavigationChunkGuard.isNavigating());
        NavigationChunkGuard.set(true);
        assertTrue(NavigationChunkGuard.isNavigating());
        boolean[] other = new boolean[1];
        Thread thread = new Thread(() -> other[0] = NavigationChunkGuard.isNavigating());
        thread.start();
        thread.join();
        assertFalse(other[0]);
        NavigationChunkGuard.set(false);
        IntCachePool pool = new IntCachePool();
        assertEquals(256, pool.largeSize);
        assertTrue(pool.freeSmall.isEmpty() && pool.inUseSmall.isEmpty() && pool.freeLarge.isEmpty() && pool.inUseLarge.isEmpty());
    }

    @Test
    void blockPosMapBehavesLikeAMapWithLiveViews() {
        BlockPosLongMap<String> map = new BlockPosLongMap<>();
        assertTrue(map.isEmpty());
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(-4, 5, -6);
        assertNull(map.put(a, "a"));
        assertEquals("a", map.put(a, "A"));
        map.put(b, "b");
        assertEquals(2, map.size());
        assertTrue(map.containsKey(a));
        assertTrue(map.containsKey(new BlockPos(1, 2, 3)));
        assertFalse(map.containsKey("not a pos"));
        assertTrue(map.containsValue("b"));
        assertEquals("A", map.get(a));
        assertNull(map.get("nope"));
        assertNull(map.remove("nope"));
        assertEquals(List.of("A", "b"), new ArrayList<>(map.values()));
        Map<BlockPos, String> more = new LinkedHashMap<>();
        more.put(new BlockPos(7, 8, 9), "c");
        map.putAll(more);
        assertEquals(3, map.size());

        // Entries expose real positions, and setValue and iterator removal reach the backing map
        assertSame(map.entrySet(), map.entrySet());
        assertEquals(3, map.entrySet().size());
        Iterator<Map.Entry<BlockPos, String>> entries = map.entrySet().iterator();
        Map.Entry<BlockPos, String> first = entries.next();
        assertEquals(a, first.getKey());
        assertEquals("A", first.getValue());
        assertEquals("A", first.setValue("A2"));
        assertEquals("A2", map.get(a));
        entries.next();
        entries.remove();
        assertFalse(map.containsKey(b));
        assertTrue(entries.hasNext());

        assertSame(map.keySet(), map.keySet());
        assertEquals(2, map.keySet().size());
        assertTrue(map.keySet().contains(a));
        assertFalse(map.keySet().remove(b));
        Iterator<BlockPos> keys = map.keySet().iterator();
        assertTrue(keys.hasNext());
        assertEquals(a, keys.next());
        keys.remove();
        assertEquals(1, map.size());
        assertTrue(map.keySet().remove(new BlockPos(7, 8, 9)));
        assertTrue(map.isEmpty());
        map.put(a, "x");
        map.entrySet().clear();
        assertTrue(map.isEmpty());
        map.put(a, "x");
        map.clear();
        assertNull(map.remove(a));
    }

    @Test
    void craftMatrixKeysIgnoreCountsAndRefuseNbt() {
        InventoryCrafting grid = new InventoryCrafting(mock(Container.class), 2, 2);
        grid.setInventorySlotContents(0, new ItemStack(Items.APPLE, 3));
        grid.setInventorySlotContents(3, new ItemStack(Blocks.STONE, 1, 2));
        CraftMatrixKey key = CraftMatrixKey.of(grid);
        InventoryCrafting same = new InventoryCrafting(mock(Container.class), 2, 2);
        same.setInventorySlotContents(0, new ItemStack(Items.APPLE, 9));
        same.setInventorySlotContents(3, new ItemStack(Blocks.STONE, 5, 2));
        CraftMatrixKey sameKey = CraftMatrixKey.of(same);
        assertEquals(key, sameKey);
        assertEquals(key.hashCode(), sameKey.hashCode());
        assertEquals(key, key);
        assertNotEquals(key, "other");
        same.setInventorySlotContents(3, new ItemStack(Blocks.STONE, 5, 3));
        assertNotEquals(key, CraftMatrixKey.of(same));
        InventoryCrafting wide = new InventoryCrafting(mock(Container.class), 4, 1);
        wide.setInventorySlotContents(0, new ItemStack(Items.APPLE, 3));
        wide.setInventorySlotContents(3, new ItemStack(Blocks.STONE, 1, 2));
        assertNotEquals(key, CraftMatrixKey.of(wide));
        ItemStack tagged = new ItemStack(Items.APPLE);
        tagged.setTagCompound(new NBTTagCompound());
        same.setInventorySlotContents(1, tagged);
        assertNull(CraftMatrixKey.of(same));
    }

    @Test
    void craftingCacheRemembersHitsAndMisses() {
        // The registry is swapped for an empty one; the harness opens up the static final so this takes effect
        RegistryNamespaced<ResourceLocation, IRecipe> original = CraftingManager.REGISTRY;
        RegistryNamespaced<ResourceLocation, IRecipe> registry = new RegistryNamespaced<>();
        Statics.set(CraftingManager.class, "REGISTRY", registry);
        try {
            Mixins.set(CraftingCache.class, "registrySize", -1);
            Mixins.set(CraftingCache.class, "lastMatch", null);
            Mixins.<Map<?, ?>>get(CraftingCache.class, "CACHE").clear();
            World world = mock(World.class);
            IRecipe apple = mock(IRecipe.class);
            IRecipe stone = mock(IRecipe.class);
            registry.register(0, new ResourceLocation("test", "apple"), apple);
            registry.register(1, new ResourceLocation("test", "stone"), stone);
            InventoryCrafting apples = new InventoryCrafting(mock(Container.class), 2, 2);
            apples.setInventorySlotContents(0, new ItemStack(Items.APPLE));
            InventoryCrafting stones = new InventoryCrafting(mock(Container.class), 2, 2);
            stones.setInventorySlotContents(0, new ItemStack(Blocks.STONE));
            InventoryCrafting empty = new InventoryCrafting(mock(Container.class), 2, 2);
            when(apple.matches(apples, world)).thenReturn(true);
            when(stone.matches(stones, world)).thenReturn(true);

            assertSame(apple, CraftingCache.findMatchingRecipe(apples, world));
            // The last match is retried first, then the shape cache, then a full scan
            assertSame(apple, CraftingCache.findMatchingRecipe(apples, world));
            Mockito.verify(apple, Mockito.times(2)).matches(apples, world);
            assertSame(stone, CraftingCache.findMatchingRecipe(stones, world));
            assertSame(apple, CraftingCache.findMatchingRecipe(apples, world));
            Mockito.verify(stone, Mockito.times(1)).matches(apples, world);
            // A miss is scanned once and then answered from the cache, after the last-match retry
            assertNull(CraftingCache.findMatchingRecipe(empty, world));
            assertNull(CraftingCache.findMatchingRecipe(empty, world));
            Mockito.verify(apple, Mockito.times(3)).matches(empty, world);
            Mockito.verify(stone, Mockito.times(1)).matches(empty, world);
            // A cached recipe that stops matching falls back to a scan
            when(stone.matches(stones, world)).thenReturn(false);
            assertNull(CraftingCache.findMatchingRecipe(stones, world));
            // NBT grids are never cached
            ItemStack tagged = new ItemStack(Items.APPLE);
            tagged.setTagCompound(new NBTTagCompound());
            InventoryCrafting nbt = new InventoryCrafting(mock(Container.class), 2, 2);
            nbt.setInventorySlotContents(0, tagged);
            when(stone.matches(nbt, world)).thenReturn(true);
            assertSame(stone, CraftingCache.findMatchingRecipe(nbt, world));
            // A registry size change drops everything
            registry.register(2, new ResourceLocation("test", "third"), mock(IRecipe.class));
            assertNull(CraftingCache.findMatchingRecipe(empty, world));
            Mockito.verify(apple, Mockito.times(4)).matches(empty, world);
            // Filling past the capacity evicts the eldest shape
            Map<?, ?> cache = Mixins.get(CraftingCache.class, "CACHE");
            for (int i = 0; i < 4100; i++) {
                InventoryCrafting shape = new InventoryCrafting(mock(Container.class), 2, 2);
                shape.setInventorySlotContents(0, new ItemStack(Items.APPLE, 1, i));
                CraftingCache.findMatchingRecipe(shape, world);
            }
            assertEquals(4096, cache.size());
            // The "nothing matched" sentinel is never registered and answers nothing
            IRecipe none = Mixins.get(CraftingCache.class, "NO_MATCH");
            assertFalse(none.matches(empty, world));
            assertSame(ItemStack.EMPTY, none.getCraftingResult(empty));
            assertFalse(none.canFit(3, 3));
            assertSame(ItemStack.EMPTY, none.getRecipeOutput());
        } finally {
            Statics.set(CraftingManager.class, "REGISTRY", original);
            Mixins.set(CraftingCache.class, "registrySize", -1);
            Mixins.set(CraftingCache.class, "lastMatch", null);
        }
    }

    @Test
    void furnaceIndexGroupsByItemAndRebuildsOnChange() {
        Map<ItemStack, Float> source = new LinkedHashMap<>();
        ItemStack ore = new ItemStack(Blocks.IRON_ORE);
        ItemStack wildcard = new ItemStack(Blocks.STONE, 1, 32767);
        source.put(ore, 0.7f);
        source.put(wildcard, 0.1f);
        source.put(ItemStack.EMPTY, 0f);
        FurnaceRecipeIndex<Float> index = new FurnaceRecipeIndex<>(source);
        List<Map.Entry<ItemStack, Float>> candidates = index.candidates(new ItemStack(Blocks.IRON_ORE));
        assertEquals(1, candidates.size());
        assertEquals(0.7f, candidates.get(0).getValue());
        assertTrue(index.candidates(new ItemStack(Items.APPLE)).isEmpty());
        source.put(new ItemStack(Items.APPLE), 0.5f);
        assertEquals(1, index.candidates(new ItemStack(Items.APPLE)).size());
        // Swapping a key for an equal-sized map leaves the stale entry in place until invalidated
        source.remove(ore);
        source.put(new ItemStack(Blocks.IRON_ORE), 0.9f);
        assertEquals(0.7f, index.candidates(new ItemStack(Blocks.IRON_ORE)).get(0).getValue());
        index.invalidate();
        assertEquals(0.9f, index.candidates(new ItemStack(Blocks.IRON_ORE)).get(0).getValue());
    }

    @Test
    void compactSineTableMatchesVanillaExactly() {
        float[] vanilla = Statics.get(MathHelper.class, "SIN_TABLE");
        CompactSineLUT.init(vanilla);
        for (float angle = -10f; angle < 10f; angle += 0.0137f) {
            assertEquals(MathHelper.sin(angle), CompactSineLUT.sin(angle));
            assertEquals(MathHelper.cos(angle), CompactSineLUT.cos(angle));
        }
        assertThrows(IllegalStateException.class, () -> CompactSineLUT.init(null));
        assertThrows(IllegalStateException.class, () -> CompactSineLUT.init(new float[10]));
        float[] corrupt = vanilla.clone();
        corrupt[40000] += 1f;
        assertThrows(IllegalStateException.class, () -> CompactSineLUT.init(corrupt));
        CompactSineLUT.init(vanilla);
    }
}
