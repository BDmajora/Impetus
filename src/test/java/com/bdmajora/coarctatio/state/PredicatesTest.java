package com.bdmajora.coarctatio.state;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.state.predicate.AllMatchAnyObject;
import com.bdmajora.coarctatio.state.predicate.AllMatchOne;
import com.bdmajora.coarctatio.state.predicate.AllMatchOneBoolean;
import com.bdmajora.coarctatio.state.predicate.CompositePredicate;
import com.bdmajora.coarctatio.state.predicate.NegatedPredicate;
import com.bdmajora.coarctatio.state.predicate.SingleMatchAny;
import com.bdmajora.coarctatio.state.predicate.SingleMatchOne;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.base.Predicate;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.multipart.ICondition;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PredicatesTest {
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
        ConditionCanonicalizer.open();
    }

    private static IBlockState fence(boolean north, boolean east) {
        return Blocks.OAK_FENCE.getDefaultState()
                .withProperty(BlockFence.NORTH, north)
                .withProperty(BlockFence.EAST, east);
    }

    private static IBlockState furnace(EnumFacing facing) {
        return Blocks.FURNACE.getDefaultState().withProperty(BlockHorizontal.FACING, facing);
    }

    @Test
    void oneLeafTestsOnePropertyAgainstOneValue() {
        SingleMatchOne north = new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE);
        assertTrue(north.apply(fence(true, false)));
        assertFalse(north.apply(fence(false, false)));
        // A null state never matches, which is what the model pipeline expects of a missing state
        assertFalse(north.apply(null));

        // Two leaves built from the same property and value are interchangeable, which is what makes them poolable
        SingleMatchOne same = new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE);
        assertEquals(north, same);
        assertEquals(north, north);
        assertEquals(north.hashCode(), same.hashCode());
        assertNotEquals(north, new SingleMatchOne(BlockFence.NORTH, Boolean.FALSE));
        assertNotEquals(north, new SingleMatchOne(BlockFence.EAST, Boolean.TRUE));
        assertNotEquals(north, "not a predicate");
    }

    @Test
    void theAnyLeafTestsOnePropertyAgainstASetOfValues() {
        SingleMatchAny facing = new SingleMatchAny(BlockHorizontal.FACING,
                new Object[] {EnumFacing.NORTH, EnumFacing.SOUTH});
        assertTrue(facing.apply(furnace(EnumFacing.NORTH)));
        assertTrue(facing.apply(furnace(EnumFacing.SOUTH)));
        assertFalse(facing.apply(furnace(EnumFacing.EAST)));
        assertFalse(facing.apply(null));

        SingleMatchAny same = new SingleMatchAny(BlockHorizontal.FACING,
                new Object[] {EnumFacing.NORTH, EnumFacing.SOUTH});
        assertEquals(facing, same);
        assertEquals(facing, facing);
        assertEquals(facing.hashCode(), same.hashCode());
        assertNotEquals(facing, new SingleMatchAny(BlockHorizontal.FACING, new Object[] {EnumFacing.NORTH}));
        assertNotEquals(facing, new SingleMatchAny(BlockFence.NORTH, new Object[] {Boolean.TRUE}));
        assertNotEquals(facing, "not a predicate");
    }

    @Test
    void negationInvertsTheInternedPredicateItWraps() {
        SingleMatchOne north = new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE);
        NegatedPredicate negated = new NegatedPredicate(north);
        assertFalse(negated.apply(fence(true, false)));
        assertTrue(negated.apply(fence(false, false)));

        assertEquals(negated, new NegatedPredicate(new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE)));
        assertEquals(~north.hashCode(), negated.hashCode());
        assertNotEquals(negated, north);
    }

    @Test
    void theCompositeAndsOrOrsWhateverItIsGiven() {
        List<Predicate<IBlockState>> children = Arrays.asList(
                new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE),
                new SingleMatchOne(BlockFence.EAST, Boolean.TRUE));
        CompositePredicate all = CompositePredicate.all(children);
        assertTrue(all.apply(fence(true, true)));
        assertFalse(all.apply(fence(true, false)));

        CompositePredicate any = CompositePredicate.any(children);
        assertTrue(any.apply(fence(true, false)));
        assertFalse(any.apply(fence(false, false)));

        // An empty AND holds and an empty OR does not, the way the loop falls out
        assertTrue(CompositePredicate.all(java.util.Collections.emptyList()).apply(fence(false, false)));
        assertFalse(CompositePredicate.any(java.util.Collections.emptyList()).apply(fence(false, false)));

        assertEquals(all, CompositePredicate.all(children));
        assertEquals(all, all);
        assertEquals(all.hashCode(), CompositePredicate.all(children).hashCode());
        // The AND/OR flag is part of the identity, so the two never share a pool entry
        assertNotEquals(all, any);
        assertNotEquals(all, "not a predicate");
    }

    @Test
    void anAllBooleanAndIsFlattenedOntoTwoArrays() {
        List<Predicate<IBlockState>> children = Arrays.asList(
                new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE),
                new SingleMatchOne(BlockFence.EAST, Boolean.FALSE));
        AllMatchOneBoolean flattened = AllMatchOneBoolean.tryFlatten(children);
        assertNotNull(flattened);
        assertTrue(flattened.apply(fence(true, false)));
        assertFalse(flattened.apply(fence(true, true)));
        assertFalse(flattened.apply(null));
        // A condition that expects true of a property whose values are not booleans simply never matches
        AllMatchOneBoolean mistyped = AllMatchOneBoolean.tryFlatten(Arrays.asList(
                new SingleMatchOne(BlockHorizontal.FACING, Boolean.TRUE)));
        assertNotNull(mistyped);
        assertFalse(mistyped.apply(furnace(EnumFacing.NORTH)));

        assertEquals(flattened, AllMatchOneBoolean.tryFlatten(children));
        assertEquals(flattened, flattened);
        assertEquals(flattened.hashCode(), AllMatchOneBoolean.tryFlatten(children).hashCode());
        assertNotEquals(flattened, AllMatchOneBoolean.tryFlatten(Arrays.asList(
                new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE),
                new SingleMatchOne(BlockFence.EAST, Boolean.TRUE))));
        assertNotEquals(flattened, "not a predicate");

        // Anything that is not an all-boolean list of leaves declines
        assertNull(AllMatchOneBoolean.tryFlatten(Arrays.asList(
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH))));
        assertNull(AllMatchOneBoolean.tryFlatten(Arrays.asList(
                new SingleMatchAny(BlockFence.NORTH, new Object[] {Boolean.TRUE}))));
    }

    @Test
    void anAndOverPlainValuesIsFlattenedTheSameWay() {
        List<Predicate<IBlockState>> children = Arrays.asList(
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH),
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH));
        AllMatchOne flattened = AllMatchOne.tryFlatten(children);
        assertNotNull(flattened);
        assertTrue(flattened.apply(furnace(EnumFacing.NORTH)));
        assertFalse(flattened.apply(furnace(EnumFacing.SOUTH)));
        assertFalse(flattened.apply(null));

        assertEquals(flattened, AllMatchOne.tryFlatten(children));
        assertEquals(flattened, flattened);
        assertEquals(flattened.hashCode(), AllMatchOne.tryFlatten(children).hashCode());
        assertNotEquals(flattened, AllMatchOne.tryFlatten(Arrays.asList(
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.SOUTH),
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH))));
        assertNotEquals(flattened, "not a predicate");
        assertNull(AllMatchOne.tryFlatten(Arrays.asList(
                new SingleMatchAny(BlockHorizontal.FACING, new Object[] {EnumFacing.NORTH}))));
    }

    @Test
    void anAndOfOrsIsFlattenedOntoArraysOfArrays() {
        List<Predicate<IBlockState>> children = Arrays.asList(
                new SingleMatchAny(BlockHorizontal.FACING, new Object[] {EnumFacing.NORTH, EnumFacing.SOUTH}),
                new SingleMatchAny(BlockHorizontal.FACING, new Object[] {EnumFacing.NORTH, EnumFacing.EAST}));
        AllMatchAnyObject flattened = AllMatchAnyObject.tryFlatten(children);
        assertNotNull(flattened);
        assertTrue(flattened.apply(furnace(EnumFacing.NORTH)));
        assertFalse(flattened.apply(furnace(EnumFacing.SOUTH)));
        assertFalse(flattened.apply(null));

        assertEquals(flattened, AllMatchAnyObject.tryFlatten(children));
        assertEquals(flattened, flattened);
        assertEquals(flattened.hashCode(), AllMatchAnyObject.tryFlatten(children).hashCode());
        assertNotEquals(flattened, AllMatchAnyObject.tryFlatten(Arrays.asList(
                new SingleMatchAny(BlockHorizontal.FACING, new Object[] {EnumFacing.SOUTH}))));
        assertNotEquals(flattened, "not a predicate");
        assertNull(AllMatchAnyObject.tryFlatten(Arrays.asList(
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH))));
    }

    @Test
    void theCanonicalizerBuildsTheSpecialisedShapeAndSharesIt() {
        BlockStateContainer container = Blocks.OAK_FENCE.getBlockState();
        Predicate<IBlockState> north = ConditionCanonicalizer.propertyValue(container, "north", "true");
        assertTrue(north.apply(fence(true, false)));
        // The same condition asked for twice is the same object
        assertSame(north, ConditionCanonicalizer.propertyValue(container, "north", "true"));
        assertTrue(ConditionCanonicalizer.sharedCount() > 0);

        // A negated condition wraps the interned one
        Predicate<IBlockState> notNorth = ConditionCanonicalizer.propertyValue(container, "north", "!true");
        assertFalse(notNorth.apply(fence(true, false)));
        assertTrue(notNorth.apply(fence(false, false)));

        // A multi-valued condition becomes the any-leaf
        BlockStateContainer furnaceStates = Blocks.FURNACE.getBlockState();
        Predicate<IBlockState> facing = ConditionCanonicalizer.propertyValue(furnaceStates, "facing", "north|south");
        assertTrue(facing.apply(furnace(EnumFacing.SOUTH)));
        assertFalse(facing.apply(furnace(EnumFacing.EAST)));

        // A blockstate file naming a property or value the block does not have is a pack bug and throws like vanilla
        assertThrows(RuntimeException.class, () -> ConditionCanonicalizer.propertyValue(container, "nope", "true"));
        assertThrows(RuntimeException.class, () -> ConditionCanonicalizer.propertyValue(container, "north", "maybe"));
        assertThrows(RuntimeException.class, () -> ConditionCanonicalizer.propertyValue(container, "north", "!"));
        assertNotNull(Mixins.construct(ConditionCanonicalizer.class));
    }

    @Test
    void combiningConditionsPicksTheNarrowestShapeThatFits() {
        Predicate<IBlockState> single = new SingleMatchOne(BlockFence.NORTH, Boolean.TRUE);
        // One condition is returned as is rather than wrapped
        assertSame(single, ConditionCanonicalizer.all(Arrays.asList(single)));
        assertSame(single, ConditionCanonicalizer.any(Arrays.asList(single)));

        assertInstanceOf(AllMatchOneBoolean.class, ConditionCanonicalizer.all(Arrays.asList(
                single, new SingleMatchOne(BlockFence.EAST, Boolean.TRUE))));
        assertInstanceOf(AllMatchOne.class, ConditionCanonicalizer.all(Arrays.asList(
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH),
                new SingleMatchOne(BlockHorizontal.FACING, EnumFacing.NORTH))));
        assertInstanceOf(AllMatchAnyObject.class, ConditionCanonicalizer.all(Arrays.asList(
                new SingleMatchAny(BlockFence.NORTH, new Object[] {Boolean.TRUE}),
                new SingleMatchAny(BlockFence.EAST, new Object[] {Boolean.TRUE}))));
        // Nothing fits, so the children are kept in an array-backed composite
        assertInstanceOf(CompositePredicate.class, ConditionCanonicalizer.all(Arrays.asList(
                single, new NegatedPredicate(single))));
        assertInstanceOf(CompositePredicate.class, ConditionCanonicalizer.any(Arrays.asList(
                single, new SingleMatchOne(BlockFence.EAST, Boolean.TRUE))));

        // Conditions are resolved in the order given, since a mod's ICondition may care
        BlockStateContainer container = Blocks.OAK_FENCE.getBlockState();
        ICondition first = states -> single;
        ICondition second = states -> new SingleMatchOne(BlockFence.EAST, Boolean.TRUE);
        List<Predicate<IBlockState>> resolved = ConditionCanonicalizer.resolve(Arrays.asList(first, second), container);
        assertEquals(2, resolved.size());
        assertSame(single, resolved.get(0));

        assertTrue(ConditionCanonicalizer.statistics().startsWith("Multipart predicates"));
        ConditionCanonicalizer.close();
        assertTrue(ConditionCanonicalizer.statistics().contains("closed"));
    }

    @Test
    void anUnknownPropertyTypeStillCompares() {
        // Properties are compared by equals, so two blocks declaring the same property share one predicate
        IProperty<?> north = Blocks.OAK_FENCE.getBlockState().getProperty("north");
        IProperty<?> spruceNorth = Blocks.SPRUCE_FENCE.getBlockState().getProperty("north");
        assertSame(north, spruceNorth);
        assertEquals(new SingleMatchOne(north, Boolean.TRUE), new SingleMatchOne(spruceNorth, Boolean.TRUE));
    }
}
