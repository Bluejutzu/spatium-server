package org.spatium.CoreServer.commands;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spatium.CoreServer.Core;
import org.spatium.CoreServer.db.DatabaseManager;

import java.util.UUID;
import java.util.logging.Level;

public class DbTestCommand implements CommandExecutor {
    private final Core plugin;

    public DbTestCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /dbtest <minecraft-uuid>/<player>");
            return true;
        }

        try {
            UUID uuid;
            try {
                // Try parsing as a UUID directly
                uuid = UUID.fromString(args[0]);
            } catch (IllegalArgumentException e) {
                // Not a UUID, so try resolving it as a player name
                Player player = Bukkit.getPlayer(args[0]);
                if (player == null) {
                    sender.sendMessage(NamedTextColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                uuid = player.getUniqueId();
            }

            sender.sendMessage("UUID: " + uuid);
            DatabaseManager db = plugin.getDatabaseManager();

            String discordId = db.getDiscordId(uuid);
            boolean hasAdmin = db.hasDiscordRoleCached(uuid, "admin");

            sender.sendMessage("Discord ID: " + discordId);
            sender.sendMessage("Has 'admin' role: " + hasAdmin);
        } catch (Exception e) {
            sender.sendMessage("[/" + label + "] §cError using: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "An error occurred while processing the command", e);
        }

        return true;
    }
}
