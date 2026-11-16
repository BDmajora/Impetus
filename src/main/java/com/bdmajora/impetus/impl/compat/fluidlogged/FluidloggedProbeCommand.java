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

// Command that prints the fluid state guesses at a block.
public class FluidloggedProbeCommand extends CommandBase {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Fluidlogged");

    // Returns the name.
    @Override
    public String getName() {
        return "impetus_fluidlog";
    }

    // Returns the usage text.
    @Override
    public String getUsage(ICommandSender sender) {
        return "/impetus_fluidlog [x y z] - explain the fluidlogging guess at the looked-at block";
    }

    // Returns the required permission level.
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // Prints the report of the targeted block.
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft client = Minecraft.getMinecraft();
        BlockPos pos;
        if (args.length >= 3) {
            pos = parseBlockPos(sender, args, 0, false);
        } else if (client.objectMouseOver != null && client.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            pos = client.objectMouseOver.getBlockPos();
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

    // Returns the report lines of a block.
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

    // Returns the stored fluid state of a block.
    private static FluidState raw(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return FluidloggedUtils.isFluid(state) ? FluidState.of(state) : FluidloggedUtils.getFluidState(world, pos);
    }

    // Returns the text of a fluid state.
    private static String describe(FluidState fluid) {
        return fluid.isEmpty() ? "none" : String.valueOf(fluid.getState());
    }
}
