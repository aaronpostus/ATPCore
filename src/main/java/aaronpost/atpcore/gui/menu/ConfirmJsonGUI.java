package aaronpost.atpcore.gui.menu;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * A json-backed "are you sure?" dialog: wording and layout in a menu json file,
 * actions in Java. The reserved ids {@code yes}/{@code no} carry defaults, so a
 * file only needs their slots. Both bindings close the inventory before running.
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

    /** Rebrands the {@code yes}/{@code no} defaults; a null arg leaves that default unchanged. */
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
