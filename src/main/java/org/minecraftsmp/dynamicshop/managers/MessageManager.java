package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles loading and formatting messages from messages.yml
 * Supports color codes and placeholders
 */
public class MessageManager {

    private final DynamicShop plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix;

    public MessageManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // INITIALIZATION
    // ------------------------------------------------------------
    public void init() {
        loadMessages();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        // Create messages.yml if it doesn't exist
        if (!messagesFile.exists()) {
            // If Nexo is installed, use the Nexo-glyph template as the default
            boolean nexoInstalled = plugin.getServer().getPluginManager().getPlugin("Nexo") != null;
            if (nexoInstalled) {
                try {
                    // Save messages_nexo_example.yml content AS messages.yml
                    InputStream nexoStream = plugin.getResource("messages_nexo_example.yml");
                    if (nexoStream != null) {
                        java.nio.file.Files.copy(nexoStream, messagesFile.toPath());
                        nexoStream.close();
                        plugin.getLogger().info("Nexo detected! Generated messages.yml with glyph support.");
                    } else {
                        plugin.saveResource("messages.yml", false);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to generate Nexo messages template, using default.");
                    plugin.saveResource("messages.yml", false);
                }
            } else {
                plugin.saveResource("messages.yml", false);
            }
        }

        // Also generate messages_nexo_example.yml template as a reference if it doesn't exist
        File nexoFile = new File(plugin.getDataFolder(), "messages_nexo_example.yml");
        if (!nexoFile.exists()) {
            try {
                plugin.saveResource("messages_nexo_example.yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Could not generate messages_nexo_example.yml template.");
            }
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from jar and merge missing keys
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream));
            messagesConfig.setDefaults(defaultConfig);

            // Auto-add any missing keys to the user's file
            boolean changed = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!messagesConfig.isSet(key)) {
                    messagesConfig.set(key, defaultConfig.get(key));
                    changed = true;
                    plugin.getLogger().info("[Messages] Added missing key: " + key);
                }
            }
            if (changed) {
                try {
                    messagesConfig.save(messagesFile);
                    plugin.getLogger().info("[Messages] Saved updated messages.yml with new keys.");
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("[Messages] Could not save updated messages.yml: " + e.getMessage());
                }
            }
        }

        // Load prefix
        prefix = messagesConfig.getString("messages.prefix", "&6&lDynamicShop &7» ");
    }

    // ------------------------------------------------------------
    // RELOAD
    // ------------------------------------------------------------
    public void reload() {
        loadMessages();
    }

    // ------------------------------------------------------------
    // GET MESSAGE (with optional placeholders)
    // ------------------------------------------------------------
    public String getMessage(String key) {
        return getMessage(key, new HashMap<>());
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString("messages." + key, "&cMessage not found: " + key);

        // If message is empty, return null to indicate it should be skipped
        if (message.isEmpty()) {
            return null;
        }

        // Replace placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Apply color codes
        message = message.replace('&', '§');

        return message;
    }

    /**
     * Parses a string into a Component.
     * If the string contains Nexo tags like &lt;glyph:...&gt; or &lt;shift:...&gt;, 
     * uses MiniMessage so Nexo's registered TagResolvers can process them.
     * Otherwise falls back to the legacy serializer for simple color-coded strings.
     */
    public static Component parseComponent(String text) {
        return parseComponent(text, null);
    }

    /**
     * Parses a string into a Component, with optional player context for Nexo permission-aware glyphs.
     */
    public static Component parseComponent(String text, org.bukkit.entity.Player player) {
        if (text == null) return Component.empty();
        
        // Check if the text contains Nexo/MiniMessage tags
        if (text.contains("<glyph:") || text.contains("<shift:")) {
            // Convert legacy color codes (§ and &) to MiniMessage format for compatibility
            String mmText = text.replace('§', '&');
            // Convert &X color codes to MiniMessage <color> tags
            mmText = convertLegacyToMiniMessage(mmText);
            // Use Nexo's MiniMessage instance which has GlyphTag and ShiftTag resolvers
            try {
                Component result;
                if (player != null) {
                    result = NexoWrapper.parseMiniMessage(mmText, player);
                } else {
                    result = NexoWrapper.parseMiniMessage(mmText);
                }
                if (result != null) return result;
            } catch (Throwable ignored) {}
            // Fallback to vanilla MiniMessage
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mmText);
        }
        
        // For simple strings without Nexo tags, use legacy serializer
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace('§', '&'));
    }

    /**
     * Convert legacy & color codes to MiniMessage format.
     * e.g. "&f" -> "<white>", "&0&l" -> "<black><bold>"
     */
    private static String convertLegacyToMiniMessage(String text) {
        // Replace formatting codes first (order matters - do these before colors)
        text = text.replace("&l", "<bold>").replace("&L", "<bold>");
        text = text.replace("&o", "<italic>").replace("&O", "<italic>");
        text = text.replace("&n", "<underlined>").replace("&N", "<underlined>");
        text = text.replace("&m", "<strikethrough>").replace("&M", "<strikethrough>");
        text = text.replace("&k", "<obfuscated>").replace("&K", "<obfuscated>");
        text = text.replace("&r", "<reset>").replace("&R", "<reset>");
        
        // Replace color codes
        text = text.replace("&0", "<black>").replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>").replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>").replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>").replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>").replace("&9", "<blue>");
        text = text.replace("&a", "<green>").replace("&A", "<green>");
        text = text.replace("&b", "<aqua>").replace("&B", "<aqua>");
        text = text.replace("&c", "<red>").replace("&C", "<red>");
        text = text.replace("&d", "<light_purple>").replace("&D", "<light_purple>");
        text = text.replace("&e", "<yellow>").replace("&E", "<yellow>");
        text = text.replace("&f", "<white>").replace("&F", "<white>");
        
        return text;
    }

    /**
     * Helper method to add a lore line only if the message is not disabled (null).
     * 
     * @param lore    The lore list to add to
     * @param message The message (can be null if disabled)
     */
    public static void addLoreIfNotEmpty(java.util.List<String> lore, String message) {
        if (message != null) {
            lore.add(message);
        }
    }

    // ------------------------------------------------------------
    // GET MESSAGE WITH PREFIX
    // ------------------------------------------------------------
    public String getMessageWithPrefix(String key) {
        return getMessageWithPrefix(key, new HashMap<>());
    }

    public String getMessageWithPrefix(String key, Map<String, String> placeholders) {
        String message = getMessage(key, placeholders);
        if (message == null) {
            return null; // Message is disabled
        }
        String prefixColored = prefix.replace('&', '§');
        return prefixColored + message;
    }

    // ------------------------------------------------------------
    // CONVENIENCE METHODS FOR COMMON MESSAGES
    // ------------------------------------------------------------

    public String noPermission() {
        return getMessageWithPrefix("no-permission");
    }

    public String notEnoughMoney(String price) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", price);
        return getMessageWithPrefix("not-enough-money", placeholders);
    }

    public String cannotSell() {
        return getMessageWithPrefix("cannot-sell");
    }

    public String cannotSellDamaged() {
        return getMessageWithPrefix("cannot-sell-damaged");
    }

    public String categoryEmpty() {
        return getMessageWithPrefix("category-empty");
    }

    public String specialPermissionSuccess(String permission) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("permission", permission);
        return getMessageWithPrefix("special-permission-success", placeholders);
    }

    public String specialPermissionFailed() {
        return getMessageWithPrefix("special-permission-failed");
    }

    public String specialPermissionAlreadyOwned() {
        return getMessageWithPrefix("special-permission-already-owned");
    }

    public String specialServerItemSuccess(String identifier) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("identifier", identifier);
        return getMessageWithPrefix("special-server-item-success", placeholders);
    }

    public String specialServerItemFailed() {
        return getMessageWithPrefix("special-server-item-failed");
    }

    public String categoryLoreItems(int count) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(count));
        return getMessage("category-lore-items", placeholders);
    }

    public String categoryLoreClickToBrowse() {
        return getMessage("category-lore-click-to-browse");
    }

    public String categoryLoreNoItems() {
        return getMessage("category-lore-no-items");
    }

    public String inventoryFull() {
        return getMessageWithPrefix("inventory-full");
    }
}