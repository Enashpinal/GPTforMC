package com.mc.gptformc;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthManager {
    private final GPTforMC plugin;
    private final File authFile;
    private final YamlConfiguration authConfig;

    public AuthManager(GPTforMC plugin) {
        this.plugin = plugin;
        this.authFile = new File(plugin.getDataFolder(), "auth.yml");
        this.authConfig = YamlConfiguration.loadConfiguration(authFile);
    }

    public boolean isSetup() {
        return authConfig.contains("username") && authConfig.contains("password");
    }

    public boolean authenticate(String username, String password) {
        String storedUsername = authConfig.getString("username");
        String storedPassword = authConfig.getString("password");
        return storedUsername != null && storedPassword != null &&
                storedUsername.equals(username) && storedPassword.equals(hashPassword(password));
    }

    public boolean setup(String username, String password) {
        if (isSetup()) {
            return false;
        }
        authConfig.set("username", username);
        authConfig.set("password", hashPassword(password));
        try {
            authConfig.save(authFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("保存认证数据失败: " + e.getMessage());
            return false;
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().severe("密码哈希失败: " + e.getMessage());
            return password;
        }
    }
}