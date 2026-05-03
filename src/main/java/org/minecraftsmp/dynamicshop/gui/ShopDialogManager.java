package org.minecraftsmp.dynamicshop.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.List;

/**
 * Manages the Paper Dialog API integration for buy/sell item interactions.
 * Shows a dialog with the item preview, a quantity slider, and Buy/Sell/Return buttons.
 */
public class ShopDialogManager {

    private final DynamicShop plugin;

    public ShopDialogManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens a buy/sell dialog for the specified material.
     *
     * @param player The player to show the dialog to
     * @param mat    The material being transacted
     * @param gui    The ShopGUI to return to (can be ShopGUI or SearchResultsGUI)
     */
    public void openDialog(Player player, Material mat, Object gui) {
        ItemStack displayItem = new ItemStack(mat);
        String itemName = formatMaterialName(mat);

        double buyPrice1 = ShopDataManager.getTotalBuyCost(mat, 1);
        double sellPrice1 = ShopDataManager.getTotalSellValue(mat, 1);

        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("item", itemName);
        placeholders.put("price", plugin.getEconomyManager().format(buyPrice1));
        
        java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
        sellPlaceholders.put("price", plugin.getEconomyManager().format(sellPrice1));

        Component titleComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-title", placeholders), player);
                
        Component buyPriceComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-buy-price", placeholders), player);
                
        Component sellPriceComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-sell-price", sellPlaceholders), player);
                
        Component qtyLabel = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-quantity"), player);
                
        Component confirmBtn = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-confirm-button"), player);
        Component confirmDesc = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-confirm-desc"), player);
                
        Component returnBtn = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-return-button"), player);
        Component returnDesc = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-return-desc"), player);

        // Build the dialog dynamically
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(titleComp)
                        .canCloseWithEscape(true)
                        .body(List.of(
                                DialogBody.item(displayItem)
                                        .showDecorations(true)
                                        .showTooltip(true)
                                        .height(110)
                                        .build(),
                                DialogBody.plainMessage(buyPriceComp, 300),
                                DialogBody.plainMessage(sellPriceComp, 300)
                        ))
                        .inputs(List.of(
                                DialogInput.numberRange("quantity", qtyLabel, -128f, 128f)
                                        .step(1f)
                                        .initial(0f)
                                        .width(300)
                                        .labelFormat("%s: %s")
                                        .build()
                        ))
                        .build()
                )
                .type(DialogType.multiAction(List.of(
                        // CONFIRM button
                        ActionButton.create(
                                confirmBtn,
                                confirmDesc,
                                200,
                                DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player p) {
                                                Float qtyFloat = view.getFloat("quantity");
                                                int qty = qtyFloat != null ? qtyFloat.intValue() : 0;
                                                
                                                if (qty == 0) {
                                                    p.sendMessage(Component.text(
                                                            "Set quantity to buy (+) or sell (-)!", NamedTextColor.RED));
                                                    return;
                                                }
                                                
                                                if (qty > 0) {
                                                    plugin.getShopListener().buyItem(p, mat, qty, gui);
                                                } else {
                                                    plugin.getShopListener().sellItem(p, mat, Math.abs(qty), gui);
                                                }
                                            }
                                        },
                                        ClickCallback.Options.builder()
                                                .uses(ClickCallback.UNLIMITED_USES)
                                                .lifetime(java.time.Duration.ofMinutes(5))
                                                .build()
                                )
                        ),
                        // RETURN button
                        ActionButton.create(
                                returnBtn,
                                returnDesc,
                                100,
                                DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player p) {
                                                p.closeInventory();
                                                // Re-open the shop GUI
                                                if (gui instanceof org.minecraftsmp.dynamicshop.gui.ShopGUI shopGUI) {
                                                    shopGUI.open();
                                                } else if (gui instanceof org.minecraftsmp.dynamicshop.gui.SearchResultsGUI searchGUI) {
                                                    searchGUI.open();
                                                }
                                            }
                                        },
                                        ClickCallback.Options.builder()
                                                .uses(1)
                                                .lifetime(java.time.Duration.ofMinutes(5))
                                                .build()
                                )
                        )
                )).build())
        );

        player.showDialog(dialog);
    }

    private String formatMaterialName(Material mat) {
        String name = mat.name().replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
