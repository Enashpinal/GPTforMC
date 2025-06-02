package com.mc.gptformc;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AIManager {
    private final GPTforMC plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;
    private final Map<String, AIConfig> aiConfigs = new HashMap<>();

    public AIManager(GPTforMC plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ai_data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("ai_data.yml", false);
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadAIs();
    }

    public void createAI(Player player, String name) {
        if (player != null && !hasPermission(player, "gpt.create")) {
            player.sendMessage("你没有权限创建AI");
            return;
        }
        String group = player != null ? plugin.getConfigManager().getPlayerGroup(player) : "admin";
        int maxAIs = plugin.getConfigManager().getConfig().getInt("permission-groups." + group + ".max-ai", 0);
        long currentAIs = aiConfigs.size();
        if (player != null && currentAIs >= maxAIs && !hasPermission(player, "gpt.bypass")) {
            player.sendMessage("已达到AI数量上限: " + maxAIs);
            return;
        }
        if (aiConfigs.containsKey(name)) {
            if (player != null) {
                player.sendMessage("AI名称 " + name + " 已存在");
            } else {
                plugin.getLogger().info("AI名称 " + name + " 已存在");
            }
            return;
        }
        boolean allowDefaultEdit = player != null && !hasPermission(player, "gpt.admin");
        AIConfig config = new AIConfig(
                name,
                new ArrayList<>(Collections.singletonList(new TriggerWord(name, 1))),
                plugin.getConfigManager().getConfig().getDouble("openai.temperature", 0.7),
                plugin.getConfigManager().getConfig().getDouble("openai.top_p", 1.0),
                plugin.getConfigManager().getConfig().getInt("openai.max_tokens", 500),
                plugin.getConfigManager().getConfig().getDouble("openai.presence_penalty", 0.0),
                plugin.getConfigManager().getConfig().getDouble("openai.frequency_penalty", 0.0),
                plugin.getConfigManager().getConfig().getBoolean("openai.force_system_prompt", false),
                plugin.getConfigManager().getConfig().getInt("openai.memory_rounds", 4),
                plugin.getConfigManager().getConfig().getString("openai.model", "gpt-4o-mini"),
                plugin.getConfigManager().getConfig().getInt("openai.timeout", 10000),
                new ArrayList<>(),
                new ArrayList<>(),
                plugin.getConfigManager().getConfig().getString("openai.message_color", "&a"),
                plugin.getConfigManager().getConfig().getString("openai.message_format", "用户{player}说：{message}"),
                allowDefaultEdit
        );
        aiConfigs.put(name, config);
        saveAI(name);
        if (player != null) {
            player.sendMessage("成功创建AI: " + name);
        } else {
            plugin.getLogger().info("成功创建AI: " + name);
        }
    }

    public void removeAI(Player player, String name) {
        AIConfig config = aiConfigs.get(name);
        if (config == null) {
            if (player != null) {
                player.sendMessage("AI " + name + " 不存在");
            } else {
                plugin.getLogger().info("AI " + name + " 不存在");
            }
            return;
        }
        if (player != null && !hasPermission(player, "gpt.admin") && !hasPermission(player, "gpt.remove")) {
            if (!config.isAllowDefaultEdit()) {
                player.sendMessage("你无权删除此AI");
                return;
            }
        }
        aiConfigs.remove(name);
        saveAI(name);
        if (player != null) {
            player.sendMessage("成功删除AI: " + name);
        } else {
            plugin.getLogger().info("成功删除AI: " + name);
        }
    }

    public List<String> listAIs(Player player, boolean isAdmin) {
        if (player == null || isAdmin) {
            return new ArrayList<>(aiConfigs.keySet());
        }
        return new ArrayList<>(aiConfigs.keySet());
    }

    public AIConfig findTriggeredAI(String message) {
        String lowerMessage = message.toLowerCase();
        return aiConfigs.values().stream()
                .filter(config -> config.getTriggerWords().stream()
                        .anyMatch(tw -> lowerMessage.contains(tw.getWord().toLowerCase())))
                .max(Comparator.comparingInt(config -> config.getTriggerWords().stream()
                        .mapToInt(TriggerWord::getPriority)
                        .max()
                        .orElse(0)))
                .orElse(null);
    }

    public void addMemory(String aiName, String userInput, String aiResponse, boolean isFirstConversation) {
        AIConfig config = aiConfigs.get(aiName);
        if (config != null) {
            if (isFirstConversation) {
                for (Prompt prompt : config.getPrompts()) {
                    config.getMemories().add(new Memory(prompt.getRole(), prompt.getContent()));
                }
            }
            config.getMemories().add(new Memory("user", userInput));
            config.getMemories().add(new Memory("assistant", aiResponse));
            if (config.getMemories().size() > config.getMaxMemory() * 2) {
                if (config.isForceSystemPrompt()) {
                    List<Memory> systemMemories = config.getMemories().stream()
                            .filter(m -> m.getRole().equals("system"))
                            .collect(Collectors.toList());
                    List<Memory> conversationMemories = config.getMemories().stream()
                            .filter(m -> m.getRole().equals("user") || m.getRole().equals("assistant"))
                            .collect(Collectors.toList());
                    if (conversationMemories.size() > config.getMaxMemory() * 2) {
                        config.getMemories().clear();
                        config.getMemories().addAll(systemMemories);
                        config.getMemories().addAll(conversationMemories.subList(conversationMemories.size() - config.getMaxMemory() * 2, conversationMemories.size()));
                    }
                } else {
                    config.getMemories().subList(0, config.getMemories().size() - config.getMaxMemory() * 2).clear();
                }
            }
            saveAI(aiName);
        }
    }

    public void clearMemory(Player player, String aiName) {
        AIConfig config = aiConfigs.get(aiName);
        if (config == null) {
            if (player != null) {
                player.sendMessage("AI " + aiName + " 不存在");
            } else {
                plugin.getLogger().info("AI " + aiName + " 不存在");
            }
            return;
        }
        if (player != null && !hasPermission(player, "gpt.admin") && !hasPermission(player, "gpt.edit")) {
            if (!config.isAllowDefaultEdit()) {
                player.sendMessage("你无权清除此AI的记忆");
                return;
            }
        }
        if (config.isForceSystemPrompt()) {
            List<Memory> systemMemories = config.getMemories().stream()
                    .filter(m -> m.getRole().equals("system"))
                    .collect(Collectors.toList());
            config.getMemories().clear();
            config.getMemories().addAll(systemMemories);
        } else {
            config.getMemories().clear();
        }
        saveAI(aiName);
        if (player != null) {
            player.sendMessage("AI " + aiName + " 的记忆已清除");
        } else {
            plugin.getLogger().info("AI " + aiName + " 的记忆已清除");
        }
    }

    public void setAIConfig(Player player, String aiName, String option, String[] values) {
        AIConfig config = aiConfigs.get(aiName);
        if (config == null) {
            if (player != null) {
                player.sendMessage("AI " + aiName + " 不存在");
            } else {
                plugin.getLogger().info("AI " + aiName + " 不存在");
            }
            return;
        }
        if (player != null && !hasPermission(player, "gpt.admin") && !hasPermission(player, "gpt.edit")) {
            if (!config.isAllowDefaultEdit()) {
                player.sendMessage("你无权修改此AI配置");
                return;
            }
        }
        try {
            switch (option.toLowerCase()) {
                case "trigger":
                    if (values.length < 2) {
                        if (player != null) {
                            player.sendMessage("用法: /gpt set <名称> trigger <词> <优先级>");
                        } else {
                            plugin.getLogger().info("用法: /gpt set <名称> trigger <词> <优先级>");
                        }
                        return;
                    }
                    config.getTriggerWords().add(new TriggerWord(values[0], Integer.parseInt(values[1])));
                    break;
                case "prompt":
                    if (values.length < 3) {
                        if (player != null) {
                            player.sendMessage("用法: /gpt set <名称> prompt <角色> <索引> <内容>");
                        } else {
                            plugin.getLogger().info("用法: /gpt set <名称> prompt <角色> <索引> <内容>");
                        }
                        return;
                    }
                    int index = Integer.parseInt(values[1]);
                    config.getPrompts().add(index, new Prompt(values[0], String.join(" ", Arrays.copyOfRange(values, 2, values.length))));
                    break;
                case "temperature":
                    config.setTemperature(Double.parseDouble(values[0]));
                    break;
                case "top_p":
                    config.setTopP(Double.parseDouble(values[0]));
                    break;
                case "max_tokens":
                    config.setMaxTokens(Integer.parseInt(values[0]));
                    break;
                case "presence_penalty":
                    config.setPresencePenalty(Double.parseDouble(values[0]));
                    break;
                case "frequency_penalty":
                    config.setFrequencyPenalty(Double.parseDouble(values[0]));
                    break;
                case "force_system_prompt":
                    config.setForceSystemPrompt(Boolean.parseBoolean(values[0]));
                    break;
                case "max_memory":
                    config.setMaxMemory(Integer.parseInt(values[0]));
                    break;
                case "model":
                    config.setModel(values[0]);
                    break;
                case "timeout":
                    config.setTimeout(Integer.parseInt(values[0]));
                    break;
                case "message_color":
                    config.setMessageColor(values[0]);
                    break;
                case "message_format":
                    config.setMessageFormat(String.join(" ", values));
                    break;
                case "allow_default_edit":
                    config.setAllowDefaultEdit(Boolean.parseBoolean(values[0]));
                    break;
                default:
                    if (player != null) {
                        player.sendMessage("未知配置选项: " + option);
                    } else {
                        plugin.getLogger().info("未知配置选项: " + option);
                    }
                    return;
            }
            saveAI(aiName);
            if (player != null) {
                player.sendMessage("AI " + aiName + " 的 " + option + " 已设置为: " + String.join(" ", values));
            } else {
                plugin.getLogger().info("AI " + aiName + " 的 " + option + " 已设置为: " + String.join(" ", values));
            }
        } catch (Exception e) {
            if (player != null) {
                player.sendMessage("设置失败: " + e.getMessage());
            } else {
                plugin.getLogger().info("设置失败: " + e.getMessage());
            }
        }
    }

    private void loadAIs() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("ai_data.yml 不存在，将使用默认配置");
            return;
        }
        int loadedAIs = 0;
        for (String key : dataConfig.getKeys(false)) {
            if (dataConfig.getConfigurationSection(key) == null) {
                plugin.getLogger().warning("AI 配置 " + key + " 无效，跳过加载");
                continue;
            }
            try {
                Map<String, Object> data = dataConfig.getConfigurationSection(key).getValues(false);
                AIConfig config = new AIConfig(
                        key,
                        ((List<Map<String, Object>>) data.getOrDefault("trigger_words", new ArrayList<>())).stream()
                                .map(tw -> new TriggerWord((String) tw.get("word"), ((Number) tw.get("priority")).intValue()))
                                .collect(Collectors.toList()),
                        ((Number) data.getOrDefault("temperature", plugin.getConfigManager().getConfig().getDouble("openai.temperature", 0.7))).doubleValue(),
                        ((Number) data.getOrDefault("top_p", plugin.getConfigManager().getConfig().getDouble("openai.top_p", 1.0))).doubleValue(),
                        ((Number) data.getOrDefault("max_tokens", plugin.getConfigManager().getConfig().getInt("openai.max_tokens", 500))).intValue(),
                        ((Number) data.getOrDefault("presence_penalty", plugin.getConfigManager().getConfig().getDouble("openai.presence_penalty", 0.0))).doubleValue(),
                        ((Number) data.getOrDefault("frequency_penalty", plugin.getConfigManager().getConfig().getDouble("openai.frequency_penalty", 0.0))).doubleValue(),
                        (Boolean) data.getOrDefault("force_system_prompt", plugin.getConfigManager().getConfig().getBoolean("openai.force_system_prompt", false)),
                        ((Number) data.getOrDefault("max_memory", plugin.getConfigManager().getConfig().getInt("openai.memory_rounds", 4))).intValue(),
                        (String) data.getOrDefault("model", plugin.getConfigManager().getConfig().getString("openai.model", "gpt-4o-mini")),
                        ((Number) data.getOrDefault("timeout", plugin.getConfigManager().getConfig().getInt("openai.timeout", 10000))).intValue(),
                        ((List<Map<String, String>>) data.getOrDefault("prompts", new ArrayList<>())).stream()
                                .map(p -> new Prompt(p.get("role"), p.get("content")))
                                .collect(Collectors.toList()),
                        ((List<Map<String, String>>) data.getOrDefault("memories", new ArrayList<>())).stream()
                                .map(m -> new Memory(m.get("role"), m.get("content")))
                                .collect(Collectors.toList()),
                        (String) data.getOrDefault("message_color", plugin.getConfigManager().getConfig().getString("openai.message_color", "&a")),
                        (String) data.getOrDefault("message_format", plugin.getConfigManager().getConfig().getString("openai.message_format", "用户{player}说：{message}")),
                        (Boolean) data.getOrDefault("allow_default_edit", false)
                );
                aiConfigs.put(key, config);
                loadedAIs++;
            } catch (Exception e) {
                plugin.getLogger().warning("加载 AI 配置 " + key + " 失败: " + e.getMessage());
            }
        }
        plugin.getLogger().info("成功加载 " + loadedAIs + " 个AI");
    }

    public void saveAllAIs() {
        aiConfigs.keySet().forEach(this::saveAI);
    }

    public void saveAI(String name) {
        AIConfig config = aiConfigs.get(name);
        if (config != null) {
            dataConfig.set(name, config.serialize());
        } else {
            dataConfig.set(name, null);
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存AI数据失败: " + e.getMessage());
        }
    }

    private boolean hasPermission(Player player, String permission) {
        if (player == null || player.isOp()) return true;
        String group = plugin.getConfigManager().getPlayerGroup(player);
        return plugin.getConfigManager().getConfig().getBoolean("permission-groups." + group + "." + permission, false);
    }

    public static class AIConfig {
        private final String name;
        private final List<TriggerWord> triggerWords;
        private double temperature;
        private double topP;
        private int maxTokens;
        private double presencePenalty;
        private double frequencyPenalty;
        private boolean force_system_prompt;
        private int maxMemory;
        private String model;
        private int timeout;
        private final List<Prompt> prompts;
        private final List<Memory> memories;
        private String messageColor;
        private String messageFormat;
        private boolean allowDefaultEdit;

        public AIConfig(String name, List<TriggerWord> triggerWords, double temperature, double topP,
                        int maxTokens, double presencePenalty, double frequencyPenalty, boolean force_system_prompt,
                        int maxMemory, String model, int timeout, List<Prompt> prompts, List<Memory> memories,
                        String messageColor, String messageFormat, boolean allowDefaultEdit) {
            this.name = name;
            this.triggerWords = triggerWords;
            this.temperature = temperature;
            this.topP = topP;
            this.maxTokens = maxTokens;
            this.presencePenalty = presencePenalty;
            this.frequencyPenalty = frequencyPenalty;
            this.force_system_prompt = force_system_prompt;
            this.maxMemory = maxMemory;
            this.model = model;
            this.timeout = timeout;
            this.prompts = prompts;
            this.memories = memories;
            this.messageColor = messageColor;
            this.messageFormat = messageFormat;
            this.allowDefaultEdit = allowDefaultEdit;
        }

        public String getName() {
            return name;
        }

        public List<TriggerWord> getTriggerWords() {
            return triggerWords;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getPresencePenalty() {
            return presencePenalty;
        }

        public void setPresencePenalty(double presencePenalty) {
            this.presencePenalty = presencePenalty;
        }

        public double getFrequencyPenalty() {
            return frequencyPenalty;
        }

        public void setFrequencyPenalty(double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
        }

        public boolean isForceSystemPrompt() {
            return force_system_prompt;
        }

        public void setForceSystemPrompt(boolean force_system_prompt) {
            this.force_system_prompt = force_system_prompt;
        }

        public int getMaxMemory() {
            return maxMemory;
        }

        public void setMaxMemory(int maxMemory) {
            this.maxMemory = maxMemory;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public List<Prompt> getPrompts() {
            return prompts;
        }

        public List<Memory> getMemories() {
            return memories;
        }

        public String getMessageColor() {
            return messageColor;
        }

        public void setMessageColor(String messageColor) {
            this.messageColor = messageColor;
        }

        public String getMessageFormat() {
            return messageFormat;
        }

        public void setMessageFormat(String messageFormat) {
            this.messageFormat = messageFormat;
        }

        public boolean isAllowDefaultEdit() {
            return allowDefaultEdit;
        }

        public void setAllowDefaultEdit(boolean allowDefaultEdit) {
            this.allowDefaultEdit = allowDefaultEdit;
        }

        public Map<String, Object> serialize() {
            Map<String, Object> data = new HashMap<>();
            data.put("trigger_words", triggerWords.stream()
                    .map(tw -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("word", tw.getWord());
                        map.put("priority", tw.getPriority());
                        return map;
                    })
                    .collect(Collectors.toList()));
            data.put("temperature", temperature);
            data.put("top_p", topP);
            data.put("max_tokens", maxTokens);
            data.put("presence_penalty", presencePenalty);
            data.put("frequency_penalty", frequencyPenalty);
            data.put("force_system_prompt", force_system_prompt);
            data.put("max_memory", maxMemory);
            data.put("model", model);
            data.put("timeout", timeout);
            data.put("prompts", prompts.stream()
                    .map(p -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("role", p.getRole());
                        map.put("content", p.getContent());
                        return map;
                    })
                    .collect(Collectors.toList()));
            data.put("memories", memories.stream()
                    .map(m -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("role", m.getRole());
                        map.put("content", m.getContent());
                        return map;
                    })
                    .collect(Collectors.toList()));
            data.put("message_color", messageColor);
            data.put("message_format", messageFormat);
            data.put("allow_default_edit", allowDefaultEdit);
            return data;
        }
    }

    public static class TriggerWord {
        private final String word;
        private final int priority;

        public TriggerWord(String word, int priority) {
            this.word = word;
            this.priority = priority;
        }

        public String getWord() {
            return word;
        }

        public int getPriority() {
            return priority;
        }
    }

    public static class Prompt {
        private final String role;
        private final String content;

        public Prompt(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    public static class Memory {
        private final String role;
        private final String content;

        public Memory(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
