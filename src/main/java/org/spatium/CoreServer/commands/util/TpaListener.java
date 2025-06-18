package org.spatium.CoreServer.commands.util;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class TpaListener implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, UUID> pendingRequests;

    public TpaListener(JavaPlugin plugin, Map<UUID, UUID> pendingRequests) {
        this.plugin = plugin;
        this.pendingRequests = pendingRequests;
        register(); // Register listener internally
    }

    private void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        pendingRequests.entrySet().removeIf(entry ->
                entry.getKey().equals(playerId) || entry.getValue().equals(playerId)
        );
    }
}
