package com.bdmajora.coarctatio.state;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.BlockFence;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExtendedStateTest {
    @TempDir
    Path dir;

    private static final IUnlistedProperty<String> MARK = new IUnlistedProperty<String>() {
        @Override
        public String getName() {
            return "mark";
        }

        // Only values starting with ok are allowed, so the refusal path has something to refuse
        @Override
        public boolean isValid(String value) {
            return value == null || value.startsWith("ok");
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String valueToString(String value) {
            return value;
        }
    };

    private static final IUnlistedProperty<String> FOREIGN = new IUnlistedProperty<String>() {
        @Override
        public String getName() {
            return "foreign";
        }

        @Override
        public boolean isValid(String value) {
            return true;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String valueToString(String value) {
            return value;
        }
    };

    private PropertyValueMapper mapper;
    private Map<Map<IProperty<?>, Comparable<?>>, CoarctatioExtendedBlockState> packed;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void packAnExtendedBlock() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);

        ExtendedBlockState container = new ExtendedBlockState(Blocks.OAK_FENCE,
                new IProperty[] {BlockFence.NORTH}, new IUnlistedProperty[] {MARK});
        this.mapper = PropertyValueMapper.create(container, Blocks.OAK_FENCE);
        assertNotNull(this.mapper);
        ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlisted = ImmutableMap.of(MARK, Optional.empty());

        this.packed = new LinkedHashMap<>();
        for (IBlockState vanilla : container.getValidStates()) {
            this.packed.put(vanilla.getProperties(), new CoarctatioExtendedBlockState(this.mapper, Blocks.OAK_FENCE,
                    ImmutableMap.copyOf(vanilla.getProperties()), unlisted, false));
        }
        for (CoarctatioExtendedBlockState state : this.packed.values()) {
            state.buildPropertyValueTable(null);
        }
    }

    private CoarctatioExtendedBlockState clean(boolean north) {
        for (Map.Entry<Map<IProperty<?>, Comparable<?>>, CoarctatioExtendedBlockState> entry : this.packed.entrySet()) {
            if (Boolean.valueOf(north).equals(entry.getKey().get(BlockFence.NORTH))) {
                return entry.getValue();
            }
        }
        throw new AssertionError("no packed state");
    }

    @Test
    void aCleanStateKnowsItsUnlistedPropertiesWithoutHoldingValues() {
        CoarctatioExtendedBlockState state = clean(false);
        assertEquals(1, state.getUnlistedNames().size());
        assertTrue(state.getUnlistedNames().contains(MARK));
        assertNull(state.getValue(MARK));
        assertEquals(Optional.empty(), state.getUnlistedProperties().get(MARK));
        // A clean state is already the clean one
        assertSame(state, state.getClean());
        // A property the block never declared is a caller bug, the way Forge reports it
        assertThrows(IllegalArgumentException.class, () -> state.getValue(FOREIGN));
        assertThrows(IllegalArgumentException.class, () -> state.withProperty(FOREIGN, "x"));
    }

    @Test
    void settingAnUnlistedValueProducesADirtyStateOverTheSamePackedIndex() {
        CoarctatioExtendedBlockState state = clean(false);
        // Setting the value it already has changes nothing
        assertSame(state, state.withProperty(MARK, null));

        IExtendedBlockState dirty = state.withProperty(MARK, "ok1");
        assertNotSame(state, dirty);
        assertEquals("ok1", dirty.getValue(MARK));
        // The clean state behind it is the registered instance, found by packed index rather than a table walk
        assertSame(state, dirty.getClean());
        // A value the property refuses is rejected rather than stored
        assertThrows(IllegalArgumentException.class, () -> dirty.withProperty(MARK, "bad"));
        // Clearing the last set value goes back to the registered clean state
        assertSame(state, dirty.withProperty(MARK, null));
    }

    @Test
    void aDirtyStateCarriesItsUnlistedValuesAcrossAListedChange() {
        CoarctatioExtendedBlockState state = clean(false);
        IExtendedBlockState dirty = state.withProperty(MARK, "ok1");
        // Setting a listed property to what it already holds still hands back the same dirty state
        assertSame(dirty, dirty.withProperty(BlockFence.NORTH, Boolean.FALSE));

        IBlockState moved = dirty.withProperty(BlockFence.NORTH, Boolean.TRUE);
        assertNotSame(dirty, moved);
        assertEquals(Boolean.TRUE, moved.getValue(BlockFence.NORTH));
        assertEquals("ok1", ((IExtendedBlockState) moved).getValue(MARK));
        // It is still a dirty state, so its clean twin is the registered one for the new value
        assertSame(clean(true), ((IExtendedBlockState) moved).getClean());

        // A clean state's listed change is just the registered instance
        assertSame(clean(true), state.withProperty(BlockFence.NORTH, Boolean.TRUE));
    }
}
