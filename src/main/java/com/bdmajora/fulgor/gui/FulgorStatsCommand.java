package com.bdmajora.fulgor.gui;

import com.bdmajora.fulgor.Fulgor;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

// Command that prints the async light counters.
public class FulgorStatsCommand extends CommandBase {
    // Returns the name.
    @Override
    public String getName() {
        return "fulgor";
    }

    // Returns the usage text.
    @Override
    public String getUsage(ICommandSender sender) {
        return "/fulgor — report the work done by Impetus' lighting subsystem";
    }

    // Returns the required permission level.
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // Prints the counters to the sender.
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        for (String line : Fulgor.statistics()) {
            sender.sendMessage(new TextComponentString(
                    (line.startsWith(" ") ? TextFormatting.GRAY : TextFormatting.AQUA) + line));
        }
    }
}
