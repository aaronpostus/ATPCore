package aaronpost.atpcore.gui.menu;

import aaronpost.atpcore.gui.ItemTemplate;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * One item in a json-defined menu: where it sits and what it looks like.
 * <p>
 * Behaviour is not defined here. An item with an {@code id} can have a click
 * action or a visibility rule attached in Java via
 * {@link AbstractJsonMenu#on} / {@link AbstractJsonMenu#showIf}; an item
 * without one is decorative.
 * <p>
 * Field names are the file format — don't rename without migrating the json.
 */
public class MenuItemData {
    // REQUIRED
    public int slot = -1;
    public String material;

    // OPTIONAL
    /** Handle Java code binds behaviour to. Must be unique within the menu. */
    public String id;
    public String name;
    public List<String> lore;
    public int amount = 1;
    /**
     * Spaces prefixed to each lore line, overriding the menu's
     * {@code loreIndent}. Write the lore unindented and let this do the
     * spacing. {@code 0} disables it for this item.
     */
    public Integer loreIndent;
    /**
     * A trivial built-in action, for buttons not worth a Java binding:
     * {@code "close"} or {@code "message:<text>"} ({@code &} colours, prefixed).
     * Anything that touches game state is bound in code instead — see
     * {@link AbstractJsonMenu#on} — and a code binding for this item's
     * {@code id} takes precedence over this field.
     */
    public String action;

    private transient ItemTemplate template;

    /** @param menuKey owning menu, for error messages only */
    public void validate(String menuKey, int inventorySize) {
        if (slot < 0 || slot >= inventorySize) {
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' has an item at slot " + slot
                            + ", outside the " + inventorySize + "-slot inventory");
        }
        if (material == null || material.isEmpty()) {
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' slot " + slot + " is missing a material");
        }
        if (Material.matchMaterial(material) == null) {
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' slot " + slot + " has unknown material '" + material + "'");
        }
        if (amount < 1) amount = 1;
        if (lore == null) lore = List.of();
        if (name == null) name = "";
        if (loreIndent != null && loreIndent < 0) loreIndent = 0;
        if (action != null && !action.isEmpty() && !isBuiltInAction(action)) {
            // Deliberately strict: json owns layout and trivial text, code owns
            // behaviour. Rejecting anything else here stops a DSL growing back.
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' slot " + slot + " has action '" + action
                            + "'; json actions are limited to \"close\" and \"message:<text>\". "
                            + "Give the item an id and bind the behaviour in code instead.");
        }
    }

    static boolean isBuiltInAction(String action) {
        return action.equals("close") || action.startsWith("message:");
    }

    /** Called by {@link MenuData#validate} so each item ends up with a concrete indent. */
    void inheritIndent(int menuIndent) {
        if (loreIndent == null) {
            loreIndent = menuIndent;
        }
    }

    /** The item's static shape; {@code {token}} placeholders are resolved per render. */
    public ItemTemplate getTemplate() {
        if (template == null) {
            int indent = loreIndent == null ? MenuData.DEFAULT_LORE_INDENT : loreIndent;
            String pad = " ".repeat(indent);
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) {
                String text = color(line);
                // Blank spacer lines stay blank — padding them just adds trailing space.
                colored.add(text.isEmpty() ? text : pad + text);
            }
            template = new ItemTemplate(Material.matchMaterial(material), color(name), colored);
        }
        return template;
    }

    private static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
