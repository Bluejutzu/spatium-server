package org.spatium.CoreServer.commands.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spatium.CoreServer.db.DatabaseManager;

import java.util.UUID;

public class VerifyCommand implements CommandExecutor {

    private final DatabaseManager db;

    public VerifyCommand(JavaPlugin plugin, DatabaseManager db) {
        this.db = db;

        PluginCommand cmd = plugin.getCommand("link");
        if (cmd != null) {
            cmd.setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(NamedTextColor.RED + "Usage: /link <code>");
            return true;
        }

        String code = args[0];
        UUID mojangUuid = player.getUniqueId();
        String mojangUsername = player.getName();

        boolean success = db.linkAccountsFromCode(mojangUuid, mojangUsername, code);
        if (success) {
            player.sendMessage(Component.text("Your Discord account has been linked!").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Invalid or expired code. Please try again.").color(NamedTextColor.RED));
        }

        return true;
    }
}
