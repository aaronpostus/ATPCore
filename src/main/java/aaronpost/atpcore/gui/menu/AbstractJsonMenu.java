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

    /**
     * @param menuKey     the json file's {@code name}
     * @param titleTokens optional {@code key, value, ...} pairs substituted into
     *                    the json {@code title}, for menus titled after the thing
     *                    they were opened from. These are resolved before the
     *                    inventory exists, so they may only come from constructor
     *                    arguments — {@link #token} is the general mechanism.
     */
    protected AbstractJsonMenu(String menuKey, String... titleTokens) {
        this(menuKey, null, titleTokens);
    }

    /**
     * Variant for a family of menus that share a title convention, so their
     * json files can leave {@code title} out entirely.
     *
     * @param defaultTitle used when the json omits {@code title}; may itself
     *                     contain {@code &} colours and {@code {tokens}}
     * @param titleTokens  an array rather than varargs purely to keep this
     *                     constructor distinct from the one above
     */
    protected AbstractJsonMenu(String menuKey, String defaultTitle, String[] titleTokens) {
        super(titleOf(menuKey, defaultTitle, titleTokens), sizeOf(menuKey));
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

    /** The slot an id sits in, or -1 if this menu's json doesn't declare it. */
    protected final int slotOf(String itemId) {
        if (data == null) return -1;
        MenuItemData item = data.getItem(itemId);
        return item == null ? -1 : item.slot;
    }

    /** True if the json places an item in this slot. */
    protected final boolean isJsonSlot(int slot) {
        if (data == null) return false;
        for (MenuItemData item : data.getItems()) {
            if (item.slot == slot) return true;
        }
        return false;
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
    private static String titleOf(String menuKey, String defaultTitle, String[] titleTokens) {
        MenuData menu = menuSource.apply(menuKey);
        if (menu == null) return menuKey;
        String title = menu.getTitle();
        if (title.isEmpty() && defaultTitle != null) {
            title = ChatColor.translateAlternateColorCodes('&', defaultTitle);
        }
        return GUIUtil.tokens(title, titleTokens == null ? new String[0] : titleTokens);
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

        // Repaint bound buttons from their icon creators first, so that
        // decorateDynamic gets the last word: a subclass that overrides the
        // icon of a json-declared item (a toggle showing live state, say) must
        // not have that overwritten by the item's own creator afterwards.
        super.decorate(player);

        decorateDynamic(player);

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

    /**
     * Builds one item with this menu's tokens applied, or null when the item is
     * code-painted — the slot stays empty until {@link #decorateDynamic} fills it.
     */
    private ItemStack render(MenuItemData item, C ctx) {
        ItemTemplate template = item.getTemplate();
        if (template == null) {
            return null;
        }
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
     * <p>
     * Runs after the json items and their bound buttons are painted, so
     * anything set here wins. That includes overriding the icon of a
     * json-declared item — declare it in json to get the slot and the click
     * binding, then repaint it here to show live state.
     */
    protected void decorateDynamic(Player player) {
    }
}
