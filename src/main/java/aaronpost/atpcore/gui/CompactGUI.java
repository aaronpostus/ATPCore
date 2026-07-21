package aaronpost.atpcore.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

/**
 * An {@link InventoryGUI} whose {@code sizeHint} is a slot <em>count</em> rather
 * than an inventory size: it is rounded up to whole rows (capped at 54) and the
 * background is pre-filled with the standard filler pane.
 */
public abstract class CompactGUI extends InventoryGUI {

    public CompactGUI(String name, int slotCount) {
        super(name, slotCount);
    }

    @Override
    protected Inventory createInventory(String name) {
        Inventory inv = Bukkit.createInventory(null, resolveSize(9), name);
        GUIUtil.fillEmptyGUISpots(inv);
        return inv;
    }
}
