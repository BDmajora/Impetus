package com.bdmajora.impetus.engine.impl.compat.platform;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.StringBuilderFormattable;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;

// Logs messages and shows them in blocking Swing dialogs
public final class MessageBoxUtil {
    // Logger that records every message
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    // System property that hides every dialog when it is true
    private static final String HIDE_PROPERTY = "impetus.hideMessageBoxes";

    // Prevents instantiation
    private MessageBoxUtil() {
    }

    // Logs the message as a warning and shows it in a warning dialog
    public static void showWarning(String title, String message) {
        show(title, message, JOptionPane.WARNING_MESSAGE);
    }

    // Logs the message as an error and shows it in an error dialog
    public static void showError(String title, String message) {
        show(title, message, JOptionPane.ERROR_MESSAGE);
    }

    // Logs the message on one line and shows a dialog of the type unless dialogs are hidden or the display is headless
    private static void show(String title, String message, int type) {
        LOGGER.log(type == JOptionPane.ERROR_MESSAGE ? Level.ERROR : Level.WARN, "{}: {}", title, new SingleLine(String.valueOf(message)));

        if (Boolean.getBoolean(HIDE_PROPERTY)) {
            return;
        }

        try {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, message, title, type);
            }
        } catch (Throwable ignored) {
            // Ignores every failure of the display probe and the dialog
        }
    }

    // Log parameter that renders text with every line break replaced by one space
    private static final class SingleLine implements StringBuilderFormattable {
        // Text to render
        private final String text;

        // Creates a parameter for the text
        SingleLine(String text) {
            this.text = text;
        }

        // Returns the index of the first carriage return or line feed at or after the start or the length when there is none
        private static int nextBreak(String text, int start) {
            int length = text.length();
            for (int i = start; i < length; i++) {
                char c = text.charAt(i);
                if (c <= '\r' && (c == '\n' || c == '\r')) {
                    return i;
                }
            }
            return length;
        }

        // Appends the text with every carriage return line feed pair and every other line break replaced by one space
        private static void append(StringBuilder buffer, String text, int firstBreak) {
            int length = text.length();
            int run = 0;
            for (int at = firstBreak; at < length; at = nextBreak(text, run)) {
                buffer.append(text, run, at).append(' ');
                run = text.charAt(at) == '\r' && at + 1 < length && text.charAt(at + 1) == '\n' ? at + 2 : at + 1;
            }
            buffer.append(text, run, length);
        }

        // Appends the text as one line
        @Override
        public void formatTo(StringBuilder buffer) {
            String text = this.text;
            buffer.ensureCapacity(buffer.length() + text.length());
            append(buffer, text, nextBreak(text, 0));
        }

        // Returns the text as one line or the text itself when it has no line break
        @Override
        public String toString() {
            String text = this.text;
            int firstBreak = nextBreak(text, 0);
            if (firstBreak == text.length()) {
                return text;
            }
            StringBuilder line = new StringBuilder(text.length());
            append(line, text, firstBreak);
            return line.toString();
        }
    }
}
