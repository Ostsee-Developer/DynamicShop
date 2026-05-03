package org.minecraftsmp.dynamicshop.managers;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

public class NexoWrapper {

    public static boolean isValid(String id) {
        try {
            return NexoItems.itemFromId(id) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    public static ItemStack getItem(String id) {
        try {
            var builder = NexoItems.itemFromId(id);
            if (builder != null) {
                return builder.build();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getCustomItemId(ItemStack stack) {
        try {
            return NexoItems.idFromItem(stack);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Parse a MiniMessage string using Nexo's GlyphTag and ShiftTag resolvers directly.
     * Bypasses all permission checks — glyphs always render.
     */
    public static Component parseMiniMessage(String text) {
        try {
            net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.builder()
                .tags(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.standard(),
                    com.nexomc.nexo.glyphs.GlyphTag.INSTANCE.getRESOLVER(),
                    com.nexomc.nexo.glyphs.ShiftTag.INSTANCE.getRESOLVER()
                ))
                .build();
            return mm.deserialize(text);
        } catch (Throwable e) {
            return null;
        }
    }

    // Overload kept for API compat — player param is ignored (no permission checks)
    public static Component parseMiniMessage(String text, org.bukkit.entity.Player player) {
        return parseMiniMessage(text);
    }

    /**
     * Get a Glyph Component by glyph ID.
     */
    public static Component getGlyphComponent(String glyphId) {
        try {
            com.nexomc.nexo.NexoPlugin plugin = com.nexomc.nexo.NexoPlugin.instance();
            com.nexomc.nexo.glyphs.Glyph glyph = plugin.fontManager().glyphFromID(glyphId);
            if (glyph != null) {
                return glyph.glyphComponent();
            }
        } catch (Throwable e) {
            // Nexo not loaded or glyph not found
        }
        return null;
    }
}
