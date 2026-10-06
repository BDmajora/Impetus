package com.bdmajora.testing;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

// LWJGL3 capabilities for tests: GLCapabilities is final with a constructor that resolves function pointers, so one is allocated bare, every boolean flag raised, and single flags lowered reflectively (its fields are final, which reflection may still write on Java 25)
public final class TestCaps {
    private TestCaps() {}

    // Every extension and core version reported present
    public static GLCapabilities all() {
        GLCapabilities caps = Mc.uninitialized(GLCapabilities.class);
        for (Field field : GLCapabilities.class.getFields()) {
            if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
                write(caps, field, true);
            }
        }
        return caps;
    }

    public static GLCapabilities with(GLCapabilities caps, String flag, boolean value) {
        try {
            write(caps, GLCapabilities.class.getField(flag), value);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        return caps;
    }

    // GL.getCapabilities() answering caps until the returned mock is closed
    public static MockedStatic<GL> install(GLCapabilities caps) {
        MockedStatic<GL> gl = Mockito.mockStatic(GL.class);
        gl.when(GL::getCapabilities).thenReturn(caps);
        return gl;
    }

    private static void write(GLCapabilities caps, Field field, boolean value) {
        try {
            field.setAccessible(true);
            field.setBoolean(caps, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
