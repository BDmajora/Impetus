package com.bdmajora.impetus.engine.impl.util;

// Turns a failure into one line of text without throwing.
public final class FailureText {
    // Largest length of a line.
    private static final int MAX_LENGTH = 256;

    // Prevents instantiation
    private FailureText() {
    }

    // Returns the first line of the class and message of a failure.
    public static String of(Throwable failure) {
        return firstLine(textOf(failure));
    }

    // Rethrows a virtual machine error and restores the interrupt flag after an interruption.
    public static void absorb(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    // Returns the text up to the first line break limited in length and rejects null.
    public static String firstLine(String text) {
        java.util.Objects.requireNonNull(text, "text");
        int end = Math.min(text.length(), MAX_LENGTH);
        for (int i = 0; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return text.substring(0, i);
            }
        }
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    // Returns the text of a failure or its class name when printing fails.
    private static String textOf(Throwable failure) {
        try {
            return String.valueOf(failure);
        } catch (Throwable unprintable) {
            absorb(unprintable);
            return failure.getClass().getName();
        }
    }
}
