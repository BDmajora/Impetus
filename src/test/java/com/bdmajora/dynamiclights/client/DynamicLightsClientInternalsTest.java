package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The package-private client internals, which only their own package can reach
class DynamicLightsClientInternalsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
        Mc.client();
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
    }

    @Test
    void theFluidloggedBridgeReadsTheFluidSharingAPosition() {
        IBlockAccess access = mock(IBlockAccess.class);
        when(access.getBlockState(any())).thenReturn(Blocks.WATER.getDefaultState());
        // With no fluidlogged data at the position the real block state is what comes back
        assertSame(Blocks.WATER.getDefaultState(), FluidloggedCompat.getFluidOrReal(access, BlockPos.ORIGIN));
    }

    @Test
    void registryIdsAreTurnedIntoReadableLabels() {
        assertEquals("Wall Banner", Mixins.call(LightSourceSettings.class, "prettify", "wall_banner"));
        assertEquals("Chest", Mixins.call(LightSourceSettings.class, "prettify", "chest"));
        assertEquals("A B C", Mixins.call(LightSourceSettings.class, "prettify", "a/b.c"));
        assertEquals("Zombie", Mixins.call(LightSourceSettings.class, "displayName", null,
                new net.minecraft.util.ResourceLocation("minecraft", "zombie")));
        assertEquals("Named", Mixins.call(LightSourceSettings.class, "displayName", "Named",
                new net.minecraft.util.ResourceLocation("minecraft", "zombie")));
    }

    @Test
    void theDefaultHandlersCoverTheVanillaGlowingEntities() {
        for (String map : new String[] {"ENTITY_HANDLERS", "ENTITY_LOOKUP"}) {
            Mixins.<java.util.Map<?, ?>>get(DynamicLightHandlers.class, map).clear();
        }
        DynamicLightHandlers.registerDefaultHandlers();
        net.minecraft.entity.monster.EntityBlaze blaze = mock(net.minecraft.entity.monster.EntityBlaze.class);
        assertEquals(10, DynamicLightHandlers.getDynamicLightHandler(blaze).getLuminance(blaze));
        assertTrue(DynamicLightHandlers.getDynamicLightHandler(blaze).isWaterSensitive(blaze));
        net.minecraft.entity.monster.EntityEnderman enderman = mock(net.minecraft.entity.monster.EntityEnderman.class);
        assertEquals(0, DynamicLightHandlers.getDynamicLightHandler(enderman).getLuminance(enderman));
        when(enderman.getHeldBlockState()).thenReturn(Blocks.GLOWSTONE.getDefaultState());
        assertEquals(15, DynamicLightHandlers.getDynamicLightHandler(enderman).getLuminance(enderman));
        net.minecraft.entity.item.EntityItemFrame frame = mock(net.minecraft.entity.item.EntityItemFrame.class);
        net.minecraft.world.World world = mock(net.minecraft.world.World.class);
        Mixins.set(frame, "world", world);
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        when(frame.getPositionEyes(org.mockito.ArgumentMatchers.anyFloat()))
                .thenReturn(new net.minecraft.util.math.Vec3d(0, 0, 0));
        when(frame.getDisplayedItem()).thenReturn(new net.minecraft.item.ItemStack(Blocks.GLOWSTONE));
        assertEquals(14, DynamicLightHandlers.getDynamicLightHandler(frame).getLuminance(frame));
        net.minecraft.entity.monster.EntityMagmaCube cube = mock(net.minecraft.entity.monster.EntityMagmaCube.class);
        cube.squishFactor = 0.1F;
        assertEquals(8, DynamicLightHandlers.getDynamicLightHandler(cube).getLuminance(cube));
        cube.squishFactor = 0.9F;
        assertEquals(11, DynamicLightHandlers.getDynamicLightHandler(cube).getLuminance(cube));
        net.minecraft.entity.projectile.EntitySpectralArrow arrow =
                mock(net.minecraft.entity.projectile.EntitySpectralArrow.class);
        assertEquals(8, DynamicLightHandlers.getDynamicLightHandler(arrow).getLuminance(arrow));
        net.minecraft.entity.item.EntityItem item = mock(net.minecraft.entity.item.EntityItem.class);
        when(item.getItem()).thenReturn(new net.minecraft.item.ItemStack(Blocks.TORCH));
        assertEquals(14, DynamicLightHandlers.getDynamicLightHandler(item).getLuminance(item));
    }
}
