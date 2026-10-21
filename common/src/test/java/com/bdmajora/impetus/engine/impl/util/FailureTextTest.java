package com.bdmajora.impetus.engine.impl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailureTextTest {
    private static final String PREFIX = "java.lang.IllegalStateException: ";

    // A failure whose own text throws what it is given
    private static final class Unprintable extends RuntimeException {
        private final Throwable thrown;

        Unprintable(Throwable thrown) {
            this.thrown = thrown;
        }

        @Override
        public String toString() {
            throw FailureTextTest.<RuntimeException>sneak(this.thrown);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable thrown) throws E {
        throw (E) thrown;
    }

    @Test
    void aFailureIsShownByTheFirstLineOfItsClassAndMessage() {
        String[][] cases = {{"first\nsecond", "first"}, {"first\r\nsecond", "first"}, {"first\rsecond", "first"}, {"\nsecond", ""}, {"", ""}, {"one line", "one line"}};
        for (String[] each : cases) {
            assertEquals(PREFIX + each[1], FailureText.of(new IllegalStateException(each[0])), each[0]);
        }
        assertEquals("java.lang.IllegalStateException", FailureText.of(new IllegalStateException()));
        assertEquals("null", FailureText.of(null));
    }

    @Test
    void theTextIsCutToTheLimitWithoutSplittingASurrogatePair() {
        assertEquals((PREFIX + "x".repeat(256)).substring(0, 256), FailureText.of(new IllegalStateException("x".repeat(100_000))));
        // A line break past the limit is never reached
        assertEquals(256, FailureText.of(new IllegalStateException("y".repeat(300) + "\nz")).length());
        // Exactly the limit is kept whole
        String exact = "w".repeat(256 - PREFIX.length());
        assertEquals(PREFIX + exact, FailureText.of(new IllegalStateException(exact)));
        // A pair across the limit is left out whole, and one ending at it stays
        assertEquals("x".repeat(255), FailureText.firstLine("x".repeat(255) + "😀tail"));
        assertEquals("x".repeat(254) + "😀", FailureText.firstLine("x".repeat(254) + "😀tail"));
        // A lone high surrogate at the very end is kept, as nothing past it is cut
        assertEquals("x".repeat(255) + "\uD83D", FailureText.firstLine("x".repeat(255) + "\uD83D"));
        assertEquals("", FailureText.firstLine(""));
    }

    @Test
    void aFailureWhoseOwnTextThrowsIsShownByItsClass() {
        assertEquals(Unprintable.class.getName(), FailureText.of(new Unprintable(new IllegalStateException("text"))));
        // An interruption keeps its flag
        try {
            assertEquals(Unprintable.class.getName(), FailureText.of(new Unprintable(new InterruptedException("text"))));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        // A virtual machine error is not held
        StackOverflowError overflow = new StackOverflowError();
        assertSame(overflow, assertThrows(StackOverflowError.class, () -> FailureText.of(new Unprintable(overflow))));
    }

    @Test
    void absorbRethrowsAVirtualMachineErrorAndRestoresAnInterruption() {
        OutOfMemoryError memory = new OutOfMemoryError();
        assertSame(memory, assertThrows(OutOfMemoryError.class, () -> FailureText.absorb(memory)));
        try {
            FailureText.absorb(new InterruptedException("stop"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        // Anything else is left for the caller to fall back from
        FailureText.absorb(new IllegalStateException("plain"));
        FailureText.absorb(new AssertionError("plain"));
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
