package org.spatium.CoreServer;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.spatium.CoreServer.Listeners.PlayerDropItem;
import org.spatium.CoreServer.commands.DbTestCommand;
import org.spatium.CoreServer.commands.HelloCommand;
import org.spatium.CoreServer.db.DatabaseManager;

public class Core extends JavaPlugin implements Listener {
    private DatabaseManager db;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlayerDropItem(this), this);

        try {
            Class.forName("org.postgresql.Driver");

            String pgUrl = System.getProperty("PG_URL");
            String pgUser = System.getProperty("PG_USER");
            String pgPassword = System.getProperty("PG_PASSWORD");
            String redisUri = System.getProperty("REDIS_URL");
            String redisToken = System.getProperty("REDIS_TOKEN");

            if (pgUrl == null) pgUrl = System.getenv("PG_URL");
            if (pgUser == null) pgUser = System.getenv("PG_USER");
            if (pgPassword == null) pgPassword = System.getenv("PG_PASSWORD");
            if (redisUri == null) redisUri = System.getenv("REDIS_URL");

            db = new DatabaseManager(pgUrl, pgUser, pgPassword, redisUri);
            getLogger().info("DatabaseManager loaded.");
        } catch (Exception e) {
            getLogger().severe("Failed to connect to DB/Redis: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }

        getCommand("hello").setExecutor(new HelloCommand());
        getCommand("dbtest").setExecutor(new DbTestCommand(this));
    }

    @Override
    public void onDisable() {
        if (db != null) db.close();
    }

    public DatabaseManager getDatabaseManager() {
        return db;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }
}