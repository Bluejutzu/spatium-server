package org.spatium.CoreServer.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spatium.CoreServer.types.LinkedUser;
import redis.clients.jedis.JedisPooled;

import java.net.URI;
import java.sql.*;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private final Connection postgres;
    private final JedisPooled redis;

    private static final ObjectMapper mapper = new ObjectMapper();

    public DatabaseManager(String pgUrl, String pgUser, String pgPassword, String redisUriString) throws Exception {
        this.postgres = DriverManager.getConnection(pgUrl, pgUser, pgPassword);
        this.redis = new JedisPooled(new URI(redisUriString));
    }

    private boolean isLikelyUUID(String id) {
        return id.matches("^[0-9a-fA-F]{32}$") || id.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private String toJson(LinkedUser user) throws JsonProcessingException {
        return mapper.writeValueAsString(user);
    }

    private LinkedUser parseCachedUser(String json) throws JsonProcessingException {
        return mapper.readValue(json, LinkedUser.class);
    }

    public void cacheRoles(UUID uuid, List<String> roles) {
        String redisKey = "roles:" + uuid;
        redis.del(redisKey);
        for (String role : roles) {
            redis.sadd(redisKey, role.toLowerCase());
        }
    }

    public LinkedUser getLinkedUser(String id) {
        boolean isMojangUuid = isLikelyUUID(id);
        String redisKey = isMojangUuid ? "link:m:" + id : "link:d:" + id;
        String cachedJson = redis.get(redisKey);

        if (cachedJson != null) {
            try {
                return parseCachedUser(cachedJson);
            } catch (Exception e) {
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

                String json = toJson(user); // Cache for both keys
                redis.setex("link:m:" + user.mojang_uuid(), 3600, json);
                redis.setex("link:d:" + user.discord_id(), 3600, json);
                return user;
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public String getDiscordId(UUID uuid) {
        LinkedUser user = getLinkedUser(uuid.toString());
        return user != null ? user.discord_id() : null;
    }

    public boolean hasDiscordRoleCached(UUID uuid, String requiredRole) {
        String discordId = getDiscordId(uuid);
        return discordId != null && redis.sismember("roles:" + discordId, requiredRole.toLowerCase());
    }

    /**
     * Inserts a new LinkedUser or updates an existing one, and caches it.
     *
     * @return boolean
     */
    public boolean linkAccounts(UUID mojangUUID, String mojangUsername, String discordId, String discordUsername) {
        String upsert = """
                MERGE INTO users AS target
                USING (VALUES (?, ?, ?, ?)) AS source (mojang_uuid, mojang_username, discord_id, discord_username)
                ON target.mojang_uuid = source.mojang_uuid
                WHEN MATCHED THEN
                    UPDATE SET 
                        mojang_username = source.mojang_username,
                        discord_id = source.discord_id,
                        discord_username = source.discord_username,
                        linked_at = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN
                    INSERT (mojang_uuid, mojang_username, discord_id, discord_username, linked_at)
                    VALUES (source.mojang_uuid, source.mojang_username, source.discord_id, source.discord_username, CURRENT_TIMESTAMP);
                """;

        try (PreparedStatement stmt = postgres.prepareStatement(upsert)) {
            stmt.setObject(1, mojangUUID);
            stmt.setString(2, mojangUsername);
            stmt.setObject(3, discordId); // If stored as UUID
            stmt.setString(4, discordUsername); // optional, or fetch via Discord API
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Optional cache
        LinkedUser user = new LinkedUser(mojangUUID, mojangUsername, discordId, discordUsername, new Timestamp(System.currentTimeMillis()));
        try {
            String json = toJson(user);
            redis.setex("link:m:" + mojangUUID, 3600, json);
            redis.setex("link:d:" + discordId, 3600, json);
            return true;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Links a Minecraft account to a Discord account using a verification code from Redis.
     *
     * @param mojangUuid     The UUID of the Minecraft player
     * @param mojangUsername The username of the Minecraft player
     * @param code           The verification code provided by the Discord bot
     * @return true if linking was successful, false otherwise
     */
    public boolean linkAccountsFromCode(UUID mojangUuid, String mojangUsername, String code) {
        String json = redis.get("verify:" + code);
        if (json == null) return false;

        try {
            JsonNode node = mapper.readTree(json);
            String discordId = node.get("discord_id").asText();
            String discordUsername = node.get("discord_username").asText();

            boolean success = linkAccounts(mojangUuid, mojangUsername, discordId, discordUsername);
            if (success) redis.del("verify:" + code);
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean unlinkUser(UUID mojangUUID) {
        String query = "DELETE FROM users WHERE mojang_uuid = ?";
        try (PreparedStatement stmt = postgres.prepareStatement(query)) {
            stmt.setObject(1, mojangUUID);
            stmt.executeUpdate();
            if (Boolean.parseBoolean(redis.get("link:m:" + mojangUUID))) {
                redis.del("link:m:" + mojangUUID);
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        try {
            postgres.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        redis.close();
    }
}