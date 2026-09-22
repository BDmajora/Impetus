package com.bdmajora.impetus.impl.compat.fluidlogged;

import git.jbredwards.fluidlogged_api.api.world.IChunkProvider;
import git.jbredwards.fluidlogged_api.api.world.IWorldProvider;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.Optional;

@Optional.InterfaceList({
        @Optional.Interface(modid = FluidloggedCompat.MODID, iface = "git.jbredwards.fluidlogged_api.api.world.IChunkProvider"),
        @Optional.Interface(modid = FluidloggedCompat.MODID, iface = "git.jbredwards.fluidlogged_api.api.world.IWorldProvider")
})
// Combines vanilla block access with Fluidlogged API's fluid state accessors; used only when the mod is present. IChunkProvider (which extends IFluidStateProvider) matters beyond fluid states: Fluidlogged's FluidCache wraps whatever block access it is handed and resolves tile entities and block states through getChunk, never through the access itself, so without it every tile entity it looks up is null and BlockShulkerBox#getBlockFaceShape crashes on any shulker box beside water
public interface FluidloggedBlockAccess extends IBlockAccess, IChunkProvider, IWorldProvider {
}
