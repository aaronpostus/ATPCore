package aaronpost.atpcore.gui.menu;

import aaronpost.atpcore.registries.IDataContainer;
import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A menu's layout, loaded from one json file per menu. The file name is not the
 * key — the {@code name} field inside is.
 * <p>
 * Field names are the file format — don't rename without migrating the json.
 */
public class MenuData implements IDataContainer {

    /** Lore is indented by this many spaces unless the json says otherwise. */
    public static final int DEFAULT_LORE_INDENT = 3;

    // REQUIRED
    private String name;

    // OPTIONAL
    private String title;
    private int rows;
    /** Whether empty slots get the standard filler pane. Defaults to true. */
    private Boolean fill;
    /**
     * Spaces prefixed to every lore line in this menu; defaults to
     * {@link #DEFAULT_LORE_INDENT}. Set {@code 0} to write lore flush left.
     * Individual items can override it with their own {@code loreIndent}.
     */
    private Integer loreIndent;
    private List<MenuItemData> items;

    @Override
    public void validate() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("A menu is missing a name");
        }
        if (rows < 1 || rows > 6) {
            rows = 3;
        }
        if (title == null) {
            title = "";
        }
        if (fill == null) {
            fill = true;
        }
        if (loreIndent == null || loreIndent < 0) {
            loreIndent = DEFAULT_LORE_INDENT;
        }
        if (items == null) {
            items = List.of();
        }
        int size = rows * 9;
        Set<String> seenIds = new HashSet<>();
        for (MenuItemData item : items) {
            item.validate(name, size);
            item.inheritIndent(loreIndent);
            if (item.id != null && !item.id.isEmpty() && !seenIds.add(item.id)) {
                throw new IllegalArgumentException(
                        "Menu '" + name + "' has two items with id '" + item.id + "'");
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public String getTitle() {
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    public int getRows() {
        return rows;
    }

    public int getSize() {
        return rows * 9;
    }

    public boolean shouldFill() {
        return fill == null || fill;
    }

    public List<MenuItemData> getItems() {
        return items;
    }

    /** Looks up an item by its {@code id}, or null if the menu has no such item. */
    public MenuItemData getItem(String id) {
        for (MenuItemData item : items) {
            if (id.equals(item.id)) return item;
        }
        return null;
    }
}
