package com.mc.gptformc;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GPTforMC extends JavaPlugin implements Listener {
    private static GPTforMC instance;
    private ConfigManager configManager;
    private AIManager aiManager;
    private OpenAIHandler openAIHandler;
    private AuthManager authManager;
    private Logger logger;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        configManager = new ConfigManager(this);
        authManager = new AuthManager(this);
        aiManager = new AIManager(this);
        openAIHandler = new OpenAIHandler(this);
        getServer().getPluginManager().registerEvents(this, this);
        logger.info("GPTforMC 插件已启用  感谢使用GPTforMC");
        logger.info("作者B站：ENA_QWQ");
        logger.info("首次使用需要在配置文件配置API接口和Key");
    }

    @Override
    public void onDisable() {
        aiManager.saveAllAIs();
        logger.info("GPTforMC 插件已禁用");
    }

    public static GPTforMC getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AIManager getAIManager() {
        return aiManager;
    }

    public OpenAIHandler getOpenAIHandler() {
        return openAIHandler;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("gpt")) {
            if (args.length == 0) {
                sender.sendMessage("用法: /gpt <create|list|remove|set|config|reload|clearmemory|help>");
                return true;
            }
            boolean isPlayer = sender instanceof Player;
            Player player = isPlayer ? (Player) sender : null;
            switch (args[0].toLowerCase()) {
                case "create":
                    if (args.length < 2) {
                        sender.sendMessage("用法: /gpt create <名称>");
                        return true;
                    }
                    if (isPlayer && !hasPermission(player, "gpt.create")) {
                        sender.sendMessage("你没有权限创建AI");
                        return true;
                    }
                    aiManager.createAI(player, args[1]);
                    break;
                case "list":
                    List<String> ais = aiManager.listAIs(player, isPlayer && hasPermission(player, "gpt.admin"));
                    sender.sendMessage("AI列表: " + String.join(", ", ais));
                    break;
                case "remove":
                    if (args.length < 2) {
                        sender.sendMessage("用法: /gpt remove <名称>");
                        return true;
                    }
                    if (isPlayer && !hasPermission(player, "gpt.remove") && !hasPermission(player, "gpt.admin")) {
                        sender.sendMessage("你没有权限删除AI");
                        return true;
                    }
                    aiManager.removeAI(player, args[1]);
                    break;
                case "set":
                    if (args.length < 3) {
                        sender.sendMessage("用法: /gpt set <名称> <选项> <值>");
                        return true;
                    }
                    if (isPlayer && !hasPermission(player, "gpt.edit") && !hasPermission(player, "gpt.admin")) {
                        sender.sendMessage("你没有权限编辑AI设置");
                        return true;
                    }
                    aiManager.setAIConfig(player, args[1], args[2], Arrays.copyOfRange(args, 3, args.length));
                    break;
                case "config":
                    if (args.length < 2) {
                        sender.sendMessage("用法: /gpt config <选项> <值>");
                        return true;
                    }
                    if (isPlayer && !hasPermission(player, "gpt.config") && !hasPermission(player, "gpt.admin")) {
                        sender.sendMessage("你没有权限修改全局配置");
                        return true;
                    }
                    configManager.setGlobalConfig(player, args[1], Arrays.copyOfRange(args, 2, args.length));
                    break;
                case "reload":
                    if (isPlayer && !hasPermission(player, "gpt.admin")) {
                        sender.sendMessage("你没有权限重载配置");
                        return true;
                    }
                    configManager.reloadConfig();
                    aiManager = new AIManager(this);
                    sender.sendMessage("GPTforMC 配置已重载");
                    break;
                case "clearmemory":
                    if (args.length < 2) {
                        sender.sendMessage("用法: /gpt clearmemory <名称>");
                        return true;
                    }
                    if (isPlayer && !hasPermission(player, "gpt.edit") && !hasPermission(player, "gpt.admin")) {
                        sender.sendMessage("你没有权限清除AI记忆");
                        return true;
                    }
                    aiManager.clearMemory(player, args[1]);
                    break;
                case "help":
                    sender.sendMessage(ChatColor.BOLD + "GPTforMC 命令帮助:");
                    if (isPlayer) {
                        String group = configManager.getPlayerGroup(player);
                        if (hasPermission(player, "gpt.create")) {
                            sender.sendMessage("/gpt create <名称> - 创建新的AI");
                        }
                        if (hasPermission(player, "gpt.use") || hasPermission(player, "gpt.admin")) {
                            sender.sendMessage("/gpt list - 列出所有AI");
                        }
                        if (hasPermission(player, "gpt.remove") || hasPermission(player, "gpt.admin")) {
                            sender.sendMessage("/gpt remove <名称> - 删除指定AI");
                        }
                        if (hasPermission(player, "gpt.edit") || hasPermission(player, "gpt.admin")) {
                            sender.sendMessage("/gpt set <名称> <选项> <值> - 设置AI配置");
                            sender.sendMessage("/gpt clearmemory <名称> - 清除AI记忆");
                        }
                        if (hasPermission(player, "gpt.config") || hasPermission(player, "gpt.admin")) {
                            sender.sendMessage("/gpt config <选项> <值> - 设置全局配置");
                        }
                        if (hasPermission(player, "gpt.admin")) {
                            sender.sendMessage("/gpt reload - 重载配置文件");
                        }
                    } else {
                        sender.sendMessage("/gpt create <名称> - 创建新的AI");
                        sender.sendMessage("/gpt list - 列出所有AI");
                        sender.sendMessage("/gpt remove <名称> - 删除指定AI");
                        sender.sendMessage("/gpt set <名称> <选项> <值> - 设置AI配置");
                        sender.sendMessage("/gpt config <选项> <值> - 设置全局配置");
                        sender.sendMessage("/gpt reload - 重载配置文件");
                        sender.sendMessage("/gpt clearmemory <名称> - 清除AI记忆");
                        sender.sendMessage("/gpt help - 显示此帮助");
                    }
                    break;
                default:
                    sender.sendMessage("未知子命令: " + args[0]);
            }
        }
        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (hasPermission(player, "gpt.use") || hasPermission(player, "gpt.admin")) {
            openAIHandler.handleChat(player, message);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hasPermission(player, "gpt.admin")) {
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "[GPTforMC] 输入 /gpt help 获取帮助");
        }
        if (hasPermission(player, "gpt.use") || hasPermission(player, "gpt.admin")) {
            List<String> aiNames = aiManager.listAIs(null, true);
            List<AIManager.AIConfig> ais = new ArrayList<>();
            for (String name : aiNames) {
                AIManager.AIConfig config = aiManager.findTriggeredAI(name);
                if (config != null) {
                    ais.add(config);
                }
            }
            if (!ais.isEmpty()) {
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "[GPTforMC] 当前可用的AI：");
                for (int i = 0; i < ais.size(); i++) {
                    AIManager.AIConfig ai = ais.get(i);
                    String triggerWords = ai.getTriggerWords().stream()
                            .map(AIManager.TriggerWord::getWord)
                            .collect(Collectors.joining("/"));
                    player.sendMessage(ChatColor.AQUA + "" + (i + 1) + ". " + ChatColor.BOLD + ai.getName() +
                            ChatColor.RESET + " - " + ai.getModel() + ChatColor.YELLOW + " 对话触发条件：");
                    player.sendMessage("发送包含字符串" + ChatColor.ITALIC + triggerWords + ChatColor.RESET + "的信息");
                }
            }
        }
    }

    private boolean hasPermission(Player player, String permission) {
        if (player == null) return true;
        String group = configManager.getPlayerGroup(player);
        return configManager.getConfig().getBoolean("permission-groups." + group + "." + permission, false);
    }
}
