package com.bdmajora.coarctatio.mixin.state;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.state.CoarctatioBlockState;
import com.bdmajora.coarctatio.state.CoarctatioExtendedBlockState;
import com.bdmajora.coarctatio.state.MappedStateOwner;
import com.bdmajora.coarctatio.state.PropertyValueMapper;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import net.minecraft.block.BlockFence;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class StateMixinsTest {
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

    // Every hash cache is the same pair of hooks; this drives one of them end to end
    private static void hashIsComputedOnceAndKept(Object mixin) {
        CallbackInfoReturnable<Integer> first = Mixins.cir();
        Mixins.call(mixin, "coarctatio$cachedHash", first);
        // Nothing cached yet, so vanilla's own hashCode runs
        assertFalse(first.isCancelled());

        Mixins.call(mixin, "coarctatio$rememberHash", Mixins.cir(1234));
        CallbackInfoReturnable<Integer> second = Mixins.cir();
        Mixins.call(mixin, "coarctatio$cachedHash", second);
        assertTrue(second.isCancelled());
        assertEquals(1234, second.getReturnValue());
    }

    @Test
    void aPropertysHashIsComputedOnceRatherThanPerLookup() {
        hashIsComputedOnceAndKept(Mixins.instance(PropertyEnumHashMixin.class));
        hashIsComputedOnceAndKept(Mixins.instance(PropertyIntegerHashMixin.class));
        hashIsComputedOnceAndKept(Mixins.instance(StateImplementationHashMixin.class));
    }

    @Test
    void aContainerBuildsItsMapperOnceAndPacksEveryState() {
        BlockStateContainerMixin container = Mixins.instance(BlockStateContainerMixin.class);
        ImmutableSortedMap<String, IProperty<?>> properties = ImmutableSortedMap.of("north", BlockFence.NORTH);
        Mixins.set(container, "properties", properties);

        PropertyValueMapper mapper = container.coarctatio$mapper(Blocks.OAK_FENCE);
        assertNotNull(mapper);
        // Built on the first state and reused by the rest
        assertSame(mapper, container.coarctatio$mapper(Blocks.OAK_FENCE));

        ImmutableMap<IProperty<?>, Comparable<?>> values = ImmutableMap.of(BlockFence.NORTH, Boolean.FALSE);
        CallbackInfoReturnable<BlockStateContainer.StateImplementation> cir = Mixins.cir();
        Mixins.call(container, "coarctatio$createPackedState", Blocks.OAK_FENCE, values, ImmutableMap.of(), cir);
        assertTrue(cir.isCancelled());
        assertInstanceOf(CoarctatioBlockState.class, cir.getReturnValue());
    }

    @Test
    void aBlockTheMapperDeclinedKeepsVanillaStates() {
        BlockStateContainerMixin container = Mixins.instance(BlockStateContainerMixin.class);
        Mixins.set(container, "properties", ImmutableSortedMap.of("north", BlockFence.NORTH));
        CoarctatioConfig.get().blockStateBlacklist = new String[] {"net.minecraft.block.BlockFence"};

        assertNull(container.coarctatio$mapper(Blocks.OAK_FENCE));
        // The decline is remembered, so the block is not analysed again per state
        assertNull(container.coarctatio$mapper(Blocks.OAK_FENCE));

        CallbackInfoReturnable<BlockStateContainer.StateImplementation> cir = Mixins.cir();
        Mixins.call(container, "coarctatio$createPackedState", Blocks.OAK_FENCE,
                ImmutableMap.of(BlockFence.NORTH, Boolean.FALSE), ImmutableMap.of(), cir);
        assertFalse(cir.isCancelled());
    }

    @Test
    void anExtendedContainerPacksOnlyTheStatesThatCarryUnlistedValues() {
        ExtendedBlockStateMixin extended = Mixins.instanceWith(ExtendedBlockStateMixin.class, MappedStateOwner.class);
        BlockStateContainerMixin owner = Mixins.instance(BlockStateContainerMixin.class);
        Mixins.set(owner, "properties", ImmutableSortedMap.of("north", BlockFence.NORTH));
        PropertyValueMapper mapper = owner.coarctatio$mapper(Blocks.OAK_FENCE);
        Mockito.doReturn(mapper).when((MappedStateOwner) extended).coarctatio$mapper(any());

        ImmutableMap<IProperty<?>, Comparable<?>> values = ImmutableMap.of(BlockFence.NORTH, Boolean.FALSE);
        IUnlistedProperty<String> unlisted = Mockito.mock(IUnlistedProperty.class);

        // Nothing unlisted means Forge delegates upward, which the base mixin already handles
        CallbackInfoReturnable<BlockStateContainer.StateImplementation> plain = Mixins.cir();
        Mixins.call(extended, "coarctatio$createPackedExtendedState", Blocks.OAK_FENCE, values, ImmutableMap.of(), plain);
        assertFalse(plain.isCancelled());
        CallbackInfoReturnable<BlockStateContainer.StateImplementation> missing = Mixins.cir();
        Mixins.call(extended, "coarctatio$createPackedExtendedState", Blocks.OAK_FENCE, values, null, missing);
        assertFalse(missing.isCancelled());

        // An unlisted map produces the packed extended state, clean while every value is still unset
        CallbackInfoReturnable<BlockStateContainer.StateImplementation> clean = Mixins.cir();
        Mixins.call(extended, "coarctatio$createPackedExtendedState", Blocks.OAK_FENCE, values,
                ImmutableMap.of(unlisted, Optional.empty()), clean);
        assertTrue(clean.isCancelled());
        assertInstanceOf(CoarctatioExtendedBlockState.class, clean.getReturnValue());
        assertFalse(Mixins.<Boolean>get(clean.getReturnValue(), "dirty"));

        // One a mod seeded with a value has to start dirty, or its next listed change would hand it to the clean instance
        CallbackInfoReturnable<BlockStateContainer.StateImplementation> seeded = Mixins.cir();
        Mixins.call(extended, "coarctatio$createPackedExtendedState", Blocks.OAK_FENCE, values,
                ImmutableMap.of(unlisted, Optional.of("seeded")), seeded);
        assertTrue(Mixins.<Boolean>get(seeded.getReturnValue(), "dirty"));

        // A declined block keeps Forge's own extended state
        Mockito.doReturn(null).when((MappedStateOwner) extended).coarctatio$mapper(any());
        CallbackInfoReturnable<BlockStateContainer.StateImplementation> declined = Mixins.cir();
        Mixins.call(extended, "coarctatio$createPackedExtendedState", Blocks.OAK_FENCE, values,
                ImmutableMap.of(unlisted, Optional.empty()), declined);
        assertFalse(declined.isCancelled());
    }
}
