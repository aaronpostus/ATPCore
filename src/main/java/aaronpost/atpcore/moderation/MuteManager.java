package aaronpost.atpcore.moderation;

import aaronpost.atpcore.ATPCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton tracking active player mutes.
 *
 * <p>Mutes are cached in memory for fast lookups in the chat listener and
 * persisted through the installed {@link MuteStore} (set one via
 * {@link #setStore} before calling {@link #loadAll()}). A mute with
 * {@code expiresAt == Long.MAX_VALUE} is treated as permanent.</p>
 */
public final class MuteManager {

    private static final MuteManager INSTANCE = new MuteManager();
    public static MuteManager getInstance() { return INSTANCE; }

    public static final long PERMANENT = Long.MAX_VALUE;

    /**
     * Snapshot of a single mute row, suitable for display.
     * {@link #expiresAt} equals {@link #PERMANENT} for never-expires.
     */
    public static final class MuteInfo {
        public final long expiresAt;
        public final String reason;
        public final String mutedBy;
        public MuteInfo(long expiresAt, String reason, String mutedBy) {
            this.expiresAt = expiresAt;
            this.reason = reason;
            this.mutedBy = mutedBy;
        }
        public boolean isPermanent() { return expiresAt == PERMANENT; }
    }

    /** uuid -> active mute snapshot. */
    private final ConcurrentHashMap<UUID, MuteInfo> active = new ConcurrentHashMap<>();

    private MuteStore store = null;

    private MuteManager() {}

    /** Install the persistence backend. Call before {@link #loadAll()}. */
    public void setStore(MuteStore store) {
        this.store = store;
    }

    /** Loads all active mutes from the store into memory. Call on plugin enable. */
    public void loadAll() {
        active.clear();
        if (store == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, MuteInfo> entry : store.loadAll().entrySet()) {
            MuteInfo info = entry.getValue();
            if (info.expiresAt != PERMANENT && info.expiresAt <= now) {
                // Lazily clean up expired rows
                store.delete(entry.getKey());
                continue;
            }
            active.put(entry.getKey(), info);
        }
        ATPCore.log("Loaded " + active.size() + " active mute(s).");
    }

    /**
     * Mute a player. If {@code durationMs <= 0} the mute is permanent.
     */
    public void mute(UUID uuid, long durationMs, String reason, String mutedBy) {
        if (uuid == null) return;
        long expiresAt = (durationMs <= 0) ? PERMANENT
                : System.currentTimeMillis() + durationMs;
        MuteInfo info = new MuteInfo(expiresAt, reason, mutedBy);
        active.put(uuid, info);
        if (store != null) {
            store.upsert(uuid, info);
        }
    }

    /** Remove the mute for the given player (no-op if not muted). */
    public void unmute(UUID uuid) {
        if (uuid == null) return;
        active.remove(uuid);
        if (store != null) {
            store.delete(uuid);
        }
    }

    /** True if the player is currently muted. Lazily clears expired entries. */
    public boolean isMuted(UUID uuid) {
        if (uuid == null) return false;
        MuteInfo info = active.get(uuid);
        if (info == null) return false;
        if (info.expiresAt != PERMANENT && info.expiresAt <= System.currentTimeMillis()) {
            unmute(uuid);
            return false;
        }
        return true;
    }

    /** Remaining mute time in ms, or {@link #PERMANENT} for permanent, or 0 if not muted. */
    public long getRemainingMs(UUID uuid) {
        if (uuid == null) return 0;
        MuteInfo info = active.get(uuid);
        if (info == null) return 0;
        if (info.expiresAt == PERMANENT) return PERMANENT;
        long remaining = info.expiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            unmute(uuid);
            return 0;
        }
        return remaining;
    }

    /**
     * Returns a snapshot of the active mute (expiresAt / reason / mutedBy),
     * or {@code null} if the player is not currently muted. Lazily clears
     * expired entries.
     */
    public MuteInfo getInfo(UUID uuid) {
        if (uuid == null) return null;
        MuteInfo info = active.get(uuid);
        if (info == null) return null;
        if (info.expiresAt != PERMANENT && info.expiresAt <= System.currentTimeMillis()) {
            unmute(uuid);
            return null;
        }
        return info;
    }

    /** Format a remaining-time value for display (e.g. "2h 13m" or "permanent"). */
    public static String formatRemaining(long remainingMs) {
        if (remainingMs == PERMANENT) return "permanent";
        if (remainingMs <= 0) return "0s";
        long secs = remainingMs / 1000L;
        long days = secs / 86400L; secs %= 86400L;
        long hours = secs / 3600L; secs %= 3600L;
        long minutes = secs / 60L; secs %= 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.length() == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }
}
