package com.bdmajora.coarctatio.gui;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.MemoryReport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

// Command that prints the memory report.
public class CoarctatioStatsCommand extends CommandBase {
    // Returns the command name.
    @Override
    public String getName() {
        return "coarctatio";
    }

    // Returns the usage text.
    @Override
    public String getUsage(ICommandSender sender) {
        return "/coarctatio — report memory saved by Impetus' memory subsystem";
    }

    // Returns the permission level that every player has.
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // Sends the memory report and the statistics to the sender.
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        for (String line : MemoryReport.lines()) {
            sender.sendMessage(new TextComponentString(
                    (line.startsWith(" ") ? TextFormatting.GRAY : TextFormatting.AQUA) + line));
        }

        for (String line : Coarctatio.statistics()) {
            sender.sendMessage(new TextComponentString(TextFormatting.DARK_GRAY + line));
        }
    }
}
