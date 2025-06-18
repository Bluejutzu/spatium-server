package org.spatium.CoreServer.commands.player;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spatium.CoreServer.db.DatabaseManager;

import java.util.UUID;

public class UnlinkCommand implements CommandExecutor {
    private final DatabaseManager db;

    public UnlinkCommand(JavaPlugin plugin, DatabaseManager db) {
        this.db = db;
        PluginCommand cmd = plugin.getCommand("unlink");
        if (cmd != null) cmd.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        boolean result = db.unlinkUser(uuid);

        if (result) {
            player.sendMessage(NamedTextColor.YELLOW + "🔓 Your account has been unlinked.");
        } else {
            player.sendMessage(NamedTextColor.RED + "❌ Could not unlink your account.");
        }

        return true;
    }
}
