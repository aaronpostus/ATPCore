package aaronpost.atpcore.gui;

import aaronpost.atpcore.ATPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for button-driven inventory GUIs. Click routing is handled by
 * {@link GUIManager}, which maps the backing {@link Inventory} to this handler,
 * so subclasses never register their own listeners.
 */
public abstract class InventoryGUI implements InventoryHandler {

    /** Protected so paged/custom subclasses can render into it directly. */
    protected final Inventory inventory;
    private final Map<Integer, InventoryButton> buttonMap = new HashMap<>();
    protected int sizeHint = -1;

    private BukkitTask refreshTask;
    private Player viewer;

    public InventoryGUI(String name) {
        this.inventory = this.createInventory(name);
    }

    /**
     * Constructor that stores a size hint before createInventory runs.
     * Subclasses can read {@code sizeHint} inside createInventory to
     * determine the inventory size without relying on other fields.
     */
    public InventoryGUI(String name, int sizeHint) {
        this.sizeHint = sizeHint;
        this.inventory = this.createInventory(name);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    /** The player currently viewing this GUI, or null before it is opened. */
    public Player getViewer() {
        return this.viewer;
    }

    public void addButton(int slot, InventoryButton button) {
        this.buttonMap.put(slot, button);
    }

    public void removeButton(int slot) {
        this.buttonMap.remove(slot);
    }

    /**
     * Convenience for the common "static icon, act on the clicking player" button.
     * The icon is placed immediately and re-applied on every {@link #decorate}.
     */
    public void setButton(int slot, ItemStack icon, Consumer<Player> action) {
        addButton(slot, new InventoryButton()
                .creator(p -> icon)
                .consumer(event -> action.accept((Player) event.getWhoClicked())));
        this.inventory.setItem(slot, icon);
    }

    public void decorate(Player player) {
        this.buttonMap.forEach((slot, button) -> {
            ItemStack icon = button.getIconCreator().apply(player);
            this.inventory.setItem(slot, icon);
        });
    }

    // --- Periodic refresh ---

    /**
     * Re-runs {@link #decorate} once per second until the GUI closes. Safe to
     * call more than once; only one task is ever scheduled. Subclasses that
     * override {@link #onClose} must call {@code super.onClose(event)} or the
     * task will leak.
     */
    protected void refreshEverySecond() {
        refreshEvery(20L);
    }

    /** Re-runs {@link #decorate} on the given tick period until the GUI closes. */
    protected void refreshEvery(long periodTicks) {
        if (refreshTask != null || ATPCore.isShuttingDown()) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(ATPCore.plugin, () -> {
            Player p = this.viewer;
            if (p == null || !p.isOnline()) {
                cancelRefresh();
                return;
            }
            decorate(p);
        }, periodTicks, periodTicks);
    }

    /** Forces an immediate re-decorate for the current viewer. */
    protected void refreshRequest() {
        Player p = this.viewer;
        if (p != null && p.isOnline()) {
            decorate(p);
        }
    }

    private void cancelRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    // --- Async pre-load hooks ---

    /**
     * Subclasses override and return {@code true} to enable an async pre-load
     * step before the inventory is shown. When enabled, {@link GUIManager#openGUI}
     * will run {@link #loadAsync(Player)} on an async thread and pass its result
     * to {@link #applyAsyncData(Object)} on the main thread before opening.
     */
    protected boolean isAsyncLoad() {
        return false;
    }

    /**
     * Optional async pre-load step. Runs off the main thread; do not touch
     * Bukkit state here. The returned value is forwarded to
     * {@link #applyAsyncData(Object)} on the main thread.
     */
    protected Object loadAsync(Player player) {
        return null;
    }

    /**
     * Applies data produced by {@link #loadAsync(Player)} on the main thread,
     * before {@link #decorate(Player)} runs.
     */
    protected void applyAsyncData(Object data) {
    }

    // --- Event routing ---

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();
        InventoryButton button = this.buttonMap.get(slot);
        if (button != null) {
            button.getEventConsumer().accept(event);
        }
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        this.viewer = (Player) event.getPlayer();
        this.decorate(this.viewer);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        cancelRefresh();
    }

    /**
     * Builds the backing inventory. The default creates a plain chest inventory
     * sized from {@code sizeHint} (rounded up to whole rows, 9..54, default 27).
     * Override only when a custom {@link org.bukkit.inventory.InventoryHolder}
     * or a non-chest inventory type is needed.
     */
    protected Inventory createInventory(String name) {
        return Bukkit.createInventory(null, resolveSize(27), name);
    }

    /** Rounds {@code sizeHint} up to whole rows, clamped to 9..54. */
    protected final int resolveSize(int fallback) {
        int count = sizeHint > 0 ? sizeHint : fallback;
        int size = Math.max(9, (int) Math.ceil(count / 9.0) * 9);
        return Math.min(size, 54);
    }
}
