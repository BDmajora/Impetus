package com.bdmajora.impetus.engine.impl.gui.framework;

// Key codes and key tests of the GUI.
public final class Keys {
    // Key code of Tab.
    public static final int TAB = 15;
    // Key code of Return.
    public static final int RETURN = 28;
    // Key code of Space.
    public static final int SPACE = 57;
    // Key code of keypad Enter.
    public static final int NUMPAD_ENTER = 156;
    // Key code of the left arrow.
    public static final int LEFT = 203;
    // Key code of the right arrow.
    public static final int RIGHT = 205;

    // Prevents instantiation
    private Keys() {
    }

    // Returns whether the key is Tab.
    public static boolean isTab(char typedChar, int keyCode) {
        return typedChar == '\t' || keyCode == TAB;
    }

    // Returns whether the key is Enter or keypad Enter or Space.
    public static boolean isPress(char typedChar, int keyCode) {
        return typedChar == '\r' || typedChar == '\n' || typedChar == ' '
                || keyCode == RETURN || keyCode == NUMPAD_ENTER || keyCode == SPACE;
    }
}
