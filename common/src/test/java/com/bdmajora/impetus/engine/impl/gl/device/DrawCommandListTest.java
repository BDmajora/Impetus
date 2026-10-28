package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

// Tests of the default close of the draw command list over mocks and over the draw command list of the render device
class DrawCommandListTest {
    // Render device whose draw command list a test uses or null when the test uses none
    private GLRenderDevice device;

    // Deactivates the render device of the test
    @AfterEach
    void deactivate() {
        if (device != null) {
            device.makeInactive();
        }
    }

    // Creates a draw command list mock whose close runs the default method
    private static DrawCommandList mockList() {
        return Mockito.mock(DrawCommandList.class, Mockito.CALLS_REAL_METHODS);
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
        DrawCommandList list = mockList();
        list.close();
        Mockito.verify(list).close();
        Mockito.verify(list).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that every repeated close flushes again
    @Test
    void repeatedCloseFlushesEveryTime() {
        DrawCommandList list = mockList();
        for (int i = 0; i < 3; i++) {
            list.close();
        }
        Mockito.verify(list, Mockito.times(3)).close();
        Mockito.verify(list, Mockito.times(3)).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that try with resources flushes once after every draw of the body
    @Test
    void tryWithResourcesFlushesOnceAfterTheBody() {
        DrawCommandList list = mockList();
        try (DrawCommandList open = list) {
            open.multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            open.multiDrawElementsBaseVertex(null, GlPrimitiveType.LINES, GlIndexType.UNSIGNED_SHORT);
        }
        InOrder order = Mockito.inOrder(list);
        order.verify(list).multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
        order.verify(list).multiDrawElementsBaseVertex(null, GlPrimitiveType.LINES, GlIndexType.UNSIGNED_SHORT);
        order.verify(list).close();
        order.verify(list).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that close flushes and never ends the tessellation on its own after an explicit end
    @Test
    void closeAfterEndTessellatingOnlyFlushes() {
        DrawCommandList list = mockList();
        try (DrawCommandList open = list) {
            open.endTessellating();
        }
        InOrder order = Mockito.inOrder(list);
        order.verify(list).endTessellating();
        order.verify(list).close();
        order.verify(list).flush();
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that close rethrows the unchecked exception of flush unchanged
    @Test
    void closeRethrowsTheRuntimeExceptionOfFlush() {
        DrawCommandList list = mockList();
        IllegalStateException failure = new IllegalStateException("flush");
        Mockito.doThrow(failure).when(list).flush();
        assertSame(failure, assertThrows(IllegalStateException.class, list::close));
    }

    // Checks that close rethrows the error of flush unchanged
    @Test
    void closeRethrowsTheErrorOfFlush() {
        DrawCommandList list = mockList();
        OutOfMemoryError failure = new OutOfMemoryError("flush");
        Mockito.doThrow(failure).when(list).flush();
        assertSame(failure, thrownBy(list::close));
    }

    // Checks that close rethrows an undeclared checked exception of flush unchanged
    @Test
    void closeRethrowsAnUndeclaredCheckedExceptionOfFlush() {
        DrawCommandList list = mockList();
        IOException failure = new IOException("flush");
        Mockito.doAnswer(invocation -> {
            throw sneaky(failure);
        }).when(list).flush();
        assertSame(failure, thrownBy(list::close));
    }

    // Checks that a failed close leaves no closed state behind and flushes again on the next close
    @Test
    void closeAfterAFailedFlushFlushesAgain() {
        DrawCommandList list = mockList();
        Mockito.doThrow(new IllegalStateException("first")).doNothing().when(list).flush();
        assertThrows(IllegalStateException.class, list::close);
        list.close();
        Mockito.verify(list, Mockito.times(2)).flush();
    }

    // Checks that a flush failure on close is suppressed behind the failure of the body
    @Test
    void flushFailureIsSuppressedBehindTheBodyFailure() {
        DrawCommandList list = mockList();
        IllegalStateException flushFailure = new IllegalStateException("flush");
        Mockito.doThrow(flushFailure).when(list).flush();
        IllegalArgumentException bodyFailure = new IllegalArgumentException("body");
        Throwable thrown = thrownBy(() -> {
            try (DrawCommandList open = list) {
                open.endTessellating();
                throw bodyFailure;
            }
        });
        assertSame(bodyFailure, thrown);
        assertArrayEquals(new Throwable[]{flushFailure}, thrown.getSuppressed());
    }

    // Checks that a flush failure on close is thrown when the body completes
    @Test
    void flushFailureIsThrownWhenTheBodyCompletes() {
        DrawCommandList list = mockList();
        IllegalStateException flushFailure = new IllegalStateException("flush");
        Mockito.doThrow(flushFailure).when(list).flush();
        Throwable thrown = thrownBy(() -> {
            try (DrawCommandList open = list) {
                open.endTessellating();
            }
        });
        assertSame(flushFailure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
    }

    // Checks that close on a hand written implementation runs the flush of that implementation
    @Test
    void closeRunsTheFlushOfAnImplementation() {
        int[] calls = new int[4];
        DrawCommandList list = new DrawCommandList() {
            // Counts the call in the first slot
            @Override
            public void multiDrawElementsBaseVertex(DirectMultiDrawBatch batch, GlPrimitiveType primitiveType, GlIndexType indexType) {
                calls[0]++;
            }

            // Counts the call in the second slot
            @Override
            public void multiDrawElementsIndirect(long offset, int count, GlPrimitiveType primitiveType, GlIndexType indexType) {
                calls[1]++;
            }

            // Counts the call in the third slot
            @Override
            public void endTessellating() {
                calls[2]++;
            }

            // Counts the call in the fourth slot
            @Override
            public void flush() {
                calls[3]++;
            }
        };
        list.close();
        list.close();
        assertArrayEquals(new int[]{0, 0, 0, 2}, calls);
    }

    // Checks that close is the only default method and declares no checked exception
    @Test
    void closeIsTheOnlyDefaultMethodAndDeclaresNoCheckedException() throws NoSuchMethodException {
        assertTrue(AutoCloseable.class.isAssignableFrom(DrawCommandList.class));
        Method close = DrawCommandList.class.getDeclaredMethod("close");
        assertTrue(close.isDefault());
        assertSame(void.class, close.getReturnType());
        assertEquals(0, close.getExceptionTypes().length);
        List<String> defaults = new ArrayList<>();
        for (Method method : DrawCommandList.class.getDeclaredMethods()) {
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

    // Checks that the interface declares the four abstract methods with their parameter types and no checked exception
    @Test
    void abstractMethodsKeepTheirSignatures() throws NoSuchMethodException {
        assertTrue(DrawCommandList.class.isInterface());
        assertTrue(Modifier.isPublic(DrawCommandList.class.getModifiers()));
        assertArrayEquals(new Class<?>[]{AutoCloseable.class}, DrawCommandList.class.getInterfaces());
        Method[] declared = {
                DrawCommandList.class.getDeclaredMethod("multiDrawElementsBaseVertex", DirectMultiDrawBatch.class, GlPrimitiveType.class, GlIndexType.class),
                DrawCommandList.class.getDeclaredMethod("multiDrawElementsIndirect", long.class, int.class, GlPrimitiveType.class, GlIndexType.class),
                DrawCommandList.class.getDeclaredMethod("endTessellating"),
                DrawCommandList.class.getDeclaredMethod("flush")
        };
        for (Method method : declared) {
            assertTrue(Modifier.isAbstract(method.getModifiers()), method.getName());
            assertSame(void.class, method.getReturnType(), method.getName());
            assertEquals(0, method.getExceptionTypes().length, method.getName());
        }
        long declaredCount = 0;
        for (Method method : DrawCommandList.class.getDeclaredMethods()) {
            if (!method.isSynthetic()) {
                declaredCount++;
            }
        }
        assertEquals(5, declaredCount);
    }

    // Checks that closing the draw command list of the device unbinds the tessellation once and issues no GL call
    @Test
    void closeOnTheDeviceListUnbindsTheTessellationOnce() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        draw.close();
        draw.close();
        draw.flush();
        InOrder order = Mockito.inOrder(tessellation);
        order.verify(tessellation).bind(list);
        order.verify(tessellation).unbind(list);
        Mockito.verifyNoMoreInteractions(tessellation);
        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Checks that closing after an explicit end leaves the tessellation unbound once
    @Test
    void closeOnTheDeviceListAfterEndTessellatingDoesNotUnbindAgain() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            draw.endTessellating();
        }
        Mockito.verify(tessellation).bind(list);
        Mockito.verify(tessellation).unbind(list);
        Mockito.verifyNoMoreInteractions(tessellation);
    }

    // Checks that try with resources unbinds the tessellation after both draws in order
    @Test
    void tryWithResourcesOnTheDeviceListUnbindsAfterTheDraws() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        LWJGLService gl = TestGl.gl();
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(2);
        try {
            batch.appendDrawCommand(3, 6, 12L);
            try (DrawCommandList draw = list.beginTessellating(tessellation)) {
                draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
                draw.multiDrawElementsIndirect(16L, 2, GlPrimitiveType.LINES, GlIndexType.UNSIGNED_SHORT);
            }
            InOrder order = Mockito.inOrder(tessellation, gl);
            order.verify(tessellation).bind(list);
            order.verify(gl).glMultiDrawElementsBaseVertex(GlPrimitiveType.TRIANGLES.getId(), batch.pElementCount,
                    GlIndexType.UNSIGNED_INT.getFormatId(), batch.pElementPointer, 1, batch.pBaseVertex);
            order.verify(gl).glMultiDrawElementsIndirect(GlPrimitiveType.LINES.getId(), GlIndexType.UNSIGNED_SHORT.getFormatId(), 16L, 2, 0);
            order.verify(tessellation).unbind(list);
            Mockito.verifyNoMoreInteractions(tessellation, gl);
        } finally {
            batch.delete();
        }
    }

    // Checks that try with resources unbinds the tessellation when the draw throws and adds no suppressed exception
    @Test
    void tryWithResourcesOnTheDeviceListUnbindsWhenTheDrawThrows() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IllegalStateException failure = new IllegalStateException("draw");
        Mockito.doThrow(failure).when(TestGl.gl()).glMultiDrawElementsIndirect(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        Throwable thrown = thrownBy(() -> {
            try (DrawCommandList draw = list.beginTessellating(tessellation)) {
                draw.multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            }
        });
        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        Mockito.verify(tessellation).unbind(list);
    }

    // Checks that an unbind failure on close is suppressed behind the draw failure and the next close unbinds again
    @Test
    void unbindFailureOnTheDeviceListIsSuppressedBehindTheDrawFailure() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IllegalStateException drawFailure = new IllegalStateException("draw");
        IllegalArgumentException unbindFailure = new IllegalArgumentException("unbind");
        Mockito.doThrow(drawFailure).when(TestGl.gl()).glMultiDrawElementsIndirect(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doThrow(unbindFailure).doNothing().when(tessellation).unbind(list);
        DrawCommandList draw = list.beginTessellating(tessellation);
        Throwable thrown = thrownBy(() -> {
            try (DrawCommandList open = draw) {
                open.multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            }
        });
        assertSame(drawFailure, thrown);
        assertArrayEquals(new Throwable[]{unbindFailure}, thrown.getSuppressed());
        draw.close();
        draw.close();
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }

    // Checks that a failed unbind on close keeps the tessellation in progress and the next close unbinds it again
    @Test
    void closeOnTheDeviceListAfterAFailedUnbindUnbindsAgain() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(tessellation).unbind(list);
        DrawCommandList draw = list.beginTessellating(tessellation);
        assertSame(failure, assertThrows(IllegalStateException.class, draw::close));
        draw.close();
        draw.close();
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }

    // Checks that every tessellation begun after a close is unbound by the next close
    @Test
    void repeatedTessellationsOnTheDeviceListAreEachUnboundOnce() {
        CommandList list = deviceList();
        GlTessellation first = Mockito.mock(GlTessellation.class);
        GlTessellation second = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(first)) {
            draw.flush();
        }
        try (DrawCommandList draw = list.beginTessellating(second)) {
            draw.endTessellating();
        }
        list.beginTessellating(first).close();
        Mockito.verify(first, Mockito.times(2)).bind(list);
        Mockito.verify(first, Mockito.times(2)).unbind(list);
        Mockito.verify(second).bind(list);
        Mockito.verify(second).unbind(list);
        Mockito.verifyNoMoreInteractions(first, second);
    }

    // Checks that a draw that rejects a null argument issues no GL call and still lets close unbind
    @Test
    void nullDrawArgumentsOnTheDeviceListIssueNoGlCall() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(1);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            assertThrows(NullPointerException.class, () -> draw.multiDrawElementsBaseVertex(null, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
            assertThrows(NullPointerException.class, () -> draw.multiDrawElementsBaseVertex(batch, null, GlIndexType.UNSIGNED_INT));
            assertThrows(NullPointerException.class, () -> draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, null));
            assertThrows(NullPointerException.class, () -> draw.multiDrawElementsIndirect(0L, 1, null, GlIndexType.UNSIGNED_INT));
            assertThrows(NullPointerException.class, () -> draw.multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, null));
        } finally {
            batch.delete();
        }
        Mockito.verifyNoInteractions(TestGl.gl());
        Mockito.verify(tessellation).unbind(list);
    }

    // Checks that an empty batch draws zero commands and a full single slot batch draws one
    @Test
    void emptyAndSingleCommandBatchesOnTheDeviceListForwardTheirSize() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        LWJGLService gl = TestGl.gl();
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(1);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.POINTS, GlIndexType.UNSIGNED_BYTE);
            batch.appendDrawCommand(Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
            draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.POINTS, GlIndexType.UNSIGNED_BYTE);
            InOrder order = Mockito.inOrder(gl);
            order.verify(gl).glMultiDrawElementsBaseVertex(GlPrimitiveType.POINTS.getId(), batch.pElementCount,
                    GlIndexType.UNSIGNED_BYTE.getFormatId(), batch.pElementPointer, 0, batch.pBaseVertex);
            order.verify(gl).glMultiDrawElementsBaseVertex(GlPrimitiveType.POINTS.getId(), batch.pElementCount,
                    GlIndexType.UNSIGNED_BYTE.getFormatId(), batch.pElementPointer, 1, batch.pBaseVertex);
            Mockito.verifyNoMoreInteractions(gl);
        } finally {
            batch.delete();
        }
    }

    // Checks that every primitive type and index type reaches both draws as its GL constant
    @Test
    void everyPrimitiveAndIndexTypeOnTheDeviceListReachesGl() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        LWJGLService gl = TestGl.gl();
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(1);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            for (GlPrimitiveType primitiveType : GlPrimitiveType.values()) {
                for (GlIndexType indexType : GlIndexType.values()) {
                    draw.multiDrawElementsBaseVertex(batch, primitiveType, indexType);
                    draw.multiDrawElementsIndirect(4L, 1, primitiveType, indexType);
                    Mockito.verify(gl).glMultiDrawElementsBaseVertex(primitiveType.getId(), batch.pElementCount,
                            indexType.getFormatId(), batch.pElementPointer, 0, batch.pBaseVertex);
                    Mockito.verify(gl).glMultiDrawElementsIndirect(primitiveType.getId(), indexType.getFormatId(), 4L, 1, 0);
                    Mockito.clearInvocations(gl);
                }
            }
        } finally {
            batch.delete();
        }
    }

    // Checks that the indirect draw forwards every aligned byte offset unchanged at zero and the int and long limits
    @ParameterizedTest
    @ValueSource(longs = {0L, 4L, 8L, 20L, Integer.MAX_VALUE - 3L, 1L + Integer.MAX_VALUE, 1L << 32, Long.MAX_VALUE - 3L})
    void indirectOffsetOnTheDeviceListReachesGlUnchanged(long offset) {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            draw.multiDrawElementsIndirect(offset, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
        }
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(GlPrimitiveType.TRIANGLES.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), offset, 1, 0);
        Mockito.verifyNoMoreInteractions(TestGl.gl());
    }

    // Checks that the indirect draw forwards the command count unchanged at zero and one and the int limit
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 1 << 16, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void indirectCountOnTheDeviceListReachesGlUnchanged(int count) {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            draw.multiDrawElementsIndirect(0L, count, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
        }
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(GlPrimitiveType.TRIANGLES.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), 0L, count, 0);
        Mockito.verifyNoMoreInteractions(TestGl.gl());
    }

    // Checks that closing the draw command list of the device leaves the command list usable and the device active
    @Test
    void closeOnTheDeviceListKeepsTheCommandListUsable() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        draw.close();
        assertSame(list, device.createCommandList());
        assertSame(draw, list.beginTessellating(tessellation));
        draw.close();
        Mockito.verify(tessellation, Mockito.times(2)).bind(list);
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }
}
