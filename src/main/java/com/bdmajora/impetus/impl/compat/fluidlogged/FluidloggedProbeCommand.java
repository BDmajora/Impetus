package com.bdmajora.impetus.impl.compat.fluidlogged;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import com.bdmajora.impetus.impl.world.WorldSlice;
import com.bdmajora.impetus.impl.world.cloned.ChunkRenderContext;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSectionCache;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// /impetus_fluidlog: copies the looked-at block's section and runs the fluidlogging guess exactly as the chunk builder does, then prints each check and what the fluid renderer reads there; lines also go to the log so they can be attached
public class FluidloggedProbeCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Fluidlogged");

    // Command name
    @Override
    public String getName() {
        return "impetus_fluidlog";
    }

    // Shown by /help
    @Override
    public String getUsage(ICommandSender sender) {
        return "/impetus_fluidlog [x y z] - explain the fluidlogging guess at the looked-at block";
    }

    // Zero so any player can run it; it only reads
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // The block under the crosshair unless coordinates are given
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft client = Minecraft.getMinecraft();
        BlockPos pos;
        if (args.length >= 3) {
            pos = parseBlockPos(sender, args, 0, false);
        } else if (client.objectMouseOver != null && client.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            pos = client.objectMouseOver.getBlockPos();
            // A full solid block never holds fluid, and the ray slips past a thin post onto the floor under it, so the probe steps out of the face that was hit
            if (client.world.getBlockState(pos).isOpaqueCube()) {
                pos = pos.offset(client.objectMouseOver.sideHit);
            }
        } else {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        for (String line : report(client.world, pos)) {
            LOGGER.info(line);
            sender.sendMessage(new TextComponentString(line));
        }
    }

    // One line per check, in the order the guess and then the fluid renderer make them
    static List<String> report(World world, BlockPos pos) {
        List<String> lines = new ArrayList<>();
        IBlockState state = world.getBlockState(pos);
        FluidloggingInference.refresh();
        ImpetusGameOptions.FluidloggingGuess option = ImpetusVintage.options().quality.inferredFluidlogging;
        lines.add("Fluidlogged probe at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ": " + state);
        lines.add(" option " + option + ", guess " + (FluidloggingInference.isActive() ? "active" : "inactive")
                + ", water renders as " + Blocks.WATER.getDefaultState().getRenderType());
        lines.add(" server data here: " + describe(FluidloggedUtils.getFluidState(world, pos)));

        FluidState above = raw(world, pos.up());
        lines.add(" above " + world.getBlockState(pos.up()) + ": " + describe(above));
        FluidState beside = FluidState.EMPTY;
        int sides = 0;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            FluidState next = raw(world, pos.offset(side));
            boolean counts = FluidloggingInference.spreads(next);
            if (counts) {
                sides++;
                beside = FluidloggingInference.fuller(beside, next);
            }
            lines.add(" " + side.getName() + " " + world.getBlockState(pos.offset(side)) + ": " + describe(next) + (counts || next.isEmpty() ? "" : " (falling, ignored)"));
        }
        lines.add(" fluids beside " + sides + ", option needs " + (option.minSides == Integer.MAX_VALUE ? "fluid above" : option.minSides + " or fluid above"));
        FluidState candidate = above.isValid() ? above.toSource() : beside;

        ChunkRenderContext context = WorldSlice.prepare(world, new SectionPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4), new ClonedChunkSectionCache(world));
        if (context == null) {
            lines.add(" the section is empty, so nothing is meshed here");
            return lines;
        }
        WorldSlice slice = new WorldSlice(world);
        slice.copyData(context);
        if (!candidate.isEmpty()) {
            lines.add(" " + describe(candidate) + ", is water " + (candidate.getState().getMaterial() == Material.WATER) + ", room for it to show " + FluidloggingInference.leavesRoom(state, slice, pos));
        }
        lines.add(" guessed into the slice: " + describe(slice.getFluidState(pos.getX(), pos.getY(), pos.getZ())));

        // What the fluid renderer sees: the cell itself, and whether each neighbouring fluid draws a face into it
        lines.add(" fluid renderer reads here: " + FluidloggedUtils.getFluidOrReal(slice, pos));
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos next = pos.offset(side);
            IBlockState neighbour = slice.getBlockState(next);
            if (FluidloggedUtils.isFluid(neighbour)) {
                lines.add(" " + side.getName() + " fluid draws a face into it: " + neighbour.shouldSideBeRendered(slice, next, side.getOpposite()));
            }
        }
        return lines;
    }

    // What the guess reads from the raw copy: a fluid block is its own fluid, anything else only counts through the server's data
    private static FluidState raw(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return FluidloggedUtils.isFluid(state) ? FluidState.of(state) : FluidloggedUtils.getFluidState(world, pos);
    }

    // The fluid's block state, or none
    private static String describe(FluidState fluid) {
        return fluid.isEmpty() ? "none" : String.valueOf(fluid.getState());
    }
}
