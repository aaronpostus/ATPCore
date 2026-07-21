package aaronpost.atpcore.gui.menu;

import aaronpost.atpcore.ATPCore;
import aaronpost.atpcore.gui.GUIUtil;
import aaronpost.atpcore.gui.InventoryButton;
import aaronpost.atpcore.gui.InventoryGUI;
import aaronpost.atpcore.gui.ItemTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A menu whose <em>layout</em> comes from json and whose <em>behaviour</em> is
 * written in Java. The json says where an item sits and what it looks like;
 * code says what it does.
 * <p>
 * Game plugins subclass this once to bind their own context type, then write
 * one small class per menu:
 *
 * <pre>
 * public class IslandShopMenu extends JsonBackedMenu {
 *     public IslandShopMenu() { super("island_shop"); }
 *
 *     &#64;Override protected void bindMenuData() {
 *         on("army", ctx -&gt; openShop("army", ctx));
 *     }
 * }
 * </pre>
 *
 * Bindings are matched to json items by their {@code id}. Binding an id the
 * json doesn't define logs SEVERE and is ignored, so renaming an item in json
 * surfaces in the log rather than failing silently. Items with no binding are
 * decorative.
 *
 * @param <C> the host plugin's menu-context type, built by {@link #createContext}
 */
public abstract class AbstractJsonMenu<C> extends InventoryGUI {

    private static Function<String, MenuData> menuSource = key -> null;
    private static String messagePrefix = "";

    /**
     * Tells ATPCore how to resolve a menu key, normally
     * {@code Registry.MenuData::get}. Call once from the host plugin's
     * {@code onEnable}, before any menu is opened.
     */
    public static void setMenuSource(Function<String, MenuData> source) {
        menuSource = source == null ? key -> null : source;
    }

    /** Chat prefix prepended to {@code message:} actions. */
    public static void setMessagePrefix(String prefix) {
        messagePrefix = prefix == null ? "" : prefix;
    }

    protected final MenuData data;
    private boolean bound;

    private final Map<String, Consumer<C>> actions = new HashMap<>();
    private final Map<String, Predicate<C>> visibility = new HashMap<>();
    private final Map<String, Function<C, String>> tokens = new LinkedHashMap<>();

    protected AbstractJsonMenu(String menuKey) {
        super(titleOf(menuKey), sizeOf(menuKey));
        this.data = menuSource.apply(menuKey);
    }

    /** Builds the per-render context handed to bound actions, conditions and tokens. */
    protected abstract C createContext(Player player);

    // --- Binding ---

    /**
     * Attaches this menu's behaviour to its json layout. Subclasses call
     * {@link #on}, {@link #showIf} and {@link #token} here.
     * <p>
     * Runs once, lazily, before the first render — so subclass fields set in
     * the constructor are available. Menus with no behaviour at all don't need
     * to override it.
     */
    protected void bindMenuData() {
    }

    /** Binds a click action to the json item with this {@code id}. */
    public AbstractJsonMenu<C> on(String itemId, Consumer<C> action) {
        if (declares(itemId, "action")) {
            actions.put(itemId, action);
        }
        return this;
    }

    /** Shows the json item with this {@code id} only when the predicate passes. */
    public AbstractJsonMenu<C> showIf(String itemId, Predicate<C> visible) {
        if (declares(itemId, "showIf")) {
            visibility.put(itemId, visible);
        }
        return this;
    }

    /** Supplies a fixed {@code {token}} value for this menu's name/lore text. */
    public AbstractJsonMenu<C> token(String key, String value) {
        String safe = value == null ? "" : value;
        tokens.put(key, ctx -> safe);
        return this;
    }

    /** Supplies a {@code {token}} value recomputed on every render, for live figures. */
    public AbstractJsonMenu<C> token(String key, Function<C, String> supplier) {
        tokens.put(key, supplier);
        return this;
    }

    public void open(Player player) {
        ATPCore.guiManager.openGUI(this, player);
    }

    private boolean declares(String itemId, String what) {
        if (data == null) return false; // already logged at startup
        if (data.getItem(itemId) == null) {
            ATPCore.logError("Menu '" + data.getName() + "' has no item with id '"
                    + itemId + "' to bind a " + what + " to.");
            return false;
        }
        return true;
    }

    // Title/size must be known before the super constructor builds the inventory,
    // so both are read straight off the source rather than off `data`.
    private static String titleOf(String menuKey) {
        MenuData menu = menuSource.apply(menuKey);
        return menu == null ? menuKey : menu.getTitle();
    }

    private static int sizeOf(String menuKey) {
        MenuData menu = menuSource.apply(menuKey);
        return menu == null ? 27 : menu.getSize();
    }

    @Override
    protected Inventory createInventory(String name) {
        return Bukkit.createInventory(null, resolveSize(27), name);
    }

    // --- Rendering ---

    @Override
    public void decorate(Player player) {
        if (data == null) {
            // Menu json missing from the server's Menus folder. Say so rather
            // than throwing; the startup log already flagged it.
            getInventory().setItem(getInventory().getSize() / 2, GUIUtil.item(
                    Material.BARRIER,
                    ChatColor.RED + "Menu unavailable",
                    ChatColor.GRAY + "This menu's configuration is missing.",
                    ChatColor.GRAY + "Ask an admin to check the server log."));
            GUIUtil.fillEmptyGUISpots(getInventory());
            return;
        }

        if (!bound) {
            bound = true;
            bindMenuData();
        }

        C ctx = createContext(player);

        for (MenuItemData item : data.getItems()) {
            Predicate<C> visible = item.id == null ? null : visibility.get(item.id);
            if (visible != null && !visible.test(ctx)) {
                getInventory().setItem(item.slot, null);
                removeButton(item.slot);
                continue;
            }

            getInventory().setItem(item.slot, render(item, ctx));

            // A code binding wins; otherwise fall back to the item's built-in
            // json action (close / message), if it has one.
            Consumer<C> action = item.id == null ? null : actions.get(item.id);
            if (action == null) {
                action = builtIn(item.action);
            }
            if (action != null) {
                Consumer<C> boundAction = action;
                addButton(item.slot, new InventoryButton()
                        .creator(p -> render(item, createContext(p)))
                        .consumer(event -> {
                            Player clicker = (Player) event.getWhoClicked();
                            boundAction.accept(createContext(clicker));
                        }));
            }
        }

        decorateDynamic(player);

        // Buttons added by decorateDynamic paint themselves here.
        super.decorate(player);

        if (data.shouldFill()) {
            GUIUtil.fillEmptyGUISpots(getInventory());
        }
    }

    /**
     * The two actions simple enough to live in json: closing the menu, and
     * sending a line of chat. Neither touches game state. Anything else is
     * rejected by {@link MenuItemData#validate}.
     */
    private Consumer<C> builtIn(String action) {
        if (action == null || action.isEmpty()) return null;
        if (action.equals("close")) {
            return ctx -> closeFor(ctx);
        }
        if (action.startsWith("message:")) {
            String text = action.substring("message:".length());
            return ctx -> {
                Player p = viewerOf(ctx);
                if (p == null) return;
                p.closeInventory();
                p.sendMessage(messagePrefix + " " + ChatColor.translateAlternateColorCodes('&', text));
            };
        }
        return null;
    }

    private void closeFor(C ctx) {
        Player p = viewerOf(ctx);
        if (p != null) p.closeInventory();
    }

    /**
     * The player a context belongs to. The base class only needs this for the
     * built-in {@code close}/{@code message:} actions; the default falls back
     * to whoever is currently viewing the menu.
     */
    protected Player viewerOf(C ctx) {
        return getViewer();
    }

    /** Builds one item with this menu's tokens applied. */
    private ItemStack render(MenuItemData item, C ctx) {
        ItemTemplate template = item.getTemplate();
        if (tokens.isEmpty()) {
            return template.build(item.amount);
        }
        List<String> kv = new ArrayList<>(tokens.size() * 2);
        for (Map.Entry<String, Function<C, String>> e : tokens.entrySet()) {
            kv.add(e.getKey());
            String value;
            try {
                value = e.getValue().apply(ctx);
            } catch (Exception ex) {
                value = "";
            }
            kv.add(value == null ? "" : value);
        }
        return template.build(item.amount, kv.toArray(new String[0]));
    }

    /**
     * Hook for the parts of a menu that can't be a fixed grid: per-player state,
     * loop-built lists, live counters. Default does nothing.
     */
    protected void decorateDynamic(Player player) {
    }
}
