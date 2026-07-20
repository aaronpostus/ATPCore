package aaronpost.atpcore.gui;

import org.bukkit.inventory.ItemStack;

public interface IDisplayable {
    /**
     * Retreive an itemstack with a nicely formatted name and icon
     **/
    ItemStack getItemStack();

    /**
     * Retreive a string with a nicely formatted name
     **/
    String getDisplayName();
}
