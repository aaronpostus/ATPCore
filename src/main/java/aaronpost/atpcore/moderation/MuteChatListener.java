package aaronpost.atpcore.moderation;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Cancels chat events from muted players and informs them how long they have
 * left on their mute. Registered by the game plugin; set the message prefix
 * via {@link #setMessagePrefix} to match the plugin's chat branding.
 */
public class MuteChatListener implements Listener {

    private static String messagePrefix = "";

    public static void setMessagePrefix(String prefix) {
        messagePrefix = prefix != null ? prefix : "";
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MuteManager mm = MuteManager.getInstance();
        if (!mm.isMuted(player.getUniqueId())) return;
        event.setCancelled(true);

        long remaining = mm.getRemainingMs(player.getUniqueId());
        String suffix;
        if (remaining == MuteManager.PERMANENT) {
            suffix = "permanently";
        } else {
            suffix = "for " + MuteManager.formatRemaining(remaining);
        }
        player.sendMessage(messagePrefix + ChatColor.RED + " You are muted " + suffix + ".");
    }
}
