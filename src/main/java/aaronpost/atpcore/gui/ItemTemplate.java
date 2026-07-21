package aaronpost.atpcore.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A reusable GUI item definition: material + name/lore that may contain
 * {@code {token}} placeholders, resolved at build time via {@link GUIUtil#tokens}.
 * Item text lives on a constant or in menu json instead of being duplicated
 * inline at every menu call site.
 */
public class ItemTemplate {
    private final Material material;
    /** When set, items are built from a clone of this instead of {@code material}. */
    private final ItemStack base;
    private final String name;
    private final List<String> lore;

    public ItemTemplate(Material material, String name, List<String> lore) {
        this.material = material;
        this.base = null;
        this.name = name;
        this.lore = lore;
    }

    public ItemTemplate(Material material, String name, String... lore) {
        this(material, name, List.of(lore));
    }

    /**
     * Template over a prepared stack — a textured head, a coloured banner,
     * anything whose appearance a {@link Material} alone can't express. The
     * stack is cloned on every build, so the original is never mutated.
     */
    public ItemTemplate(ItemStack base, String name, List<String> lore) {
        this.material = base.getType();
        this.base = base;
        this.name = name;
        this.lore = lore;
    }

    public Material getMaterial() {
        return material;
    }

    public String getRawName() {
        return name;
    }

    public List<String> getRawLore() {
        return lore;
    }

    /** Builds the item, substituting any {@code {token}} placeholders with the given key/value pairs. */
    public ItemStack build(String... tokenKv) {
        ItemStack stack = base != null ? base.clone() : new ItemStack(material);
        return GUIUtil.attachNameAndLore(stack,
                GUIUtil.tokens(name, tokenKv),
                GUIUtil.tokens(lore, tokenKv));
    }

    /** Builds the item with a specific stack size (e.g. amount reflecting a count), clamped to 1-64. */
    public ItemStack build(int amount, String... tokenKv) {
        ItemStack stack = build(tokenKv);
        stack.setAmount(Math.max(1, Math.min(amount, 64)));
        return stack;
    }

    /** Builds onto a caller-supplied base stack (for dynamic materials, e.g. a clan banner). */
    public ItemStack buildOn(ItemStack base, String... tokenKv) {
        return GUIUtil.attachNameAndLore(base,
                GUIUtil.tokens(name, tokenKv),
                GUIUtil.tokens(lore, tokenKv));
    }
}
