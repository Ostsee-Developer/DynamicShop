package org.minecraftsmp.dynamicshop.web;

import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages web admin user accounts with salted SHA-256 password hashing.
 * Credentials are persisted to web-admins.yml in the plugin data folder.
 * Also manages session tokens for logged-in users.
 */
public class WebAdminUserManager {

    private static final long SESSION_EXPIRY_MS = 4 * 60 * 60 * 1000; // 4 hours

    private final DynamicShop plugin;
    private final File credentialsFile;
    private YamlConfiguration credentials;

    // Active sessions: sessionToken -> expiry timestamp
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    public WebAdminUserManager(DynamicShop plugin) {
        this.plugin = plugin;
        this.credentialsFile = new File(plugin.getDataFolder(), "web-admins.yml");
        load();
    }

    /**
     * Load credentials from file.
     */
    private void load() {
        if (!credentialsFile.exists()) {
            credentials = new YamlConfiguration();
            save();
        } else {
            credentials = YamlConfiguration.loadConfiguration(credentialsFile);
        }
    }

    /**
     * Save credentials to file.
     */
    private void save() {
        try {
            credentials.save(credentialsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save web-admins.yml", e);
        }
    }

    /**
     * Check if any admin users exist.
     */
    public boolean hasUsers() {
        var section = credentials.getConfigurationSection("users");
        return section != null && !section.getKeys(false).isEmpty();
    }

    /**
     * Check if a specific user exists.
     */
    public boolean userExists(String username) {
        return credentials.contains("users." + username.toLowerCase());
    }

    /**
     * Register a new admin user. Returns false if username already exists.
     */
    public boolean register(String username, String password) {
        String key = username.toLowerCase();
        if (credentials.contains("users." + key)) {
            return false; // User already exists
        }

        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        credentials.set("users." + key + ".salt", salt);
        credentials.set("users." + key + ".hash", hash);
        save();

        plugin.getLogger().info("[WebAdmin] Admin user '" + username + "' registered.");
        return true;
    }

    /**
     * Authenticate a user. Returns a session token on success, null on failure.
     */
    public String login(String username, String password) {
        String key = username.toLowerCase();
        if (!credentials.contains("users." + key)) {
            return null;
        }

        String salt = credentials.getString("users." + key + ".salt");
        String storedHash = credentials.getString("users." + key + ".hash");

        if (salt == null || storedHash == null) return null;

        String hash = hashPassword(password, salt);
        if (!hash.equals(storedHash)) {
            return null;
        }

        // Generate session
        String sessionToken = UUID.randomUUID().toString().replace("-", "");
        sessions.put(sessionToken, System.currentTimeMillis() + SESSION_EXPIRY_MS);
        sessionUsers.put(sessionToken, username.toLowerCase()); // Track user
        return sessionToken;
    }

    /**
     * Validate a session token.
     */
    public boolean isValidSession(String token) {
        if (token == null || token.isEmpty()) return false;
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Logout (invalidate session).
     */
    public void logout(String token) {
        sessions.remove(token);
        sessionUsers.remove(token);
    }

    /**
     * Get the username associated with a session token.
     */
    public String getUsername(String token) {
        if (!isValidSession(token)) return null;
        return sessionUsers.get(token);
    }

    /**
     * Delete an admin user.
     */
    public boolean deleteUser(String username) {
        String key = username.toLowerCase();
        if (!credentials.contains("users." + key)) {
            return false;
        }
        credentials.set("users." + key, null);
        save();
        return true;
    }

    /**
     * Hash a password with a salt using SHA-256.
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generate a random salt.
     */
    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Cleanup expired sessions.
     */
    public void cleanupSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> {
            if (now > e.getValue()) {
                sessionUsers.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
