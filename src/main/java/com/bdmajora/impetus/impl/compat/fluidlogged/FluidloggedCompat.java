package com.bdmajora.impetus.impl.compat.fluidlogged;

import git.jbredwards.fluidlogged_api.api.block.IFluidloggable;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.Loader;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;

// Compatibility with Fluidlogged API.
public class FluidloggedCompat {
    // Mod id of Fluidlogged API.
    public static final String MODID = "fluidlogged_api";
    // Whether Fluidlogged API is installed.
    public static final boolean IS_LOADED = Loader.isModLoaded(MODID);

    // Returns the empty fluid state.
    public static FluidState getEmptyFluidState() {
        return FluidState.EMPTY;
    }

    // Draws the fluid state of a block.
    public static void renderFluidState(ImpetusBlockAccess blockAccess, BlockPos pos, IBlockState state, VintageChunkBuildContext context, BlockRendererDispatcher dispatcher) {
        FluidState fluidState = blockAccess.getFluidState(pos);
        if (fluidState != FluidState.EMPTY && (!(state.getBlock() instanceof IFluidloggable) || ((IFluidloggable)state.getBlock()).shouldFluidRender(blockAccess, pos, state, fluidState))) {
            IBlockState renderState = fluidState.getState().getActualState(blockAccess, pos);
            var block = renderState.getBlock();

            for (BlockRenderLayer layer : VintageChunkBuildContext.LAYERS) {
                if (block.canRenderInLayer(renderState, layer)) {
                    ForgeHooksClient.setRenderLayer(layer);
                    var buffer = context.getBufferForLayer(layer);
                    dispatcher.renderBlock(renderState, pos, blockAccess, buffer);
                    context.recordVanillaBlockAttribution(layer, renderState, pos);
                }
            }
        }
    }
}
