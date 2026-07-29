package aaronpost.atpcore.gui;

import aaronpost.atpcore.ATPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Paginated inventory menu. Constructing one opens it for the player, and decorates it
 * once more on the next tick so subclass fields assigned after {@code super(...)} are
 * reflected.
 * <p>
 * Click/close routing goes through {@link GUIManager} like every other
 * {@link InventoryGUI}; this class does not register listeners of its own.
 * The bottom row is reserved for navigation: a left arrow at slot
 * {@code itemsPerPage}, a right arrow at the last slot, and whatever
 * {@link #renderFooter()} draws in between.
 */
public abstract class AbstractPagedMenu<T> extends InventoryGUI {

    protected final Player player;
    protected List<T> entries;

    protected int currentPage;
    protected int totalPages;
    protected final int itemsPerPage;
    protected final int inventorySize;

    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 200;

    /** False until construction has finished; see {@link #decorate(Player)}. */
    private boolean constructed;

    public AbstractPagedMenu(Player player, String title, List<T> entries) {
        this(player, title, entries, 5);
    }

    public AbstractPagedMenu(Player player, String title, List<T> entries, int itemRows) {
        super(ChatColor.GREEN + title, (itemRows + 1) * 9);
        this.player = player;
        this.entries = entries;
        this.itemsPerPage = itemRows * 9;
        this.inventorySize = (itemRows + 1) * 9;
        this.currentPage = 1;
        this.totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) itemsPerPage));

        ATPCore.guiManager.openGUI(this, player);
        scheduleConstructionRender();
    }

    /** Opening decorates the menu before any subclass constructor body has run. */
    private void scheduleConstructionRender() {
        if (ATPCore.isShuttingDown()) return;
        Bukkit.getScheduler().runTask(ATPCore.plugin, () -> {
            constructed = true;
            if (player != null && player.isOnline()) {
                decorate(player);
            }
        });
    }

    @Override
    protected Inventory createInventory(String name) {
        return Bukkit.createInventory(null, resolveSize(54), name);
    }

    // --- Async pre-load, bridged onto the InventoryGUI hooks ---

    /**
     * Subclasses override and return {@code true} to opt into async pre-loading
     * of the entry list. When enabled, the menu is not rendered or opened until
     * {@link #loadAsyncEntries(Player)} completes off-thread and
     * {@link #applyAsyncEntries(List)} runs on the main thread.
     */
    @Override
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

    @Override
    protected final Object loadAsync(Player player) {
        return loadAsyncEntries(player);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final void applyAsyncData(Object data) {
        if (data instanceof List<?> list) {
            applyAsyncEntries((List<T>) list);
        }
    }

    // --- Rendering ---

    /** Called to populate a given page's items */
    protected abstract void renderItems(int startIndex, int endIndex);

    /** Called when an item is clicked */
    protected abstract void onItemClick(ItemStack item, T data, InventoryClickEvent event);

    /** Called when the menu is closed */
    protected void onMenuClose() { }

    @Override
    public void decorate(Player player) {
        if (!constructed) {
            // Subclass state may be missing; scheduleConstructionRender() redraws shortly.
            try {
                setupNavigation();
                renderPage(currentPage);
            } catch (RuntimeException ignored) {
            }
            return;
        }
        setupNavigation();
        renderPage(currentPage);
    }

    private void setupNavigation() {
        // Left arrow
        ItemStack leftArrow = OfflineSkull.getSkull(GUIUtil.LEFT_ARROW_URL);
        ItemMeta meta = leftArrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "<==");
            leftArrow.setItemMeta(meta);
        }
        getInventory().setItem(itemsPerPage, leftArrow);

        // Right arrow
        ItemStack rightArrow = OfflineSkull.getSkull(GUIUtil.RIGHT_ARROW_URL);
        meta = rightArrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "==>");
            rightArrow.setItemMeta(meta);
        }
        getInventory().setItem(inventorySize - 1, rightArrow);
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
        GUIUtil.fillEmptyGUISpots(getInventory());
    }

    private void clearPage() {
        for (int slot = 0; slot < itemsPerPage; slot++) {
            getInventory().setItem(slot, null);
        }
    }

    // --- Event routing ---

    @Override
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(true);

        if (!e.getWhoClicked().equals(player)) return;
        if (e.getCurrentItem() == null) return;

        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) {
            return;
        }
        lastClickTime = now;

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

    @Override
    public void onClose(InventoryCloseEvent e) {
        super.onClose(e);
        onMenuClose();
    }
}
