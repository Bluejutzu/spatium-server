package org.spatium.CoreServer.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.spatium.CoreServer.Core;
import org.spatium.CoreServer.db.DatabaseManager;
import org.spatium.CoreServer.types.LinkedUser;

public class PlayerDropItem implements Listener {
    private final Core plugin;

    public PlayerDropItem(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        DatabaseManager db = plugin.getDatabaseManager();
        LinkedUser user = db.getLinkedUser(player.getUniqueId().toString());

        if (droppedItem.getType().name().equalsIgnoreCase("DIAMOND")) {
            player.sendMessage("You cannot drop diamonds!");
            event.setCancelled(true);
        } else {
            player.sendMessage("You dropped: " + droppedItem.getType().name());
        }

        if (user != null) {
            player.sendMessage(
                    user.discord_id() + " " +
                            user.discord_username() + " " +
                            user.mojang_uuid() + " " +
                            user.mojang_username()
            );
        } else {
            player.sendMessage("Your Discord account is not linked.");
        }
    }
}
