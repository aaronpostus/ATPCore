package aaronpost.atpcore.schematics;

import aaronpost.atpcore.gui.AbstractPagedMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AdminSchematicMenu extends AbstractPagedMenu<Schematic> {

    public AdminSchematicMenu(Player player) {
        super(
                player,
                "+ Schematics +",
                Schematics.s.getSchematics().stream()
                        .sorted(Comparator.comparing(Schematic::getName))
                        .toList()
        );
    }

    @Override
    protected void renderItems(int startIndex, int endIndex) {

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Schematic schematic = entries.get(i);
            ItemStack stack;
            ArrayList<String> lore = new ArrayList<>();
            if(Schematics.s.isNew(schematic))
            {
                stack = new ItemStack(Material.FILLED_MAP);
                lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "Created/Modified.");
            }
            else
            {
                stack = new ItemStack(Material.MAP);
            }
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + schematic.getName());
            lore.add(ChatColor.GRAY + "Y-Offset: " + ChatColor.YELLOW + schematic.getYOffset());
            meta.setLore(lore);
            stack.setItemMeta(meta);
            inventory.setItem(slot++, stack);
        }

        // Info sign
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Menu Tutorial:");
        meta.setLore(List.of(
                ChatColor.GRAY + "Left Click: " + ChatColor.YELLOW + "Paste schematic",
                ChatColor.GRAY + "Shift Left Click: " + ChatColor.YELLOW + "+1 Y-Offset",
                ChatColor.GRAY + "Shift Right Click: " + ChatColor.YELLOW + "-1 Y-Offset"
        ));
        info.setItemMeta(meta);
        inventory.setItem(49, info);
    }

    @Override
    protected void onItemClick(ItemStack item, Schematic schematic, InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        if (!e.getClick().isShiftClick()) {
            if (e.getClick().isLeftClick()) {
                schematic.pasteSchematic(p.getLocation(), true, null);
                p.closeInventory();
            }
        } else { // Shift-clicks adjust Y offset
            if (e.getClick().isLeftClick()) {
                schematic.setyOffset(schematic.getYOffset() + 1);
            } else if (e.getClick().isRightClick()) {
                schematic.setyOffset(schematic.getYOffset() - 1);
            }
            renderPage(currentPage);
        }
    }
}
