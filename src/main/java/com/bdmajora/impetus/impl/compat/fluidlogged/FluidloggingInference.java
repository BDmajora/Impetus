package com.bdmajora.impetus.impl.compat.fluidlogged;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.impl.world.WorldSlice;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

// Guesser of water inside blocks on the client.
public final class FluidloggingInference {
    // Creates a fluidlogging inference with default state.
    private FluidloggingInference() {}

    // Guess mode from the game options.
    private static volatile ImpetusGameOptions.FluidloggingGuess mode = ImpetusGameOptions.FluidloggingGuess.OFF;

    // Reloads the guess mode from the options.
    public static void refresh() {
        mode = Minecraft.getMinecraft().isIntegratedServerRunning()
                ? ImpetusGameOptions.FluidloggingGuess.OFF
                : ImpetusVintage.options().quality.inferredFluidlogging;
    }

    // Returns whether fluidlogged rendering is inferred on this client.
    public static boolean isActive() {
        return mode != ImpetusGameOptions.FluidloggingGuess.OFF;
    }

    // Adds guessed water to the fluid states of a slice.
    public static void apply(WorldSlice slice, int baseX, int baseY, int baseZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        ImpetusGameOptions.FluidloggingGuess guess = mode;
        if (guess == ImpetusGameOptions.FluidloggingGuess.OFF) {
            return;
        }
        IntArrayList hitPositions = new IntArrayList();
        ObjectArrayList<FluidState> hitStates = new ObjectArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    IBlockState state = slice.getBlockStateRelative(x, y, z);
                    if (state.getMaterial() == Material.AIR || FluidloggedUtils.isFluid(state) || !((FluidState) slice.getFluidStateRelative(x, y, z)).isEmpty()) {
                        continue;
                    }

                    FluidState beside = FluidState.EMPTY;
                    int sides = 0;
                    FluidState side;
                    if (x > minX && spreads(side = fluidAt(slice, x - 1, y, z))) { sides++; beside = fuller(beside, side); }
                    if (x < maxX && spreads(side = fluidAt(slice, x + 1, y, z))) { sides++; beside = fuller(beside, side); }
                    if (z > minZ && spreads(side = fluidAt(slice, x, y, z - 1))) { sides++; beside = fuller(beside, side); }
                    if (z < maxZ && spreads(side = fluidAt(slice, x, y, z + 1))) { sides++; beside = fuller(beside, side); }

                    FluidState above = y < maxY ? fluidAt(slice, x, y + 1, z) : FluidState.EMPTY;
                    FluidState fluid;
                    if (above.isValid()) {
                        fluid = above.toSource();
                    } else if (sides >= guess.minSides) {
                        fluid = beside;
                    } else {
                        continue;
                    }
                    if (fluid.getState().getMaterial() != Material.WATER) {
                        continue;
                    }
                    pos.setPos(baseX + x, baseY + y, baseZ + z);
                    if (!leavesRoom(state, slice, pos)) {
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

    // Returns whether a state leaves room for water.
    static boolean leavesRoom(IBlockState state, WorldSlice slice, BlockPos pos) {
        if (state.isFullCube()) {
            return false;
        }
        List<AxisAlignedBB> boxes = new ArrayList<>();
        try {
            state.addCollisionBoxToList(slice.getWorld(), pos, new AxisAlignedBB(pos), boxes, null, true);
        } catch (RuntimeException e) {
            return false;
        }
        double bottom = pos.getY() + 0.125;
        double surface = pos.getY() + 0.875;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                double sx = pos.getX() + (i + 0.5) / 16.0;
                double sz = pos.getZ() + (j + 0.5) / 16.0;
                boolean covered = false;
                for (AxisAlignedBB box : boxes) {
                    if (box.minY <= bottom && box.maxY >= surface && sx >= box.minX && sx <= box.maxX && sz >= box.minZ && sz <= box.maxZ) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return true;
                }
            }
        }
        return false;
    }

    // Returns whether a fluid can flow sideways.
    static boolean spreads(FluidState fluid) {
        return fluid.isSource() || (fluid.isValid() && fluid.getLevel() < 8);
    }

    // Returns the fuller of two fluid states.
    static FluidState fuller(FluidState best, FluidState other) {
        if (best.isEmpty() || (other.isSource() && !best.isSource())) {
            return other;
        }
        if (best.isSource() || other.isSource()) {
            return best;
        }
        return other.getLevel() < best.getLevel() ? other : best;
    }

    // Returns the fluid of a slice position.
    private static FluidState fluidAt(WorldSlice slice, int x, int y, int z) {
        IBlockState state = slice.getBlockStateRelative(x, y, z);
        if (FluidloggedUtils.isFluid(state)) {
            return FluidState.of(state);
        }
        return (FluidState) slice.getFluidStateRelative(x, y, z);
    }
}
