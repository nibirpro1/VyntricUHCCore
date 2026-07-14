package com.vyntric.uhccore.discord;

import com.vyntric.uhccore.VyntricUHCCore;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Fires simple messages to a Discord webhook URL (configured under discord.webhook-url).
 * Every send happens off the main thread so a slow/unreachable webhook never causes
 * server lag or a TPS drop.
 */
public class DiscordWebhook {

    private final VyntricUHCCore plugin;

    public DiscordWebhook(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    /** Sends a plain text message to the configured webhook, if Discord integration is enabled. */
    public void send(String content) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;

        String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isBlank() || url.contains("YOUR_WEBHOOK")) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> sendBlocking(url, content));
    }

    private void sendBlocking(String url, String content) {
        try {
            String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            String json = "{\"content\":\"" + escaped + "\"}";

            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + code);
            }
            connection.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
        }
    }
}
