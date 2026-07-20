package aaronpost.atpcore.schematics;

import aaronpost.atpcore.gui.AbstractPagedMenu;
import aaronpost.atpcore.registries.Registry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Admin menu listing every saved coordinate ({@link Registry#LocationData});
 * clicking teleports to it. Game plugins can brand the chat prefix and the
 * "how to save a coordinate" hint via the static setters.
 */
public class AdminCoordinateMenu extends AbstractPagedMenu<LocationWrapper2> {

    private static String messagePrefix = "";
    private static String saveCommandHint = "/savecoordinates [name]";

    public static void setMessagePrefix(String prefix) {
        messagePrefix = prefix != null ? prefix : "";
    }

    public static void setSaveCommandHint(String hint) {
        saveCommandHint = hint != null ? hint : "";
    }

    public AdminCoordinateMenu(Player player) {
        super(
                player,
                "+ Coordinates +",
                Registry.LocationData != null
                        ? new ArrayList<>(Registry.LocationData.getAllValues())
                        : Collections.emptyList()
        );
    }

    @Override
    protected void renderItems(int startIndex, int endIndex) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            LocationWrapper2 location = entries.get(i);
            meta.setDisplayName(ChatColor.GOLD + location.getName());
            ArrayList<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to tp:");
            lore.add(ChatColor.BLUE + location.toString());
            meta.setLore(lore);
            paper.setItemMeta(meta);
            inventory.setItem(slot++, paper);
        }

        // Info sign
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Add new coordinates:");
        meta.setLore(List.of(ChatColor.GRAY + saveCommandHint));
        info.setItemMeta(meta);
        inventory.setItem(49, info);
    }

    @Override
    protected void onItemClick(ItemStack item, LocationWrapper2 location, InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        if (e.getClick().isLeftClick()) {
            p.closeInventory();
            p.teleport(location.getLoc());
            p.sendMessage(messagePrefix + ChatColor.GRAY + " Sent you to " + ChatColor.BLUE + location.getName() + ChatColor.GRAY + ".");
        }

    }
}
