package com.bdmajora.impetus.impl.compat.fluidlogged;

import git.jbredwards.fluidlogged_api.api.world.IChunkProvider;
import git.jbredwards.fluidlogged_api.api.world.IWorldProvider;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.Optional;

// Contract of the fluidlogged block access.
@Optional.InterfaceList({
        @Optional.Interface(modid = FluidloggedCompat.MODID, iface = "git.jbredwards.fluidlogged_api.api.world.IChunkProvider"),
        @Optional.Interface(modid = FluidloggedCompat.MODID, iface = "git.jbredwards.fluidlogged_api.api.world.IWorldProvider")
})
public interface FluidloggedBlockAccess extends IBlockAccess, IChunkProvider, IWorldProvider {
}
