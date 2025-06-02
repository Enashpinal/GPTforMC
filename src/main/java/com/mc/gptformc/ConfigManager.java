package com.mc.gptformc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final GPTforMC plugin;
    private File configFile;
    private FileConfiguration config;

    public ConfigManager(GPTforMC plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    private void setupConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存配置文件: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("配置文件已重载");
    }

    public void setGlobalConfig(Player player, String option, String[] values) {
        if (player != null && !hasPermission(player, "gpt.config") && !hasPermission(player, "gpt.admin")) {
            player.sendMessage("你没有权限修改全局配置");
            return;
        }
        try {
            switch (option.toLowerCase()) {
                case "api-url":
                    config.set("openai.api-url", values[0]);
                    break;
                case "api-key":
                    config.set("openai.api-key", values[0]);
                    break;
                case "model":
                    config.set("openai.model", values[0]);
                    break;
                case "timeout":
                    config.set("openai.timeout", Integer.parseInt(values[0]));
                    break;
                case "memory_rounds":
                    config.set("openai.memory_rounds", Integer.parseInt(values[0]));
                    break;
                case "temperature":
                    config.set("openai.temperature", Double.parseDouble(values[0]));
                    break;
                case "top_p":
                    config.set("openai.top_p", Double.parseDouble(values[0]));
                    break;
                case "max_tokens":
                    config.set("openai.max_tokens", Integer.parseInt(values[0]));
                    break;
                case "presence_penalty":
                    config.set("openai.presence_penalty", Double.parseDouble(values[0]));
                    break;
                case "frequency_penalty":
                    config.set("openai.frequency_penalty", Double.parseDouble(values[0]));
                    break;
                case "force_system_prompt":
                    config.set("openai.force_system_prompt", Boolean.parseBoolean(values[0]));
                    break;
                case "message_color":
                    config.set("openai.message_color", values[0]);
                    break;
                case "message_format":
                    config.set("openai.message_format", String.join(" ", values));
                    break;
                default:
                    if (player != null) {
                        player.sendMessage("未知配置选项: " + option);
                    } else {
                        plugin.getLogger().info("未知配置选项: " + option);
                    }
                    return;
            }
            saveConfig();
            if (player != null) {
                player.sendMessage("全局配置 " + option + " 已设置为: " + String.join(" ", values));
            } else {
                plugin.getLogger().info("全局配置 " + option + " 已设置为: " + String.join(" ", values));
            }
        } catch (Exception e) {
            if (player != null) {
                player.sendMessage("设置失败: " + e.getMessage());
            } else {
                plugin.getLogger().info("设置失败: " + e.getMessage());
            }
        }
    }

    public String getPlayerGroup(Player player) {
        return player.isOp() ? "admin" : "default";
    }

    private boolean hasPermission(Player player, String permission) {
        if (player == null || player.isOp()) return true;
        String group = getPlayerGroup(player);
        return config.getBoolean("permission-groups." + group + "." + permission, false);
    }
}
