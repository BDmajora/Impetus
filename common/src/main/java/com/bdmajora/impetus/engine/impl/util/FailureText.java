package com.bdmajora.impetus.engine.impl.util;

// A failure from someone else's code as a toast line, reading its text without letting that text's own code throw past
public final class FailureText {
    // Longest text given to a toast line, which the toast cuts to its width anyway
    private static final int MAX_LENGTH = 256;

    private FailureText() {
    }

    // The first line of the failure's class and message, or its class alone when its own text throws
    public static String of(Throwable failure) {
        return firstLine(textOf(failure));
    }

    // Rethrows a virtual machine error and restores the interrupt flag after an interruption, so the caller can fall back
    public static void absorb(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    // The text up to its first line break, cut to the toast limit without splitting a surrogate pair
    public static String firstLine(String text) {
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

    // The failure's class and message, or its class alone when its own text throws
    private static String textOf(Throwable failure) {
        try {
            return String.valueOf(failure);
        } catch (Throwable unprintable) {
            absorb(unprintable);
            return failure.getClass().getName();
        }
    }
}
