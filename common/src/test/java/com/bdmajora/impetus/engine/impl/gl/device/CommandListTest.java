package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the default close of the command list over mocks and over the command list of the render device
class CommandListTest {
    // Render device whose command list a test uses or null when the test uses none
    private GLRenderDevice device;

    // Deactivates the render device of the test
    @AfterEach
    void deactivate() {
        if (device != null) {
            device.makeInactive();
        }
    }

    // Creates a command list mock whose close runs the default method
    private static CommandList mockList() {
        return Mockito.mock(CommandList.class, Mockito.CALLS_REAL_METHODS);
    }

    // Activates a fresh render device and returns its command list with no recorded GL calls
    private CommandList deviceList() {
        device = new GLRenderDevice();
        device.makeActive();
        CommandList list = device.createCommandList();
        Mockito.clearInvocations(TestGl.gl());
        return list;
    }

    // Returns the exception that the body throws
    private static Throwable thrownBy(Runnable body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            return thrown;
        }
        throw new AssertionError("Body did not throw");
    }

    // Returns the checked exception as an unchecked throw
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable thrown) throws T {
        throw (T) thrown;
    }

    // Checks that close calls flush once and nothing else
    @Test
    void closeFlushesOnceAndCallsNothingElse() {
        CommandList list = mockList();
        list.close();
        Mockito.verify(list).close();
        Mockito.verify(list).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that every repeated close flushes again
    @Test
    void repeatedCloseFlushesEveryTime() {
        CommandList list = mockList();
        for (int i = 0; i < 3; i++) {
            list.close();
        }
        Mockito.verify(list, Mockito.times(3)).close();
        Mockito.verify(list, Mockito.times(3)).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that try with resources flushes once after every command of the body
    @Test
    void tryWithResourcesFlushesOnceAfterTheBody() {
        CommandList list = mockList();
        try (CommandList open = list) {
            open.createMutableBuffer();
            open.unbindVertexArray();
        }
        InOrder order = Mockito.inOrder(list);
        order.verify(list).createMutableBuffer();
        order.verify(list).unbindVertexArray();
        order.verify(list).close();
        order.verify(list).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that close rethrows the unchecked exception of flush unchanged
    @Test
    void closeRethrowsTheRuntimeExceptionOfFlush() {
        CommandList list = mockList();
        IllegalStateException failure = new IllegalStateException("flush");
        Mockito.doThrow(failure).when(list).flush();
        assertSame(failure, assertThrows(IllegalStateException.class, list::close));
    }

    // Checks that close rethrows the error of flush unchanged
    @Test
    void closeRethrowsTheErrorOfFlush() {
        CommandList list = mockList();
        StackOverflowError failure = new StackOverflowError("flush");
        Mockito.doThrow(failure).when(list).flush();
        assertSame(failure, thrownBy(list::close));
    }

    // Checks that close rethrows an undeclared checked exception of flush unchanged
    @Test
    void closeRethrowsAnUndeclaredCheckedExceptionOfFlush() {
        CommandList list = mockList();
        IOException failure = new IOException("flush");
        Mockito.doAnswer(invocation -> {
            throw sneaky(failure);
        }).when(list).flush();
        assertSame(failure, thrownBy(list::close));
    }

    // Checks that a failed close leaves no closed state behind and flushes again on the next close
    @Test
    void closeAfterAFailedFlushFlushesAgain() {
        CommandList list = mockList();
        Mockito.doThrow(new IllegalStateException("first")).doNothing().when(list).flush();
        assertThrows(IllegalStateException.class, list::close);
        list.close();
        Mockito.verify(list, Mockito.times(2)).flush();
    }

    // Checks that a flush failure on close is suppressed behind the failure of the body
    @Test
    void flushFailureIsSuppressedBehindTheBodyFailure() {
        CommandList list = mockList();
        IllegalStateException flushFailure = new IllegalStateException("flush");
        Mockito.doThrow(flushFailure).when(list).flush();
        IllegalArgumentException bodyFailure = new IllegalArgumentException("body");
        Throwable thrown = thrownBy(() -> {
            try (CommandList open = list) {
                open.createFence();
                throw bodyFailure;
            }
        });
        assertSame(bodyFailure, thrown);
        assertArrayEquals(new Throwable[]{flushFailure}, thrown.getSuppressed());
    }

    // Checks that a flush failure on close is thrown when the body completes
    @Test
    void flushFailureIsThrownWhenTheBodyCompletes() {
        CommandList list = mockList();
        IllegalStateException flushFailure = new IllegalStateException("flush");
        Mockito.doThrow(flushFailure).when(list).flush();
        Throwable thrown = thrownBy(() -> {
            try (CommandList open = list) {
                open.createFence();
            }
        });
        assertSame(flushFailure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
    }

    // Checks that close is the only default method and declares no checked exception
    @Test
    void closeIsTheOnlyDefaultMethodAndDeclaresNoCheckedException() throws NoSuchMethodException {
        assertTrue(AutoCloseable.class.isAssignableFrom(CommandList.class));
        Method close = CommandList.class.getDeclaredMethod("close");
        assertTrue(close.isDefault());
        assertSame(void.class, close.getReturnType());
        assertEquals(0, close.getExceptionTypes().length);
        List<String> defaults = new ArrayList<>();
        for (Method method : CommandList.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (method.isDefault()) {
                defaults.add(method.getName());
            } else {
                assertTrue(Modifier.isAbstract(method.getModifiers()), method.getName());
            }
        }
        assertEquals(List.of("close"), defaults);
    }

    // Checks that closing the command list of the device issues no GL call and keeps it usable
    @Test
    void closeOnTheDeviceListIssuesNoGlCallAndKeepsTheListUsable() {
        CommandList list = deviceList();
        list.close();
        list.close();
        Mockito.verifyNoInteractions(TestGl.gl());
        assertSame(list, device.createCommandList());
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        Mockito.verify(TestGl.gl()).glBindBuffer(GlBufferTarget.ELEMENT_BUFFER.getTargetParameter(), buffer.handle());
    }

    // Checks that closing the command list of the device keeps the tracked bindings
    @Test
    void closeOnTheDeviceListKeepsTheTrackedBindings() {
        CommandList list = deviceList();
        GlMutableBuffer buffer;
        try (CommandList open = list) {
            buffer = open.createMutableBuffer();
            open.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
        }
        list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glBindBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), buffer.handle());
    }

    // Checks that closing the command list of an inactive device neither throws nor issues a GL call
    @Test
    void closeOnTheListOfAnInactiveDeviceIssuesNoGlCall() {
        CommandList list = deviceList();
        device.makeInactive();
        Mockito.clearInvocations(TestGl.gl());
        list.close();
        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Checks that closing the command list of the device leaves the tessellation in progress bound
    @Test
    void closeOnTheDeviceListLeavesTheTessellationBound() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        list.close();
        Mockito.verify(tessellation).bind(list);
        Mockito.verify(tessellation, Mockito.never()).unbind(Mockito.any());
        draw.close();
        Mockito.verify(tessellation).unbind(list);
    }
}
