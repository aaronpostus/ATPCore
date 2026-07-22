package aaronpost.atpcore.gui.menu;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * A json-backed "are you sure?" dialog: the wording and layout live in a menu
 * json file, the actions stay in Java.
 *
 * <pre>
 * ConfirmJsonGUI.of("confirm_disband_clan")
 *         .yes(p -&gt; disband(p))
 *         .no(p -&gt; p.sendMessage("Cancelled."))
 *         .open(player);
 * </pre>
 *
 * <h3>Reserved ids</h3>
 * {@code yes} and {@code no} carry defaults, so the smallest useful file is
 * just two slots:
 *
 * <pre>
 * { "name": "confirm_x", "title": "&amp;cReally?", "rows": 1,
 *   "items": [ { "id": "yes", "slot": 2 }, { "id": "no", "slot": 6 } ] }
 * </pre>
 *
 * Defaults apply per field, so a file can override the name and still inherit
 * the material. A {@code no} that only needs to shut the dialog can skip the
 * Java binding entirely and use {@code "action": "close"} in json.
 * <p>
 * Both bindings close the inventory before running, matching what a player
 * expects from a dialog.
 */
public class ConfirmJsonGUI extends AbstractJsonMenu<Player> {

    public static final String ID_YES = "yes";
    public static final String ID_NO = "no";

    private static String defaultYesMaterial = "LIME_WOOL";
    private static String defaultNoMaterial = "RED_WOOL";
    private static String defaultYesName = "&aConfirm";
    private static String defaultNoName = "&cCancel";
    private static List<String> defaultYesLore = null;
    private static List<String> defaultNoLore = null;

    /**
     * Overrides the look every {@code yes}/{@code no} falls back to, so a game
     * can brand its dialogs once instead of per file. Call from {@code onEnable}.
     * Null leaves a default unchanged.
     */
    public static void setDefaults(String yesMaterial, String yesName, List<String> yesLore,
                                   String noMaterial, String noName, List<String> noLore) {
        if (yesMaterial != null) defaultYesMaterial = yesMaterial;
        if (yesName != null) defaultYesName = yesName;
        if (yesLore != null) defaultYesLore = yesLore;
        if (noMaterial != null) defaultNoMaterial = noMaterial;
        if (noName != null) defaultNoName = noName;
        if (noLore != null) defaultNoLore = noLore;
    }

    /** @param titleTokens {@code key, value, ...} pairs for the json {@code title} */
    public static ConfirmJsonGUI of(String menuKey, String... titleTokens) {
        return new ConfirmJsonGUI(menuKey, titleTokens);
    }

    protected ConfirmJsonGUI(String menuKey, String... titleTokens) {
        super(menuKey, titleTokens);
        applyButtonDefaults();
    }

    private void applyButtonDefaults() {
        if (data == null) return;
        MenuItemData yes = data.getItem(ID_YES);
        if (yes != null) {
            yes.applyDefaults(defaultYesMaterial, defaultYesName, defaultYesLore);
        }
        MenuItemData no = data.getItem(ID_NO);
        if (no != null) {
            no.applyDefaults(defaultNoMaterial, defaultNoName, defaultNoLore);
        }
    }

    @Override
    protected Player createContext(Player player) {
        return player;
    }

    @Override
    protected Player viewerOf(Player ctx) {
        return ctx;
    }

    public ConfirmJsonGUI yes(Consumer<Player> action) {
        return bindClosing(ID_YES, action);
    }

    public ConfirmJsonGUI no(Consumer<Player> action) {
        return bindClosing(ID_NO, action);
    }

    /** Binds any other id in the dialog, for prompts with a third choice. */
    public ConfirmJsonGUI choice(String itemId, Consumer<Player> action) {
        return bindClosing(itemId, action);
    }

    private ConfirmJsonGUI bindClosing(String itemId, Consumer<Player> action) {
        on(itemId, player -> {
            player.closeInventory();
            action.accept(player);
        });
        return this;
    }

    // Covariant overrides so the fluent chain keeps working after a token call.

    @Override
    public ConfirmJsonGUI token(String key, String value) {
        super.token(key, value);
        return this;
    }

    @Override
    public ConfirmJsonGUI token(String key, java.util.function.Function<Player, String> supplier) {
        super.token(key, supplier);
        return this;
    }
}
