package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;

// Graphics vendor classified from a PCI vendor id or from the strings of a GL context
public enum GraphicsVendor {
    // NVIDIA hardware
    NVIDIA,
    // AMD hardware including cards sold under the ATI name
    AMD,
    // Intel hardware
    INTEL,
    // Any driver of the Mesa project whatever the hardware below it
    MESA,
    // Vendor that matches no known id or name
    OTHER;

    // Bit that turns an upper case ASCII letter into its lower case form
    private static final int ASCII_LOWER_CASE_BIT = 0x20;
    // Capital I with dot above whose lower case form is an i followed by a combining dot
    private static final char CAPITAL_I_WITH_DOT_ABOVE = '\u0130';

    // Returns the vendor a known PCI vendor id names or OTHER for any other id
    public static GraphicsVendor fromPciVendorId(int id) {
        return switch (id) {
            case 0x10DE -> NVIDIA;
            case 0x1002, 0x1022 -> AMD;
            case 0x8086 -> INTEL;
            default -> OTHER;
        };
    }

    // Returns MESA for a Mesa version or renderer or an X.Org vendor and otherwise classifies the vendor string
    public static GraphicsVendor fromContext(GlContextInfo context) {
        if (namesMesa(context.version()) || namesMesa(context.renderer())) {
            return MESA;
        }

        return classifyVendor(context.vendor());
    }

    // Returns true when the text holds mesa with ASCII case ignored and false for a null text
    private static boolean namesMesa(String text) {
        if (text == null) {
            return false;
        }

        for (int i = 0, length = text.length(); i < length; i++) {
            if ((text.charAt(i) | ASCII_LOWER_CASE_BIT) == 'm' && matches(text, i, "mesa")) {
                return true;
            }
        }

        return false;
    }

    // Returns MESA for an X.Org vendor and otherwise the vendor named with NVIDIA ahead of AMD and AMD ahead of Intel and OTHER for a null vendor
    private static GraphicsVendor classifyVendor(String vendor) {
        var named = OTHER;

        if (vendor == null) {
            return named;
        }

        for (int i = 0, length = vendor.length(); i < length; i++) {
            switch (vendor.charAt(i) | ASCII_LOWER_CASE_BIT) {
                case 'x' -> {
                    if (matches(vendor, i, "x.org")) {
                        return MESA;
                    }
                }
                case 'n' -> {
                    if (named != NVIDIA && matches(vendor, i, "nvidia")) {
                        named = NVIDIA;
                    }
                }
                case 'a' -> {
                    if ((named == OTHER || named == INTEL)
                            && (matchesWord(vendor, i, "ati") || matches(vendor, i, "amd") || matches(vendor, i, "advanced micro devices"))) {
                        named = AMD;
                    }
                }
                case 'i' -> {
                    if (named == OTHER && matches(vendor, i, "intel")) {
                        named = INTEL;
                    }
                }
            }
        }

        return named;
    }

    // Returns true when the pattern starts at the index and no ASCII letter sits right before or right after it
    private static boolean matchesWord(String text, int index, String pattern) {
        int end = index + pattern.length();

        return matches(text, index, pattern)
                && (index == 0 || !isAsciiLetter(text.charAt(index - 1)))
                && (end == text.length() || !isAsciiLetter(text.charAt(end)));
    }

    // Returns true when the char is an ASCII letter of either case
    private static boolean isAsciiLetter(char c) {
        int lower = c | ASCII_LOWER_CASE_BIT;
        return lower >= 'a' && lower <= 'z';
    }

    // Returns true when the pattern starts at the index with ASCII case ignored for letters and a capital I with dot above read as a final i
    private static boolean matches(String text, int index, String pattern) {
        int length = pattern.length();

        if (index > text.length() - length) {
            return false;
        }

        for (int j = 0; j < length; j++) {
            char actual = text.charAt(index + j);
            char expected = pattern.charAt(j);
            if (actual != expected
                    && (expected < 'a' || (actual | ASCII_LOWER_CASE_BIT) != expected)
                    && (actual != CAPITAL_I_WITH_DOT_ABOVE || expected != 'i' || j != length - 1)) {
                return false;
            }
        }

        return true;
    }
}
