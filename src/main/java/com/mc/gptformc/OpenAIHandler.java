package com.mc.gptformc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpenAIHandler {
    private final GPTforMC plugin;
    private final String apiUrl;
    private final String apiKey;

    public OpenAIHandler(GPTforMC plugin) {
        this.plugin = plugin;
        this.apiUrl = plugin.getConfigManager().getConfig().getString("openai.api-url", "https://api.openai.com/v1/chat/completions");
        this.apiKey = plugin.getConfigManager().getConfig().getString("openai.api-key", "");
        if (apiKey.isEmpty()) {
            plugin.getLogger().warning("OpenAI API Key 未配置");
        }
    }

    public void handleChat(Player player, String message) {
        AIManager.AIConfig aiConfig = plugin.getAIManager().findTriggeredAI(message);
        if (aiConfig == null) {
            return;
        }
        String aiName = aiConfig.getName();
        boolean isFirstConversation = aiConfig.getMemories().isEmpty();
        plugin.getLogger().info("触发AI: " + aiName);
        String formattedMessage = aiConfig.getMessageFormat()
                .replace("{player}", player.getName())
                .replace("{message}", message);
        new BukkitRunnable() {
            @Override
            public void run() {
                String response = sendOpenAIRequest(player, aiConfig, formattedMessage, isFirstConversation);
                if (response != null) {
                    String coloredResponse = ChatColor.translateAlternateColorCodes('&', aiConfig.getMessageColor() + "[" + aiName + "] " + response);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.getServer().broadcastMessage(coloredResponse);
                    });
                    plugin.getAIManager().addMemory(aiName, formattedMessage, response, isFirstConversation);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private String sendOpenAIRequest(Player player, AIManager.AIConfig aiConfig, String userInput, boolean isFirstConversation) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(aiConfig.getTimeout());
            conn.setReadTimeout(aiConfig.getTimeout());
            conn.setDoOutput(true);

            JsonArray messages = new JsonArray();
            if (aiConfig.isForceSystemPrompt()) {
                for (AIManager.Prompt prompt : aiConfig.getPrompts()) {
                    JsonObject promptMsg = new JsonObject();
                    promptMsg.addProperty("role", prompt.getRole());
                    promptMsg.addProperty("content", prompt.getContent());
                    messages.add(promptMsg);
                }
            }

            List<AIManager.Memory> memories = aiConfig.getMemories();
            for (int i = Math.max(0, memories.size() - aiConfig.getMaxMemory() * 2); i < memories.size(); i++) {
                AIManager.Memory memory = memories.get(i);
                JsonObject memMsg = new JsonObject();
                memMsg.addProperty("role", memory.getRole());
                memMsg.addProperty("content", memory.getContent());
                messages.add(memMsg);
            }

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userInput);
            messages.add(userMsg);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", aiConfig.getModel());
            requestBody.add("messages", messages);
            requestBody.addProperty("max_tokens", aiConfig.getMaxTokens());
            requestBody.addProperty("temperature", aiConfig.getTemperature());
            requestBody.addProperty("top_p", aiConfig.getTopP());
            requestBody.addProperty("presence_penalty", aiConfig.getPresencePenalty());
            requestBody.addProperty("frequency_penalty", aiConfig.getFrequencyPenalty());

            String jsonInput = requestBody.toString();
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
                plugin.getLogger().warning("OpenAI请求失败: " + responseCode + " - " + errorResponse);
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                plugin.getLogger().warning("OpenAI响应缺少choices数组");
                return null;
            }
            return choices.get(0).getAsJsonObject()
                    .get("message").getAsJsonObject()
                    .get("content").getAsString();
        } catch (Exception e) {
            plugin.getLogger().severe("OpenAI请求错误: " + e.getMessage());
            return null;
        }
    }
}