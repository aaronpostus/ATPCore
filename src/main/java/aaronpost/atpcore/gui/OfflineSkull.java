package aaronpost.atpcore.gui;

import aaronpost.atpcore.ATPCore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

public class OfflineSkull {
    public static ItemStack getSkullByUUID(UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(player);

        skull.setItemMeta(meta);
        return skull;
    }

    public static ItemStack getSkullByPlayerName(String playerName) {
        // Create a new ItemStack of the player head type
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        // Get the created item's ItemMeta and cast it to SkullMeta
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

        // Set the skull's owner by getting an offline player
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        skullMeta.setOwningPlayer(player);

        // Apply the modified meta to the initial created item
        skull.setItemMeta(skullMeta);
        return skull;
    }
    // Method by Stef: SpigotMC Forums (Somewhat modified by me)
    public static ItemStack getSkull(String url) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null || url == null || url.isEmpty()) return skull;

        PlayerProfile profile = Bukkit.getServer().createPlayerProfile(UUID.randomUUID(),"skull");

        try {
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url)); // set the texture URL
            profile.setTextures(textures);
            meta.setOwnerProfile(profile); // apply to skull
        } catch (MalformedURLException e) {
            ATPCore.plugin.getLogger().log(Level.WARNING, "Invalid skin URL for skull: " + url, e);
        }

        skull.setItemMeta(meta);
        return skull;
    }

    public static ItemStack getSkullFromBase64(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null || base64 == null || base64.isEmpty()) return skull;

        String skinUrl = skinUrlFromBase64(base64);
        if (skinUrl == null) return skull;

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(skinUrl));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException e) {
            ATPCore.plugin.getLogger().log(Level.WARNING, "Invalid skin URL from base64 encoding", e);
        }

        skull.setItemMeta(meta);
        return skull;
    }

    public static String skinUrlFromBase64(String base64) {
        try {
            String json = new String(
                    Base64.getDecoder().decode(base64),
                    StandardCharsets.UTF_8
            );

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.getAsJsonObject("textures")
                    .getAsJsonObject("SKIN")
                    .get("url")
                    .getAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
