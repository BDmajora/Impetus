package com.bdmajora.coarctatio.mixin.client.model.multipart;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.state.ConditionCanonicalizer;
import com.bdmajora.coarctatio.state.predicate.AllMatchOneBoolean;
import com.bdmajora.coarctatio.state.predicate.CompositePredicate;
import com.bdmajora.coarctatio.state.predicate.SingleMatchOne;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.base.Predicate;
import net.minecraft.block.BlockFence;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.multipart.ICondition;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MultipartMixinsTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshPool() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
        ConditionCanonicalizer.open();
    }

    private static ICondition leaf(net.minecraft.block.properties.IProperty<Boolean> property, boolean value) {
        return container -> new SingleMatchOne(property, value);
    }

    @Test
    void anAndOfPlainTestsCollapsesToOneFlattenedPredicate() {
        ConditionAndMixin condition = Mixins.instance(ConditionAndMixin.class);
        Mixins.set(condition, "conditions", Arrays.asList(
                leaf(BlockFence.NORTH, true), leaf(BlockFence.EAST, true)));

        BlockStateContainer container = Blocks.OAK_FENCE.getBlockState();
        CallbackInfoReturnable<Predicate<IBlockState>> cir = Mixins.cir();
        Mixins.call(condition, "coarctatio$flatten", container, cir);
        assertTrue(cir.isCancelled());
        assertInstanceOf(AllMatchOneBoolean.class, cir.getReturnValue());
        assertTrue(cir.getReturnValue().apply(Blocks.OAK_FENCE.getDefaultState()
                .withProperty(BlockFence.NORTH, true).withProperty(BlockFence.EAST, true)));
    }

    @Test
    void anOrKeepsItsChildrenInAnArrayBackedComposite() {
        ConditionOrMixin condition = Mixins.instance(ConditionOrMixin.class);
        Mixins.set(condition, "conditions", Arrays.asList(
                leaf(BlockFence.NORTH, true), leaf(BlockFence.EAST, true)));

        CallbackInfoReturnable<Predicate<IBlockState>> cir = Mixins.cir();
        Mixins.call(condition, "coarctatio$flatten", Blocks.OAK_FENCE.getBlockState(), cir);
        assertTrue(cir.isCancelled());
        assertInstanceOf(CompositePredicate.class, cir.getReturnValue());
        assertTrue(cir.getReturnValue().apply(Blocks.OAK_FENCE.getDefaultState().withProperty(BlockFence.NORTH, true)));
    }

    @Test
    void aLeafConditionIsInternedRatherThanClosedOver() {
        ConditionPropertyValueMixin condition = Mixins.instance(ConditionPropertyValueMixin.class);
        Mixins.set(condition, "key", "north");
        Mixins.set(condition, "value", "true");

        BlockStateContainer container = Blocks.OAK_FENCE.getBlockState();
        CallbackInfoReturnable<Predicate<IBlockState>> first = Mixins.cir();
        Mixins.call(condition, "coarctatio$canonicalize", container, first);
        assertTrue(first.isCancelled());
        assertTrue(first.getReturnValue().apply(Blocks.OAK_FENCE.getDefaultState().withProperty(BlockFence.NORTH, true)));

        // The same leaf asked for twice is one object, unlike vanilla's fresh closure per call
        CallbackInfoReturnable<Predicate<IBlockState>> second = Mixins.cir();
        Mixins.call(condition, "coarctatio$canonicalize", container, second);
        assertSame(first.getReturnValue(), second.getReturnValue());
    }
}
