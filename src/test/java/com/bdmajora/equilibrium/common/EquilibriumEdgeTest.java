package com.bdmajora.equilibrium.common;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.common.collections.BlockPosLongMap;
import com.bdmajora.equilibrium.common.crafting.FurnaceRecipeIndex;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EquilibriumEdgeTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void positionMapRoundTripsEntriesAndViews() {
        BlockPosLongMap<String> map = new BlockPosLongMap<>();
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(-30_000_000, 255, 29_999_999);
        assertNull(map.put(a, "a"));
        assertEquals("a", map.put(a, "a2"));
        map.put(b, null);
        assertEquals(2, map.size());
        assertTrue(map.containsKey(b));
        assertNull(map.get(b));
        assertFalse(map.containsKey("not a position"));
        assertNull(map.get(null));
        assertNull(map.remove("not a position"));
        assertTrue(map.keySet().contains(a));
        assertFalse(map.keySet().remove(new BlockPos(9, 9, 9)));
        assertTrue(map.keySet().remove(b));
        Map.Entry<BlockPos, String> entry = map.entrySet().iterator().next();
        assertEquals(a, entry.getKey());
        assertEquals("a2", entry.setValue("a3"));
        assertEquals("a3", map.get(a));
        assertEquals(Map.entry(a, "a3").hashCode(), entry.hashCode());
        assertEquals(entry, Map.entry(a, "a3"));
        assertEquals(a + "=a3", entry.toString());
        java.util.Iterator<BlockPos> it = map.keySet().iterator();
        it.next();
        it.remove();
        assertTrue(map.isEmpty());
        assertThrows(NullPointerException.class, () -> map.put(null, "x"));
        map.putAll(Map.of(a, "x"));
        assertEquals(1, map.size());
        map.clear();
        assertEquals(0, map.size());
    }

    @Test
    void furnaceIndexRefusesNullSourceAndFollowsChanges() {
        assertThrows(NullPointerException.class, () -> new FurnaceRecipeIndex<String>(null));
        Map<ItemStack, String> source = new HashMap<>();
        FurnaceRecipeIndex<String> index = new FurnaceRecipeIndex<>(source);
        assertTrue(index.candidates(new ItemStack(Items.IRON_INGOT)).isEmpty());
        source.put(new ItemStack(Items.IRON_INGOT), "ingot");
        source.put(ItemStack.EMPTY, "ignored");
        assertEquals(1, index.candidates(new ItemStack(Items.IRON_INGOT)).size());
        assertTrue(index.candidates(new ItemStack(Items.GOLD_INGOT)).isEmpty());
        source.remove(ItemStack.EMPTY);
        index.invalidate();
        assertEquals(1, index.candidates(new ItemStack(Items.IRON_INGOT)).size());
    }

    @Test
    void configRefusesNullFileAndSavesWithoutLeavingATempFile(@TempDir Path dir) throws Exception {
        assertThrows(NullPointerException.class, () -> EquilibriumConfig.load(null));
        Path file = dir.resolve("nested/equilibrium.properties");
        EquilibriumConfig config = EquilibriumConfig.load(file);
        assertTrue(Files.isRegularFile(file));
        config.setOptionEnabled("mixin.world", false);
        config.setOptionEnabled("mixin.nonexistent", false);
        config.save();
        assertTrue(Files.readString(file).contains("mixin.world=false"));
        try (var children = Files.list(file.getParent())) {
            assertEquals(1, children.count());
        }
        assertEquals(0, EquilibriumConfig.load(dir.resolve("empty.properties")).getOptionOverrideCount()
                - EquilibriumConfig.load(dir.resolve("empty.properties")).getOptionOverrideCount());
    }

    @Test
    void sharedConfigIsCreatedOnceAndCanBeReplaced(@TempDir Path dir) {
        Mixins.set(Equilibrium.class, "config", null);
        EquilibriumConfig custom = EquilibriumConfig.load(dir.resolve("a.properties"));
        Equilibrium.setConfig(custom);
        assertSame(custom, Equilibrium.config());
        assertFalse(Equilibrium.isEnabled("mixin.missing"));
        Mixins.set(Equilibrium.class, "config", null);
    }
}
