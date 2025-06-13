package org.spatium.CoreServer.types;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Record to encapsulate a linked Mojang + Discord user.
 */
public record LinkedUser(UUID mojang_uuid, String mojang_username, String discord_id, String discord_username, Timestamp linked_at) {}