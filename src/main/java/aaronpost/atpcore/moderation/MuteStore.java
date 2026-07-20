package aaronpost.atpcore.moderation;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence backend for {@link MuteManager}. Game plugins provide an
 * implementation (e.g. Craft of Clans backs it with its SQL database).
 * Without a store installed, mutes are memory-only and lost on restart.
 */
public interface MuteStore {
    /** Load every persisted mute row, keyed by player UUID. May include expired rows. */
    Map<UUID, MuteManager.MuteInfo> loadAll();

    /** Insert or update the mute row for a player. */
    void upsert(UUID uuid, MuteManager.MuteInfo info);

    /** Delete the mute row for a player (no-op if absent). */
    void delete(UUID uuid);
}
