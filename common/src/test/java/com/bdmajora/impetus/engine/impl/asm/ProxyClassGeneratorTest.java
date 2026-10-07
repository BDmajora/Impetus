package com.bdmajora.impetus.engine.impl.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProxyClassGeneratorTest {
    public interface Greeter {
        String greet(String name, int times);

        long widen(int a, long b, double c);

        void nothing();
    }

    public static class Impl implements Greeter {
        int calls;

        @Override
        public String greet(String name, int times) {
            return name.repeat(times);
        }

        @Override
        public long widen(int a, long b, double c) {
            return a + b + (long) c;
        }

        @Override
        public void nothing() {
            calls++;
        }

        public static void ignored() {}
    }

    @Test
    void generatedWrapperForwardsEveryInterfaceMethod() {
        ProxyClassGenerator<Impl, Greeter> generator = new ProxyClassGenerator<>(Impl.class, "GreeterProxy" + System.nanoTime(), Greeter.class);
        Impl impl = new Impl();
        Greeter proxy = generator.generateWrapper(impl);
        assertNotSame(impl, proxy);
        assertEquals("abab", proxy.greet("ab", 2));
        assertEquals(6L, proxy.widen(1, 2L, 3.5));
        proxy.nothing();
        assertEquals(1, impl.calls);
        assertThrows(RuntimeException.class, () -> generator.generateWrapper(null).nothing());
    }

    @Test
    void proxiesLiveBesideTheGeneratorAndNamesAreDefinedOnce() {
        String name = "OnceProxy" + System.nanoTime();
        Greeter proxy = new ProxyClassGenerator<>(Impl.class, name, Greeter.class).generateWrapper(new Impl());
        assertSame(ProxyClassGenerator.class.getClassLoader(), proxy.getClass().getClassLoader());
        assertEquals(ProxyClassGenerator.class.getPackageName(), proxy.getClass().getPackageName());
        // A second class under the same name is a linkage error, reported as the generator's own failure
        assertThrows(RuntimeException.class, () -> new ProxyClassGenerator<>(Impl.class, name, Greeter.class));
    }
}
