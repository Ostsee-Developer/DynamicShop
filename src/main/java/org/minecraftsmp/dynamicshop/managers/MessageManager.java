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

            // Collect missing keys and their default values
            java.util.List<String[]> missing = new java.util.ArrayList<>();
            for (String key : defaultConfig.getKeys(true)) {
                if (!messagesConfig.isSet(key)) {
                    Object val = defaultConfig.get(key);
                    if (val instanceof String) {
                        messagesConfig.set(key, val); // Add to in-memory config
                        missing.add(new String[]{key, (String) val});
                        plugin.getLogger().info("[Messages] Added missing key: " + key);
                    }
                }
            }

            // Append missing keys to the file WITHOUT re-serializing the whole thing.
            // This prevents Bukkit's SnakeYAML from corrupting MiniMessage tags like
            // <glyph:...> and <shift:...> which get mangled when the file is re-saved.
            if (!missing.isEmpty()) {
                try (java.io.FileWriter fw = new java.io.FileWriter(messagesFile, true)) {
                    fw.write("\n  # --- Auto-added missing keys ---\n");
                    for (String[] entry : missing) {
                        // entry[0] = "messages.key-name", entry[1] = "value"
                        String shortKey = entry[0].startsWith("messages.") 
                            ? entry[0].substring("messages.".length()) 
                            : entry[0];
                        // Escape the value for YAML (wrap in double quotes)
                        String escaped = entry[1].replace("\\", "\\\\").replace("\"", "\\\"");
                        fw.write("  " + shortKey + ": \"" + escaped + "\"\n");
                    }
                    plugin.getLogger().info("[Messages] Appended " + missing.size() + " missing keys to messages.yml (safe append, no reformatting).");
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("[Messages] Could not append missing keys to messages.yml: " + e.getMessage());
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
            // Fallback: strip Nexo-specific tags so vanilla MiniMessage doesn't render garbage
            String stripped = mmText.replaceAll("<glyph:[^>]*>", "").replaceAll("<shift:[^>]*>", "");
            org.bukkit.Bukkit.getLogger().warning("[DynamicShop] Nexo glyph tags could not be resolved — rendering without glyphs. Text: " + text);
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(stripped);
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