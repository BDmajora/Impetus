package com.bdmajora.coarctatio.mixin.item;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.registries.IRegistryDelegate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemStackCapabilityMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    // Returns a stack of the given item.
    private static ItemStackCapabilityMixin stack(Item item) {
        ItemStackCapabilityMixin stack = Mixins.instance(ItemStackCapabilityMixin.class);
        Mixins.stub(stack, "getItemRaw", invocation -> item);
        return stack;
    }

    @Test
    void constructingAStackOnlyResolvesItsItemDelegate() {
        Item stone = Item.getItemFromBlock(Blocks.STONE);
        ItemStackCapabilityMixin stack = stack(stone);
        Mixins.call(stack, "forgeInit");
        assertSame(stone.delegate, Mixins.<IRegistryDelegate<Item>>get(stack, "delegate"));
        // The dispatcher every ItemStack used to build is not there yet
        assertNull(Mixins.get(stack, "capabilities"));
        assertFalse(Mixins.<Boolean>get(stack, "coarctatio$capabilitiesReady"));

        // An empty stack has no item to resolve
        ItemStackCapabilityMixin empty = stack(null);
        Mixins.call(empty, "forgeInit");
        assertNull(Mixins.get(empty, "delegate"));
    }

    @Test
    void theDispatcherIsBuiltOnTheFirstCapabilityQuery() {
        ItemStackCapabilityMixin stack = stack(null);
        Capability<?> capability = mock(Capability.class);

        // An empty stack answers without building anything
        Mixins.set(stack, "isEmpty", true);
        assertFalse(stack.hasCapability(capability, null));
        assertNull(stack.getCapability(capability, null));
        assertFalse(Mixins.<Boolean>get(stack, "coarctatio$capabilitiesReady"));

        // Checks a real one builds once.
        Mixins.set(stack, "isEmpty", false);
        assertFalse(stack.hasCapability(capability, null));
        assertTrue(Mixins.<Boolean>get(stack, "coarctatio$capabilitiesReady"));
        assertNull(stack.getCapability(capability, null));

        // Once a dispatcher exists the queries go through it
        CapabilityDispatcher dispatcher = mock(CapabilityDispatcher.class);
        when(dispatcher.hasCapability(any(), any())).thenReturn(true);
        Mixins.set(stack, "capabilities", dispatcher);
        assertTrue(stack.hasCapability(capability, null));
        stack.getCapability(capability, null);
    }

    @Test
    void twoStacksCompareTheirCapabilitiesAfterBothAreBuilt() {
        ItemStackCapabilityMixin left = stack(null);
        ItemStackCapabilityMixin right = stack(null);
        // Checks compatible empty capabilities.
        assertTrue(left.areCapsCompatible((ItemStack) (Object) right));

        CapabilityDispatcher dispatcher = mock(CapabilityDispatcher.class);
        when(dispatcher.areCompatible(any())).thenReturn(false);
        Mixins.set(left, "capabilities", dispatcher);
        assertFalse(left.areCapsCompatible((ItemStack) (Object) right));

        // One side without a dispatcher is asked whether it is compatible with nothing
        CapabilityDispatcher other = mock(CapabilityDispatcher.class);
        when(other.areCompatible(null)).thenReturn(true);
        Mixins.set(left, "capabilities", null);
        Mixins.set(right, "capabilities", other);
        assertTrue(left.areCapsCompatible((ItemStack) (Object) right));
    }

    @Test
    void aStackThatWasNeverQueriedStillSavesTheCapabilitiesItLoaded() {
        ItemStackCapabilityMixin stack = stack(null);
        NBTTagCompound caps = new NBTTagCompound();
        caps.setInteger("energy", 500);
        Mixins.set(stack, "capNBT", caps);

        NBTTagCompound saved = new NBTTagCompound();
        Mixins.call(stack, "coarctatio$writeUnrealisedCaps", saved, Mixins.cir(saved));
        assertSame(caps, saved.getTag("ForgeCaps"));

        // Checks the cases with nothing to carry.
        Mixins.set(stack, "coarctatio$capabilitiesReady", true);
        NBTTagCompound built = new NBTTagCompound();
        Mixins.call(stack, "coarctatio$writeUnrealisedCaps", built, Mixins.cir(built));
        assertFalse(built.hasKey("ForgeCaps"));

        Mixins.set(stack, "coarctatio$capabilitiesReady", false);
        Mixins.set(stack, "capNBT", new NBTTagCompound());
        NBTTagCompound nothing = new NBTTagCompound();
        Mixins.call(stack, "coarctatio$writeUnrealisedCaps", nothing, Mixins.cir(nothing));
        assertFalse(nothing.hasKey("ForgeCaps"));

        Mixins.set(stack, "capNBT", caps);
        NBTTagCompound already = new NBTTagCompound();
        already.setTag("ForgeCaps", new NBTTagCompound());
        Mixins.call(stack, "coarctatio$writeUnrealisedCaps", already, Mixins.cir(already));
        assertTrue(already.getCompoundTag("ForgeCaps").isEmpty());
    }

    @Test
    void aCopyOfAnUnqueriedStackKeepsTheLoadedCapabilities() {
        ItemStackCapabilityMixin stack = stack(null);
        NBTTagCompound caps = new NBTTagCompound();
        caps.setInteger("energy", 500);
        Mixins.set(stack, "capNBT", caps);
        ItemStackCapabilityMixin copy = stack(null);

        Mixins.call(stack, "coarctatio$copyUnrealisedCaps", Mixins.cir((ItemStack) (Object) copy));
        NBTTagCompound carried = Mixins.get(copy, "capNBT");
        assertEquals(caps, carried);
        assertNotSame(caps, carried);

        // A built dispatcher serialises itself and an existing tag on the copy is kept
        ItemStackCapabilityMixin ready = stack(null);
        Mixins.set(ready, "capNBT", caps);
        Mixins.set(ready, "coarctatio$capabilitiesReady", true);
        ItemStackCapabilityMixin other = stack(null);
        Mixins.call(ready, "coarctatio$copyUnrealisedCaps", Mixins.cir((ItemStack) (Object) other));
        assertNull(Mixins.get(other, "capNBT"));
        NBTTagCompound mine = new NBTTagCompound();
        mine.setInteger("energy", 1);
        ItemStackCapabilityMixin keeps = stack(null);
        Mixins.set(keeps, "capNBT", mine);
        Mixins.call(stack, "coarctatio$copyUnrealisedCaps", Mixins.cir((ItemStack) (Object) keeps));
        assertSame(mine, Mixins.get(keeps, "capNBT"));
    }
}
