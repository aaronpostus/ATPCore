package aaronpost.atpcore.gui;

import aaronpost.atpcore.ATPCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes inventory events to the {@link InventoryHandler} that owns the
 * inventory. A single instance ({@link ATPCore#guiManager}) serves every plugin
 * on the server, so one {@link GUIListener} registration covers them all.
 */
public class GUIManager {
    private final Map<Inventory, InventoryHandler> activeInventories = new HashMap<>();

    public void openGUI(InventoryGUI gui, Player player) {
        if (gui.isAsyncLoad()) {
            openGUIAsync(gui, player);
            return;
        }
        showInventory(gui, player);
    }

    private void openGUIAsync(InventoryGUI gui, Player player) {
        if (ATPCore.isShuttingDown()) return;
        Bukkit.getScheduler().runTaskAsynchronously(ATPCore.plugin, () -> {
            Object data = gui.loadAsync(player);
            if (ATPCore.isShuttingDown()) return;
            Bukkit.getScheduler().runTask(ATPCore.plugin, () -> {
                if (!player.isOnline()) return;
                gui.applyAsyncData(data);
                showInventory(gui, player);
            });
        });
    }

    private void showInventory(InventoryGUI gui, Player player) {
        player.playSound(player.getEyeLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 0.5f, 1f);
        this.registerHandledInventory(gui.getInventory(), gui);
        player.openInventory(gui.getInventory());
    }

    public void registerHandledInventory(Inventory inventory, InventoryHandler handler) {
        this.activeInventories.put(inventory, handler);
    }

    public void unregisterInventory(Inventory inventory) {
        this.activeInventories.remove(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());
        if (handler != null) {
            handler.onClick(event);
        }
    }

    public void handleOpen(InventoryOpenEvent event) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());
        if (handler != null) {
            handler.onOpen(event);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHandler handler = this.activeInventories.get(inventory);
        if (handler != null) {
            handler.onClose(event);
            this.unregisterInventory(inventory);
        }
    }
}
