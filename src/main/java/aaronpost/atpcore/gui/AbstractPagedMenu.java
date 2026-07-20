package aaronpost.atpcore.gui;

import aaronpost.atpcore.ATPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Abstract class for a paginated inventory menu.
 * Uses a custom InventoryHolder to isolate click handling to this instance.
 */
public abstract class AbstractPagedMenu<T> implements Listener {

    protected final Player player;
    protected final Inventory inventory;
    protected List<T> entries;

    protected int currentPage;
    protected int totalPages;
    protected final int itemsPerPage;

    private final String title;
    protected final int inventorySize;

    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 200;

    public AbstractPagedMenu(Player player, String title, List<T> entries) {
        this(player, title, entries, 5);
    }

    public AbstractPagedMenu(Player player, String title, List<T> entries, int itemRows) {
        this.player = player;
        this.title = title;
        this.entries = entries;
        this.itemsPerPage = itemRows * 9;
        this.inventorySize = (itemRows + 1) * 9;
        this.currentPage = 1;
        this.totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) itemsPerPage));

        this.inventory = Bukkit.createInventory(new MenuHolder(this), inventorySize, ChatColor.GREEN + title);

        Bukkit.getPluginManager().registerEvents(this, ATPCore.plugin);

        if (isAsyncLoad()) {
            // Defer rendering and opening until async data has been loaded.
            scheduleAsyncOpen();
            return;
        }

        setupNavigation();
        renderPage(1);
        player.openInventory(inventory);
        player.playSound(player.getEyeLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 0.5f, 1f);
    }

    /**
     * Subclasses override and return {@code true} to opt into async pre-loading
     * of the entry list. When enabled, the constructor will not render or open
     * the inventory until {@link #loadAsyncEntries(Player)} completes off-thread
     * and {@link #applyAsyncEntries(List)} runs on the main thread.
     */
    protected boolean isAsyncLoad() {
        return false;
    }

    /** Async-thread hook to load entries. Default returns an empty list. */
    protected List<T> loadAsyncEntries(Player player) {
        return java.util.Collections.emptyList();
    }

    /**
     * Main-thread hook to apply async-loaded entries before the inventory is
     * shown. Default replaces the entry list and recomputes pagination.
     */
    protected void applyAsyncEntries(List<T> loaded) {
        this.entries = loaded;
        this.totalPages = Math.max(1, (int) Math.ceil(loaded.size() / (double) itemsPerPage));
    }

    private void scheduleAsyncOpen() {
        if (ATPCore.isShuttingDown()) return;
        Bukkit.getScheduler().runTaskAsynchronously(ATPCore.plugin, () -> {
            List<T> loaded = loadAsyncEntries(player);
            if (ATPCore.isShuttingDown()) return;
            Bukkit.getScheduler().runTask(ATPCore.plugin, () -> {
                if (!player.isOnline()) {
                    // Avoid leaking the registered listener if the player disconnected mid-load.
                    HandlerList.unregisterAll(this);
                    return;
                }
                applyAsyncEntries(loaded);
                setupNavigation();
                renderPage(1);
                player.openInventory(inventory);
                player.playSound(player.getEyeLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 0.5f, 1f);
            });
        });
    }

    /** Called to populate a given page’s items */
    protected abstract void renderItems(int startIndex, int endIndex);

    /** Called when an item is clicked */
    protected abstract void onItemClick(ItemStack item, T data, InventoryClickEvent event);

    /** Called when the menu is closed */
    protected void onMenuClose() { }

    private void setupNavigation() {
        // Left arrow
        ItemStack leftArrow = OfflineSkull.getSkull(GUIUtil.LEFT_ARROW_URL);
        ItemMeta meta = leftArrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "<==");
            leftArrow.setItemMeta(meta);
        }
        inventory.setItem(itemsPerPage, leftArrow);

        // Right arrow
        ItemStack rightArrow = OfflineSkull.getSkull(GUIUtil.RIGHT_ARROW_URL);
        meta = rightArrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "==>");
            rightArrow.setItemMeta(meta);
        }
        inventory.setItem(inventorySize - 1, rightArrow);
        renderFooter();
    }

    /** Hook for subclasses to render custom footer items in the navigation row. */
    protected void renderFooter() { }

    /** Hook for subclasses to handle footer-item clicks; return true to mark handled. */
    protected boolean onFooterClick(int slot, InventoryClickEvent e) { return false; }

    /** Re-renders the custom footer. */
    protected final void refreshFooter() {
        renderFooter();
    }

    protected void renderPage(int page) {
        clearPage();
        this.currentPage = page;
        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, entries.size());
        renderItems(start, end);
        GUIUtil.fillEmptyGUISpots(inventory);
    }

    private void clearPage() {
        for (int slot = 0; slot < itemsPerPage; slot++) {
            inventory.setItem(slot, null);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // Scope clicks only to this menu instance
        if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.getMenu() != this) return;
        if (!e.getWhoClicked().equals(player)) return;
        if (e.getCurrentItem() == null) return;

        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) {
            e.setCancelled(true);
            return;
        }
        lastClickTime = now;

        e.setCancelled(true);

        ItemStack item = e.getCurrentItem();
        String display = item.hasItemMeta() ? ChatColor.stripColor(item.getItemMeta().getDisplayName()) : "";

        // Navigation arrows
        if (item.getType() == Material.PLAYER_HEAD) {
            if (display.equals("<==") && currentPage > 1) {
                renderPage(currentPage - 1);
                return;
            } else if (display.equals("==>") && currentPage < totalPages) {
                renderPage(currentPage + 1);
                return;
            }
        }

        int slot = e.getSlot();
        if (slot > itemsPerPage && slot < inventorySize - 1) {
            if (onFooterClick(slot, e)) return;
        }

        // Clicked a data item
        int index = (currentPage - 1) * itemsPerPage + e.getSlot();
        if (index < entries.size()) {
            onItemClick(item, entries.get(index), e);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.getMenu() != this) return;

        HandlerList.unregisterAll(this);
        onMenuClose();
    }

    /**
     * Custom holder to isolate click events to a specific menu instance.
     */
    public static class MenuHolder implements InventoryHolder {
        private final AbstractPagedMenu<?> menu;
        public MenuHolder(AbstractPagedMenu<?> menu) {
            this.menu = menu;
        }
        @Override
        public Inventory getInventory() {
            return null;
        }
        public AbstractPagedMenu<?> getMenu() {
            return menu;
        }
    }
}
