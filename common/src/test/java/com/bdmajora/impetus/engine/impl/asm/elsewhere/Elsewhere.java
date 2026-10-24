package com.bdmajora.impetus.engine.impl.asm.elsewhere;

// Proxy fixtures in another package, holding types a proxy in the generator's package cannot access
public final class Elsewhere {
    private Elsewhere() {
    }

    public interface Visible {
        int visible();
    }

    // package-private, so a proxy in another package may neither implement it nor name it in a call
    interface Hidden {
        int hidden();

        default int fallback() {
            return 6;
        }
    }

    // public, but its methods all come from the package-private interface
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

    // keeps Hidden's default, so the only declaration of fallback sits in an inaccessible interface
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

    // calls through the public interface, the way code in this package would
    public static int callHidden(Exposed exposed) {
        return exposed.hidden();
    }

    public static int callFallback(Exposed exposed) {
        return exposed.fallback();
    }
}
