package com.bdmajora.equilibrium.gui;

import com.bdmajora.equilibrium.Equilibrium;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

// Command that prints which patches are active.
public class EquilibriumStatsCommand extends CommandBase {
    // Returns the command name.
    @Override
    public String getName() {
        return "equilibrium";
    }

    // Returns the usage text.
    @Override
    public String getUsage(ICommandSender sender) {
        return "/equilibrium — report which of Impetus' general performance patches are active";
    }

    // Returns the permission level that every player has.
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // Sends the statistics to the sender.
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        for (String line : Equilibrium.statistics()) {
            sender.sendMessage(new TextComponentString(
                    (line.startsWith(" ") ? TextFormatting.GRAY : TextFormatting.AQUA) + line));
        }
    }
}
