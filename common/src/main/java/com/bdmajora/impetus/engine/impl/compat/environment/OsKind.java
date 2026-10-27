package com.bdmajora.impetus.engine.impl.compat.environment;

// Coarse operating system classification of the running JVM
public enum OsKind {
    // Microsoft Windows
    WINDOWS,
    // Linux and every BSD or Unix system
    LINUX,
    // Apple macOS
    MACOS,
    // Operating system that matches no known name
    UNKNOWN;

    // Bit that turns an upper case ASCII letter into its lower case form
    private static final int ASCII_LOWER_CASE_BIT = 0x20;
    // Classification of the running JVM
    private static final OsKind CURRENT = detect(System.getProperty("os.name", ""));

    // Returns the classification of the running JVM
    public static OsKind current() {
        return CURRENT;
    }

    // Returns the kind an os.name value names with Windows ahead of macOS and macOS ahead of the Linux family
    private static OsKind detect(String name) {
        // Treats a missing name as unknown
        if (name == null) {
            return UNKNOWN;
        }

        var kind = UNKNOWN;

        for (int i = 0, length = name.length(); i < length; i++) {
            switch (name.charAt(i) | ASCII_LOWER_CASE_BIT) {
                // Skips a win that ends a darwin
                case 'w' -> {
                    if (matches(name, i, "win") && !matches(name, i - 3, "dar")) {
                        return WINDOWS;
                    }
                }
                case 'm' -> {
                    if (kind != MACOS && matches(name, i, "mac")) {
                        kind = MACOS;
                    }
                }
                case 'd' -> {
                    if (kind != MACOS && matches(name, i, "darwin")) {
                        kind = MACOS;
                    }
                }
                case 'l' -> {
                    if (kind == UNKNOWN && matches(name, i, "linux")) {
                        kind = LINUX;
                    }
                }
                case 'b' -> {
                    if (kind == UNKNOWN && matches(name, i, "bsd")) {
                        kind = LINUX;
                    }
                }
                case 'u' -> {
                    if (kind == UNKNOWN && matches(name, i, "unix")) {
                        kind = LINUX;
                    }
                }
            }
        }

        return kind;
    }

    // Returns true when the characters of the name from the index on equal a pattern of lower case ASCII letters with ASCII case ignored
    private static boolean matches(String name, int index, String pattern) {
        int length = pattern.length();

        if (index < 0 || index > name.length() - length) {
            return false;
        }

        for (int j = 0; j < length; j++) {
            if ((name.charAt(index + j) | ASCII_LOWER_CASE_BIT) != pattern.charAt(j)) {
                return false;
            }
        }

        return true;
    }
}
