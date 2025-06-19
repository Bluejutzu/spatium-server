package org.spatium.CoreServer;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.spatium.CoreServer.Listeners.Player.PlayerDropItem;
import org.spatium.CoreServer.commands.DbTestCommand;
import org.spatium.CoreServer.commands.HelloCommand;
import org.spatium.CoreServer.commands.misc.AdvancementCheck;
import org.spatium.CoreServer.commands.player.UnlinkCommand;
import org.spatium.CoreServer.commands.player.VerifyCommand;
import org.spatium.CoreServer.commands.util.TPAHandler;
import org.spatium.CoreServer.db.DatabaseManager;

import java.util.Objects;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;

public class Core extends JavaPlugin implements Listener {
    private DatabaseManager db;

    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (!(plugin instanceof CoreProtect)) {
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (!CoreProtect.isEnabled()) {
            return null;
        }

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 10) {
            return null;
        }

        return CoreProtect;
    }

    public String getPublicIp() {
        try {
            URL url = URI.create("https://api.ipify.org").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
            );
            String ip = in.readLine();
            in.close();
            return ip;
        } catch (Exception e) {
            this.getLogger().warning("Could not connect to IP address " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        try {
            Class.forName("org.postgresql.Driver");

            String pgUrl = System.getProperty("PG_URL");
            String pgUser = System.getProperty("PG_USER");
            String pgPassword = System.getProperty("PG_PASSWORD");
            String redisUri = System.getProperty("REDIS_URL");

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

        // Events/Commands (internal event-register)
        new PlayerDropItem(this);
        new TPAHandler(this);
        new VerifyCommand(this, db);
        new UnlinkCommand(this, db);
        new AdvancementCheck(this, db);
        Objects.requireNonNull(getCommand("hello")).setExecutor(new HelloCommand());
        Objects.requireNonNull(getCommand("dbtest")).setExecutor(new DbTestCommand(this));

        if (getConfig().getBoolean("devMode")) {
            getLogger().warning("Development mode is ENABLED. Some commands may work different or may not work.");
        }
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

        CoreProtectAPI api = getCoreProtect();
        if (api == null) {
            getLogger().warning("[CoreProtect] API is not available.");
        } else {
            getLogger().info("[CoreProtect] API loaded successfully.");
        }
    }
}