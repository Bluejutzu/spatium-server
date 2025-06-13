package org.spatium.CoreServer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.spatium.CoreServer.Core;
import org.spatium.CoreServer.db.DatabaseManager;

import java.util.UUID;

public class DbTestCommand implements CommandExecutor {
    private final Core plugin;

    public DbTestCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /dbtest <minecraft-uuid>");
            return true;
        }

        try {
            UUID uuid = UUID.fromString(args[0]);
            sender.sendMessage("UUID: " + uuid);
            DatabaseManager db = plugin.getDatabaseManager();

            String discordId = db.getDiscordId(uuid);
            boolean hasAdmin = db.hasDiscordRoleCached(uuid, "admin");

            sender.sendMessage("Discord ID: " + discordId);
            sender.sendMessage("Has 'admin' role: " + hasAdmin);
        } catch (Exception e) {
            sender.sendMessage("§cError: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}
