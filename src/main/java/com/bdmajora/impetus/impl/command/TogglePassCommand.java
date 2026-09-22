package com.bdmajora.impetus.impl.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Debug command to enable/disable a specific terrain render pass at runtime, e.g. for isolating shader issues
public class TogglePassCommand extends CommandBase {
    // Zero; a client-side debug command
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // Command name
    @Override
    public String getName() {
        return "impetus_toggle_pass";
    }

    // Shown by /help
    @Override
    public String getUsage(ICommandSender sender) {
        return "/impetus_toggle_pass [pass_name]";
    }

    // Every pass the current configuration defines
    private static Stream<TerrainRenderPass> getAllPasses() {
        var renderer = ImpetusWorldRenderer.instanceNullable();

        // renderer isn't initialized yet (e.g. command ran before world load), so no passes exist
        if (renderer == null) {
            return Stream.empty();
        }

        return renderer.getRenderPassConfiguration().getAllKnownRenderPasses();
    }

    // Pass names
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        return getAllPasses().map(TerrainRenderPass::name).collect(Collectors.toList());
    }

    // Flips one pass's enabled flag for debugging
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if(args.length < 1) {
            sender.sendMessage(new TextComponentString("Pass name must be provided"));
            return;
        }

        Optional<TerrainRenderPass> foundPass = getAllPasses().filter(pass -> pass.name().equals(args[0])).findFirst();
        if (foundPass.isPresent()) {
            ImpetusWorldRenderer.instance().getRenderSectionManager().toggleRenderingForTerrainPass(foundPass.get());
        } else {
            sender.sendMessage(new TextComponentString("Pass " + args[0] + " not found"));
        }
    }
}
