package org.spatium.CoreServer.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spatium.CoreServer.types.LinkedUser;
import redis.clients.jedis.JedisPooled;

import java.net.URI;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {
    private final Connection postgres;
    private final JedisPooled redis;

    private boolean isLikelyUUID(String id) {
        return id.matches("^[0-9a-fA-F]{32}$") || id.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    private String toJson(LinkedUser user) throws JsonProcessingException {
        return mapper.writeValueAsString(user);
    }

    private LinkedUser parseCachedUser(String json) throws JsonProcessingException {
        return mapper.readValue(json, LinkedUser.class);
    }

    public DatabaseManager(String pgUrl, String pgUser, String pgPassword, String redisUriString) throws Exception {
        this.postgres = DriverManager.getConnection(pgUrl, pgUser, pgPassword);

        // Parse Redis URI (supports redis://user:pass@host:port)
        URI redisUri = new URI(redisUriString);
        this.redis = new JedisPooled(redisUri);
    }

    public LinkedUser getLinkedUser(String id) {
        boolean isMojangUuid = isLikelyUUID(id);
        String redisKey = isMojangUuid ? "link:m:" + id : "link:d:" + id;
        String cachedJson = redis.get(redisKey);

        if (cachedJson != null) {
            try {
                return parseCachedUser(cachedJson);
            } catch (Exception e) {
                e.printStackTrace();
                redis.del(redisKey); // Invalidate corrupted cache
            }
        }

        String query = isMojangUuid
                ? "SELECT * FROM users WHERE mojang_uuid = ?::uuid"
                : "SELECT * FROM users WHERE discord_id = ?::uuid";

        try (PreparedStatement stmt = postgres.prepareStatement(query)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                LinkedUser user = new LinkedUser(
                        UUID.fromString(rs.getString("mojang_uuid")),
                        rs.getString("mojang_username"),
                        rs.getString("discord_id"),
                        rs.getString("discord_username"),
                        rs.getTimestamp("linked_at")
                );

                String json = toJson(user); // Serialize for caching
                redis.setex("link:m:" + user.mojang_uuid(), 3600, json);
                redis.setex("link:d:" + user.discord_id(), 3600, json);
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return null;
    }


    /**
     * Compatibility method – returns only Discord ID.
     */
    public String getDiscordId(UUID uuid) {
        LinkedUser user = getLinkedUser(uuid.toString());
        return user != null ? user.discord_id() : null;
    }

    /**
     * Check if a cached Discord ID has a specific role.
     */
    public boolean hasDiscordRoleCached(UUID uuid, String requiredRole) {
        String discordId = getDiscordId(uuid);
        if (discordId == null) return false;
        return redis.sismember("roles:" + discordId, requiredRole.toLowerCase());
    }

    public void close() {
        try {
            postgres.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        redis.close();
    }
}
