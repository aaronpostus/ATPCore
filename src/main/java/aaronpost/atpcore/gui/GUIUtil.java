package aaronpost.atpcore.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generic inventory/item helpers shared by every plugin built on ATPCore.
 * Nothing here may reference game-specific registries or data types.
 */
public final class GUIUtil {
    public static final String RIGHT_ARROW_URL = "http://textures.minecraft.net/texture/1a4f68c8fb279e50ab786f9fa54c88ca4ecfe1eb5fd5f0c38c54c9b1c7203d7a";
    public static final String LEFT_ARROW_URL = "http://textures.minecraft.net/texture/737648ae7a564a5287792b05fac79c6b6bd47f616a559ce8b543e6947235bce";

    private static final ItemStack emptyItem;
    private static final ChatColor[] colors = new ChatColor[] {
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE,
            ChatColor.AQUA
    };

    static {
        emptyItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = emptyItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLACK + " ");
        }
        emptyItem.setItemMeta(meta);
    }

    private GUIUtil() {}

    // --- Background filling ---

    public static void fillEmptyGUISpots(Inventory inventory) {
        fillEmptyGUISpots(inventory, inventory.getSize(), emptyItem);
    }

    public static void fillEmptyGUISpots(Inventory inventory, int inventorySize, ItemStack filler) {
        for (int i = 0; i < inventorySize; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /** Fills empty slots with a deterministic (seeded) scatter of the given materials. */
    public static void fillEmptyGUISpots(Inventory inventory, Material[] materials, int seed) {
        Random r = new Random(seed);
        int length = materials.length;
        ItemStack[] stacks = new ItemStack[materials.length];
        for (int i = 0; i < length; i++) {
            ItemStack item = new ItemStack(materials[i]);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.BLACK + " ");
            }
            item.setItemMeta(meta);
            stacks[i] = item;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, stacks[r.nextInt(length)]);
            }
        }
    }

    // --- Text helpers ---

    public static List<String> prettyIndent(List<String> lore, int indent) {
        String indentation = " ".repeat(indent);
        lore.replaceAll(s -> indentation + s);
        return lore;
    }

    public static String makeRainbow(String str) {
        StringBuilder rainbowStr = new StringBuilder();
        Random random = new Random();
        for (char c : str.toCharArray()) {
            if (c == ' ') { rainbowStr.append(" "); continue; }
            rainbowStr.append(colors[random.nextInt(colors.length)]).append(c);
        }
        return rainbowStr.toString();
    }

    public static String enumStringToUserFriendly(String name) {
        String s = name.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Substitutes {@code {token}} placeholders, e.g. {@code tokens("Delete {name}?", "name", clanName)}.
     * {@code kv} is alternating key/value pairs; a key appears in the template as {@code {key}}.
     */
    public static String tokens(String template, String... kv) {
        String result = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            result = result.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return result;
    }

    /** {@link List}&lt;String&gt; overload of {@link #tokens(String, String...)}, applied line by line. */
    public static List<String> tokens(List<String> template, String... kv) {
        List<String> result = new ArrayList<>(template.size());
        for (String line : template) {
            result.add(tokens(line, kv));
        }
        return result;
    }

    // --- Lore colour ---

    /** Colour used for lore lines that don't set one; vanilla would render them purple italic. */
    public static final ChatColor DEFAULT_LORE_COLOR = ChatColor.GRAY;

    /** Prefixes {@link #DEFAULT_LORE_COLOR} onto every lore line that doesn't already pick a colour. */
    public static List<String> defaultLoreColor(List<String> lore) {
        if (lore == null) return null;
        List<String> result = new ArrayList<>(lore.size());
        for (String line : lore) {
            result.add(defaultLoreColor(line));
        }
        return result;
    }

    public static String defaultLoreColor(String line) {
        if (line == null || line.isBlank() || setsOwnColor(line)) {
            return line;
        }
        return DEFAULT_LORE_COLOR + line;
    }

    /** True when the first colour code in the line comes before any visible text. */
    private static boolean setsOwnColor(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ChatColor.COLOR_CHAR) {
                if (i + 1 >= line.length()) return false;
                char code = Character.toLowerCase(line.charAt(i + 1));
                if (code == 'x' || code == 'r') return true; // hex prefix / explicit reset
                ChatColor color = ChatColor.getByChar(code);
                return color != null && color.isColor();
            }
            if (c != ' ') return false;
        }
        return false;
    }

    // --- Item meta helpers ---

    public static ItemStack attachComingSoonLore(ItemStack stack, String name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(ChatColor.RED + "  Coming soon."));
            meta.setDisplayName(name);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack attachLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(defaultLoreColor(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack attachNameAndLore(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(defaultLoreColor(lore));
            meta.setDisplayName(name);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack attachNameAndData(ItemStack item, String name, NamespacedKey data, String dataKey) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(data, PersistentDataType.STRING, dataKey);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack attachData(ItemStack item, NamespacedKey data, String dataKey) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(data, PersistentDataType.STRING, dataKey);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack attachName(ItemStack item, String displayName) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
        }
        item.setItemMeta(meta);
        return item;
    }

    // --- Item construction (lore auto-indented by 3) ---

    public static ItemStack item(Material mat, String name, String... lore) {
        return attachNameAndLore(new ItemStack(mat), name, prettyIndent(new ArrayList<>(List.of(lore)), 3));
    }

    public static ItemStack item(Material mat, String name, List<String> lore) {
        return attachNameAndLore(new ItemStack(mat), name, prettyIndent(new ArrayList<>(lore), 3));
    }

    public static ItemStack item(ItemStack stack, String name, String... lore) {
        return attachNameAndLore(stack, name, prettyIndent(new ArrayList<>(List.of(lore)), 3));
    }

    public static ItemStack item(ItemStack stack, String name, List<String> lore) {
        return attachNameAndLore(stack, name, prettyIndent(new ArrayList<>(lore), 3));
    }

    // --- Grid/slot math ---

    /**
     * Converts world-space (x, y) coordinates into a chest inventory slot index,
     * allowing for a shifted origin (useful for scrolling/panning views).
     *
     * @param x the node's x-coordinate (0-based from left, bottom origin)
     * @param y the node's y-coordinate (0-based from bottom)
     * @param rows number of rows in the chest (e.g., 3 for a 27-slot chest)
     * @param originX the x-coordinate that represents the *leftmost visible column*
     * @param originY the y-coordinate that represents the *bottommost visible row*
     * @return the inventory slot index (0–rows*9-1), or -1 if outside visible area
     */
    public static int toSlotIndex(int x, int y, int rows, int originX, int originY) {
        int width = 9;

        // Translate coordinates relative to the visible origin
        int localX = x - originX;
        int localY = y - originY;

        // Check if the point is outside the visible chest area
        if (localX < 0 || localX >= width || localY < 0 || localY >= rows)
            return -1; // Not visible in this view

        // Flip Y to match inventory's top-down layout
        int invertedY = (rows - 1) - localY;

        return invertedY * width + localX;
    }

    public static void drawLineIntoArray(
            ItemStack[][] map,
            int x1,
            int y1,
            int x2,
            int y2,
            ItemStack item,
            boolean horizontalFirst
    ) {
        if (map == null || item == null) return;

        int dx = Integer.compare(x2, x1); // +1, 0, or -1
        int dy = Integer.compare(y2, y1);

        int cx = x1;
        int cy = y1;

        // clone the item so we don't accidentally share references
        ItemStack pathItem = item.clone();

        if (horizontalFirst) {
            // Move horizontally first, then vertically
            while (cx != x2) {
                cx += dx;
                setSafe(map, cx, cy, pathItem);
            }
            while (cy != y2) {
                cy += dy;
                setSafe(map, cx, cy, pathItem);
            }
        } else {
            // Move vertically first, then horizontally
            while (cy != y2) {
                cy += dy;
                setSafe(map, cx, cy, pathItem);
            }
            while (cx != x2) {
                cx += dx;
                setSafe(map, cx, cy, pathItem);
            }
        }
    }

    private static void setSafe(ItemStack[][] map, int x, int y, ItemStack item) {
        if (x < 0 || y < 0) return;
        if (x >= map.length) return;
        if (y >= map[0].length) return;
        map[x][y] = item.clone();
    }
}
