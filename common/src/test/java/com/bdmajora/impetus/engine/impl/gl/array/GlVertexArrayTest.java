package com.bdmajora.impetus.engine.impl.gl.array;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of every member of the vertex array alone and behind a real command list
class GlVertexArrayTest {
    // Message of a handle read after delete
    private static final String INVALID = "Handle is not valid";
    // Message of a zero name from the driver
    private static final String NO_NAME = "Failed to generate a vertex array";
    // Number of arrays in the bulk tests
    private static final int MANY = 1000;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Creates an array whose generated name is the given value
    private GlVertexArray named(int name) {
        Mockito.doReturn(name).when(gl).glGenVertexArrays();
        return new GlVertexArray();
    }

    // Creates an array from exactly one generated name and makes no other GL call
    @Test
    void constructorGeneratesExactlyOneName() {
        GlVertexArray array = new GlVertexArray();
        Mockito.verify(gl).glGenVertexArrays();
        Mockito.verifyNoMoreInteractions(gl);
        assertEquals(1, array.handle());
    }

    // Keeps every nonzero name the driver returns including the unsigned names above the int limit
    @ParameterizedTest
    @ValueSource(ints = {1, 2, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -2, -1})
    void handleReturnsTheGeneratedName(int name) {
        assertEquals(name, named(name).handle());
    }

    // Rejects a zero name from the driver and frees nothing
    @Test
    void constructorRejectsAZeroName() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> named(GlVertexArray.NULL_ARRAY_ID));
        assertEquals(NO_NAME, error.getMessage());
        Mockito.verify(gl).glGenVertexArrays();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Creates a working array after the driver once returned a zero name
    @Test
    void constructorRecoversAfterAZeroName() {
        Mockito.doReturn(0).doReturn(9).when(gl).glGenVertexArrays();
        assertThrows(IllegalStateException.class, GlVertexArray::new);
        GlVertexArray array = new GlVertexArray();
        assertEquals(9, array.handle());
        array.delete();
        Mockito.verify(gl).glDeleteVertexArrays(9);
        Mockito.verify(gl, Mockito.never()).glDeleteVertexArrays(0);
    }

    // Passes on the exact failure of name generation and frees nothing
    @Test
    void constructorPropagatesAGenerationFailure() {
        UnsupportedOperationException failure = new UnsupportedOperationException("VAO not supported");
        Mockito.doThrow(failure).when(gl).glGenVertexArrays();
        assertSame(failure, assertThrows(UnsupportedOperationException.class, GlVertexArray::new));
        Mockito.verify(gl).glGenVertexArrays();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Creates a working array after one failed generation
    @Test
    void constructorRecoversAfterAGenerationFailure() {
        Mockito.doThrow(new IllegalStateException("No context")).doReturn(4).when(gl).glGenVertexArrays();
        assertThrows(IllegalStateException.class, GlVertexArray::new);
        assertEquals(4, new GlVertexArray().handle());
    }

    // Returns the same name on repeated reads without any GL call
    @Test
    void handleRepeatsWithoutGlCalls() {
        GlVertexArray array = named(12);
        for (int i = 0; i < 3; i++) {
            assertEquals(12, array.handle());
        }
        Mockito.verify(gl).glGenVertexArrays();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Deletes the generated name once and makes no other GL call
    @Test
    void deleteDeletesTheGeneratedNameOnce() {
        GlVertexArray array = named(5);
        array.delete();
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glGenVertexArrays();
        order.verify(gl).glDeleteVertexArrays(5);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Deletes names at the int limits without changing them
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, Integer.MIN_VALUE, -1})
    void deletePassesLimitNamesUnchanged(int name) {
        named(name).delete();
        Mockito.verify(gl).glDeleteVertexArrays(name);
    }

    // Rejects a handle read after delete
    @Test
    void handleRejectsUseAfterDelete() {
        GlVertexArray array = named(3);
        array.delete();
        assertEquals(INVALID, assertThrows(IllegalStateException.class, array::handle).getMessage());
        assertEquals(INVALID, assertThrows(IllegalStateException.class, array::handle).getMessage());
    }

    // Ignores every delete after the first
    @Test
    void repeatedDeleteFreesTheNameOnce() {
        GlVertexArray array = named(6);
        for (int i = 0; i < 3; i++) {
            array.delete();
        }
        Mockito.verify(gl, Mockito.times(1)).glDeleteVertexArrays(6);
    }

    // Deletes through the alias exactly like delete and ignores every later call of either
    @Test
    @SuppressWarnings("deprecation")
    void destroyDeletesLikeDelete() {
        GlVertexArray array = named(8);
        array.destroy();
        array.destroy();
        array.delete();
        Mockito.verify(gl, Mockito.times(1)).glDeleteVertexArrays(8);
        assertThrows(IllegalStateException.class, array::handle);

        GlVertexArray other = named(10);
        other.delete();
        other.destroy();
        Mockito.verify(gl, Mockito.times(1)).glDeleteVertexArrays(10);
    }

    // Keeps the array live after a failed delete and deletes it on the retry
    @Test
    void failedDeleteKeepsTheArrayLive() {
        GlVertexArray array = named(11);
        IllegalStateException failure = new IllegalStateException("Context lost");
        Mockito.doThrow(failure).doNothing().when(gl).glDeleteVertexArrays(11);
        assertSame(failure, assertThrows(IllegalStateException.class, array::delete));
        assertEquals(11, array.handle());
        array.delete();
        assertThrows(IllegalStateException.class, array::handle);
        array.delete();
        Mockito.verify(gl, Mockito.times(2)).glDeleteVertexArrays(11);
    }

    // Deletes the name from the template hook and leaves the handle to the base class
    @Test
    void destroyInternalDeletesTheNameOnly() {
        GlVertexArray array = named(13);
        array.destroyInternal();
        Mockito.verify(gl).glDeleteVertexArrays(13);
        assertEquals(13, array.handle());
    }

    // Rejects the template hook on a deleted array before any GL call
    @Test
    void destroyInternalRejectsADeletedArray() {
        GlVertexArray array = named(14);
        array.delete();
        assertEquals(INVALID, assertThrows(IllegalStateException.class, array::destroyInternal).getMessage());
        Mockito.verify(gl, Mockito.times(1)).glDeleteVertexArrays(14);
    }

    // Keeps distinct names apart and leaves one array live after the other is deleted
    @Test
    void deleteLeavesOtherArraysLive() {
        GlVertexArray first = new GlVertexArray();
        GlVertexArray second = new GlVertexArray();
        assertNotEquals(first.handle(), second.handle());
        int kept = second.handle();
        first.delete();
        assertEquals(kept, second.handle());
        Mockito.verify(gl, Mockito.never()).glDeleteVertexArrays(kept);
    }

    // Deletes each of many arrays by its own name exactly once
    @Test
    void manyArraysDeleteTheirOwnNames() {
        List<GlVertexArray> arrays = new ArrayList<>();
        List<Integer> names = new ArrayList<>();
        Set<Integer> unique = new HashSet<>();
        for (int i = 0; i < MANY; i++) {
            GlVertexArray array = new GlVertexArray();
            arrays.add(array);
            names.add(array.handle());
            assertTrue(unique.add(array.handle()));
        }
        for (GlVertexArray array : arrays) {
            array.delete();
            array.delete();
        }
        ArgumentCaptor<Integer> deleted = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(gl, Mockito.times(MANY)).glDeleteVertexArrays(deleted.capture());
        assertEquals(names, deleted.getAllValues());
    }

    // Lets two arrays share one name the driver reused and frees it once for each
    @Test
    void reusedNameBelongsToEachArrayInTurn() {
        GlVertexArray first = named(7);
        first.delete();
        GlVertexArray second = named(7);
        assertEquals(7, second.handle());
        first.delete();
        Mockito.verify(gl, Mockito.times(1)).glDeleteVertexArrays(7);
        second.delete();
        Mockito.verify(gl, Mockito.times(2)).glDeleteVertexArrays(7);
    }

    // Binds the name through a real command list and rejects the array after the list deletes it
    @Test
    void commandListBindsAndDeletesTheName() {
        GLRenderDevice device = new GLRenderDevice();
        device.makeActive();
        try {
            CommandList list = device.createCommandList();
            GlVertexArray first = named(7);
            list.bindVertexArray(first);
            list.deleteVertexArray(first);
            Mockito.verify(gl).glBindVertexArray(7);
            Mockito.verify(gl).glDeleteVertexArrays(7);
            assertThrows(IllegalStateException.class, () -> list.bindVertexArray(first));

            GlVertexArray second = named(7);
            list.bindVertexArray(second);
            Mockito.verify(gl, Mockito.times(2)).glBindVertexArray(7);
            list.unbindVertexArray();
            Mockito.verify(gl).glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
        } finally {
            device.makeInactive();
        }
    }

    // Exposes the reserved name zero as a compile time constant
    @Test
    void nullArrayIdIsZero() throws ReflectiveOperationException {
        assertEquals(0, GlVertexArray.NULL_ARRAY_ID);
        Field field = GlVertexArray.class.getDeclaredField("NULL_ARRAY_ID");
        assertEquals(int.class, field.getType());
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers());
    }

    // Keeps the public class with one public constructor and one protected hook
    @Test
    void classKeepsItsShape() throws ReflectiveOperationException {
        Class<GlVertexArray> type = GlVertexArray.class;
        assertEquals(Modifier.PUBLIC, type.getModifiers());
        assertSame(GlObject.class, type.getSuperclass());

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Modifier.PUBLIC, constructors[0].getModifiers());
        assertEquals(0, constructors[0].getParameterCount());

        List<String> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                fields.add(field.getName());
            }
        }
        assertEquals(List.of("NULL_ARRAY_ID"), fields);

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic()) {
                methods.add(method.getName());
            }
        }
        assertEquals(List.of("destroyInternal"), methods);
        Method hook = type.getDeclaredMethod("destroyInternal");
        assertEquals(Modifier.PROTECTED, hook.getModifiers());
        assertEquals(void.class, hook.getReturnType());
        assertFalse(hook.isAnnotationPresent(Deprecated.class));
    }
}
