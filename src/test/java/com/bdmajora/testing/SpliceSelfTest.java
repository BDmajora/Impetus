package com.bdmajora.testing;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.bdmajora.equilibrium.mixin.world.raycast.WorldMixin;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpliceSelfTest {
    @Test
    void theHarnessSplicesInterfacesAndReParentsMixins() {
        Mc.bootstrap();
        // A final game class still gains the accessor interface its mixin adds
        ItemStack stack = new ItemStack(net.minecraft.init.Items.APPLE, 1);
        assertInstanceOf(PreviousStackSize.class, stack);
        ((PreviousStackSize) (Object) stack).equilibrium$setPreviousCount(4);
        assertEquals(4, ((PreviousStackSize) (Object) stack).equilibrium$previousCount());
        // A mixin instance is an instance of the class it targets
        assertInstanceOf(World.class, Mixins.instance(WorldMixin.class));

        // A @Shadow method with a placeholder body keeps that body, and calls to it from inside the mixin are
        // answered from the stub table instead
        var wire = Mixins.instance(com.bdmajora.equilibrium.mixin.block.redstone_wire.BlockRedstoneWireMixin.class);
        assertThrows(AssertionError.class, () -> Mixins.call(wire, "getMaxCurrentStrength", null, null, 0));
        ShadowStubs.clear();
        Splice.install();
    }
}
