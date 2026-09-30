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

// Makes blocks look waterlogged when the server cannot report it (vanilla or Bukkit servers, servers on another version behind a proxy): a block with water directly above it, or standing water on enough of its horizontal sides, is drawn holding that water whenever its shape leaves room for it to show. Visual only: the guess lives in the chunk builder's copy and never reaches the world, and Fluidlogged is not asked whether the block could really hold water. One pass over the raw copied data and never over earlier guesses, so a block's answer depends on its six neighbours alone; the slice's two-block margin then gives every block the fluid renderer reads the same answer the neighbouring section computes for it, and seams stay closed
public final class FluidloggingInference {
    private FluidloggingInference() {}

    // Snapshotted on the main thread per prepared task for the builder threads
    private static volatile ImpetusGameOptions.FluidloggingGuess mode = ImpetusGameOptions.FluidloggingGuess.OFF;

    // Singleplayer's integrated server runs this same pack, Fluidlogged included, so its data is real and the guess stays off; FML keeps no list of a remote server's mods on the client, so on a modded server with Fluidlogged the option is the switch
    public static void refresh() {
        mode = Minecraft.getMinecraft().isIntegratedServerRunning()
                ? ImpetusGameOptions.FluidloggingGuess.OFF
                : ImpetusVintage.options().quality.inferredFluidlogging;
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
                    if (state.getMaterial() == Material.AIR || FluidloggedUtils.isFluid(state) || !((FluidState) slice.getFluidStateRelative(x, y, z)).isEmpty()) {
                        continue;
                    }

                    // Beside it any fluid counts but a falling one, since servers can send a still pool as flowing levels (3 and 4 around a wall at MCParks), and the fullest sets the level so the guess sits flush with the pool
                    FluidState beside = FluidState.EMPTY;
                    int sides = 0;
                    FluidState side;
                    if (x > minX && spreads(side = fluidAt(slice, x - 1, y, z))) { sides++; beside = fuller(beside, side); }
                    if (x < maxX && spreads(side = fluidAt(slice, x + 1, y, z))) { sides++; beside = fuller(beside, side); }
                    if (z > minZ && spreads(side = fluidAt(slice, x, y, z - 1))) { sides++; beside = fuller(beside, side); }
                    if (z < maxZ && spreads(side = fluidAt(slice, x, y, z + 1))) { sides++; beside = fuller(beside, side); }

                    // The fluid above wins since a submerged block is the surest case, and it is held full
                    FluidState above = y < maxY ? fluidAt(slice, x, y + 1, z) : FluidState.EMPTY;
                    FluidState fluid;
                    if (above.isValid()) {
                        fluid = above.toSource();
                    } else if (sides >= guess.minSides) {
                        fluid = beside;
                    } else {
                        continue;
                    }
                    // Waterlogging on the servers this is for is water only; lava beside a fence leaves it dry
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

    // Whether water would show in the block: some of its footprint must be open over the height the water stands at, judged from its collision boxes, so walls, fences, panes, stairs, slabs, heads, chests and flowers qualify while full cubes, farmland and paths, where it could only flicker against the block's own faces, do not
    static boolean leavesRoom(IBlockState state, WorldSlice slice, BlockPos pos) {
        if (state.isFullCube()) {
            return false;
        }
        List<AxisAlignedBB> boxes = new ArrayList<>();
        try {
            // The signature wants a World; with the state passed as actual, vanilla shapes other than chests never read it
            state.addCollisionBoxToList(slice.getWorld(), pos, new AxisAlignedBB(pos), boxes, null, true);
        } catch (RuntimeException e) {
            // A modded shape that fails off the render thread stays dry rather than failing the chunk build
            return false;
        }
        double bottom = pos.getY() + 0.125;
        double surface = pos.getY() + 0.875;
        // A 16x16 grid of sample columns; one not inside a box that spans the water's height is open water
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

    // A fluid a block beside it can be standing in: anything valid but falling water, whose level of 8 or more only says it pours from above
    static boolean spreads(FluidState fluid) {
        return fluid.isSource() || (fluid.isValid() && fluid.getLevel() < 8);
    }

    // The fuller of two fluids that spread: a source first, then the lower level
    static FluidState fuller(FluidState best, FluidState other) {
        if (best.isEmpty() || (other.isSource() && !best.isSource())) {
            return other;
        }
        if (best.isSource() || other.isSource()) {
            return best;
        }
        return other.getLevel() < best.getLevel() ? other : best;
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
