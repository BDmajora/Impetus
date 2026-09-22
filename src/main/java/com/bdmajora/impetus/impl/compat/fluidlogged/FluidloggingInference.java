package com.bdmajora.impetus.impl.compat.fluidlogged;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.impl.world.WorldSlice;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

// Guesses fluidlogging for the chunk builder when the server cannot report any (vanilla or Bukkit servers, servers on another version behind a proxy): a fluidloggable block with a fluid directly above it, or fluid sources on enough of its horizontal sides, is rendered as holding that fluid. Visual only, nothing is written to the world. One pass over the raw copied data and never over earlier guesses, so a block's answer depends on its six neighbours alone; the slice's two-block margin then gives every block the fluid renderer reads the same answer the neighbouring section computes for it, and seams stay closed
public final class FluidloggingInference {
    private FluidloggingInference() {}

    // Rechecked on the main thread per prepared task against the connection's handshake mod list; a server with the mod owns the data and the guess stays off
    private static NetworkManager checkedManager;
    private static boolean remoteHasMod;
    private static volatile ImpetusGameOptions.FluidloggingGuess mode = ImpetusGameOptions.FluidloggingGuess.OFF;

    public static void refresh() {
        ImpetusGameOptions.FluidloggingGuess option = ImpetusVintage.options().quality.inferredFluidlogging;
        if (option == ImpetusGameOptions.FluidloggingGuess.OFF) {
            mode = option;
            return;
        }
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();
        NetworkManager manager = connection != null ? connection.getNetworkManager() : null;
        if (manager != checkedManager) {
            NetworkDispatcher dispatcher = manager != null ? NetworkDispatcher.get(manager) : null;
            remoteHasMod = dispatcher != null && dispatcher.getModList().containsKey(FluidloggedCompat.MODID);
            checkedManager = manager;
        }
        mode = remoteHasMod ? ImpetusGameOptions.FluidloggingGuess.OFF : option;
    }

    public static boolean isActive() {
        return mode != ImpetusGameOptions.FluidloggingGuess.OFF;
    }

    // Fills the slice's empty fluid slots inside the given slice-relative bounds; base is the world position of relative (0,0,0)
    public static void apply(WorldSlice slice, int baseX, int baseY, int baseZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        ImpetusGameOptions.FluidloggingGuess guess = mode;
        if (guess == ImpetusGameOptions.FluidloggingGuess.OFF) {
            return;
        }
        // Results are held back until the pass is over so a guess never feeds a neighbour's guess
        IntArrayList hitPositions = new IntArrayList();
        ObjectArrayList<FluidState> hitStates = new ObjectArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    IBlockState state = slice.getBlockStateRelative(x, y, z);
                    if (state.getBlock() == Blocks.AIR || FluidloggedUtils.isFluid(state) || !((FluidState) slice.getFluidStateRelative(x, y, z)).isEmpty()) {
                        continue;
                    }

                    // The fluid above wins the tie since a submerged block is the surest case; sides only count sources so the flowing skirt of a waterfall does not fill the bank
                    FluidState above = y < maxY ? fluidAt(slice, x, y + 1, z) : FluidState.EMPTY;
                    FluidState candidate = above;
                    int sides = 0;
                    FluidState side;
                    if (x > minX && (side = fluidAt(slice, x - 1, y, z)).isSource()) { sides++; if (candidate.isEmpty()) candidate = side; }
                    if (x < maxX && (side = fluidAt(slice, x + 1, y, z)).isSource()) { sides++; if (candidate.isEmpty()) candidate = side; }
                    if (z > minZ && (side = fluidAt(slice, x, y, z - 1)).isSource()) { sides++; if (candidate.isEmpty()) candidate = side; }
                    if (z < maxZ && (side = fluidAt(slice, x, y, z + 1)).isSource()) { sides++; if (candidate.isEmpty()) candidate = side; }
                    if (candidate.isEmpty() || (above.isEmpty() && sides < guess.minSides)) {
                        continue;
                    }

                    FluidState fluid = candidate.toSource();
                    pos.setPos(baseX + x, baseY + y, baseZ + z);
                    if (!fluid.isFluidloggable() || !FluidloggedUtils.isStateFluidloggable(state, slice, pos, fluid)) {
                        continue;
                    }
                    hitPositions.add(x | y << 6 | z << 12);
                    hitStates.add(fluid);
                }
            }
        }

        for (int i = 0; i < hitPositions.size(); i++) {
            int packed = hitPositions.getInt(i);
            slice.setFluidStateRelative(packed & 63, (packed >> 6) & 63, packed >> 12, hitStates.get(i));
        }
    }

    // The fluid really at a position: a fluid block is its own state, anything else is whatever the server's fluidlogging data holds
    private static FluidState fluidAt(WorldSlice slice, int x, int y, int z) {
        IBlockState state = slice.getBlockStateRelative(x, y, z);
        if (FluidloggedUtils.isFluid(state)) {
            return FluidState.of(state);
        }
        return (FluidState) slice.getFluidStateRelative(x, y, z);
    }
}
