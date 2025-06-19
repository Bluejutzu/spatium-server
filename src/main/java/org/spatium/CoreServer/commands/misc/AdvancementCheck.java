package org.spatium.CoreServer.commands.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spatium.CoreServer.db.DatabaseManager;
import redis.clients.jedis.JedisPooled;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AdvancementCheck implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final JedisPooled redis;

    private JsonNode parseJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(file);
    }

    public AdvancementCheck(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.redis = db.getRedis();

        PluginCommand cmd = plugin.getCommand("advancement");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /advancement <check|add|remove> <player> <key>", NamedTextColor.RED));
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        String key = args.length >= 3 ? args[2] : null;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        File advancementFile = new File(worldFolder, "advancements/" + target.getUniqueId() + ".json");

        plugin.getLogger().info("Advancement file path: " + advancementFile.getPath());

        try {
            JsonNode root = parseJson(advancementFile);

            switch (action) {
                case "check" -> {
                    if (key == null) {
                        sender.sendMessage(Component.text("Missing key for check action.", NamedTextColor.RED));
                        return true;
                    }
                    JsonNode advancement = root.get(key);
                    boolean completed = advancement != null && advancement.get("done") != null && advancement.get("done").asBoolean();
                    sender.sendMessage(Component.text("Advancement '" + key + "': " + (completed ? "✔ completed" : "❌ not completed"), NamedTextColor.YELLOW));
                }
                case "add", "remove" -> {
                    if (!sender.isOp()) {
                        sender.sendMessage(Component.text("You don't have permission to perform this action.", NamedTextColor.RED));
                        return true;
                    }

                    if (key == null) {
                        sender.sendMessage(Component.text("Missing key.", NamedTextColor.RED));
                        return true;
                    }

                    if (action.equals("add")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement grant " + target.getName() + " only " + key);
                        sender.sendMessage(Component.text("✅ Granted advancement.", NamedTextColor.GREEN));
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke " + target.getName() + " only " + key);
                        sender.sendMessage(Component.text("❌ Revoked advancement.", NamedTextColor.RED));
                    }
                }
                default -> {
                    sender.sendMessage(Component.text("Unknown action. Use check, add or remove.", NamedTextColor.RED));
                }
            }

        } catch (IOException e) {
            sender.sendMessage(Component.text("Failed to read advancement data.", NamedTextColor.RED));
           throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return List.of("check", "add", "remove");
        }

        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                String redisKey = "advkeys:" + target.getUniqueId();
                if (redis.exists(redisKey)) {
                    return redis.smembers(redisKey).stream()
                            .filter(k -> k.startsWith(args[2]))
                            .toList();
                } else {
                    File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
                    File advFile = new File(worldFolder, "advancements/" + target.getUniqueId() + ".json");

                    if (advFile.exists()) {
                        try {
                            JsonNode root = parseJson(advFile);
                            Set<String> keys = new HashSet<>();
                            root.fieldNames().forEachRemaining(keys::add);
                            if (!keys.isEmpty()) redis.sadd(redisKey, keys.toArray(new String[0]));
                            redis.expire(redisKey, 300);
                            return keys.stream()
                                    .filter(k -> k.startsWith(args[2]))
                                    .toList();
                        } catch (IOException e) {
                            plugin.getLogger().warning("Failed to parse advancement file for tab complete.");
                        }
                    }
                }
            }
        }

        return List.of();
    }
}
