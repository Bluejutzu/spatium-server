package org.spatium.testplugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.jetbrains.annotations.NotNull;

public class HelloCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§aHello from your plugin! " + sender.getName() + " used the command " + command.getName());
        return true;
    }
}