package aaronpost.atpcore.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GUIUtil {
    public static final String RIGHT_ARROW_URL = "http://textures.minecraft.net/texture/1a4f68c8fb279e50ab786f9fa54c88ca4ecfe1eb5fd5f0c38c54c9b1c7203d7a";
    public static final String LEFT_ARROW_URL = "http://textures.minecraft.net/texture/737648ae7a564a5287792b05fac79c6b6bd47f616a559ce8b543e6947235bce";

    private static final ItemStack emptyItem;

    static {
        emptyItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = emptyItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLACK + " ");
        }
        emptyItem.setItemMeta(meta);
    }

    private GUIUtil() {}

    public static void fillEmptyGUISpots(Inventory inventory) {
        fillEmptyGUISpots(inventory, inventory.getSize(), emptyItem);
    }

    public static ItemStack attachNameAndLore(ItemStack item, String name, java.util.List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            meta.setDisplayName(name);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static java.util.List<String> prettyIndent(java.util.List<String> lore, int indent) {
        String indentation = " ".repeat(indent);
        lore.replaceAll(s -> indentation + s);
        return lore;
    }

    public static void fillEmptyGUISpots(Inventory inventory, int inventorySize, ItemStack filler) {
        for (int i = 0; i < inventorySize; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
}
