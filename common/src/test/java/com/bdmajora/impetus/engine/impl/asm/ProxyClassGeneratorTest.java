package com.bdmajora.impetus.engine.impl.asm;

import com.bdmajora.testing.Statics;
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
    void bothDefinersCanDefineAClass() throws Exception {
        Object modern = Statics.call(ProxyClassGenerator.class, "modernDefiner");
        Object legacy = Statics.call(ProxyClassGenerator.class, "legacyDefiner");
        assertNotNull(modern);
        assertNotNull(legacy);
        // The generator defines its class under this exact name, so the legacy definer gets fresh bytes for a different one
        String name = "LegacyProxy" + System.nanoTime();
        ProxyClassGenerator<Impl, Greeter> generator = new ProxyClassGenerator<>(Impl.class, name + "Modern", Greeter.class);
        Statics.set(generator.getClass(), "DEFINER", null);
        var nameField = ProxyClassGenerator.class.getDeclaredField("proxyClassName");
        nameField.setAccessible(true);
        nameField.set(generator, "com/bdmajora/impetus/engine/impl/asm/" + name);
        byte[] bytes = Statics.callInstance(generator, "createWrapperClassBytecode");
        var define = legacy.getClass().getDeclaredMethods()[0];
        define.setAccessible(true);
        Class<?> defined = (Class<?>) define.invoke(legacy, bytes, "com.bdmajora.impetus.engine.impl.asm." + name);
        assertEquals("com.bdmajora.impetus.engine.impl.asm." + name, defined.getName());
    }
}
