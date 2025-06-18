package org.spatium.CoreServer.commands.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TPAHandler {
    private final JavaPlugin plugin;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>(); // target -> sender
    private final Map<UUID, BukkitRunnable> expiryTasks = new HashMap<>();

    public TPAHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        registerCommand("tpa", new TPACommand());
        registerCommand("tpaccept", new TPAcceptCommand());
        registerCommand("tpdeny", new TPDenyCommand());

        new TpaListener(plugin, pendingRequests);
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
    }

    private class TPACommand implements TabExecutor {
        @Override
        public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, String @NotNull [] args) {
            if (!(sender instanceof Player player)) return false;
            if (args.length != 1) {
                player.sendMessage(Component.text("Usage: /tpa <player>").color(NamedTextColor.RED));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            assert target != null;

            if (target.equals(player) && plugin.getConfig().getBoolean("devMode")) {
                player.sendMessage(Component.text("Sending TPA to yourself for testing.").color(NamedTextColor.YELLOW));
                // Allow it for dev testing:
                pendingRequests.put(player.getUniqueId(), player.getUniqueId());
            } else if (target.equals(player) && !(plugin.getConfig().getBoolean("devMode"))) {
                player.sendMessage(Component.text("You can't teleport to yourself").color(NamedTextColor.RED));
                return true;
            }

            if (pendingRequests.containsKey(target.getUniqueId())) {
                player.sendMessage(Component.text("That player already has a pending request.").color(NamedTextColor.YELLOW));
                return true;
            }

            UUID senderId = player.getUniqueId();
            UUID targetId = target.getUniqueId();

            pendingRequests.put(targetId, senderId);

            // Expire in 60 seconds
            BukkitRunnable expiry = new BukkitRunnable() {
                @Override
                public void run() {
                    pendingRequests.remove(targetId);
                    expiryTasks.remove(targetId);
                    target.sendMessage(Component.text("TPA request from " + player.getName() + " expired.").color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("Your TPA request to " + target.getName() + " has expired.").color(NamedTextColor.GRAY));
                }
            };
            expiry.runTaskLater(plugin, 20 * 60);
            expiryTasks.put(targetId, expiry);

            // Notify target
            target.sendMessage(
                    Component.text("TPA request from " + player.getName()).color(NamedTextColor.AQUA)
                            .append(Component.newline())
                            .append(Component.text("[Accept]").color(NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept")))
                            .append(Component.text(" | "))
                            .append(Component.text("[Deny]").color(NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny")))
            );

            // Notify sender
            player.sendMessage(
                    Component.text("TPA sent to " + target.getName() + ". ")
                            .append(Component.text("[Click here to cancel]").color(NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny")))
                            .append(Component.text(" or run /tpdeny").color(NamedTextColor.GRAY))
            );
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String alias, String[] args) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            }
            return List.of();
        }
    }

    private class TPAcceptCommand implements TabExecutor {
        @Override
        public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, String @NotNull [] args) {
            if (!(sender instanceof Player target)) return false;

            UUID targetId = target.getUniqueId();
            UUID senderId = pendingRequests.remove(targetId);

            if (senderId == null) {
                target.sendMessage(Component.text("No TPA request to accept.").color(NamedTextColor.YELLOW));
                return true;
            }

            BukkitRunnable task = expiryTasks.remove(targetId);
            if (task != null) task.cancel();

            Player senderPlayer = Bukkit.getPlayer(senderId);
            if (senderPlayer == null) {
                target.sendMessage(Component.text("The requesting player is no longer online.").color(NamedTextColor.RED));
                return true;
            }

            senderPlayer.teleport(target);
            senderPlayer.sendMessage(Component.text("Teleported to " + target.getName()).color(NamedTextColor.GREEN));
            target.sendMessage(Component.text(senderPlayer.getName() + " has teleported to you.").color(NamedTextColor.GREEN));

            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String alias, String @NotNull [] args) {
            return List.of();
        }
    }

    private class TPDenyCommand implements TabExecutor {
        @Override
        public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, String @NotNull [] args) {
            if (!(sender instanceof Player target)) return false;

            UUID targetId = target.getUniqueId();
            UUID senderId = pendingRequests.remove(targetId);

            if (senderId == null) {
                target.sendMessage(Component.text("No TPA request to deny.").color(NamedTextColor.YELLOW));
                return true;
            }

            BukkitRunnable task = expiryTasks.remove(targetId);
            if (task != null) task.cancel();

            Player senderPlayer = Bukkit.getPlayer(senderId);
            if (senderPlayer != null) {
                senderPlayer.sendMessage(Component.text("Your TPA request to " + target.getName() + " was denied.").color(NamedTextColor.RED));
            }

            target.sendMessage(Component.text("TPA request denied.").color(NamedTextColor.RED));
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String alias, String @NotNull [] args) {
            return List.of();
        }
    }
}
