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

public class FluidloggedCompat {
    // Mod ID of Fluidlogged API
    public static final String MODID = "fluidlogged_api";
    public static final boolean IS_LOADED = Loader.isModLoaded(MODID);

    // Fluidlogged's sentinel for no fluid
    public static FluidState getEmptyFluidState() {
        return FluidState.EMPTY;
    }

    // Renders the fluid layer of a fluidlogged block through vanilla's dispatcher into the vintage buffers
    public static void renderFluidState(ImpetusBlockAccess blockAccess, BlockPos pos, IBlockState state, VintageChunkBuildContext context, BlockRendererDispatcher dispatcher) {
        FluidState fluidState = blockAccess.getFluidState(pos);
        // only render the fluid if the block isn't fluidloggable, or if it explicitly opts in to rendering the fluid underneath it
        if (fluidState != FluidState.EMPTY && (!(state.getBlock() instanceof IFluidloggable) || ((IFluidloggable)state.getBlock()).shouldFluidRender(blockAccess, pos, state, fluidState))) {
            IBlockState renderState = fluidState.getState().getActualState(blockAccess, pos);
            var block = renderState.getBlock();

            for (BlockRenderLayer layer : VintageChunkBuildContext.LAYERS) {
                if (block.canRenderInLayer(renderState, layer)) {
                    ForgeHooksClient.setRenderLayer(layer);
                    var buffer = context.getBufferForLayer(layer);
                    dispatcher.renderBlock(renderState, pos, blockAccess, buffer);
                    // The shader material of these quads is the fluid's (water, lava), not the block's it sits inside; attributing them to the fence or trapdoor drew the water as a plain textured block
                    context.recordVanillaBlockAttribution(layer, renderState, pos);
                }
            }
        }
    }
}
