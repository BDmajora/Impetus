package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.bdmajora.equilibrium.common.advancements.SizeCheckedPredicate;
import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import com.bdmajora.equilibrium.common.advancements.StackSizeThresholds;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.AdvancementManagerMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.ContainerMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.EntityPlayerMPMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.InventoryChangeTriggerInstanceMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.InventoryChangeTriggerMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.InventoryPlayerMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.ItemPredicateMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.ItemPredicateAccessor;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.ItemStackMixin;
import com.bdmajora.equilibrium.mixin.advancements.inventory_trigger.MinMaxBoundsAccessor;
import com.bdmajora.testing.Mc;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.bdmajora.testing.Mixins;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvancementsMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void resetThresholds() {
        StackSizeThresholds.clear();
    }

    private static ItemStack counted(int count, int previous) {
        ItemStack stack = new ItemStack(Items.APPLE, count);
        ((PreviousStackSize) (Object) stack).equilibrium$setPreviousCount(previous);
        return stack;
    }

    // A predicate that answers a fixed crossing result, standing in for the mixin on ItemPredicate
    private static ItemPredicate predicate(boolean crossing) {
        ItemPredicate predicate = Mc.mock(ItemPredicate.class, SizeCheckedPredicate.class);
        when(((SizeCheckedPredicate) predicate).equilibrium$matchesCrossing(Mockito.any())).thenReturn(crossing);
        return predicate;
    }

    // Bounds carrying the accessor the mixins read them through
    private static MinMaxBounds bounds(Float min, Float max) {
        MinMaxBounds value = Mc.mock(MinMaxBounds.class, MinMaxBoundsAccessor.class);
        when(((MinMaxBoundsAccessor) value).equilibrium$min()).thenReturn(min);
        when(((MinMaxBoundsAccessor) value).equilibrium$max()).thenReturn(max);
        when(value.test(Mockito.anyFloat())).thenAnswer(invocation -> {
            float count = invocation.getArgument(0);
            return (min == null || count >= min) && (max == null || count <= max);
        });
        return value;
    }

    // A predicate whose declared count bound is visible through the accessor
    private static ItemPredicate withCount(MinMaxBounds count) {
        ItemPredicate predicate = Mc.mock(ItemPredicate.class, ItemPredicateAccessor.class);
        when(((ItemPredicateAccessor) predicate).equilibrium$count()).thenReturn(count);
        return predicate;
    }

    @Test
    void stackAndInventoryCarryTheTriggerContext() {
        ItemStackMixin stack = Mixins.instance(ItemStackMixin.class);
        assertEquals(0, stack.equilibrium$previousCount());
        stack.equilibrium$setPreviousCount(12);
        assertEquals(12, stack.equilibrium$previousCount());

        InventoryPlayerMixin inventory = Mixins.instance(InventoryPlayerMixin.class);
        assertNull(inventory.equilibrium$changedStack());
        assertNull(inventory.equilibrium$slotCounts());
        ItemStack changed = counted(3, 1);
        inventory.equilibrium$setChangedStack(changed);
        inventory.equilibrium$setSlotCounts(1, 2, 3);
        assertSame(changed, inventory.equilibrium$changedStack());
        assertEquals(1, inventory.equilibrium$slotCounts().full);
        assertEquals(2, inventory.equilibrium$slotCounts().empty);
        assertEquals(3, inventory.equilibrium$slotCounts().occupied);
        inventory.equilibrium$clear();
        assertNull(inventory.equilibrium$changedStack());
        assertNull(inventory.equilibrium$slotCounts());

        // A reload drops every collected threshold
        StackSizeThresholds.add(16);
        assertTrue(StackSizeThresholds.crossesAny(counted(16, 15)));
        Mixins.call(Mixins.instance(AdvancementManagerMixin.class), "equilibrium$resetThresholds", Mixins.ci());
        assertFalse(StackSizeThresholds.crossesAny(counted(16, 15)));
    }

    @Test
    void containerSeedsAndRecordsSlotContents() {
        ContainerMixin container = Mixins.instance(ContainerMixin.class);
        ItemStack empty = ItemStack.EMPTY;
        Slot playerSlot = mock(Slot.class);
        Mixins.set(playerSlot, "inventory", mock(InventoryPlayer.class));
        when(playerSlot.getHasStack()).thenReturn(true);
        ItemStack held = new ItemStack(Items.APPLE, 5);
        when(playerSlot.getStack()).thenReturn(held);
        // The mirror is seeded with a copy so a later in-place count change still compares unequal
        Object seeded = Mixins.call(container, "equilibrium$seedKnownContents", empty, playerSlot);
        assertNotSame(held, seeded);
        assertEquals(5, ((ItemStack) seeded).getCount());
        when(playerSlot.getHasStack()).thenReturn(false);
        assertSame(empty, Mixins.call(container, "equilibrium$seedKnownContents", empty, playerSlot));
        Slot chestSlot = mock(Slot.class);
        Mixins.set(chestSlot, "inventory", mock(net.minecraft.inventory.InventoryBasic.class));
        when(chestSlot.getHasStack()).thenReturn(true);
        assertSame(empty, Mixins.call(container, "equilibrium$seedKnownContents", empty, chestSlot));

        // The previous count only carries over when the same item is still in the slot
        var ref = Mc.<ItemStack>ref(null);
        ItemStack previous = new ItemStack(Items.APPLE, 2);
        Mixins.call(container, "equilibrium$capturePrevious", Mixins.ci(), previous, ref);
        assertSame(previous, ref.get());
        ItemStack replacement = new ItemStack(Items.APPLE, 7);
        Mixins.call(container, "equilibrium$recordPrevious", Mixins.ci(), replacement, ref);
        assertEquals(2, ((PreviousStackSize) (Object) replacement).equilibrium$previousCount());
        ItemStack other = new ItemStack(Items.DIAMOND, 7);
        Mixins.call(container, "equilibrium$recordPrevious", Mixins.ci(), other, ref);
        assertEquals(0, ((PreviousStackSize) (Object) other).equilibrium$previousCount());
        ref.set(null);
        Mixins.call(container, "equilibrium$recordPrevious", Mixins.ci(), replacement, ref);
        assertEquals(2, ((PreviousStackSize) (Object) replacement).equilibrium$previousCount());
    }

    @Test
    void theTriggerIsGatedOnTheChangedStack() {
        InventoryChangeTriggerMixin trigger = Mixins.instance(InventoryChangeTriggerMixin.class);
        InventoryPlayerMixin context = Mixins.instance(InventoryPlayerMixin.class);
        InventoryPlayer inventory = Mc.backed(InventoryPlayer.class, context);
        when(inventory.getSizeInventory()).thenReturn(4);
        when(inventory.getStackInSlot(0)).thenReturn(new ItemStack(Items.APPLE, 64));
        when(inventory.getStackInSlot(1)).thenReturn(new ItemStack(Items.APPLE, 2));
        when(inventory.getStackInSlot(2)).thenReturn(ItemStack.EMPTY);
        when(inventory.getStackInSlot(3)).thenReturn(ItemStack.EMPTY);

        // Without a changed stack the tallies are still shared, and the trigger proceeds
        var ci = Mixins.ci();
        Mixins.call(trigger, "equilibrium$gateOnChangedStack", null, inventory, ci);
        assertFalse(ci.isCancelled());
        SlotChangeContext.SlotCounts counts = context.equilibrium$slotCounts();
        assertEquals(1, counts.full);
        assertEquals(2, counts.empty);
        assertEquals(2, counts.occupied);

        // A stack that emptied, shrank, or grew without crossing a threshold cancels the trigger
        context.equilibrium$setChangedStack(counted(0, 3));
        var emptied = Mixins.ci();
        Mixins.call(trigger, "equilibrium$gateOnChangedStack", null, inventory, emptied);
        assertTrue(emptied.isCancelled());
        context.equilibrium$setChangedStack(counted(2, 5));
        var shrank = Mixins.ci();
        Mixins.call(trigger, "equilibrium$gateOnChangedStack", null, inventory, shrank);
        assertTrue(shrank.isCancelled());
        StackSizeThresholds.add(16);
        context.equilibrium$setChangedStack(counted(5, 3));
        var belowThreshold = Mixins.ci();
        Mixins.call(trigger, "equilibrium$gateOnChangedStack", null, inventory, belowThreshold);
        assertTrue(belowThreshold.isCancelled());
        context.equilibrium$setChangedStack(counted(16, 15));
        var crossed = Mixins.ci();
        Mixins.call(trigger, "equilibrium$gateOnChangedStack", null, inventory, crossed);
        assertFalse(crossed.isCancelled());

        // Deserialising a criterion contributes every count bound it declares
        Mixins.call(trigger, "equilibrium$collectThresholds", null, null, Mixins.cir(),
                new ItemPredicate[] {withCount(bounds(32.0F, null)), withCount(bounds(1.0F, null)), withCount(bounds(null, null))});
        assertTrue(StackSizeThresholds.crossesAny(counted(32, 31)));
        assertFalse(StackSizeThresholds.crossesAny(counted(40, 32)));
    }

    @Test
    void theFastTestShortCircuitsTheInventoryWalk() {
        InventoryChangeTriggerInstanceMixin instance = Mixins.instance(InventoryChangeTriggerInstanceMixin.class);
        Mixins.set(instance, "full", MinMaxBounds.UNBOUNDED);
        Mixins.set(instance, "empty", MinMaxBounds.UNBOUNDED);
        Mixins.set(instance, "occupied", MinMaxBounds.UNBOUNDED);
        Mixins.set(instance, "items", new ItemPredicate[0]);
        InventoryPlayerMixin context = Mixins.instance(InventoryPlayerMixin.class);
        InventoryPlayer inventory = Mc.backed(InventoryPlayer.class, context);

        // Nothing prepared: vanilla's own test runs
        var untouched = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, untouched);
        assertFalse(untouched.isCancelled());

        // Tallies outside the declared bounds answer false without looking at any stack
        context.equilibrium$setSlotCounts(1, 2, 3);
        Mixins.set(instance, "full", new MinMaxBounds(5.0F, null));
        var tooFewFull = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, tooFewFull);
        assertTrue(tooFewFull.isCancelled());
        assertFalse(tooFewFull.getReturnValue());
        Mixins.set(instance, "full", MinMaxBounds.UNBOUNDED);

        // Tallies matched but no stack context: vanilla's scan again
        var noStack = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, noStack);
        assertFalse(noStack.isCancelled());

        // A criterion with no item predicates is satisfied by the tallies alone
        context.equilibrium$setChangedStack(counted(3, 1));
        var noPredicates = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, noPredicates);
        assertTrue(noPredicates.getReturnValue());

        // One predicate is answered by the changed stack alone
        Mixins.set(instance, "items", new ItemPredicate[] {predicate(true)});
        var single = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, single);
        assertTrue(single.getReturnValue());
        Mixins.set(instance, "items", new ItemPredicate[] {predicate(false)});
        var singleMiss = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, singleMiss);
        assertFalse(singleMiss.getReturnValue());
        context.equilibrium$setChangedStack(counted(0, 0));
        var emptyChange = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, emptyChange);
        assertFalse(emptyChange.getReturnValue());
        context.equilibrium$setChangedStack(counted(3, 1));

        // Several predicates: the changed stack must match one, then the walk fills the rest
        ItemPredicate matchingAll = predicate(true);
        Mixins.set(instance, "items", new ItemPredicate[] {matchingAll, predicate(false)});
        when(inventory.getSizeInventory()).thenReturn(2);
        when(inventory.getStackInSlot(0)).thenReturn(ItemStack.EMPTY);
        when(inventory.getStackInSlot(1)).thenReturn(new ItemStack(Items.APPLE, 1));
        var unfinished = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, unfinished);
        assertFalse(unfinished.getReturnValue());
        Mixins.set(instance, "items", new ItemPredicate[] {matchingAll, predicate(true)});
        var completed = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, completed);
        assertTrue(completed.getReturnValue());
        Mixins.set(instance, "items", new ItemPredicate[] {predicate(false), predicate(false)});
        var noneMatch = Mixins.<Boolean>cir();
        Mixins.call(instance, "equilibrium$fastTest", inventory, noneMatch);
        assertFalse(noneMatch.getReturnValue());
    }

    @Test
    void predicatesMatchOnlyOnACrossing() {
        ItemPredicateMixin predicate = Mixins.instance(ItemPredicateMixin.class);
        Mockito.doReturn(true).when(predicate).test(Mockito.any());
        Mixins.set(predicate, "count", bounds(16.0F, 32.0F));
        assertTrue(predicate.equilibrium$matchesCrossing(counted(16, 15)));
        assertFalse(predicate.equilibrium$matchesCrossing(counted(20, 16)));
        assertFalse(predicate.equilibrium$matchesCrossing(counted(40, 15)));
        Mixins.set(predicate, "count", bounds(null, null));
        assertTrue(predicate.equilibrium$matchesCrossing(counted(1, 0)));
        assertFalse(predicate.equilibrium$matchesCrossing(counted(2, 1)));
        Mockito.doReturn(false).when(predicate).test(Mockito.any());
        assertFalse(predicate.equilibrium$matchesCrossing(counted(1, 0)));
    }

    @Test
    void theSendingPlayerAttachesAndClearsTheContext() {
        EntityPlayerMPMixin player = Mixins.instance(EntityPlayerMPMixin.class);
        InventoryPlayerMixin context = Mixins.instance(InventoryPlayerMixin.class);
        InventoryPlayer inventory = Mc.backed(InventoryPlayer.class, context);
        ItemStack stack = counted(3, 1);
        InventoryChangeTrigger trigger = mock(InventoryChangeTrigger.class);
        EntityPlayerMP owner = mock(EntityPlayerMP.class);
        // The context is visible for the length of the original call and gone afterwards
        int[] calls = new int[1];
        Operation<Void> original = args -> {
            calls[0]++;
            assertSame(stack, context.equilibrium$changedStack());
            return null;
        };
        Mixins.call(player, "equilibrium$triggerWithContext", trigger, owner, inventory, original, null, 0, stack);
        assertEquals(1, calls[0]);
        assertNull(context.equilibrium$changedStack());
        // It is cleared even when the trigger throws
        Operation<Void> throwing = args -> {
            throw new IllegalStateException("boom");
        };
        assertThrows(IllegalStateException.class, () -> Mixins.call(player, "equilibrium$triggerWithContext",
                trigger, owner, inventory, throwing, null, 0, stack));
        assertNull(context.equilibrium$changedStack());
    }
}
