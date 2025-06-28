package org.spatium.CoreServer.Discord;

import okhttp3.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordApi {
    private final OkHttpClient client = new OkHttpClient();
    private final String DISCORD_API_BASE = "https://discord.com/api/v10";
    private final String botToken;

    public DiscordApi(String botToken) {
        this.botToken = botToken;
    }

    public CompletableFuture<String> getUserAsync(String userId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Request request = new Request.Builder()
                .url(this.DISCORD_API_BASE + "/users/" + userId)
                .header("Authorization", "Bot " + botToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    future.completeExceptionally(new IOException("Request failed with code: " + response.code()));
                    return;
                }

                future.complete(response.body().string());
            }
        });

        return future;
    }

    public CompletableFuture<String> sendPlayerListEmbed(Collection<? extends Player> players) {
        CompletableFuture<String> future = new CompletableFuture<>();

        StringBuilder fieldsBuilder = new StringBuilder();
        Iterator<? extends Player> iterator = players.iterator();

        while (iterator.hasNext()) {
            Player player = iterator.next();
            String name = player.getName();
            UUID uuid = player.getUniqueId();

            String field = String.format("""
                    {
                      "name": "%s",
                      "value": "[%s](https://crafatar.com/avatars/%s)",
                      "inline": true
                    }""", name, uuid.toString(), uuid.toString());

            fieldsBuilder.append(field);

            if (iterator.hasNext()) {
                fieldsBuilder.append(",\n");
            }
        }

        if (players.isEmpty()) {
            fieldsBuilder.append("No player online");
        }

        String jsonPayload = String.format("""
                {
                  "embeds": [
                    {
                      "title": "Online Players",
                      "description": "Here is a list of players currently online.",
                      "fields": [
                        %s
                      ],
                      "color": 5814783
                    }
                  ]
                }
                """, fieldsBuilder.toString());

        Request request = new Request.Builder()
                .url(this.DISCORD_API_BASE + "/channels/1168943371981697024/messages")
                .post(RequestBody.create(jsonPayload, MediaType.parse("application/json")))
                .addHeader("Authorization", "Bot " + this.botToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try (response) {  // This try-with-resources automatically closes response
                    if (response.isSuccessful()) {
                        future.complete("✅ Message sent successfully");
                    } else {
                        future.completeExceptionally(new IOException("❌ Failed with status code: " + response.code()));
                    }
                }
            }
        });

        return future;
    }

}
