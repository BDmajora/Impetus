package com.bdmajora.impetus.api.config.theme;

import java.util.Objects;
import java.util.OptionalInt;

// Global lookup of each mod's accent colour keyed by mod id
public final class ConfigThemeRegistry {
    // Empty starting table with a single unused pair
    private static final Object[] EMPTY = new Object[2];

    // Current table of pairs with each mod id at an even index and its accent at the next index
    private static volatile Object[] table = EMPTY;

    // Lock held by register() while the table is replaced
    private static final Object WRITE_LOCK = new Object();

    // Number of registrations made so far
    private static volatile int generation;

    // Prevents instantiation
    private ConfigThemeRegistry() {
    }

    // Stores the theme's accent under its mod id and replaces any earlier registration for that mod
    public static void register(ConfigTheme theme) {
        Objects.requireNonNull(theme, "Theme must not be null");
        // Wraps the accent once for every later lookup to return
        OptionalInt accent = OptionalInt.of(theme.accentColor());
        synchronized (WRITE_LOCK) {
            // Replaces the table with a copy that includes the new theme
            table = with(table, theme.modId(), accent);
            // Increments the registration count after the table is replaced
            generation++;
        }
    }

    // Returns a number that changes whenever a theme is registered
    public static int generation() {
        return generation;
    }

    // Returns the mod's accent colour or an empty result when the mod has no registered theme
    public static OptionalInt getAccentColor(String modId) {
        // Returns empty for a null id
        if (modId == null) {
            return OptionalInt.empty();
        }
        // Takes one snapshot of the table for both lookups
        Object[] slots = table;
        // Looks up the id exactly as given
        OptionalInt accent = find(slots, modId);
        if (accent != null) {
            return accent;
        }
        // Normalises the id after a miss and returns null if it is blank
        String canonical = ConfigTheme.canonicalModIdOrNull(modId);
        // Returns empty if the id is blank or normalising left it unchanged
        if (canonical == null || canonical == modId) {
            return OptionalInt.empty();
        }
        // Looks up the normalised id
        accent = find(slots, canonical);
        return accent != null ? accent : OptionalInt.empty();
    }

    // Probes pair by pair from the id's home index and returns its accent or null at the first empty pair
    private static OptionalInt find(Object[] slots, String id) {
        int last = slots.length - 1;
        for (int i = home(id, slots.length); ; i = (i + 2) & last) {
            Object key = slots[i];
            if (key == null) {
                return null;
            }
            if (id.equals(key)) {
                return (OptionalInt) slots[i + 1];
            }
        }
    }

    // Returns a new table at most a quarter full with every existing pair plus the id
    private static Object[] with(Object[] slots, String id, OptionalInt accent) {
        // Counts the existing pairs plus one if the id is new
        int count = find(slots, id) == null ? 1 : 0;
        for (int i = 0; i < slots.length; i += 2) {
            if (slots[i] != null) {
                count++;
            }
        }
        // Allocates the smallest table that is a power of two with at least four pairs per entry
        Object[] next = new Object[Integer.highestOneBit(4 * count - 1) << 2];
        int last = next.length - 1;
        // Copies each existing pair into the first free pair from its home index
        for (int i = 0; i < slots.length; i += 2) {
            if (slots[i] != null) {
                int j = home((String) slots[i], next.length);
                while (next[j] != null) {
                    j = (j + 2) & last;
                }
                next[j] = slots[i];
                next[j + 1] = slots[i + 1];
            }
        }
        // Writes the id and accent over its existing entry or into the first free pair
        int j = home(id, next.length);
        while (next[j] != null && !id.equals(next[j])) {
            j = (j + 2) & last;
        }
        next[j] = id;
        next[j + 1] = accent;
        return next;
    }

    // Returns the index of the id's first pair by mixing the high hash bits into the low bits
    private static int home(String id, int length) {
        int hash = id.hashCode();
        return ((hash ^ (hash >>> 16)) << 1) & (length - 1);
    }
}