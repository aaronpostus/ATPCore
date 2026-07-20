package aaronpost.atpcore.moderation;

import aaronpost.atpcore.ATPCore;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Server-local profanity / inappropriate content filter.
 * <p>
 * The actual word lists live in {@code content_filter.json} inside the folder
 * passed to {@link #init(File)} (the game plugin's data folder) and are
 * <b>not</b> committed to any repository. This keeps explicit terms out of
 * source control while still letting the server operator tune the filter.
 * <p>
 * Two separate lists are supported:
 * <ul>
 *   <li><b>clanNameBlocklist</b> — applied to persistent player-visible names
 *       (clan names/abbreviations, level names, ...). Should contain both
 *       casual swears <em>and</em> explicit / sexual / slur terms.</li>
 *   <li><b>chatBlocklist</b> — applied to in-game chat. Should generally contain
 *       only explicit / sexual / slur terms; casual swearing in chat is allowed
 *       by design.</li>
 * </ul>
 * <p>
 * Inputs are normalized before matching to defeat common bypass attempts:
 * <ol>
 *   <li>lower-cased</li>
 *   <li>leetspeak digits/symbols mapped to letters
 *       (e.g. {@code 4→a, 1→i, 0→o, 3→e, 5→s, 7→t, @→a, $→s, !→i})</li>
 *   <li>all non-letters stripped (so {@code f.u.c.k}, {@code f-u-c-k},
 *       {@code f u c k} all collapse to {@code fuck})</li>
 *   <li>runs of the same letter collapsed to a single letter
 *       (so {@code fuuuuck} → {@code fuk}; blocked words are also normalized
 *       the same way before matching, so this works for both sides)</li>
 * </ol>
 * After normalization a simple substring match is performed.
 */
public final class ContentFilter {

    private static final String FILE_NAME = "content_filter.json";

    private static volatile ContentFilter instance;

    /** Normalized blocked tokens (already passed through {@link #normalize(String)}). */
    private final List<String> normalizedClanNameBlocklist;
    private final List<String> normalizedChatBlocklist;

    private ContentFilter(List<String> clanNameBlocklist, List<String> chatBlocklist) {
        this.normalizedClanNameBlocklist = normalizeAll(clanNameBlocklist);
        this.normalizedChatBlocklist = normalizeAll(chatBlocklist);
    }

    public static ContentFilter get() {
        ContentFilter local = instance;
        if (local == null) {
            // Defensive fallback — should not happen if init() ran on enable.
            // Use the same lock as init() to avoid racing assignments.
            synchronized (ContentFilter.class) {
                local = instance;
                if (local == null) {
                    local = new ContentFilter(Collections.emptyList(), Collections.emptyList());
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Loads the filter from disk, creating a placeholder file if missing.
     * Safe to call multiple times — each call replaces the cached instance.
     *
     * @param dataFolder the consuming plugin's data folder
     */
    public static synchronized void init(File dataFolder) {
        File file = new File(dataFolder, FILE_NAME);

        if (!file.exists()) {
            try {
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                writePlaceholder(file);
                ATPCore.log("Created placeholder " + FILE_NAME + " in plugin folder. "
                        + "Populate it with blocked words to enable the content filter.");
            } catch (IOException e) {
                ATPCore.plugin.getLogger().log(Level.WARNING,
                        "Could not create placeholder " + FILE_NAME, e);
            }
            instance = new ContentFilter(Collections.emptyList(), Collections.emptyList());
            return;
        }

        FilterFile parsed;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader)) {
            Gson gson = new Gson();
            parsed = gson.fromJson(br, FilterFile.class);
        } catch (IOException | JsonSyntaxException e) {
            ATPCore.plugin.getLogger().log(Level.WARNING,
                    "Could not read " + FILE_NAME + " — content filter disabled until fixed", e);
            instance = new ContentFilter(Collections.emptyList(), Collections.emptyList());
            return;
        }

        List<String> clanList = (parsed != null && parsed.clanNameBlocklist != null)
                ? parsed.clanNameBlocklist : Collections.emptyList();
        List<String> chatList = (parsed != null && parsed.chatBlocklist != null)
                ? parsed.chatBlocklist : Collections.emptyList();

        instance = new ContentFilter(clanList, chatList);
        ATPCore.log("Content filter loaded (" + clanList.size() + " clan-name terms, "
                + chatList.size() + " chat terms).");
    }

    private static void writePlaceholder(File file) throws IOException {
        // Write a JSON-with-comments-style placeholder. We use a top-level "_comment"
        // key (and an empty array for the lists) so the file stays valid JSON for Gson.
        String body = "{\n"
                + "  \"_comment\": \"Server-local content filter. NOT committed to source control. "
                + "Populate the arrays below with words to block. Matching is case-insensitive, "
                + "ignores non-letters (spaces, dots, hyphens), normalizes common leetspeak "
                + "(4→a, 1→l, 0→o, 3→e, 5→s, 7→t, @→a, $→s, !→i), and collapses repeated letters. "
                + "Use 'clanNameBlocklist' for terms blocked from persistent names (include casual "
                + "swears and slurs). Use 'chatBlocklist' for terms blocked from chat (typically "
                + "only explicit/sexual/slur terms — casual swearing is allowed in chat).\",\n"
                + "  \"clanNameBlocklist\": [],\n"
                + "  \"chatBlocklist\": []\n"
                + "}\n";
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(body);
        }
    }

    // ── public API ───────────────────────────────────────────────────────

    /** True if the input is acceptable as a clan name / abbreviation. */
    public boolean isClanNameAllowed(String input) {
        return !containsBlocked(input, normalizedClanNameBlocklist);
    }

    /** True if the input is acceptable as a chat message. */
    public boolean isChatAllowed(String input) {
        return !containsBlocked(input, normalizedChatBlocklist);
    }

    // ── matching ─────────────────────────────────────────────────────────

    private static boolean containsBlocked(String input, List<String> normalizedBlocklist) {
        if (input == null || input.isEmpty() || normalizedBlocklist.isEmpty()) {
            return false;
        }
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String word : normalizedBlocklist) {
            if (!word.isEmpty() && normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalizeAll(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null) continue;
            String n = normalize(s);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Normalize a string for filter matching. Visible for testing/debugging.
     * <ul>
     *   <li>Lowercases the input.</li>
     *   <li>Maps common leetspeak chars to letters.</li>
     *   <li>Strips every non-letter character.</li>
     *   <li>Collapses runs of the same letter to one.</li>
     * </ul>
     */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        // NFKD decomposes accented / fullwidth / circled characters into base
        // letter + combining marks. Stripping the combining marks turns
        // "fück", "ｆｕｃｋ", "Ⓕⓤⓒⓚ" all into plain "fuck" before further
        // processing, which closes a common evasion path.
        String decomposed = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKD);
        String lower = decomposed.toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        char last = 0;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            // Drop Unicode combining marks left over from NFKD decomposition.
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            char mapped = mapLeet(c);
            if (mapped < 'a' || mapped > 'z') {
                // Strip everything that isn't a letter after leet mapping.
                continue;
            }
            if (mapped != last) {
                sb.append(mapped);
                last = mapped;
            }
            // else: skip — collapse repeated letters
        }
        return sb.toString();
    }

    private static char mapLeet(char c) {
        switch (c) {
            case '0': return 'o';
            case '1': return 'l'; // 1 visually subs for both i and l; l is more common (e.g. "he11o")
            case '3': return 'e';
            case '4': return 'a';
            case '5': return 's';
            case '7': return 't';
            case '8': return 'b';
            case '9': return 'g';
            case '@': return 'a';
            case '$': return 's';
            case '!': return 'i';
            case '|': return 'i';
            default:  return c;
        }
    }

    // ── JSON DTO ─────────────────────────────────────────────────────────

    @SuppressWarnings("unused") // populated by Gson via reflection
    private static final class FilterFile {
        String _comment;
        List<String> clanNameBlocklist;
        List<String> chatBlocklist;
    }
}
