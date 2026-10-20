package com.bdmajora.impetus.engine.impl.gui.framework;

// Keys the GUI reacts to, by the key code vanilla screens pass on (lwjglx keeps the LWJGL2 numbering) and, where the key types one, by its character
public final class Keys {
    public static final int TAB = 15;
    public static final int RETURN = 28;
    public static final int SPACE = 57;
    public static final int NUMPAD_ENTER = 156;
    public static final int LEFT = 203;
    public static final int RIGHT = 205;

    private Keys() {
    }

    // Tab, which moves keyboard focus; Shift picks the direction, not the key
    public static boolean isTab(char typedChar, int keyCode) {
        return typedChar == '\t' || keyCode == TAB;
    }

    // Enter, keypad Enter or Space: the keys that press a focused control the way a left click does
    public static boolean isPress(char typedChar, int keyCode) {
        return typedChar == '\r' || typedChar == '\n' || typedChar == ' '
                || keyCode == RETURN || keyCode == NUMPAD_ENTER || keyCode == SPACE;
    }
}
