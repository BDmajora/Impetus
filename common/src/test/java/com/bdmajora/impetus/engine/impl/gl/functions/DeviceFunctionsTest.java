package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

// Tests of the pair of copy and map strategies and of its choice from the GL context
class DeviceFunctionsTest {
    // Mocked GL service that answers the capability queries
    private final LWJGLService gl = TestGl.gl();
    // Device that the strategy choice receives
    private final RenderDevice device = Mockito.mock(RenderDevice.class);

    // Forgets the queries made while the test fixtures loaded
    @BeforeEach
    void forgetEarlierQueries() {
        Mockito.clearInvocations(gl);
    }

    // Returns every pair of one copy strategy and one map strategy
    static Stream<Arguments> pairs() {
        List<Arguments> pairs = new ArrayList<>();

        for (BufferCopyFunctions copy : BufferCopyFunctions.values()) {
            for (BufferMapRangeFunctions map : BufferMapRangeFunctions.values()) {
                pairs.add(Arguments.of(copy, map));
            }
        }

        return pairs.stream();
    }

    // Answers every capability query with the given support
    private void supportAll(boolean supported) {
        Mockito.doReturn(supported).when(gl).isOpenGLVersionSupported(anyInt(), anyInt());
        Mockito.doReturn(supported).when(gl).isExtensionSupported(any());
    }

    // Checks that the canonical constructor keeps every pair of strategies unchanged
    @ParameterizedTest
    @MethodSource("pairs")
    void canonicalConstructorKeepsEveryPair(BufferCopyFunctions copy, BufferMapRangeFunctions map) {
        DeviceFunctions functions = new DeviceFunctions(copy, map);
        assertSame(copy, functions.bufferCopyFunctions());
        assertSame(map, functions.bufferMapRangeFunctions());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that the canonical constructor rejects a null copy strategy
    @ParameterizedTest
    @EnumSource(BufferMapRangeFunctions.class)
    void canonicalConstructorRejectsANullCopyStrategy(BufferMapRangeFunctions map) {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new DeviceFunctions(null, map));
        assertEquals("Buffer copy functions must not be null", error.getMessage());
    }

    // Checks that the canonical constructor rejects a null map strategy
    @ParameterizedTest
    @EnumSource(BufferCopyFunctions.class)
    void canonicalConstructorRejectsANullMapStrategy(BufferCopyFunctions copy) {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new DeviceFunctions(copy, null));
        assertEquals("Buffer map range functions must not be null", error.getMessage());
    }

    // Checks that two null strategies report the copy strategy first
    @Test
    void canonicalConstructorReportsTheCopyStrategyFirst() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new DeviceFunctions(null, null));
        assertEquals("Buffer copy functions must not be null", error.getMessage());
    }

    // Checks that the device constructor follows every combination of version and extension support
    @ParameterizedTest
    @CsvSource({
            "true, true, true, true, CORE, CORE",
            "true, true, true, false, CORE, CORE",
            "true, true, false, true, CORE, CORE",
            "true, true, false, false, CORE, MAP_FULL_AND_SLICE",
            "true, false, true, true, CORE, CORE",
            "true, false, true, false, CORE, CORE",
            "true, false, false, true, CORE, CORE",
            "true, false, false, false, CORE, MAP_FULL_AND_SLICE",
            "false, true, true, true, CORE, CORE",
            "false, true, true, false, CORE, CORE",
            "false, true, false, true, CORE, CORE",
            "false, true, false, false, CORE, MAP_FULL_AND_SLICE",
            "false, false, true, true, PIXEL_PACK, CORE",
            "false, false, true, false, PIXEL_PACK, CORE",
            "false, false, false, true, PIXEL_PACK, CORE",
            "false, false, false, false, PIXEL_PACK, MAP_FULL_AND_SLICE"
    })
    void deviceConstructorFollowsTheCapabilities(boolean copyVersion, boolean copyExtension, boolean mapVersion, boolean mapExtension,
                                                 BufferCopyFunctions copy, BufferMapRangeFunctions map) {
        Mockito.doReturn(copyVersion).when(gl).isOpenGLVersionSupported(3, 1);
        Mockito.doReturn(copyExtension).when(gl).isExtensionSupported(GLExtension.ARB_copy_buffer);
        Mockito.doReturn(mapVersion).when(gl).isOpenGLVersionSupported(3, 0);
        Mockito.doReturn(mapExtension).when(gl).isExtensionSupported(GLExtension.ARB_map_buffer_range);

        DeviceFunctions functions = new DeviceFunctions(device);
        assertSame(copy, functions.bufferCopyFunctions());
        assertSame(map, functions.bufferMapRangeFunctions());

        // Verifies that the copy queries precede the map queries and that a supported version skips its extension query
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).isOpenGLVersionSupported(3, 1);
        order.verify(gl, Mockito.times(copyVersion ? 0 : 1)).isExtensionSupported(GLExtension.ARB_copy_buffer);
        order.verify(gl).isOpenGLVersionSupported(3, 0);
        order.verify(gl, Mockito.times(mapVersion ? 0 : 1)).isExtensionSupported(GLExtension.ARB_map_buffer_range);
        Mockito.verifyNoMoreInteractions(gl);
        Mockito.verifyNoInteractions(device);
    }

    // Checks that the device constructor matches the canonical constructor fed with both choices
    @Test
    void deviceConstructorEqualsTheCanonicalPairOfChoices() {
        assertEquals(new DeviceFunctions(BufferCopyFunctions.pickBest(device), BufferMapRangeFunctions.pickBest(device)), new DeviceFunctions(device));
        supportAll(false);
        assertEquals(new DeviceFunctions(BufferCopyFunctions.pickBest(device), BufferMapRangeFunctions.pickBest(device)), new DeviceFunctions(device));
    }

    // Checks that the device constructor accepts a null device
    @Test
    void deviceConstructorAcceptsANullDevice() {
        assertEquals(new DeviceFunctions(BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE), new DeviceFunctions((RenderDevice) null));
        supportAll(false);
        assertEquals(new DeviceFunctions(BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.MAP_FULL_AND_SLICE), new DeviceFunctions((RenderDevice) null));
    }

    // Checks that the device constructor never calls into a device that refuses every call
    @Test
    void deviceConstructorIgnoresADeletedDevice() {
        RenderDevice deleted = Mockito.mock(RenderDevice.class, call -> {
            throw new IllegalStateException("Device was deleted");
        });

        DeviceFunctions functions = new DeviceFunctions(deleted);
        assertSame(BufferCopyFunctions.CORE, functions.bufferCopyFunctions());
        assertSame(BufferMapRangeFunctions.CORE, functions.bufferMapRangeFunctions());
    }

    // Checks that every call reads the capabilities again and leaves earlier pairs unchanged
    @Test
    void deviceConstructorReadsTheCapabilitiesOnEveryCall() {
        DeviceFunctions first = new DeviceFunctions(device);
        DeviceFunctions second = new DeviceFunctions(device);
        assertNotSame(first, second);
        assertEquals(first, second);

        supportAll(false);
        DeviceFunctions legacy = new DeviceFunctions(device);
        assertNotEquals(first, legacy);
        assertSame(BufferCopyFunctions.PIXEL_PACK, legacy.bufferCopyFunctions());
        assertSame(BufferMapRangeFunctions.MAP_FULL_AND_SLICE, legacy.bufferMapRangeFunctions());
        assertSame(BufferCopyFunctions.CORE, first.bufferCopyFunctions());
        assertSame(BufferMapRangeFunctions.CORE, first.bufferMapRangeFunctions());

        Mockito.verify(gl, Mockito.times(3)).isOpenGLVersionSupported(3, 1);
        Mockito.verify(gl, Mockito.times(3)).isOpenGLVersionSupported(3, 0);
    }

    // Checks that a failing copy query stops the constructor before the map query
    @Test
    void deviceConstructorPropagatesACopyQueryFailure() {
        IllegalStateException failure = new IllegalStateException("No GL context is current");
        Mockito.doThrow(failure).when(gl).isOpenGLVersionSupported(3, 1);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> new DeviceFunctions(device)));
        Mockito.verify(gl).isOpenGLVersionSupported(3, 1);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that a failing map query propagates after the copy query
    @Test
    void deviceConstructorPropagatesAMapQueryFailure() {
        IllegalStateException failure = new IllegalStateException("No GL context is current");
        Mockito.doReturn(false).when(gl).isOpenGLVersionSupported(3, 0);
        Mockito.doThrow(failure).when(gl).isExtensionSupported(GLExtension.ARB_map_buffer_range);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> new DeviceFunctions(device)));
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).isOpenGLVersionSupported(3, 1);
        order.verify(gl).isOpenGLVersionSupported(3, 0);
        order.verify(gl).isExtensionSupported(GLExtension.ARB_map_buffer_range);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that equality and the hash follow both strategies
    @ParameterizedTest
    @MethodSource("pairs")
    void equalsAndHashCodeFollowBothStrategies(BufferCopyFunctions copy, BufferMapRangeFunctions map) {
        DeviceFunctions functions = new DeviceFunctions(copy, map);
        DeviceFunctions twin = new DeviceFunctions(copy, map);
        assertEquals(functions, functions);
        assertEquals(functions, twin);
        assertEquals(twin, functions);
        assertEquals(functions.hashCode(), twin.hashCode());
        assertEquals(functions.hashCode(), functions.hashCode());
        assertNotEquals(null, functions);
        assertFalse(functions.equals(copy));
        assertFalse(functions.equals(map));

        // Verifies that the pair is unequal to every pair that differs in one strategy
        pairs().map(Arguments::get).filter(other -> other[0] != copy || other[1] != map).forEach(other -> {
            DeviceFunctions different = new DeviceFunctions((BufferCopyFunctions) other[0], (BufferMapRangeFunctions) other[1]);
            assertNotEquals(functions, different);
            assertNotEquals(different, functions);
        });
    }

    // Checks that the text names the record and both strategies
    @ParameterizedTest
    @MethodSource("pairs")
    void toStringNamesBothStrategies(BufferCopyFunctions copy, BufferMapRangeFunctions map) {
        String expected = "DeviceFunctions[bufferCopyFunctions=" + copy.name() + ", bufferMapRangeFunctions=" + map.name() + "]";
        assertEquals(expected, new DeviceFunctions(copy, map).toString());
    }

    // Checks the record components and the two public constructors
    @Test
    void recordShapeIsTheTwoStrategies() throws ReflectiveOperationException {
        assertTrue(DeviceFunctions.class.isRecord());
        assertTrue(Modifier.isFinal(DeviceFunctions.class.getModifiers()));
        assertTrue(Modifier.isPublic(DeviceFunctions.class.getModifiers()));

        RecordComponent[] components = DeviceFunctions.class.getRecordComponents();
        assertEquals(2, components.length);
        assertEquals("bufferCopyFunctions", components[0].getName());
        assertSame(BufferCopyFunctions.class, components[0].getType());
        assertEquals("bufferMapRangeFunctions", components[1].getName());
        assertSame(BufferMapRangeFunctions.class, components[1].getType());

        Constructor<?>[] constructors = DeviceFunctions.class.getDeclaredConstructors();
        assertEquals(2, constructors.length);
        for (Constructor<?> constructor : constructors) {
            assertTrue(Modifier.isPublic(constructor.getModifiers()));
        }
        assertNotNull(DeviceFunctions.class.getConstructor(BufferCopyFunctions.class, BufferMapRangeFunctions.class));
        assertNotNull(DeviceFunctions.class.getConstructor(RenderDevice.class));
    }
}
