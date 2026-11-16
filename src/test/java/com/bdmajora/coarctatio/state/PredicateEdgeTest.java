package com.bdmajora.coarctatio.state;

import com.bdmajora.coarctatio.state.predicate.AllMatchAnyObject;
import com.bdmajora.coarctatio.state.predicate.AllMatchOne;
import com.bdmajora.coarctatio.state.predicate.AllMatchOneBoolean;
import com.bdmajora.coarctatio.state.predicate.CompositePredicate;
import com.bdmajora.coarctatio.state.predicate.NegatedPredicate;
import com.bdmajora.coarctatio.state.predicate.SingleMatchAny;
import com.bdmajora.coarctatio.state.predicate.SingleMatchOne;
import com.google.common.base.Predicate;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PredicateEdgeTest {
    private static IBlockState state(IProperty<?> property, Object value) {
        IBlockState state = mock(IBlockState.class);
        when(state.getValue(property)).thenReturn(null);
        doValue(state, property, value);
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void doValue(IBlockState state, IProperty property, Object value) {
        when(state.getValue(property)).thenReturn((Comparable) value);
    }

    @Test
    void constructorsRefuseNullParts() {
        PropertyBool flag = PropertyBool.create("flag");
        assertThrows(NullPointerException.class, () -> new NegatedPredicate(null));
        assertThrows(NullPointerException.class, () -> new SingleMatchOne(null, true));
        assertThrows(NullPointerException.class, () -> new SingleMatchAny(null, new Object[0]));
        assertThrows(NullPointerException.class, () -> new SingleMatchAny(flag, null));
    }

    @Test
    void nullStatesNeverMatch() {
        PropertyBool flag = PropertyBool.create("flag");
        assertFalse(new SingleMatchOne(flag, true).apply(null));
        assertFalse(new SingleMatchAny(flag, new Object[] {true}).apply(null));
        List<Predicate<IBlockState>> one = Arrays.asList(new SingleMatchOne(flag, true));
        assertFalse(AllMatchOneBoolean.tryFlatten(one).apply(null));
        assertFalse(AllMatchOne.tryFlatten(one).apply(null));
        assertFalse(AllMatchAnyObject.tryFlatten(Arrays.asList(new SingleMatchAny(flag, new Object[] {true}))).apply(null));
    }

    @Test
    void emptyCombinationsFollowLogic() {
        IBlockState any = mock(IBlockState.class);
        assertTrue(CompositePredicate.all(Collections.emptyList()).apply(any));
        assertFalse(CompositePredicate.any(Collections.emptyList()).apply(any));
        assertTrue(ConditionCanonicalizer.all(Collections.emptyList()).apply(any));
        assertFalse(ConditionCanonicalizer.any(Collections.emptyList()).apply(any));
        assertFalse(new SingleMatchAny(PropertyBool.create("x"), new Object[0]).apply(any));
    }

    @Test
    void flatteningRefusesForeignMembers() {
        PropertyBool flag = PropertyBool.create("flag");
        Predicate<IBlockState> foreign = s -> true;
        assertNull(AllMatchOne.tryFlatten(Arrays.asList(new SingleMatchOne(flag, true), foreign)));
        assertNull(AllMatchOneBoolean.tryFlatten(Arrays.asList(new SingleMatchOne(flag, "text"))));
        assertNull(AllMatchAnyObject.tryFlatten(Arrays.asList(new SingleMatchOne(flag, true))));
    }

    @Test
    void equalPredicatesHashAlike() {
        PropertyBool flag = PropertyBool.create("flag");
        assertEquals(new SingleMatchOne(flag, true), new SingleMatchOne(flag, true));
        assertEquals(new SingleMatchOne(flag, true).hashCode(), new SingleMatchOne(flag, true).hashCode());
        assertNotEquals(new SingleMatchOne(flag, true), new SingleMatchOne(flag, false));
        assertNotEquals(new SingleMatchOne(flag, null), new SingleMatchOne(flag, false));
        assertEquals(new NegatedPredicate(new SingleMatchOne(flag, true)), new NegatedPredicate(new SingleMatchOne(flag, true)));
        assertNotEquals(new SingleMatchOne(flag, true), "text");
        assertEquals(CompositePredicate.all(Collections.emptyList()), CompositePredicate.all(Collections.emptyList()));
        assertNotEquals(CompositePredicate.all(Collections.emptyList()), CompositePredicate.any(Collections.emptyList()));
        IBlockState on = state(flag, true);
        assertTrue(new SingleMatchOne(flag, true).apply(on));
        assertFalse(new NegatedPredicate(new SingleMatchOne(flag, true)).apply(on));
    }
}
