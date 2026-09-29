package com.bdmajora.coarctatio.state;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateMappingTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    // Builds one packed state per vanilla state, the way BlockStateContainerMixin does during block registration
    private static Map<Map<IProperty<?>, Comparable<?>>, CoarctatioBlockState> pack(Block block) {
        BlockStateContainer container = block.getBlockState();
        PropertyValueMapper mapper = PropertyValueMapper.create(container, block);
        assertNotNull(mapper);
        Map<Map<IProperty<?>, Comparable<?>>, CoarctatioBlockState> packed = new java.util.LinkedHashMap<>();
        for (IBlockState vanilla : container.getValidStates()) {
            CoarctatioBlockState state = new CoarctatioBlockState(mapper, block,
                    ImmutableMap.copyOf(vanilla.getProperties()));
            packed.put(vanilla.getProperties(), state);
        }
        for (CoarctatioBlockState state : packed.values()) {
            state.buildPropertyValueTable(null);
        }
        return packed;
    }

    private static CoarctatioBlockState find(Map<Map<IProperty<?>, Comparable<?>>, CoarctatioBlockState> packed,
                                             IBlockState vanilla) {
        return packed.get(vanilla.getProperties());
    }

    @Test
    void everyPropertyOfABlockIsPackedIntoOneInt() {
        PropertyValueMapper mapper = PropertyValueMapper.create(Blocks.OAK_FENCE.getBlockState(), Blocks.OAK_FENCE);
        assertNotNull(mapper);
        List<IBlockState> states = new ArrayList<>(Blocks.OAK_FENCE.getBlockState().getValidStates());
        int first = mapper.register(states.get(0));
        assertSame(states.get(0), mapper.byValue(first));
        // Two states never share an index, and a second registration of one is a corrupted layout
        int second = mapper.register(states.get(1));
        assertNotEquals(first, second);
        assertThrows(IllegalStateException.class, () -> mapper.register(states.get(0)));

        // A property value the block does not allow cannot be packed
        IBlockState broken = mock(IBlockState.class);
        when(broken.getValue(any())).thenReturn("not a fence value");
        assertThrows(IllegalStateException.class, () -> mapper.register(broken));
        assertTrue(PropertyValueMapper.statistics().contains("blocks packed"));
    }

    @Test
    void oneValueIsChangedByMaskingItsSliceOfThePackedInt() {
        BlockStateContainer container = Blocks.OAK_FENCE.getBlockState();
        PropertyValueMapper mapper = PropertyValueMapper.create(container, Blocks.OAK_FENCE);
        IBlockState state = Blocks.OAK_FENCE.getDefaultState();
        int packed = mapper.register(state);

        int north = mapper.withValue(packed, BlockFence.NORTH, Boolean.TRUE);
        assertNotEquals(packed, north);
        assertEquals(packed, mapper.withValue(north, BlockFence.NORTH, Boolean.FALSE));
        // A property this block never declared, or a value it does not allow, is refused rather than guessed at
        assertEquals(-1, mapper.withValue(packed, BlockHorizontal.FACING, EnumFacing.NORTH));
        assertEquals(-1, mapper.withValue(packed, BlockFence.NORTH, "yes"));
        // Nothing is registered at that index yet, so it reads as a hole
        assertNull(mapper.byValue(north));
    }

    @Test
    void theSharedKeyArrayIsTakenFromTheFirstStateAndReusedByTheRest() {
        BlockStateContainer container = Blocks.OAK_FENCE.getBlockState();
        PropertyValueMapper mapper = PropertyValueMapper.create(container, Blocks.OAK_FENCE);
        ImmutableMap<IProperty<?>, Comparable<?>> properties =
                ImmutableMap.copyOf(Blocks.OAK_FENCE.getDefaultState().getProperties());
        Object[] keys = mapper.sharedKeys(properties);
        assertNotNull(keys);
        assertEquals(properties.size(), keys.length);
        assertSame(keys, mapper.sharedKeys(properties));
        // A map of a different shape cannot share the array, so the caller keeps Guava's own map
        assertNull(mapper.sharedKeys(ImmutableMap.of()));
    }

    @Test
    void aBlockIsLeftOnVanillaStatesWhenItCannotBePacked() {
        // No block at all, and a blacklisted implementation class
        assertNull(PropertyValueMapper.create(Blocks.OAK_FENCE.getBlockState(), null));
        CoarctatioConfig.get().blockStateBlacklist = new String[] {"net.minecraft.block.BlockFence"};
        assertNull(PropertyValueMapper.create(Blocks.OAK_FENCE.getBlockState(), Blocks.OAK_FENCE));
        CoarctatioConfig.get().blockStateBlacklist = new String[] {"nothing.matches"};

        // A property with no values at all cannot be indexed
        assertNull(PropertyValueMapper.create(containerOf(propertyWithValues()), Blocks.STONE));

        // More than thirty bits of properties, and a layout whose state array would be too large
        assertNull(PropertyValueMapper.create(containerOf(booleans(31)), Blocks.STONE));
        assertNull(PropertyValueMapper.create(containerOf(booleans(21)), Blocks.STONE));
        assertTrue(PropertyValueMapper.statistics().contains("left on vanilla states"));
    }

    // A container that only has to answer getProperties, so a layout can be tried without building its states
    private static BlockStateContainer containerOf(List<IProperty<?>> properties) {
        BlockStateContainer container = mock(BlockStateContainer.class);
        when(container.getProperties()).thenReturn(properties);
        return container;
    }

    private static List<IProperty<?>> booleans(int count) {
        List<IProperty<?>> properties = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            properties.add(PropertyBool.create("p" + i));
        }
        return properties;
    }

    private static List<IProperty<?>> propertyWithValues(Object... values) {
        IProperty<?> property = mock(IProperty.class);
        when(property.getName()).thenReturn("empty");
        org.mockito.Mockito.doReturn(Arrays.asList(values)).when(property).getAllowedValues();
        return java.util.Collections.singletonList(property);
    }

    @Test
    void eachPropertyTypeGetsTheCheapestIndexingItCanUse() {
        // Booleans index directly, enums by ordinal when every constant is allowed
        PropertyValueMapper booleans = PropertyValueMapper.create(Blocks.OAK_FENCE.getBlockState(), Blocks.OAK_FENCE);
        assertEquals(0, booleans.withValue(0, BlockFence.NORTH, Boolean.FALSE));
        PropertyValueMapper ordinals = PropertyValueMapper.create(Blocks.PISTON.getBlockState(), Blocks.PISTON);
        assertNotNull(ordinals);
        assertTrue(ordinals.withValue(0, net.minecraft.block.BlockDirectional.FACING, EnumFacing.UP) >= 0);

        // A contiguous integer range is its own index; a filtered enum falls back to an explicit value map
        PropertyValueMapper integers = PropertyValueMapper.create(Blocks.WATER.getBlockState(), Blocks.WATER);
        assertNotNull(integers);
        assertTrue(integers.withValue(0, net.minecraft.block.BlockLiquid.LEVEL, 7) > 0);
        assertEquals(-1, integers.withValue(0, net.minecraft.block.BlockLiquid.LEVEL, 99));
        assertEquals(-1, integers.withValue(0, net.minecraft.block.BlockLiquid.LEVEL, "seven"));
        PropertyValueMapper mapped = PropertyValueMapper.create(Blocks.FURNACE.getBlockState(), Blocks.FURNACE);
        assertNotNull(mapped);
        assertTrue(mapped.withValue(0, BlockHorizontal.FACING, EnumFacing.SOUTH) >= 0);
        assertEquals(-1, mapped.withValue(0, BlockHorizontal.FACING, null));
    }

    @Test
    void anIntegerRangeWithHolesIsNotUsedAsItsOwnIndex() throws Exception {
        Class<?> type = Class.forName("com.bdmajora.coarctatio.state.PropertyValueMapper$ContiguousIntegerEntry");
        IProperty<?> property = PropertyInteger.create("level", 0, 15);
        // Values that are not integers, or that skip one, cannot be indexed by subtraction
        assertNull(Mixins.call(type, "tryCreate", property, Arrays.asList("a", "b")));
        assertNull(Mixins.call(type, "tryCreate", property, Arrays.asList(1, 3)));
        assertNotNull(Mixins.call(type, "tryCreate", property, Arrays.asList(1, 2, 3)));
    }

    @Test
    void aPackedStateResolvesItsNeighboursThroughTheSharedArray() {
        Map<Map<IProperty<?>, Comparable<?>>, CoarctatioBlockState> packed = pack(Blocks.OAK_FENCE);
        CoarctatioBlockState state = find(packed, Blocks.OAK_FENCE.getDefaultState());
        assertNotNull(state);

        // Setting a property to what it already holds hands back the same instance
        assertSame(state, state.withProperty(BlockFence.NORTH, Boolean.FALSE));
        IBlockState north = state.withProperty(BlockFence.NORTH, Boolean.TRUE);
        assertNotSame(state, north);
        assertEquals(Boolean.TRUE, north.getValue(BlockFence.NORTH));
        // And back again is the registered instance it started from
        assertSame(state, north.withProperty(BlockFence.NORTH, Boolean.FALSE));

        // The messages match vanilla's, since mods match on them
        assertThrows(IllegalArgumentException.class,
                () -> state.withProperty(BlockHorizontal.FACING, EnumFacing.NORTH));
        assertThrows(IllegalArgumentException.class,
                () -> state.withProperty(net.minecraft.block.BlockLiquid.LEVEL, 99));
    }

    @Test
    void theTableAModAsksForIsRebuiltOnDemandAndKept() {
        Map<Map<IProperty<?>, Comparable<?>>, CoarctatioBlockState> packed = pack(Blocks.OAK_FENCE);
        CoarctatioBlockState state = find(packed, Blocks.OAK_FENCE.getDefaultState());
        var table = state.getPropertyValueTable();
        assertNotNull(table);
        assertFalse(table.isEmpty());
        // Built once and cached in the field vanilla reads directly
        assertSame(table, state.getPropertyValueTable());
        assertEquals(Boolean.TRUE, table.get(BlockFence.NORTH, Boolean.TRUE).getValue(BlockFence.NORTH));
    }

    @Test
    void thePropertyMapIsReplacedByOneSharingTheBlocksKeyArray() {
        CoarctatioConfig.get().compactStateProperties = true;
        Map<Map<IProperty<?>, Comparable<?>>, CoarctatioBlockState> packed = pack(Blocks.OAK_FENCE);
        CoarctatioBlockState state = find(packed, Blocks.OAK_FENCE.getDefaultState());
        assertEquals("com.google.common.collect.CoarctatioPropertyMap", state.getProperties().getClass().getName());
        assertTrue(CompactPropertyMaps.compacted() > 0);
        assertTrue(CompactPropertyMaps.statistics().contains("compacted"));

        // Without a shared key array there is nothing to share, so Guava's own map is kept
        ImmutableMap<IProperty<?>, Comparable<?>> original =
                ImmutableMap.copyOf(Blocks.OAK_FENCE.getDefaultState().getProperties());
        assertSame(original, CompactPropertyMaps.compact(null, original));
        // A key array that does not line up with the map is refused rather than silently mismatched
        assertSame(original, CompactPropertyMaps.compact(new Object[] {BlockFence.NORTH}, original));
        assertSame(original, CompactPropertyMaps.compact(new Object[original.size() + 1], original));
        assertNotNull(Mixins.construct(CompactPropertyMaps.class));
    }
}
