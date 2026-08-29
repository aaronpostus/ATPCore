package aaronpost.atpcore.gui.menu;

import aaronpost.atpcore.gui.GUIUtil;
import aaronpost.atpcore.gui.ItemTemplate;
import aaronpost.atpcore.gui.OfflineSkull;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
    /**
     * Omit to reserve the slot without an icon — the menu's
     * {@link AbstractJsonMenu#decorateDynamic} paints it instead. Only allowed
     * on an item with an {@code id}, since something has to identify the slot
     * to the code that fills it. Use this for buttons whose icon genuinely
     * varies at render time (a toggle, a cost that may be unaffordable).
     */
    public String material;

    // OPTIONAL
    /** Handle Java code binds behaviour to. Must be unique within the menu. */
    public String id;
    public String name;
    public List<String> lore;
    public int amount = 1;
    /**
     * Texture URL for a custom player head, e.g.
     * {@code "http://textures.minecraft.net/texture/<hash>"}. Requires
     * {@code material} to be {@code PLAYER_HEAD}. Without this a head renders
     * untextured, which silently loses the icon a menu was designed around.
     */
    public String skullUrl;
    /**
     * Spaces prefixed to each lore line, overriding the menu's
     * {@code loreIndent}. Write the lore unindented and let this do the
     * spacing. {@code 0} disables it for this item.
     */
    public Integer loreIndent;
    /**
     * {@code minecraft:custom_model_data} strings (usually one, e.g.
     * {@code ["builder_tool"]}) so a resource pack can retexture just this item
     * instead of every stack of the material.
     */
    public List<String> modelData;
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
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException(
                        "Menu '" + menuKey + "' slot " + slot + " has no material and no id. "
                                + "Give it a material, or give it an id and paint it in decorateDynamic.");
            }
        } else if (Material.matchMaterial(material) == null) {
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' slot " + slot + " has unknown material '" + material + "'");
        }
        if (amount < 1) amount = 1;
        if (lore == null) lore = List.of();
        if (name == null) name = "";
        if (loreIndent != null && loreIndent < 0) loreIndent = 0;
        if (skullUrl != null && !skullUrl.isEmpty()
                && (isCodePainted() || Material.matchMaterial(material) != Material.PLAYER_HEAD)) {
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' slot " + slot + " sets skullUrl but its material is "
                            + (isCodePainted() ? "code-painted" : "'" + material + "'")
                            + "; skullUrl requires PLAYER_HEAD.");
        }
        if (modelData != null && !modelData.isEmpty() && isCodePainted()) {
            throw new IllegalArgumentException(
                    "Menu '" + menuKey + "' slot " + slot + " sets modelData but is code-painted; "
                            + "the code that paints the slot owns the item's tags.");
        }
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

    /**
     * True when the json reserves this slot but leaves the icon to code.
     * {@link #getTemplate()} returns null for these.
     */
    public boolean isCodePainted() {
        return material == null || material.isEmpty();
    }

    /** Fills in any appearance field the json left unset; idempotent. */
    public void applyDefaults(String defaultMaterial, String defaultName, List<String> defaultLore) {
        boolean changed = false;
        if (isCodePainted() && defaultMaterial != null) {
            material = defaultMaterial;
            changed = true;
        }
        if ((name == null || name.isEmpty()) && defaultName != null) {
            name = defaultName;
            changed = true;
        }
        if ((lore == null || lore.isEmpty()) && defaultLore != null) {
            lore = defaultLore;
            changed = true;
        }
        // The template caches the old appearance; drop it so it rebuilds.
        if (changed) {
            template = null;
        }
    }

    /**
     * The item's static shape, or null if this item is
     * {@linkplain #isCodePainted() code-painted}. {@code {token}} placeholders
     * are resolved per render.
     */
    public ItemTemplate getTemplate() {
        if (isCodePainted()) {
            return null;
        }
        if (template == null) {
            int indent = loreIndent == null ? MenuData.DEFAULT_LORE_INDENT : loreIndent;
            String pad = " ".repeat(indent);
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) {
                String text = color(line);
                // Blank spacer lines stay blank — padding them just adds trailing space.
                colored.add(text.isEmpty() ? text : pad + text);
            }
            ItemStack base = (skullUrl == null || skullUrl.isEmpty())
                    ? new ItemStack(Material.matchMaterial(material))
                    : OfflineSkull.getSkull(skullUrl);
            if (modelData != null && !modelData.isEmpty()) {
                GUIUtil.tagModelData(base, modelData);
            }
            template = new ItemTemplate(base, color(name), colored);
        }
        return template;
    }

    private static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
