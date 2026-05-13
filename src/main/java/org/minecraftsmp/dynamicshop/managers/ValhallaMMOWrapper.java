package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.inventory.ItemStack;

/**
 * Wrapper for ValhallaMMO custom item integration.
 * Uses ValhallaMMO's CustomItemRegistry to fetch items with full stat modifiers applied,
 * so purchased weapons/armor have the same stats as if the player crafted them.
 *
 * All methods are wrapped in try/catch to prevent ClassNotFoundError when ValhallaMMO is absent.
 */
public class ValhallaMMOWrapper {

    /**
     * Check if a ValhallaMMO custom item ID exists in the registry.
     */
    public static boolean isValid(String id) {
        try {
            return me.athlaeos.valhallammo.item.CustomItemRegistry.getItem(id) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Get a fully-processed ItemStack from ValhallaMMO by its custom item ID.
     * The returned item has all Dynamic Item Modifiers applied (stats, lore, etc.)
     * so it matches what a player would get from crafting.
     *
     * @param id The ValhallaMMO custom item ID
     * @return The processed ItemStack, or null if not found
     */
    public static ItemStack getItem(String id) {
        try {
            return me.athlaeos.valhallammo.item.CustomItemRegistry.getProcessedItem(id);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get a fully-processed ItemStack from ValhallaMMO, with modifiers resolved
     * relative to a specific player's skill level.
     *
     * @param id     The ValhallaMMO custom item ID
     * @param player The player whose stats should influence the modifiers
     * @return The processed ItemStack, or null if not found
     */
    public static ItemStack getItem(String id, org.bukkit.entity.Player player) {
        try {
            return me.athlaeos.valhallammo.item.CustomItemRegistry.getProcessedItem(id, player);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Attempt to identify a ValhallaMMO custom item from an existing ItemStack.
     * Checks the item's PersistentDataContainer for ValhallaMMO's internal ID tag,
     * then matches it against all registered custom items.
     *
     * @param stack The ItemStack to identify
     * @return The custom item string ID, or null if not a ValhallaMMO item
     */
    public static String getCustomItemId(ItemStack stack) {
        try {
            if (stack == null || !stack.hasItemMeta()) return null;

            var items = me.athlaeos.valhallammo.item.CustomItemRegistry.getItems();
            if (items == null || items.isEmpty()) return null;

            // ValhallaMMO stores an integer ID on the item's PDC via CustomID.
            // We check if the held item has this tag, then search registered items
            // for a match by comparing the base material and PDC integer ID.
            org.bukkit.inventory.meta.ItemMeta heldMeta = stack.getItemMeta();
            Integer heldPdcId = me.athlaeos.valhallammo.item.CustomID.getID(heldMeta);

            if (heldPdcId != null) {
                // We have a PDC ID — scan registered items to find a match
                for (var entry : items.entrySet()) {
                    me.athlaeos.valhallammo.item.CustomItem customItem = entry.getValue();
                    ItemStack registeredStack = customItem.getItem();
                    if (registeredStack != null && registeredStack.hasItemMeta()) {
                        Integer registeredId = me.athlaeos.valhallammo.item.CustomID.getID(registeredStack.getItemMeta());
                        if (heldPdcId.equals(registeredId)) {
                            return entry.getKey();
                        }
                    }
                }
            }

            return null;
        } catch (Throwable e) {
            // ValhallaMMO not loaded or API incompatibility
        }
        return null;
    }

    /**
     * Get all registered ValhallaMMO custom item IDs.
     * Useful for tab completion and item listing.
     *
     * @return A collection of item IDs, or an empty set if unavailable
     */
    public static java.util.Set<String> getAllItemIds() {
        try {
            var items = me.athlaeos.valhallammo.item.CustomItemRegistry.getItems();
            if (items != null) {
                return items.keySet();
            }
        } catch (Throwable e) {
            // ValhallaMMO not loaded
        }
        return java.util.Collections.emptySet();
    }
}
