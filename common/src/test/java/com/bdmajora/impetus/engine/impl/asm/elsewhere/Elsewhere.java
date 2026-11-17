package com.bdmajora.impetus.engine.impl.asm.elsewhere;

// Proxy fixtures in another package.
public final class Elsewhere {
    private Elsewhere() {
    }

    public interface Visible {
        int visible();
    }

    // Package private interface.
    interface Hidden {
        int hidden();

        default int fallback() {
            return 6;
        }
    }

    // Public class with hidden methods.
    public interface Exposed extends Hidden {
    }

    public static class Both implements Visible, Hidden {
        @Override
        public int visible() {
            return 1;
        }

        @Override
        public int hidden() {
            return 2;
        }
    }

    // Keeps the hidden default.
    public static class Through implements Exposed {
        @Override
        public int hidden() {
            return 4;
        }
    }

    static class Secret implements Visible {
        @Override
        public int visible() {
            return 3;
        }
    }

    public static Class<?> secret() {
        return Secret.class;
    }

    public static Class<?> hidden() {
        return Hidden.class;
    }

    // Calls through the public interface.
    public static int callHidden(Exposed exposed) {
        return exposed.hidden();
    }

    public static int callFallback(Exposed exposed) {
        return exposed.fallback();
    }
}
