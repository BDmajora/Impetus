package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.FaceLightOcclusion;
import com.bdmajora.fulgor.mixin.block.BlockMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.bdmajora.testing.Statics;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightInfoAndFacesTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @AfterEach
    void forgetCompatibility() {
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
        ShadowStubs.clear();
    }

    @Test
    void packedInfoCarriesOpacityEmissionAndFaces() {
        int glowstone = LightInfo.of(Blocks.GLOWSTONE.getDefaultState());
        assertEquals(15, LightInfo.emission(glowstone));
        assertEquals(15, LightInfo.opacity(glowstone));
        assertFalse(LightInfo.hasContextualValues(glowstone));
        int air = LightInfo.of(Blocks.AIR.getDefaultState());
        assertEquals(0, LightInfo.emission(air));
        assertEquals(0, LightInfo.opacity(air));

        // Replacing the values keeps the flags and re-derives the sided bit
        int replaced = LightInfo.withLightValues(glowstone, 3, 4);
        assertEquals(3, LightInfo.opacity(replaced));
        assertEquals(4, LightInfo.emission(replaced));
        assertTrue((replaced & LightInfo.COMPUTED) != 0);
        // Values outside the range are clamped rather than masked
        assertEquals(15, LightInfo.opacity(LightInfo.withLightValues(glowstone, 400, 0)));
        assertEquals(0, LightInfo.emission(LightInfo.withLightValues(glowstone, 0, -4)));

        // A block whose light varies with position keeps its contextual flags
        IBlockState contextual = mock(IBlockState.class);
        when(contextual.getBlock()).thenReturn(new ContextualBlock());
        when(contextual.getLightOpacity()).thenReturn(1);
        when(contextual.getLightValue()).thenReturn(0);
        when(contextual.getLightOpacity(any(), any())).thenReturn(7);
        when(contextual.getLightValue(any(), any())).thenReturn(9);
        int info = LightInfo.compute(contextual);
        assertTrue(LightInfo.hasContextualValues(info));
        IBlockAccess access = mock(IBlockAccess.class);
        int resolved = LightInfo.resolveContextual(info, contextual, access, new BlockPos.MutableBlockPos(), 1, 2, 3);
        assertEquals(7, LightInfo.opacity(resolved));
        assertEquals(9, LightInfo.emission(resolved));
        // A block with no contextual values is returned untouched
        assertEquals(glowstone, LightInfo.resolveContextual(glowstone, Blocks.GLOWSTONE.getDefaultState(),
                access, new BlockPos.MutableBlockPos(), 1, 2, 3));
    }

    @Test
    void absorptionFollowsTheFaceLightEnters() {
        int plain = LightInfo.of(Blocks.STONE.getDefaultState());
        assertEquals(15, LightInfo.absorption(plain, Blocks.STONE.getDefaultState(), 0));
        // An air-like block still absorbs one level per step
        assertEquals(1, LightInfo.absorption(LightInfo.of(Blocks.AIR.getDefaultState()), Blocks.AIR.getDefaultState(), 0));

        // The per-face table is built from every block at post-init
        FaceOcclusion.registerDefaults();
        FaceOcclusion.registerDefaults();
        assertTrue(FaceOcclusion.hasSidedTransparency(Blocks.STONE_SLAB));
        assertFalse(FaceOcclusion.hasSidedTransparency(Blocks.STONE));
        // A slab is solid on the face it fills and open on the other
        assertTrue(FaceOcclusion.isFaceSolid(Blocks.STONE_SLAB, 0, 5));
        assertFalse(FaceOcclusion.isFaceSolid(Blocks.STONE_SLAB, 0, 4));
        // An unknown block or an impossible meta reads as solid
        assertTrue(FaceOcclusion.isFaceSolid(Blocks.STONE, 0, 0));
        assertTrue(FaceOcclusion.isFaceSolid(Blocks.STONE_SLAB, 99, 0));

        IBlockState slab = Blocks.STONE_SLAB.getDefaultState();
        int slabInfo = LightInfo.of(slab);
        assertTrue((slabInfo & LightInfo.REGISTRY) != 0);
        assertEquals(255 & 0xF, LightInfo.opacity(slabInfo) & 0xF);
        assertEquals(1, LightInfo.absorption(slabInfo, slab, 4));
        assertEquals(LightInfo.opacity(slabInfo), LightInfo.absorption(slabInfo, slab, 5));
        assertTrue(LightInfo.isFaceSolid(slabInfo, 5));
        assertTrue(LightInfo.faceBits(slabInfo) != 0);
        // The scalar answer is the block's own opacity, which for a full cube is vanilla's 255
        assertEquals(255, FaceOcclusion.resolveScalarAbsorption(Blocks.STONE.getDefaultState(), 0));
        assertEquals(1, FaceOcclusion.resolveScalarAbsorption(slab, 4));
    }

    // A block whose light values depend on where it is, which is what the contextual flags are for
    private static final class ContextualBlock extends Block {
        ContextualBlock() {
            super(net.minecraft.block.material.Material.ROCK);
        }

        @Override
        public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
            return 7;
        }

        @Override
        public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
            return 9;
        }
    }

    // A block that answers per-face occlusion itself
    private static final class SidedBlock extends Block implements FaceLightOcclusion {
        SidedBlock() {
            super(net.minecraft.block.material.Material.ROCK);
        }

        @Override
        public int getDirectionalLightOpacity(IBlockState state, EnumFacing direction) {
            return direction == EnumFacing.UP ? 0 : 12;
        }
    }

    @Test
    void aBlockMayAnswerPerFaceOcclusionItself() {
        SidedBlock block = new SidedBlock();
        IBlockState state = mock(IBlockState.class);
        when(state.getBlock()).thenReturn(block);
        when(state.getLightOpacity()).thenReturn(12);
        when(state.getLightValue()).thenReturn(0);
        int info = LightInfo.compute(state);
        assertTrue((info & LightInfo.DYNAMIC) != 0);
        // Directions here are the engine's own order (east, west, south, north, up, down), not EnumFacing's
        assertEquals(1, LightInfo.absorption(info, state, 4));
        assertEquals(12, LightInfo.absorption(info, state, 3));
        assertEquals(12, FaceOcclusion.resolveScalarAbsorption(state, 3));
    }

    @Test
    void blocksRememberWhetherTheirLightVariesWithPosition() {
        BlockMixin block = Mixins.instance(BlockMixin.class);
        // Block itself does not override the position-aware methods
        assertFalse(block.fulgor$hasPositionAwareLightValue());
        assertFalse(block.fulgor$hasPositionAwareOpacity());
        // The flags are resolved once and then reused
        assertFalse(block.fulgor$hasPositionAwareLightValue());
        assertTrue((Mixins.<Byte>get(block, "fulgor$lightInfoFlags") & 1) != 0);

        // A block that does override them reports so
        BlockMixin contextual = Mixins.instance(ContextualBlockMixin.class);
        assertTrue(contextual.fulgor$hasPositionAwareLightValue());
        assertTrue(contextual.fulgor$hasPositionAwareOpacity());
        // A name that cannot be resolved is treated as overridden, the safe answer
        assertTrue((boolean) Mixins.call(block, "fulgor$overrides", "noSuchMethod"));
    }

    // A mixin subclass standing in for a block that overrides both position-aware methods
    public abstract static class ContextualBlockMixin extends BlockMixin {
        public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
            return 4;
        }

        public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
            return 5;
        }
    }
}
