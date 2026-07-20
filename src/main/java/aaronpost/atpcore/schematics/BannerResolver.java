package aaronpost.atpcore.schematics;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * Hook for game-specific banner handling during schematic pastes.
 * Craft of Clans installs an implementation that swaps banners to the
 * owner's clan color and applies clan banner patterns. When no resolver
 * is installed, banners paste unchanged.
 */
public interface BannerResolver {
    /**
     * Resolve the material a banner should be pasted as for the given owner.
     * @param original the banner material stored in the schematic (wall or standing)
     * @param ownerUuid the player the paste is for (may be null for ownerless pastes)
     * @return the material to place, or null to remove the banner entirely
     */
    Material resolveBanner(Material original, UUID ownerUuid);

    /**
     * Called after a banner block has been placed, so implementations can
     * apply extra state (e.g. clan banner patterns).
     */
    default void decorateBanner(Block banner, UUID ownerUuid) {}
}
