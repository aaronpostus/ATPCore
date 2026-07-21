package aaronpost.atpcore;

import aaronpost.atpcore.gui.GUIListener;
import aaronpost.atpcore.gui.GUIManager;
import aaronpost.atpcore.schematics.Controller;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared core plugin. Owns the schematic system, the data-registry framework,
 * and reusable GUI base classes. Game plugins (Craft of Clans, parkour, ...)
 * declare {@code depend: [ATPCore]} and build on top of these systems.
 */
public final class ATPCore extends JavaPlugin {
    public static ATPCore plugin;

    /**
     * Server-wide inventory-GUI router. One instance backs every plugin's menus,
     * so only one {@link GUIListener} is ever registered.
     */
    public static GUIManager guiManager;

    private static volatile boolean shuttingDown = false;

    public static boolean isShuttingDown() { return shuttingDown; }

    public static void log(String info) {
        plugin.getLogger().info(info);
    }

    public static void logWarning(String info) {
        plugin.getLogger().warning(info);
    }

    public static void logError(String info) {
        plugin.getLogger().severe(info);
    }

    @Override
    public void onEnable() {
        plugin = this;
        shuttingDown = false;
        // Inventory-GUI routing, shared by every plugin built on ATPCore
        guiManager = new GUIManager();
        getServer().getPluginManager().registerEvents(new GUIListener(guiManager), this);
        // Schematic + coordinate wand handling (wands themselves are given out by game plugins)
        getServer().getPluginManager().registerEvents(new Controller(), this);
        log("ATPCore enabled.");
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
    }
}
