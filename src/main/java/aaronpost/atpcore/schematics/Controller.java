package aaronpost.atpcore.schematics;

import aaronpost.atpcore.ATPCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller handles creating schematics and saving coordinates.
 * Registered by ATPCore itself; game plugins hand out the wands.
 */
public class Controller implements Listener {
    public static final String SCHEMATIC_WAND_NAME = ChatColor.BLUE + "Schematic Wand";
    public static final String EVENT_BLOCK_EDITOR_NAME = ChatColor.BLUE + "Schematic Event Block Editor";
    public static final String SAVE_COORDINATES_WAND_NAME = ChatColor.LIGHT_PURPLE + "Save Coordinates Wand";

    public static boolean checkForCompleteLore(ArrayList<String> lore, int numOfCoord) {
        for (int i = 0; i < 3 * numOfCoord; i++) {
            if (ChatColor.stripColor(lore.get(i)).length() < 4) {
                return false;
            }
        }
        return true;
    }

    public static int getCoordFromString(String str) {
        return Integer.parseInt(ChatColor.stripColor(str.substring(5)));
    }

    @EventHandler
    public void onClick(PlayerInteractEvent i) {
        if (i.hasItem()) {
            ItemStack item = i.getItem();
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.getDisplayName().equals(SCHEMATIC_WAND_NAME)) {
                    if (i.hasBlock()) {
                        EquipmentSlot slot = i.getHand(); //Get the hand of the event and set it to 'e'.
                        if (slot.equals(EquipmentSlot.HAND)) { // checks for main hand to prevent from running twice

                            Location l = i.getClickedBlock().getLocation();
                            ArrayList<String> lore = new ArrayList<>(meta.getLore());

                            int x = (int) Math.floor(l.getX());
                            int y = (int) Math.floor(l.getY());
                            int z = (int) Math.floor(l.getZ());

                            if (i.getAction() == Action.LEFT_CLICK_BLOCK) {
                                i.getPlayer().sendMessage(ChatColor.GOLD + "Position 1: " + ChatColor.GRAY + x + ", " + y + ", " + z);
                                lore.set(0, ChatColor.YELLOW + "x: " + x);
                                lore.set(1, ChatColor.YELLOW + "y: " + y);
                                lore.set(2, ChatColor.YELLOW + "z: " + z);
                                i.setCancelled(true);
                            } else if (i.getAction() == Action.RIGHT_CLICK_BLOCK) {
                                lore.set(3, ChatColor.YELLOW + "x: " + x);
                                lore.set(4, ChatColor.YELLOW + "y: " + y);
                                lore.set(5, ChatColor.YELLOW + "z: " + z);
                                i.getPlayer().sendMessage(ChatColor.GOLD + "Position 2: " + ChatColor.GRAY + x + ", " + y + ", " + z);
                                i.setCancelled(true);
                            }
                            meta.setLore(lore);
                            item.setItemMeta(meta);
                        }
                    }
                } else if (meta.getDisplayName().equals(SAVE_COORDINATES_WAND_NAME)) {
                    if (i.hasBlock()) {
                        EquipmentSlot slot = i.getHand(); //Get the hand of the event and set it to 'e'.
                        if (slot.equals(EquipmentSlot.HAND)) { // checks for main hand to prevent from running twice
                            Location l = i.getClickedBlock().getLocation();
                            ArrayList<String> lore = new ArrayList<>(meta.getLore());
                            int x = (int) Math.floor(l.getX());
                            int y = (int) Math.floor(l.getY());
                            int z = (int) Math.floor(l.getZ());
                            lore.set(0, ChatColor.YELLOW + "x: " + x);
                            lore.set(1, ChatColor.YELLOW + "y: " + y);
                            lore.set(2, ChatColor.YELLOW + "z: " + z);
                            i.getPlayer().sendMessage(ChatColor.GOLD + "Coordinate: " + ChatColor.GRAY + x + ", " + y + ", " + z);
                            meta.setLore(lore);
                            item.setItemMeta(meta);
                        }
                    }
                } else if (meta.getDisplayName().equals(EVENT_BLOCK_EDITOR_NAME) && i.getClickedBlock() != null) {
                    List<String> lore = meta.getLore();
                    if (lore != null && lore.size() == 2) {
                        i.setCancelled(true);
                        i.getPlayer().sendMessage(lore.toString());
                        Schematics.s.getSchematic(lore.get(0)).setEventBlockLoc((lore.get(1)), i.getClickedBlock().getLocation());
                        i.getPlayer().sendMessage(ChatColor.BLUE + "Saved event block '" + lore.get(1) + "' for schematic '" + lore.get(0) + "'.");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCrouch(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && e.getPlayer().getInventory().getItemInMainHand().hasItemMeta()) {
            if (e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.BLAZE_ROD)) {
                if (e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getDisplayName().equals(SCHEMATIC_WAND_NAME)) {
                    if (!Schematics.s.getSchematics().isEmpty()) {
                        // The menu registers itself as a listener in its constructor.
                        new AdminSchematicMenu(e.getPlayer());
                    }
                }
            }
        }
    }
}
