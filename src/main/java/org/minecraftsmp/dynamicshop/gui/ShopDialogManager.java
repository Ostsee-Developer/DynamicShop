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

        // Build the dialog dynamically
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(itemName, NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .body(List.of(
                                DialogBody.item(displayItem)
                                        .showDecorations(true)
                                        .showTooltip(true)
                                        .width(64)
                                        .height(64)
                                        .build(),
                                DialogBody.plainMessage(
                                        Component.text("Buy Price: ", NamedTextColor.GRAY)
                                                .append(Component.text(
                                                        plugin.getEconomyManager().format(buyPrice1) + " each",
                                                        TextColor.color(0xAEFFC1))),
                                        300),
                                DialogBody.plainMessage(
                                        Component.text("Sell Price: ", NamedTextColor.GRAY)
                                                .append(Component.text(
                                                        plugin.getEconomyManager().format(sellPrice1) + " each",
                                                        TextColor.color(0xFFA0B1))),
                                        300)
                        ))
                        .inputs(List.of(
                                DialogInput.numberRange("quantity",
                                                Component.text("Quantity (-128 Sell, +128 Buy)", NamedTextColor.WHITE),
                                                -128f, 128f)
                                        .step(1f)
                                        .initial(0f)
                                        .width(300)
                                        .labelFormat("Quantity: %.0f")
                                        .build()
                        ))
                        .build()
                )
                .type(DialogType.multiAction(List.of(
                        // CONFIRM button
                        ActionButton.create(
                                Component.text("Confirm Transaction", TextColor.color(0xAEFFC1)),
                                Component.text("Process buy/sell based on slider value"),
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
                                Component.text("Return", NamedTextColor.GRAY),
                                Component.text("Go back to the shop"),
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
