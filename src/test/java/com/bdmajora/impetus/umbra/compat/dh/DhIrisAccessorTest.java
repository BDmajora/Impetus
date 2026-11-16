package com.bdmajora.impetus.umbra.compat.dh;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

// Tests the proxy handler that answers the Distant Horizons shader accessor.
class DhIrisAccessorTest {
    // Interface standing in for the accessor.
    interface Accessor {
        // Returns the mod name.
        String getModName();

        // Returns the depth texture of a framebuffer.
        int getFramebufferDepthTextureId(Object framebuffer);

        // Returns a depth texture without arguments.
        int getFramebufferDepthTextureId();

        // Returns a long of an unknown call.
        long unknownLong();

        // Returns a boolean of an unknown call.
        boolean unknownBoolean();

        // Returns a double of an unknown call.
        double unknownDouble();

        // Returns an object of an unknown call.
        Object unknownObject();

        // Runs an unknown call.
        void unknownVoid();
    }

    // Answers known calls and gives zero values to unknown ones.
    @Test
    void answersCallsAndZeroes() {
        Accessor accessor = (Accessor) Proxy.newProxyInstance(Accessor.class.getClassLoader(),
                new Class<?>[]{Accessor.class}, new DhIrisAccessor());
        assertEquals("Impetus", accessor.getModName());
        assertEquals(-1, accessor.getFramebufferDepthTextureId(new Object()));
        assertEquals(-1, accessor.getFramebufferDepthTextureId());
        assertEquals(0L, accessor.unknownLong());
        assertFalse(accessor.unknownBoolean());
        assertEquals(0.0, accessor.unknownDouble());
        assertNull(accessor.unknownObject());
        accessor.unknownVoid();
        assertEquals(accessor, accessor);
        assertNotEquals(accessor, new Object());
        assertEquals(System.identityHashCode(accessor), accessor.hashCode());
        assertEquals("Impetus IIrisAccessor", accessor.toString());
    }

    // Returns a boxed zero for every primitive type.
    @Test
    void zeroValues() {
        assertEquals((byte) 0, DhIrisAccessor.zeroOf(byte.class));
        assertEquals((short) 0, DhIrisAccessor.zeroOf(short.class));
        assertEquals((char) 0, DhIrisAccessor.zeroOf(char.class));
        assertEquals(0, DhIrisAccessor.zeroOf(int.class));
        assertEquals(0.0f, DhIrisAccessor.zeroOf(float.class));
        assertNull(DhIrisAccessor.zeroOf(void.class));
        assertNull(DhIrisAccessor.zeroOf(String.class));
    }
}
