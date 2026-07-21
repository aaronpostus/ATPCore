package aaronpost.atpcore.gui;

import aaronpost.atpcore.ATPCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent confirmation dialog builder, replacing the old pattern of writing a
 * dedicated InventoryGUI subclass for every "are you sure?" prompt:
 * <pre>
 * ConfirmGUI.title(ChatColor.DARK_RED + "Delete X?")
 *     .yes(Material.LIME_WOOL, "Yes, Delete", lore, p -&gt; {...})
 *     .no(Material.RED_WOOL, "Cancel", lore, p -&gt; {...})
 *     .open(player);
 * </pre>
 * The clicked player's inventory is closed automatically before the action runs.
 * For choices beyond plain yes/no (e.g. a third option), use {@link #button}.
 */
public class ConfirmGUI extends CompactGUI {

    public static ConfirmGUI title(String title) {
        return new ConfirmGUI(title, 9);
    }

    public static ConfirmGUI title(String title, int size) {
        return new ConfirmGUI(title, size);
    }

    private int yesSlot = 2, noSlot = 6;

    private ConfirmGUI(String title, int size) {
        super(title, size);
    }

    public ConfirmGUI slots(int yesSlot, int noSlot) {
        this.yesSlot = yesSlot;
        this.noSlot = noSlot;
        return this;
    }

    public ConfirmGUI button(int slot, Material material, String name, List<String> lore, Consumer<Player> action) {
        return button(slot, GUIUtil.attachNameAndLore(new ItemStack(material), name, lore), action);
    }

    /** Same as {@link #button(int, Material, String, List, Consumer)} but takes an already-built icon, e.g. from an {@code ItemTemplate}. */
    public ConfirmGUI button(int slot, ItemStack icon, Consumer<Player> action) {
        addButton(slot, new InventoryButton()
                .creator(p -> icon)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    action.accept(p);
                }));
        return this;
    }

    public ConfirmGUI yes(Material material, String name, List<String> lore, Consumer<Player> action) {
        return button(yesSlot, material, name, lore, action);
    }

    public ConfirmGUI yes(ItemStack icon, Consumer<Player> action) {
        return button(yesSlot, icon, action);
    }

    public ConfirmGUI no(Material material, String name, List<String> lore, Consumer<Player> action) {
        return button(noSlot, material, name, lore, action);
    }

    public ConfirmGUI no(ItemStack icon, Consumer<Player> action) {
        return button(noSlot, icon, action);
    }

    public ConfirmGUI info(int slot, Material material, String name, List<String> lore) {
        getInventory().setItem(slot, GUIUtil.attachNameAndLore(new ItemStack(material), name, lore));
        return this;
    }

    public void open(Player player) {
        ATPCore.guiManager.openGUI(this, player);
    }
}
