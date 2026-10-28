package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlImmutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlVertexArrayTessellation;
import com.bdmajora.impetus.engine.impl.gl.tessellation.TessellationBinding;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.engine.impl.gpu.device.GpuDevice;
import com.bdmajora.impetus.engine.impl.gpu.device.GpuDeviceFeature;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// Tests of the render device and of its command list and draw command list
class GLRenderDeviceTest {
    // Message of every refusal outside managed code
    private static final String UNMANAGED = "Tried to access device from unmanaged context";
    // Message of every refusal while no tessellation is in progress
    private static final String NO_TESSELLATION = "No tessellation is in progress";
    // GL enum of the array buffer binding point
    private static final int ARRAY = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
    // GL enum of the element buffer binding point
    private static final int ELEMENT = GlBufferTarget.ELEMENT_BUFFER.getTargetParameter();
    // GL enum of the copy read binding point
    private static final int COPY_READ = GlBufferTarget.COPY_READ_BUFFER.getTargetParameter();
    // GL enum of the copy write binding point
    private static final int COPY_WRITE = GlBufferTarget.COPY_WRITE_BUFFER.getTargetParameter();
    // GL enum of the vendor string query
    private static final int GL_VENDOR = 0x1F00;
    // GL enum of the renderer string query
    private static final int GL_RENDERER = 0x1F01;
    // GL enum of the version string query
    private static final int GL_VERSION = 0x1F02;
    // Capacity in bytes of the mapping that flush tests open
    private static final int MAPPING_BYTES = 64;

    // Mocked LWJGL service that records every GL call
    private final LWJGLService gl = TestGl.gl();
    // Native allocations that the test frees afterwards
    private final List<Long> allocations = new ArrayList<>();
    // Render device under test
    private GLRenderDevice device;

    // Creates a fresh render device and forgets the GL calls of its construction
    @BeforeEach
    void create() {
        device = new GLRenderDevice();
        Mockito.clearInvocations(gl);
    }

    // Deactivates the render device and frees the native allocations
    @AfterEach
    void release() {
        device.makeInactive();
        for (long address : allocations) {
            MemoryUtil.nmemFree(address);
        }
    }

    // Activates the render device and returns its command list with no recorded GL call
    private CommandList list() {
        device.makeActive();
        Mockito.clearInvocations(gl);
        return device.createCommandList();
    }

    // Asserts that the call throws the type with the message and issues no GL call
    private <T extends Throwable> T refusedWithoutGl(Class<T> type, String message, Executable call) {
        Mockito.clearInvocations(gl);
        T thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        Mockito.verifyNoInteractions(gl);
        return thrown;
    }

    // Asserts that the call throws a null pointer exception with the message and issues no GL call
    private void nullRefusedWithoutGl(String message, Executable call) {
        refusedWithoutGl(NullPointerException.class, message, call);
    }

    // Returns a direct buffer of the capacity over a one byte allocation that nothing reads
    private ByteBuffer hugeView(int capacity) {
        long address = MemoryUtil.nmemAlloc(1);
        allocations.add(address);
        return MemoryUtil.memByteBuffer(address, capacity);
    }

    // Returns a render device whose context supports neither core copies nor core range maps
    private static GLRenderDevice legacyDevice() {
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(false).when(gl).isOpenGLVersionSupported(Mockito.anyInt(), Mockito.anyInt());
        Mockito.doReturn(false).when(gl).isExtensionSupported(Mockito.any());
        GLRenderDevice legacy = new GLRenderDevice();
        Mockito.reset(gl);
        return legacy;
    }

    // Returns map flags with READ for bit 0 and WRITE for bit 1 and PERSISTENT for bit 2 plus UNSYNCHRONIZED
    private static EnumBitField<GlBufferMapFlags> mapFlags(int mask) {
        List<GlBufferMapFlags> flags = new ArrayList<>(List.of(GlBufferMapFlags.UNSYNCHRONIZED));
        if ((mask & 1) != 0) {
            flags.add(GlBufferMapFlags.READ);
        }
        if ((mask & 2) != 0) {
            flags.add(GlBufferMapFlags.WRITE);
        }
        if ((mask & 4) != 0) {
            flags.add(GlBufferMapFlags.PERSISTENT);
        }
        return EnumBitField.of(flags.toArray(GlBufferMapFlags[]::new));
    }

    // Returns storage flags with MAP_READ for bit 0 and MAP_WRITE for bit 1 and PERSISTENT for bit 2 plus CLIENT_STORAGE
    private static EnumBitField<GlBufferStorageFlags> storageFlags(int mask) {
        List<GlBufferStorageFlags> flags = new ArrayList<>(List.of(GlBufferStorageFlags.CLIENT_STORAGE));
        if ((mask & 1) != 0) {
            flags.add(GlBufferStorageFlags.MAP_READ);
        }
        if ((mask & 2) != 0) {
            flags.add(GlBufferStorageFlags.MAP_WRITE);
        }
        if ((mask & 4) != 0) {
            flags.add(GlBufferStorageFlags.PERSISTENT);
        }
        return EnumBitField.of(flags.toArray(GlBufferStorageFlags[]::new));
    }

    // Returns storage flags that allow a writable explicitly flushed persistent mapping
    private static EnumBitField<GlBufferStorageFlags> writableStorage() {
        return EnumBitField.of(GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.PERSISTENT);
    }

    // Returns map flags of a writable explicitly flushed persistent mapping
    private static EnumBitField<GlBufferMapFlags> writableMap() {
        return EnumBitField.of(GlBufferMapFlags.WRITE, GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.EXPLICIT_FLUSH);
    }

    // Returns a mapping of MAPPING_BYTES bytes over a new immutable buffer
    private static GlBufferMapping openMapping(CommandList list) {
        GlImmutableBuffer buffer = list.createImmutableBuffer(MAPPING_BYTES, writableStorage());
        return list.mapBuffer(buffer, 0L, MAPPING_BYTES, writableMap());
    }

    // Checks that an inactive device refuses the command list without a GL call
    @Test
    void createCommandListRejectsAnInactiveDeviceWithoutAGlCall() {
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, device::createCommandList);
        device.makeActive();
        device.makeInactive();
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, device::createCommandList);
    }

    // Checks that an active device hands out the same command list every time
    @Test
    void createCommandListReturnsTheSameListWhileActive() {
        CommandList list = list();
        assertSame(list, device.createCommandList());
        device.makeInactive();
        device.makeActive();
        assertSame(list, device.createCommandList());
    }

    // Checks that the first activation unbinds the array buffer once and captures the device once
    @Test
    void makeActiveUnbindsTheArrayBufferAndCapturesTheDeviceOnce() {
        device.makeActive();
        device.makeActive();
        Mockito.verify(gl).glBindBuffer(ARRAY, 0);
        Mockito.verify(gl).glGetString(GL_VENDOR);
        Mockito.verify(gl).glGetString(GL_RENDERER);
        Mockito.verify(gl).glGetString(GL_VERSION);
    }

    // Checks that every activation unbinds the array buffer and only the first captures the device
    @Test
    void reactivationUnbindsAgainButCapturesOnlyOnce() {
        device.makeActive();
        GpuDevice captured = device.getGpuDevice();
        device.makeInactive();
        device.makeActive();
        Mockito.verify(gl, Mockito.times(3)).glBindBuffer(ARRAY, 0);
        Mockito.verify(gl).glGetString(GL_VENDOR);
        assertSame(captured, device.getGpuDevice());
    }

    // Checks that activation forgets the bindings cached before it
    @Test
    void makeActiveForgetsBindingsCachedBeforeIt() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        device.makeInactive();
        device.makeActive();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ELEMENT, buffer.handle());
    }

    // Checks that deactivation forgets the bindings cached before it
    @Test
    void makeInactiveForgetsBindingsCachedBeforeIt() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        device.makeInactive();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ELEMENT, buffer.handle());
    }

    // Checks that deactivating an inactive device issues no GL call
    @Test
    void makeInactiveOnAnInactiveDeviceIssuesNoGlCall() {
        device.makeInactive();
        device.makeActive();
        device.makeInactive();
        Mockito.clearInvocations(gl);
        device.makeInactive();
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that a failed capture leaves the device inactive and a later activation captures again
    @Test
    void failedCaptureLeavesTheDeviceInactiveAndARetryActivates() {
        IllegalStateException failure = new IllegalStateException("capture");
        Mockito.doThrow(failure).doReturn("Vendor").when(gl).glGetString(GL_VENDOR);
        assertSame(failure, assertThrows(IllegalStateException.class, device::makeActive));
        assertEquals(UNMANAGED, assertThrows(IllegalStateException.class, device::createCommandList).getMessage());
        assertEquals(UNMANAGED, assertThrows(IllegalStateException.class, device::getGpuDevice).getMessage());
        device.makeActive();
        assertNotNull(device.createCommandList());
        assertEquals("Vendor", device.getGpuDevice().vendor());
        Mockito.verify(gl, Mockito.times(2)).glGetString(GL_VENDOR);
    }

    // Checks that a failed unbind leaves the device inactive without a capture
    @Test
    void failedUnbindLeavesTheDeviceInactive() {
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(gl).glBindBuffer(ARRAY, 0);
        assertSame(failure, assertThrows(IllegalStateException.class, device::makeActive));
        assertThrows(IllegalStateException.class, device::createCommandList);
        Mockito.verify(gl, Mockito.never()).glGetString(Mockito.anyInt());
        device.makeActive();
        assertNotNull(device.createCommandList());
    }

    // Checks that a device that never became active refuses the capability description without a GL call
    @Test
    void getGpuDeviceRejectsADeviceThatNeverBecameActiveWithoutAGlCall() {
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, device::getGpuDevice);
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, device::getGpuDevice);
    }

    // Checks that the capability description stays readable after deactivation without a GL call
    @Test
    void getGpuDeviceStaysReadableAfterDeactivation() {
        device.makeActive();
        GpuDevice captured = device.getGpuDevice();
        device.makeInactive();
        Mockito.clearInvocations(gl);
        assertSame(captured, device.getGpuDevice());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that the capability description reports the context strings and every supported feature
    @Test
    void getGpuDeviceReportsTheContextStringsAndFeatures() {
        Mockito.doReturn("Vendor").when(gl).glGetString(GL_VENDOR);
        Mockito.doReturn("Renderer").when(gl).glGetString(GL_RENDERER);
        Mockito.doReturn(null).when(gl).glGetString(GL_VERSION);
        device.makeActive();
        GpuDevice info = device.getGpuDevice();
        assertEquals("Vendor", info.vendor());
        assertEquals("Renderer", info.renderer());
        assertEquals("", info.version());
        for (GpuDeviceFeature feature : GpuDeviceFeature.values()) {
            assertTrue(info.supports(feature), feature.name());
        }
    }

    // Checks that the device picks the core functions when the context supports them and keeps them
    @Test
    void getDeviceFunctionsPicksTheCoreFunctions() {
        assertSame(BufferCopyFunctions.CORE, device.getDeviceFunctions().bufferCopyFunctions());
        assertSame(BufferMapRangeFunctions.CORE, device.getDeviceFunctions().bufferMapRangeFunctions());
        assertSame(device.getDeviceFunctions(), device.getDeviceFunctions());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that a legacy context gets the fallback functions and reports no copy or range map feature
    @Test
    void getDeviceFunctionsPicksTheFallbacksOnALegacyContext() {
        GLRenderDevice legacy = legacyDevice();
        try {
            assertSame(BufferCopyFunctions.PIXEL_PACK, legacy.getDeviceFunctions().bufferCopyFunctions());
            assertSame(BufferMapRangeFunctions.MAP_FULL_AND_SLICE, legacy.getDeviceFunctions().bufferMapRangeFunctions());
            legacy.makeActive();
            assertFalse(legacy.getGpuDevice().supports(GpuDeviceFeature.BUFFER_COPY));
            assertFalse(legacy.getGpuDevice().supports(GpuDeviceFeature.BUFFER_MAP_RANGE));
        } finally {
            legacy.makeInactive();
        }
    }

    // Checks that managed code activates and deactivates the shared device
    @Test
    void managedCodeActivatesTheSharedDevice() {
        RenderDevice.enterManagedCode();
        try {
            assertNotNull(RenderDevice.INSTANCE.createCommandList());
            assertNotNull(RenderDevice.INSTANCE.getGpuDevice());
        } finally {
            RenderDevice.exitManagedCode();
        }
        assertEquals(UNMANAGED, assertThrows(IllegalStateException.class, RenderDevice.INSTANCE::createCommandList).getMessage());
    }

    // Checks that a vertex array is bound once until another array is bound
    @Test
    void bindVertexArrayBindsOnceUntilTheArrayChanges() {
        CommandList list = list();
        GlVertexArray first = new GlVertexArray();
        GlVertexArray second = new GlVertexArray();
        list.bindVertexArray(first);
        list.bindVertexArray(first);
        list.bindVertexArray(second);
        list.bindVertexArray(first);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glBindVertexArray(first.handle());
        order.verify(gl).glBindVertexArray(second.handle());
        order.verify(gl).glBindVertexArray(first.handle());
        Mockito.verify(gl, Mockito.times(2)).glBindVertexArray(first.handle());
    }

    // Checks that a null vertex array is refused without a GL call and without a change to the cache
    @Test
    void bindVertexArrayRejectsNullWithoutTouchingTheCache() {
        CommandList list = list();
        nullRefusedWithoutGl("Vertex array must not be null", () -> list.bindVertexArray(null));
        list.unbindVertexArray();
        Mockito.verify(gl).glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
    }

    // Checks that a deleted vertex array is refused without a GL call
    @Test
    void bindVertexArrayRejectsADeletedArrayWithoutAGlCall() {
        CommandList list = list();
        GlVertexArray array = new GlVertexArray();
        array.delete();
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.bindVertexArray(array));
    }

    // Checks that a vertex array change forgets the cached buffer bindings and a repeated bind keeps them
    @Test
    void vertexArrayChangeForgetsBufferBindings() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        GlVertexArray array = new GlVertexArray();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        list.bindVertexArray(array);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        list.bindVertexArray(array);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ELEMENT, buffer.handle());
    }

    // Checks that vertex array names at the int limits reach GL unchanged
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void vertexArrayNamesAtTheIntLimitsReachGl(int name) {
        CommandList list = list();
        Mockito.doReturn(name).when(gl).glGenVertexArrays();
        GlVertexArray array = new GlVertexArray();
        list.bindVertexArray(array);
        Mockito.verify(gl).glBindVertexArray(name);
    }

    // Checks that a fresh device binds vertex array zero once
    @Test
    void unbindVertexArrayBindsZeroOnceOnAFreshDevice() {
        CommandList list = list();
        list.unbindVertexArray();
        list.unbindVertexArray();
        Mockito.verify(gl).glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that an unbind after a bind binds vertex array zero again
    @Test
    void unbindVertexArrayAfterABindBindsZeroAgain() {
        CommandList list = list();
        GlVertexArray array = new GlVertexArray();
        list.unbindVertexArray();
        list.bindVertexArray(array);
        list.unbindVertexArray();
        Mockito.verify(gl, Mockito.times(2)).glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
    }

    // Checks that an upload binds the buffer once and records the remaining bytes and keeps the position
    @Test
    void uploadDataBindsTheBufferAndRecordsTheRemainingBytes() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(32).position(4).limit(20);
        list.uploadData(buffer, data, GlBufferUsage.STATIC_DRAW);
        list.uploadData(buffer, data, GlBufferUsage.STATIC_DRAW);
        assertEquals(16L, buffer.getSize());
        assertEquals(4, data.position());
        Mockito.verify(gl).glBindBuffer(ARRAY, buffer.handle());
        Mockito.verify(gl, Mockito.times(2)).glBufferData(ARRAY, data, GlBufferUsage.STATIC_DRAW.getId());
    }

    // Checks that an empty upload reaches GL and records zero
    @Test
    void uploadDataOfAnEmptyBufferRecordsZero() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.allocateStorage(buffer, 8L, GlBufferUsage.STATIC_DRAW);
        ByteBuffer empty = ByteBuffer.allocateDirect(0);
        list.uploadData(buffer, empty, GlBufferUsage.STATIC_DRAW);
        assertEquals(0L, buffer.getSize());
        Mockito.verify(gl).glBufferData(ARRAY, empty, GlBufferUsage.STATIC_DRAW.getId());
    }

    // Checks that every usage reaches GL as its GL enum
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void uploadDataForwardsEveryUsage(GlBufferUsage usage) {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(4);
        list.uploadData(buffer, data, usage);
        list.uploadData(buffer, 0L, 4L, usage);
        list.allocateStorage(buffer, 4L, usage);
        Mockito.verify(gl).glBufferData(ARRAY, data, usage.getId());
        Mockito.verify(gl).glBufferData(ARRAY, 4L, 0L, usage.getId());
        Mockito.verify(gl).glBufferData(ARRAY, 4L, usage.getId());
    }

    // Checks that an upload of the int limit records the int limit
    @Test
    void uploadDataRecordsTheIntLimit() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        ByteBuffer data = hugeView(Integer.MAX_VALUE);
        list.uploadData(buffer, data, GlBufferUsage.STREAM_DRAW);
        assertEquals(Integer.MAX_VALUE, buffer.getSize());
    }

    // Checks that null upload arguments are refused without a GL call
    @Test
    void uploadDataRejectsNullArgumentsWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(4);
        nullRefusedWithoutGl("Buffer must not be null", () -> list.uploadData(null, data, GlBufferUsage.STATIC_DRAW));
        nullRefusedWithoutGl("Data must not be null", () -> list.uploadData(buffer, null, GlBufferUsage.STATIC_DRAW));
        nullRefusedWithoutGl("Usage must not be null", () -> list.uploadData(buffer, data, null));
        assertEquals(0L, buffer.getSize());
    }

    // Checks that heap data is refused without a GL call
    @Test
    void uploadDataRejectsHeapDataWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        String message = "Data must be a direct buffer";
        refusedWithoutGl(IllegalArgumentException.class, message, () -> list.uploadData(buffer, ByteBuffer.allocate(8), GlBufferUsage.STATIC_DRAW));
        refusedWithoutGl(IllegalArgumentException.class, message, () -> list.uploadData(buffer, ByteBuffer.wrap(new byte[0]), GlBufferUsage.STATIC_DRAW));
        refusedWithoutGl(IllegalArgumentException.class, message, () -> list.uploadData(buffer, ByteBuffer.allocate(8).asReadOnlyBuffer(), GlBufferUsage.STATIC_DRAW));
        assertEquals(0L, buffer.getSize());
    }

    // Checks that a read only direct buffer uploads like any direct buffer
    @Test
    void uploadDataAcceptsReadOnlyDirectData() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(12).asReadOnlyBuffer();
        list.uploadData(buffer, data, GlBufferUsage.STATIC_DRAW);
        assertEquals(12L, buffer.getSize());
    }

    // Checks that an upload into a deleted buffer is refused without a GL call
    @Test
    void uploadDataRejectsADeletedBufferWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.deleteBuffer(buffer);
        ByteBuffer data = ByteBuffer.allocateDirect(4);
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.uploadData(buffer, data, GlBufferUsage.STATIC_DRAW));
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.uploadData(buffer, 0L, 4L, GlBufferUsage.STATIC_DRAW));
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.allocateStorage(buffer, 4L, GlBufferUsage.STATIC_DRAW));
    }

    // Checks that every way of replacing the storage disposes the open mapping
    @Test
    void replacingTheStorageDisposesTheOpenMapping() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        EnumBitField<GlBufferMapFlags> write = EnumBitField.of(GlBufferMapFlags.WRITE);
        list.allocateStorage(buffer, 8L, GlBufferUsage.STATIC_DRAW);
        GlBufferMapping first = list.mapBuffer(buffer, 0L, 8L, write);
        list.uploadData(buffer, ByteBuffer.allocateDirect(8), GlBufferUsage.STATIC_DRAW);
        GlBufferMapping second = list.mapBuffer(buffer, 0L, 8L, write);
        list.uploadData(buffer, 0L, 8L, GlBufferUsage.STATIC_DRAW);
        GlBufferMapping third = list.mapBuffer(buffer, 0L, 8L, write);
        list.allocateStorage(buffer, 8L, GlBufferUsage.STATIC_DRAW);
        assertTrue(first.isDisposed());
        assertTrue(second.isDisposed());
        assertTrue(third.isDisposed());
        assertNull(buffer.getActiveMapping());
    }

    // Checks that a pointer upload forwards the address and size and records the size
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 3L, Integer.MAX_VALUE, 1L + Integer.MAX_VALUE, Long.MAX_VALUE})
    void pointerUploadForwardsTheAddressAndRecordsTheSize(long bytes) {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.uploadData(buffer, 0x1000L, bytes, GlBufferUsage.DYNAMIC_DRAW);
        list.uploadData(buffer, MemoryUtil.NULL, bytes, GlBufferUsage.DYNAMIC_DRAW);
        assertEquals(bytes, buffer.getSize());
        Mockito.verify(gl).glBufferData(ARRAY, bytes, 0x1000L, GlBufferUsage.DYNAMIC_DRAW.getId());
        Mockito.verify(gl).glBufferData(ARRAY, bytes, MemoryUtil.NULL, GlBufferUsage.DYNAMIC_DRAW.getId());
    }

    // Checks that a negative pointer upload size is refused without a GL call
    @ParameterizedTest
    @ValueSource(longs = {-1L, Integer.MIN_VALUE, Long.MIN_VALUE})
    void pointerUploadRejectsANegativeSizeWithoutAGlCall(long bytes) {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.uploadData(buffer, 0x1000L, 8L, GlBufferUsage.STATIC_DRAW);
        refusedWithoutGl(IllegalArgumentException.class, "Size must not be negative",
                () -> list.uploadData(buffer, 0x1000L, bytes, GlBufferUsage.STATIC_DRAW));
        assertEquals(8L, buffer.getSize());
    }

    // Checks that null pointer upload arguments are refused without a GL call
    @Test
    void pointerUploadRejectsNullArgumentsWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        nullRefusedWithoutGl("Buffer must not be null", () -> list.uploadData(null, 0L, 4L, GlBufferUsage.STATIC_DRAW));
        nullRefusedWithoutGl("Usage must not be null", () -> list.uploadData(buffer, 0L, 4L, null));
        nullRefusedWithoutGl("Usage must not be null", () -> list.uploadData(buffer, 0L, -1L, null));
    }

    // Checks that an allocation forwards the size and records it
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 3L, Integer.MAX_VALUE, 1L + Integer.MAX_VALUE, Long.MAX_VALUE})
    void allocateStorageForwardsAndRecordsTheSize(long bytes) {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.allocateStorage(buffer, bytes, GlBufferUsage.STREAM_COPY);
        assertEquals(bytes, buffer.getSize());
        Mockito.verify(gl).glBindBuffer(ARRAY, buffer.handle());
        Mockito.verify(gl).glBufferData(ARRAY, bytes, GlBufferUsage.STREAM_COPY.getId());
    }

    // Checks that a negative allocation size is refused without a GL call
    @ParameterizedTest
    @ValueSource(longs = {-1L, Integer.MIN_VALUE, Long.MIN_VALUE})
    void allocateStorageRejectsANegativeSizeWithoutAGlCall(long bytes) {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.allocateStorage(buffer, 16L, GlBufferUsage.STATIC_DRAW);
        refusedWithoutGl(IllegalArgumentException.class, "Size must not be negative",
                () -> list.allocateStorage(buffer, bytes, GlBufferUsage.STATIC_DRAW));
        assertEquals(16L, buffer.getSize());
    }

    // Checks that null allocation arguments are refused without a GL call
    @Test
    void allocateStorageRejectsNullArgumentsWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        nullRefusedWithoutGl("Buffer must not be null", () -> list.allocateStorage(null, 4L, GlBufferUsage.STATIC_DRAW));
        nullRefusedWithoutGl("Usage must not be null", () -> list.allocateStorage(buffer, 4L, null));
    }

    // Returns copy ranges that every copy accepts
    static Stream<Arguments> acceptedCopies() {
        return Stream.of(
                Arguments.of(0L, 0L, 0L),
                Arguments.of(1L, 3L, 5L),
                Arguments.of(Long.MAX_VALUE - 8L, 0L, 8L),
                Arguments.of(0L, Long.MAX_VALUE - 8L, 8L),
                Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, 0L),
                Arguments.of((long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE));
    }

    // Checks that a copy binds the source and the destination and forwards the range unchanged
    @ParameterizedTest
    @MethodSource("acceptedCopies")
    void copyBindsBothBuffersAndForwardsTheRange(long readOffset, long writeOffset, long bytes) {
        CommandList list = list();
        GlMutableBuffer src = list.createMutableBuffer();
        GlMutableBuffer dst = list.createMutableBuffer();
        list.copyBufferSubData(src, dst, readOffset, writeOffset, bytes);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glBindBuffer(COPY_READ, src.handle());
        order.verify(gl).glBindBuffer(COPY_WRITE, dst.handle());
        order.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, readOffset, writeOffset, bytes);
    }

    // Checks that a copy within one buffer binds it to both binding points
    @Test
    void copyWithinOneBufferBindsItToBothBindingPoints() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.copyBufferSubData(buffer, buffer, 0L, 16L, 16L);
        list.copyBufferSubData(buffer, buffer, 16L, 0L, 16L);
        Mockito.verify(gl).glBindBuffer(COPY_READ, buffer.handle());
        Mockito.verify(gl).glBindBuffer(COPY_WRITE, buffer.handle());
        Mockito.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, 0L, 16L, 16L);
        Mockito.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, 16L, 0L, 16L);
    }

    // Returns copy ranges that every copy refuses with the message
    static Stream<Arguments> refusedCopies() {
        return Stream.of(
                Arguments.of(-1L, 0L, 0L, "Read offset must not be negative: -1"),
                Arguments.of(Long.MIN_VALUE, 0L, 0L, "Read offset must not be negative: " + Long.MIN_VALUE),
                Arguments.of(0L, -1L, 0L, "Write offset must not be negative: -1"),
                Arguments.of(0L, Long.MIN_VALUE, 8L, "Write offset must not be negative: " + Long.MIN_VALUE),
                Arguments.of(0L, 0L, -1L, "Size must not be negative: -1"),
                Arguments.of(0L, 0L, Long.MIN_VALUE, "Size must not be negative: " + Long.MIN_VALUE),
                Arguments.of(Long.MAX_VALUE - 7L, 0L, 8L, "Copy range overflows: " + (Long.MAX_VALUE - 7L) + " + 8"),
                Arguments.of(0L, Long.MAX_VALUE, 1L, "Copy range overflows: " + Long.MAX_VALUE + " + 1"),
                Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, "Copy range overflows: " + Long.MAX_VALUE + " + " + Long.MAX_VALUE));
    }

    // Checks that a negative or overflowing copy range is refused without a GL call
    @ParameterizedTest
    @MethodSource("refusedCopies")
    void copyRejectsNegativeOrOverflowingRangesWithoutAGlCall(long readOffset, long writeOffset, long bytes, String message) {
        CommandList list = list();
        GlMutableBuffer src = list.createMutableBuffer();
        GlMutableBuffer dst = list.createMutableBuffer();
        refusedWithoutGl(IllegalArgumentException.class, message, () -> list.copyBufferSubData(src, dst, readOffset, writeOffset, bytes));
    }

    // Checks that the fallback copy refuses a negative range before it maps either buffer
    @Test
    void legacyCopyRejectsANegativeRangeBeforeMapping() {
        GLRenderDevice legacy = legacyDevice();
        try {
            legacy.makeActive();
            CommandList list = legacy.createCommandList();
            GlMutableBuffer src = list.createMutableBuffer();
            GlMutableBuffer dst = list.createMutableBuffer();
            refusedWithoutGl(IllegalArgumentException.class, "Size must not be negative: -8", () -> list.copyBufferSubData(src, dst, 0L, 0L, -8L));
            refusedWithoutGl(IllegalArgumentException.class, "Read offset must not be negative: -8", () -> list.copyBufferSubData(src, dst, -8L, 0L, 8L));
        } finally {
            legacy.makeInactive();
        }
    }

    // Checks that null copy buffers are refused without a GL call
    @Test
    void copyRejectsNullBuffersWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        nullRefusedWithoutGl("Source buffer must not be null", () -> list.copyBufferSubData(null, buffer, 0L, 0L, 4L));
        nullRefusedWithoutGl("Destination buffer must not be null", () -> list.copyBufferSubData(buffer, null, 0L, 0L, 4L));
        nullRefusedWithoutGl("Source buffer must not be null", () -> list.copyBufferSubData(null, null, -1L, -1L, -1L));
    }

    // Checks that a deleted copy buffer is refused before the copy
    @Test
    void copyRejectsADeletedBufferBeforeTheCopy() {
        CommandList list = list();
        GlMutableBuffer live = list.createMutableBuffer();
        GlMutableBuffer deleted = list.createMutableBuffer();
        list.deleteBuffer(deleted);
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.copyBufferSubData(deleted, live, 0L, 0L, 4L));
        assertThrows(IllegalStateException.class, () -> list.copyBufferSubData(live, deleted, 0L, 0L, 4L));
        Mockito.verify(gl, Mockito.never()).glCopyBufferSubData(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong());
    }

    // Checks that every binding point binds a buffer once and unbinds it once for null
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void bindBufferBindsOnceAndUnbindsOnceForNull(GlBufferTarget target) {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        Mockito.clearInvocations(gl);
        list.bindBuffer(target, buffer);
        list.bindBuffer(target, buffer);
        list.bindBuffer(target, null);
        list.bindBuffer(target, null);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glBindBuffer(target.getTargetParameter(), buffer.handle());
        order.verify(gl).glBindBuffer(target.getTargetParameter(), 0);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that a bind on one binding point keeps the bindings cached on the others
    @Test
    void bindingsOnOneTargetLeaveOtherTargetsCached() {
        CommandList list = list();
        GlMutableBuffer first = list.createMutableBuffer();
        GlMutableBuffer second = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, first);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, first);
        list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, second);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, first);
        Mockito.verify(gl).glBindBuffer(ELEMENT, first.handle());
        Mockito.verify(gl).glBindBuffer(ARRAY, second.handle());
    }

    // Checks that a null binding point is refused without a GL call
    @Test
    void bindBufferRejectsANullTargetWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        nullRefusedWithoutGl("Target must not be null", () -> list.bindBuffer(null, buffer));
        nullRefusedWithoutGl("Target must not be null", () -> list.bindBuffer(null, null));
    }

    // Checks that a deleted buffer is refused without a GL call
    @Test
    void bindBufferRejectsADeletedBufferWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        buffer.delete();
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer));
    }

    // Checks that buffer names at the int limits reach GL unchanged
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void bindBufferForwardsNamesAtTheIntLimits(int name) {
        CommandList list = list();
        Mockito.doReturn(name).when(gl).glGenBuffers();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer);
        Mockito.verify(gl).glBindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER.getTargetParameter(), name);
    }

    // Checks that a deletion deletes the name once and forgets every binding of it
    @Test
    void deleteBufferDeletesTheNameAndForgetsItsBindings() {
        CommandList list = list();
        Mockito.doReturn(7).when(gl).glGenBuffers();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        list.bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, buffer);
        list.deleteBuffer(buffer);
        Mockito.verify(gl).glDeleteBuffers(7);
        assertThrows(IllegalStateException.class, buffer::handle);
        GlMutableBuffer reused = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, reused);
        list.bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, reused);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ELEMENT, 7);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(COPY_WRITE, 7);
    }

    // Checks that a deletion keeps the cached bindings of other buffers
    @Test
    void deleteBufferKeepsTheBindingsOfOtherBuffers() {
        CommandList list = list();
        GlMutableBuffer kept = list.createMutableBuffer();
        GlMutableBuffer deleted = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, kept);
        list.deleteBuffer(deleted);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, kept);
        Mockito.verify(gl).glBindBuffer(ELEMENT, kept.handle());
    }

    // Checks that a deletion unmaps a mapped buffer before it deletes the name
    @Test
    void deleteBufferUnmapsAMappedBufferFirst() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        GlImmutableBuffer buffer = (GlImmutableBuffer) mapping.getBufferObject();
        int name = buffer.handle();
        list.deleteBuffer(buffer);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glUnmapBuffer(ARRAY);
        order.verify(gl).glDeleteBuffers(name);
        assertTrue(mapping.isDisposed());
        assertNull(buffer.getActiveMapping());
    }

    // Checks that a deletion unmaps an open mapping that was disposed directly
    @Test
    void deleteBufferUnmapsAMappingThatWasDisposedDirectly() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        GlImmutableBuffer buffer = (GlImmutableBuffer) mapping.getBufferObject();
        int name = buffer.handle();
        mapping.dispose();
        list.deleteBuffer(buffer);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glUnmapBuffer(ARRAY);
        order.verify(gl).glDeleteBuffers(name);
        assertThrows(IllegalStateException.class, buffer::handle);
    }

    // Checks that a failed unmap keeps the buffer and its mapping and a retry deletes it
    @Test
    void deleteBufferKeepsTheBufferWhenTheUnmapThrows() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        GlImmutableBuffer buffer = (GlImmutableBuffer) mapping.getBufferObject();
        IllegalStateException failure = new IllegalStateException("unmap");
        Mockito.doThrow(failure).doNothing().when(gl).glUnmapBuffer(ARRAY);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.deleteBuffer(buffer)));
        assertSame(mapping, buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());
        Mockito.verify(gl, Mockito.never()).glDeleteBuffers(Mockito.anyInt());
        list.deleteBuffer(buffer);
        assertTrue(mapping.isDisposed());
        assertThrows(IllegalStateException.class, buffer::handle);
    }

    // Checks that a null or deleted buffer is refused without a GL call
    @Test
    void deleteBufferRejectsANullOrDeletedBufferWithoutAGlCall() {
        CommandList list = list();
        nullRefusedWithoutGl("Buffer must not be null", () -> list.deleteBuffer(null));
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.deleteBuffer(buffer);
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.deleteBuffer(buffer));
    }

    // Checks that a vertex array deletion deletes the name once and forgets the binding
    @Test
    void deleteVertexArrayDeletesTheNameAndForgetsTheBinding() {
        CommandList list = list();
        Mockito.doReturn(9).when(gl).glGenVertexArrays();
        GlVertexArray array = new GlVertexArray();
        list.bindVertexArray(array);
        list.deleteVertexArray(array);
        Mockito.verify(gl).glDeleteVertexArrays(9);
        assertThrows(IllegalStateException.class, array::handle);
        list.bindVertexArray(new GlVertexArray());
        Mockito.verify(gl, Mockito.times(2)).glBindVertexArray(9);
    }

    // Checks that deleting an unbound vertex array keeps the cached binding
    @Test
    void deletingAnUnboundVertexArrayKeepsTheCachedBinding() {
        CommandList list = list();
        GlVertexArray bound = new GlVertexArray();
        GlVertexArray other = new GlVertexArray();
        list.bindVertexArray(bound);
        list.deleteVertexArray(other);
        list.bindVertexArray(bound);
        Mockito.verify(gl).glBindVertexArray(bound.handle());
    }

    // Checks that a null or deleted vertex array is refused without a GL call
    @Test
    void deleteVertexArrayRejectsANullOrDeletedArrayWithoutAGlCall() {
        CommandList list = list();
        nullRefusedWithoutGl("Vertex array must not be null", () -> list.deleteVertexArray(null));
        GlVertexArray array = new GlVertexArray();
        list.deleteVertexArray(array);
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.deleteVertexArray(array));
    }

    // Checks that flush and close on the command list issue no GL call and leave the tessellation bound
    @Test
    void commandListFlushIssuesNoGlCall() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        list.beginTessellating(tessellation);
        Mockito.clearInvocations(gl);
        list.flush();
        list.flush();
        list.close();
        Mockito.verifyNoInteractions(gl);
        Mockito.verify(tessellation, Mockito.never()).unbind(Mockito.any());
    }

    // Checks that a tessellation is bound through the command list and gets the shared draw command list
    @Test
    void beginTessellatingBindsAndReturnsTheSharedDrawList() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        Mockito.verify(tessellation).bind(list);
        draw.close();
        assertSame(draw, list.beginTessellating(tessellation));
        draw.close();
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }

    // Checks that a null tessellation is refused and keeps the tessellation in progress
    @Test
    void beginTessellatingRejectsNullAndKeepsTheTessellationInProgress() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        nullRefusedWithoutGl("Tessellation must not be null", () -> list.beginTessellating(null));
        draw.flush();
        Mockito.verify(tessellation).unbind(list);
    }

    // Checks that a new tessellation ends a different tessellation in progress before it binds
    @Test
    void beginTessellatingEndsADifferentTessellationFirst() {
        CommandList list = list();
        GlTessellation first = Mockito.mock(GlTessellation.class);
        GlTessellation second = Mockito.mock(GlTessellation.class);
        list.beginTessellating(first);
        DrawCommandList draw = list.beginTessellating(second);
        draw.close();
        InOrder order = Mockito.inOrder(first, second);
        order.verify(first).bind(list);
        order.verify(first).unbind(list);
        order.verify(second).bind(list);
        order.verify(second).unbind(list);
        Mockito.verifyNoMoreInteractions(first, second);
    }

    // Checks that beginning the tessellation in progress again binds it again without an unbind
    @Test
    void beginTessellatingTheSameTessellationAgainBindsWithoutUnbinding() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        list.beginTessellating(tessellation);
        DrawCommandList draw = list.beginTessellating(tessellation);
        Mockito.verify(tessellation, Mockito.times(2)).bind(list);
        Mockito.verify(tessellation, Mockito.never()).unbind(Mockito.any());
        draw.close();
        Mockito.verify(tessellation).unbind(list);
    }

    // Checks that a failed unbind of the previous tessellation keeps it in progress and leaves the new one unbound
    @Test
    void failedUnbindOfThePreviousTessellationKeepsItInProgress() {
        CommandList list = list();
        GlTessellation first = Mockito.mock(GlTessellation.class);
        GlTessellation second = Mockito.mock(GlTessellation.class);
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(first).unbind(list);
        DrawCommandList draw = list.beginTessellating(first);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.beginTessellating(second)));
        Mockito.verifyNoInteractions(second);
        draw.flush();
        Mockito.verify(first, Mockito.times(2)).unbind(list);
    }

    // Checks that a failed bind keeps the tessellation in progress for the next flush
    @Test
    void failedBindKeepsTheTessellationInProgress() {
        CommandList list = list();
        GlTessellation other = Mockito.mock(GlTessellation.class);
        GlTessellation failing = Mockito.mock(GlTessellation.class);
        IllegalStateException failure = new IllegalStateException("bind");
        Mockito.doThrow(failure).when(failing).bind(list);
        DrawCommandList draw = list.beginTessellating(other);
        draw.close();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.beginTessellating(failing)));
        draw.flush();
        Mockito.verify(failing).unbind(list);
    }

    // Checks that deleting a tessellation outside a draw deletes it through the command list only
    @Test
    void deleteTessellationDeletesThroughTheCommandList() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        list.deleteTessellation(tessellation);
        list.deleteTessellation(tessellation);
        Mockito.verify(tessellation, Mockito.times(2)).delete(list);
        Mockito.verifyNoMoreInteractions(tessellation);
    }

    // Checks that deleting the tessellation in progress ends it before the deletion
    @Test
    void deleteTessellationEndsTheTessellationInProgressFirst() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        list.deleteTessellation(tessellation);
        draw.flush();
        InOrder order = Mockito.inOrder(tessellation);
        order.verify(tessellation).bind(list);
        order.verify(tessellation).unbind(list);
        order.verify(tessellation).delete(list);
        Mockito.verifyNoMoreInteractions(tessellation);
        refusedWithoutGl(IllegalStateException.class, NO_TESSELLATION, () -> draw.multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
    }

    // Checks that a failed unbind keeps the tessellation in progress and skips the deletion
    @Test
    void deleteTessellationKeepsTheTessellationWhenItsUnbindThrows() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(tessellation).unbind(list);
        DrawCommandList draw = list.beginTessellating(tessellation);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.deleteTessellation(tessellation)));
        Mockito.verify(tessellation, Mockito.never()).delete(Mockito.any());
        draw.flush();
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }

    // Checks that deleting another tessellation keeps the tessellation in progress
    @Test
    void deleteTessellationOfAnotherKeepsTheTessellationInProgress() {
        CommandList list = list();
        GlTessellation active = Mockito.mock(GlTessellation.class);
        GlTessellation other = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(active);
        list.deleteTessellation(other);
        Mockito.verify(active, Mockito.never()).unbind(Mockito.any());
        draw.flush();
        Mockito.verify(active).unbind(list);
        Mockito.verify(other).delete(list);
        Mockito.verifyNoMoreInteractions(other);
    }

    // Checks that a null tessellation deletion is refused without a GL call
    @Test
    void deleteTessellationRejectsNull() {
        CommandList list = list();
        nullRefusedWithoutGl("Tessellation must not be null", () -> list.deleteTessellation(null));
    }

    // Checks that a mapping binds the array buffer and forwards the range and flags and records the mapping
    @Test
    void mapBufferBindsTheArrayBufferAndRecordsTheMapping() {
        CommandList list = list();
        GlImmutableBuffer buffer = list.createImmutableBuffer(64L, writableStorage());
        EnumBitField<GlBufferMapFlags> flags = writableMap();
        ByteBuffer memory = ByteBuffer.allocateDirect(48);
        Mockito.doReturn(memory).when(gl).glMapBufferRange(ARRAY, 16L, 48L, flags.getBitField());
        GlBufferMapping mapping = list.mapBuffer(buffer, 16L, 48L, flags);
        assertSame(memory, mapping.getMemoryBuffer());
        assertSame(buffer, mapping.getBufferObject());
        assertSame(mapping, buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());
        Mockito.verify(gl).glBindBuffer(ARRAY, buffer.handle());
    }

    // Checks every subset of READ WRITE PERSISTENT against a mutable buffer and every storage subset
    @Test
    void mapBufferChecksEveryFlagSubsetAgainstEveryStorageSubset() {
        CommandList list = list();
        for (int map = 0; map < 8; map++) {
            String mutableRefusal = (map & 4) != 0 ? "Tried to map mutable buffer as persistent" : null;
            GlMutableBuffer mutable = list.createMutableBuffer();
            list.allocateStorage(mutable, 8L, GlBufferUsage.STATIC_DRAW);
            checkMapRequest(list, mutable, map, mutableRefusal, IllegalStateException.class);
            for (int storage = 0; storage < 8; storage++) {
                String refusal = null;
                Class<? extends RuntimeException> type = IllegalStateException.class;
                if ((map & 4) != 0 && (storage & 4) == 0) {
                    refusal = "Tried to map non-persistent buffer as persistent";
                    type = IllegalArgumentException.class;
                } else if ((map & 2) != 0 && (storage & 2) == 0) {
                    refusal = "Tried to map non-writable buffer as writable";
                } else if ((map & 1) != 0 && (storage & 1) == 0) {
                    refusal = "Tried to map non-readable buffer as readable";
                }
                checkMapRequest(list, list.createImmutableBuffer(8L, storageFlags(storage)), map, refusal, type);
            }
        }
    }

    // Asserts that the map request is refused with the message and no GL call or succeeds when the message is null
    private void checkMapRequest(CommandList list, GlBuffer buffer, int map,
                                 String refusal, Class<? extends RuntimeException> type) {
        if (refusal != null) {
            refusedWithoutGl(type, refusal, () -> list.mapBuffer(buffer, 0L, 8L, mapFlags(map)));
            assertNull(buffer.getActiveMapping());
        } else {
            list.unmap(list.mapBuffer(buffer, 0L, 8L, mapFlags(map)));
        }
    }

    // Checks that a mapped buffer is refused a second mapping without a GL call
    @Test
    void mapBufferRejectsAMappedBufferWithoutAGlCall() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        refusedWithoutGl(IllegalStateException.class, "Buffer is already mapped", () -> list.mapBuffer(mapping.getBufferObject(), 0L, 8L, writableMap()));
        refusedWithoutGl(IllegalStateException.class, "Buffer is already mapped", () -> list.mapBuffer(mapping.getBufferObject(), -1L, -1L, mapFlags(4)));
        assertSame(mapping, mapping.getBufferObject().getActiveMapping());
    }

    // Checks that null map arguments are refused without a GL call
    @Test
    void mapBufferRejectsNullArgumentsWithoutAGlCall() {
        CommandList list = list();
        GlMutableBuffer buffer = list.createMutableBuffer();
        nullRefusedWithoutGl("Buffer must not be null", () -> list.mapBuffer(null, 0L, 8L, writableMap()));
        nullRefusedWithoutGl("Flags must not be null", () -> list.mapBuffer(buffer, 0L, 8L, null));
    }

    // Returns map ranges that every mapping refuses with the message
    static Stream<Arguments> refusedMapRanges() {
        return Stream.of(
                Arguments.of(-1L, 8L, "Offset must not be negative: -1"),
                Arguments.of(Long.MIN_VALUE, 8L, "Offset must not be negative: " + Long.MIN_VALUE),
                Arguments.of(0L, -1L, "Length must not be negative: -1"),
                Arguments.of(0L, Long.MIN_VALUE, "Length must not be negative: " + Long.MIN_VALUE),
                Arguments.of(0L, 1L + Integer.MAX_VALUE, "Length must not exceed 2147483647: 2147483648"),
                Arguments.of(0L, Long.MAX_VALUE, "Length must not exceed 2147483647: " + Long.MAX_VALUE),
                Arguments.of(Long.MAX_VALUE, 1L, "Map range overflows: " + Long.MAX_VALUE + " + 1"),
                Arguments.of(Long.MAX_VALUE - 7L, 8L, "Map range overflows: " + (Long.MAX_VALUE - 7L) + " + 8"));
    }

    // Checks that a negative or overflowing or oversized map range is refused without a GL call
    @ParameterizedTest
    @MethodSource("refusedMapRanges")
    void mapBufferRejectsInvalidRangesWithoutAGlCall(long offset, long length, String message) {
        CommandList list = list();
        GlImmutableBuffer buffer = list.createImmutableBuffer(64L, writableStorage());
        refusedWithoutGl(IllegalArgumentException.class, message, () -> list.mapBuffer(buffer, offset, length, writableMap()));
        assertNull(buffer.getActiveMapping());
    }

    // Checks that map ranges at the limits and misaligned ranges reach GL unchanged
    @ParameterizedTest
    @MethodSource("acceptedMapRanges")
    void mapBufferForwardsRangesAtTheLimits(long offset, long length) {
        CommandList list = list();
        GlImmutableBuffer buffer = list.createImmutableBuffer(64L, writableStorage());
        ByteBuffer memory = ByteBuffer.allocateDirect(8);
        Mockito.doReturn(memory).when(gl).glMapBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        GlBufferMapping mapping = list.mapBuffer(buffer, offset, length, writableMap());
        Mockito.verify(gl).glMapBufferRange(ARRAY, offset, length, writableMap().getBitField());
        assertSame(memory, mapping.getMemoryBuffer());
    }

    // Returns map ranges that every mapping forwards to GL
    static Stream<Arguments> acceptedMapRanges() {
        return Stream.of(
                Arguments.of(0L, 0L),
                Arguments.of(3L, 5L),
                Arguments.of(Long.MAX_VALUE - 8L, 8L),
                Arguments.of(Long.MAX_VALUE, 0L),
                Arguments.of(0L, (long) Integer.MAX_VALUE),
                Arguments.of(Long.MAX_VALUE - Integer.MAX_VALUE, (long) Integer.MAX_VALUE));
    }

    // Checks that a null memory buffer from GL is a failure that leaves the buffer unmapped
    @Test
    void mapBufferReportsANullMappingAsAFailureAndStaysUnmapped() {
        CommandList list = list();
        GlImmutableBuffer buffer = list.createImmutableBuffer(64L, writableStorage());
        Mockito.doReturn(null).when(gl).glMapBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        assertEquals("Failed to map buffer", assertThrows(RuntimeException.class, () -> list.mapBuffer(buffer, 0L, 0L, writableMap())).getMessage());
        assertNull(buffer.getActiveMapping());
        Mockito.reset(gl);
        assertNotNull(list.mapBuffer(buffer, 0L, 64L, writableMap()));
    }

    // Checks that a deleted buffer is refused a mapping without a GL call
    @Test
    void mapBufferRejectsADeletedBufferWithoutAGlCall() {
        CommandList list = list();
        GlImmutableBuffer buffer = list.createImmutableBuffer(64L, writableStorage());
        list.deleteBuffer(buffer);
        refusedWithoutGl(IllegalStateException.class, "Handle is not valid", () -> list.mapBuffer(buffer, 0L, 8L, writableMap()));
    }

    // Checks that the fallback map slices the full mapping to the range
    @Test
    void legacyMapBufferSlicesTheFullMapping() {
        GLRenderDevice legacy = legacyDevice();
        try {
            legacy.makeActive();
            CommandList list = legacy.createCommandList();
            GlImmutableBuffer buffer = list.createImmutableBuffer(64L, EnumBitField.of(GlBufferStorageFlags.MAP_WRITE));
            GlBufferMapping mapping = list.mapBuffer(buffer, 8L, 16L, EnumBitField.of(GlBufferMapFlags.WRITE));
            assertEquals(16, mapping.getMemoryBuffer().capacity());
            list.unmap(mapping);
            assertTrue(mapping.isDisposed());
        } finally {
            legacy.makeInactive();
        }
    }

    // Checks that an unmap binds the array buffer and unmaps it and clears and disposes the mapping
    @Test
    void unmapBindsUnmapsClearsAndDisposes() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        GlImmutableBuffer buffer = (GlImmutableBuffer) mapping.getBufferObject();
        list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        Mockito.clearInvocations(gl);
        list.unmap(mapping);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glBindBuffer(ARRAY, buffer.handle());
        order.verify(gl).glUnmapBuffer(ARRAY);
        Mockito.verifyNoMoreInteractions(gl);
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        assertNotSame(mapping, list.mapBuffer(buffer, 0L, 8L, writableMap()));
    }

    // Checks that a disposed mapping is refused an unmap without a GL call
    @Test
    void unmapRejectsADisposedMappingWithoutAGlCall() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        list.unmap(mapping);
        refusedWithoutGl(IllegalStateException.class, "Buffer mapping is already disposed", () -> list.unmap(mapping));
        GlBufferMapping deleted = openMapping(list);
        list.deleteBuffer(deleted.getBufferObject());
        refusedWithoutGl(IllegalStateException.class, "Buffer mapping is already disposed", () -> list.unmap(deleted));
        GlBufferMapping disposedDirectly = openMapping(list);
        disposedDirectly.dispose();
        refusedWithoutGl(IllegalStateException.class, "Buffer mapping is already disposed", () -> list.unmap(disposedDirectly));
    }

    // Checks that a mapping that is not open on its buffer is refused an unmap without a GL call
    @Test
    void unmapRejectsAMappingThatIsNotOpenWithoutAGlCall() {
        CommandList list = list();
        GlImmutableBuffer unmapped = list.createImmutableBuffer(8L, writableStorage());
        GlBufferMapping stray = new GlBufferMapping(unmapped, ByteBuffer.allocateDirect(8));
        refusedWithoutGl(IllegalStateException.class, "Mapping is not the open mapping of its buffer", () -> list.unmap(stray));
        GlBufferMapping open = openMapping(list);
        GlBufferMapping second = new GlBufferMapping(open.getBufferObject(), ByteBuffer.allocateDirect(8));
        refusedWithoutGl(IllegalStateException.class, "Mapping is not the open mapping of its buffer", () -> list.unmap(second));
        assertSame(open, open.getBufferObject().getActiveMapping());
        assertFalse(open.isDisposed());
    }

    // Checks that a null mapping is refused an unmap without a GL call
    @Test
    void unmapRejectsNullWithoutAGlCall() {
        CommandList list = list();
        nullRefusedWithoutGl("Mapping must not be null", () -> list.unmap(null));
    }

    // Checks that a failed unmap keeps the mapping open and a retry unmaps it
    @Test
    void unmapKeepsTheMappingOpenWhenTheUnmapThrows() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        IllegalStateException failure = new IllegalStateException("unmap");
        Mockito.doThrow(failure).doNothing().when(gl).glUnmapBuffer(ARRAY);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.unmap(mapping)));
        assertSame(mapping, mapping.getBufferObject().getActiveMapping());
        assertFalse(mapping.isDisposed());
        list.unmap(mapping);
        assertTrue(mapping.isDisposed());
    }

    // Returns flush ranges within a mapping of MAPPING_BYTES bytes
    static Stream<Arguments> acceptedFlushes() {
        return Stream.of(
                Arguments.of(0, 0),
                Arguments.of(0, MAPPING_BYTES),
                Arguments.of(MAPPING_BYTES - 1, 1),
                Arguments.of(MAPPING_BYTES, 0),
                Arguments.of(3, 5),
                Arguments.of(32, 32));
    }

    // Checks that a flush binds the copy read binding point and forwards the range unchanged
    @ParameterizedTest
    @MethodSource("acceptedFlushes")
    void flushMappedRangeBindsCopyReadAndForwardsTheRange(int offset, int length) {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        Mockito.clearInvocations(gl);
        list.flushMappedRange(mapping, offset, length);
        list.flushMappedRange(mapping, offset, length);
        Mockito.verify(gl).glBindBuffer(COPY_READ, mapping.getBufferObject().handle());
        Mockito.verify(gl, Mockito.times(2)).glFlushMappedBufferRange(COPY_READ, offset, length);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Returns flush ranges that a mapping of MAPPING_BYTES bytes refuses with the message
    static Stream<Arguments> refusedFlushes() {
        String past = "Flush range exceeds the mapping of " + MAPPING_BYTES + " bytes: ";
        return Stream.of(
                Arguments.of(-1, 1, "Offset must not be negative: -1"),
                Arguments.of(Integer.MIN_VALUE, 0, "Offset must not be negative: " + Integer.MIN_VALUE),
                Arguments.of(0, -1, "Length must not be negative: -1"),
                Arguments.of(0, Integer.MIN_VALUE, "Length must not be negative: " + Integer.MIN_VALUE),
                Arguments.of(MAPPING_BYTES - 4, 5, past + (MAPPING_BYTES - 4) + " + 5"),
                Arguments.of(MAPPING_BYTES + 1, 0, past + (MAPPING_BYTES + 1) + " + 0"),
                Arguments.of(0, MAPPING_BYTES + 1, past + "0 + " + (MAPPING_BYTES + 1)),
                Arguments.of(Integer.MAX_VALUE, 1, past + Integer.MAX_VALUE + " + 1"),
                Arguments.of(1, Integer.MAX_VALUE, past + "1 + " + Integer.MAX_VALUE),
                Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE, past + Integer.MAX_VALUE + " + " + Integer.MAX_VALUE));
    }

    // Checks that a flush range outside the mapping is refused without a GL call
    @ParameterizedTest
    @MethodSource("refusedFlushes")
    void flushMappedRangeRejectsRangesOutsideTheMappingWithoutAGlCall(int offset, int length, String message) {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        refusedWithoutGl(IllegalArgumentException.class, message, () -> list.flushMappedRange(mapping, offset, length));
    }

    // Checks that a mapping of the int limit accepts flushes up to its end and refuses one past it
    @Test
    void flushMappedRangeAcceptsTheIntLimitOfALargeMapping() {
        CommandList list = list();
        GlImmutableBuffer buffer = list.createImmutableBuffer(1L + Integer.MAX_VALUE, writableStorage());
        Mockito.doReturn(hugeView(Integer.MAX_VALUE)).when(gl).glMapBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        GlBufferMapping mapping = list.mapBuffer(buffer, 1L, Integer.MAX_VALUE, writableMap());
        list.flushMappedRange(mapping, Integer.MAX_VALUE - 1, 1);
        list.flushMappedRange(mapping, 0, Integer.MAX_VALUE);
        list.flushMappedRange(mapping, Integer.MAX_VALUE, 0);
        Mockito.verify(gl).glFlushMappedBufferRange(COPY_READ, Integer.MAX_VALUE - 1, 1);
        Mockito.verify(gl).glFlushMappedBufferRange(COPY_READ, 0, Integer.MAX_VALUE);
        Mockito.verify(gl).glFlushMappedBufferRange(COPY_READ, Integer.MAX_VALUE, 0);
        assertThrows(IllegalArgumentException.class, () -> list.flushMappedRange(mapping, Integer.MAX_VALUE, 1));
    }

    // Checks that a disposed or stray mapping is refused a flush without a GL call
    @Test
    void flushMappedRangeRejectsADisposedOrStrayMappingWithoutAGlCall() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        list.unmap(mapping);
        refusedWithoutGl(IllegalStateException.class, "Buffer mapping is already disposed", () -> list.flushMappedRange(mapping, 0, 1));
        refusedWithoutGl(IllegalStateException.class, "Buffer mapping is already disposed", () -> list.flushMappedRange(mapping, -1, -1));
        GlBufferMapping stray = new GlBufferMapping(mapping.getBufferObject(), ByteBuffer.allocateDirect(8));
        refusedWithoutGl(IllegalStateException.class, "Mapping is not the open mapping of its buffer", () -> list.flushMappedRange(stray, 0, 1));
        nullRefusedWithoutGl("Mapping must not be null", () -> list.flushMappedRange(null, 0, 1));
    }

    // Checks that a flush leaves the cached array buffer binding of the mapping
    @Test
    void flushMappedRangeLeavesTheArrayBufferBinding() {
        CommandList list = list();
        GlBufferMapping mapping = openMapping(list);
        Mockito.clearInvocations(gl);
        list.flushMappedRange(mapping, 0, 8);
        list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, mapping.getBufferObject());
        Mockito.verify(gl, Mockito.never()).glBindBuffer(Mockito.eq(ARRAY), Mockito.anyInt());
    }

    // Checks that a fence wraps the sync object that GL creates for every prior command
    @ParameterizedTest
    @ValueSource(longs = {1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE})
    void createFenceForwardsTheSyncObject(long sync) {
        CommandList list = list();
        Mockito.doReturn(sync).when(gl).glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        GlFence fence = list.createFence();
        fence.delete();
        Mockito.verify(gl).glDeleteSync(sync);
    }

    // Checks that repeated fences wrap distinct sync objects
    @Test
    void repeatedFencesWrapDistinctSyncObjects() {
        CommandList list = list();
        list.createFence().delete();
        list.createFence().delete();
        Mockito.verify(gl, Mockito.times(2)).glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        Mockito.verify(gl).glDeleteSync(1L);
        Mockito.verify(gl).glDeleteSync(2L);
    }

    // Checks that a zero sync object is refused
    @Test
    void createFenceRejectsAZeroSyncObject() {
        CommandList list = list();
        Mockito.doReturn(0L).when(gl).glFenceSync(Mockito.anyInt(), Mockito.anyInt());
        assertEquals("Failed to create a fence", assertThrows(IllegalStateException.class, list::createFence).getMessage());
    }

    // Checks that a mutable buffer gets a fresh name and no storage and no binding
    @Test
    void createMutableBufferGeneratesAFreshUnmappedBufferWithoutBinding() {
        CommandList list = list();
        GlMutableBuffer first = list.createMutableBuffer();
        GlMutableBuffer second = list.createMutableBuffer();
        assertNotEquals(first.handle(), second.handle());
        assertEquals(0L, first.getSize());
        assertNull(first.getActiveMapping());
        Mockito.verify(gl, Mockito.times(2)).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that a zero buffer name from GL is refused
    @Test
    void createMutableBufferRejectsAZeroName() {
        CommandList list = list();
        Mockito.doReturn(0).when(gl).glGenBuffers();
        assertEquals("Failed to generate a buffer", assertThrows(IllegalStateException.class, list::createMutableBuffer).getMessage());
    }

    // Checks that an immutable buffer binds once and allocates its storage of the size
    @ParameterizedTest
    @ValueSource(longs = {1L, 3L, Integer.MAX_VALUE, 1L + Integer.MAX_VALUE, Long.MAX_VALUE})
    void createImmutableBufferBindsAndAllocatesTheStorage(long bytes) {
        CommandList list = list();
        EnumBitField<GlBufferStorageFlags> flags = writableStorage();
        GlImmutableBuffer buffer = list.createImmutableBuffer(bytes, flags);
        assertSame(flags, buffer.getFlags());
        assertNull(buffer.getActiveMapping());
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glGenBuffers();
        order.verify(gl).glBindBuffer(ARRAY, buffer.handle());
        order.verify(gl).glBufferStorage(ARRAY, bytes, flags.getBitField());
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that every nonempty storage flag subset reaches GL as the OR of its GL bits
    @Test
    void createImmutableBufferForwardsEveryFlagSubset() {
        CommandList list = list();
        GlBufferStorageFlags[] all = GlBufferStorageFlags.values();
        for (int mask = 1; mask < 1 << all.length; mask++) {
            List<GlBufferStorageFlags> subset = new ArrayList<>();
            int bits = 0;
            for (GlBufferStorageFlags flag : all) {
                if ((mask & (1 << flag.ordinal())) != 0) {
                    subset.add(flag);
                    bits |= flag.getBits();
                }
            }
            list.createImmutableBuffer(8L, EnumBitField.of(subset.toArray(GlBufferStorageFlags[]::new)));
            Mockito.verify(gl).glBufferStorage(ARRAY, 8L, bits);
            Mockito.clearInvocations(gl);
        }
    }

    // Checks that a size below one is refused before a buffer name is generated
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Integer.MIN_VALUE, Long.MIN_VALUE})
    void createImmutableBufferRejectsASizeBelowOneWithoutAGlCall(long bytes) {
        CommandList list = list();
        refusedWithoutGl(IllegalArgumentException.class, "Size must be positive: " + bytes, () -> list.createImmutableBuffer(bytes, writableStorage()));
    }

    // Checks that null flags are refused before a buffer name is generated
    @Test
    void createImmutableBufferRejectsNullFlagsWithoutAGlCall() {
        CommandList list = list();
        nullRefusedWithoutGl("Flags must not be null", () -> list.createImmutableBuffer(8L, null));
        nullRefusedWithoutGl("Flags must not be null", () -> list.createImmutableBuffer(0L, null));
    }

    // Checks that a failed storage call deletes the new buffer and forgets its binding and rethrows
    @Test
    void createImmutableBufferDeletesTheBufferWhenTheStorageCallThrows() {
        CommandList list = list();
        IllegalStateException failure = new IllegalStateException("storage");
        Mockito.doReturn(5).when(gl).glGenBuffers();
        Mockito.doThrow(failure).when(gl).glBufferStorage(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.createImmutableBuffer(8L, writableStorage())));
        assertEquals(0, failure.getSuppressed().length);
        Mockito.verify(gl).glDeleteBuffers(5);
        list.bindBuffer(GlBufferTarget.ARRAY_BUFFER, list.createMutableBuffer());
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ARRAY, 5);
    }

    // Checks that a failed deletion after a failed storage call is suppressed behind the storage failure
    @Test
    void createImmutableBufferKeepsTheStorageFailureWhenTheDeletionThrows() {
        CommandList list = list();
        IllegalStateException failure = new IllegalStateException("storage");
        IllegalArgumentException deleteFailure = new IllegalArgumentException("delete");
        Mockito.doThrow(failure).when(gl).glBufferStorage(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt());
        Mockito.doThrow(deleteFailure).when(gl).glDeleteBuffers(Mockito.anyInt());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.createImmutableBuffer(8L, writableStorage())));
        assertArrayEquals(new Throwable[]{deleteFailure}, failure.getSuppressed());
    }

    // Checks that a deletion that rethrows the storage failure adds no suppressed exception to it
    @Test
    void createImmutableBufferDoesNotSuppressTheStorageFailureInItself() {
        CommandList list = list();
        IllegalStateException failure = new IllegalStateException("storage");
        Mockito.doThrow(failure).when(gl).glBufferStorage(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt());
        Mockito.doThrow(failure).when(gl).glDeleteBuffers(Mockito.anyInt());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> list.createImmutableBuffer(8L, writableStorage())));
        assertEquals(0, failure.getSuppressed().length);
    }

    // Checks that a direct draw forwards the batch arrays and size inside a tessellation
    @Test
    void baseVertexDrawForwardsTheBatchArrays() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(4);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            batch.appendDrawCommand(0, 6, 0L);
            batch.appendDrawCommand(4, 12, 24L);
            draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            Mockito.verify(gl).glMultiDrawElementsBaseVertex(GlPrimitiveType.TRIANGLES.getId(), batch.pElementCount,
                    GlIndexType.UNSIGNED_INT.getFormatId(), batch.pElementPointer, 2, batch.pBaseVertex);
        } finally {
            batch.delete();
        }
    }

    // Checks that a deleted batch is refused a draw without a GL call
    @Test
    void baseVertexDrawRejectsADeletedBatchWithoutAGlCall() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(2);
        batch.appendDrawCommand(0, 6, 0L);
        batch.delete();
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            refusedWithoutGl(IllegalStateException.class, "Batch is deleted", () -> draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
        }
    }

    // Checks that null direct draw arguments are refused without a GL call
    @Test
    void baseVertexDrawRejectsNullArgumentsWithoutAGlCall() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(1);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            nullRefusedWithoutGl("Batch must not be null", () -> draw.multiDrawElementsBaseVertex(null, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
            nullRefusedWithoutGl("Primitive type must not be null", () -> draw.multiDrawElementsBaseVertex(batch, null, GlIndexType.UNSIGNED_INT));
            nullRefusedWithoutGl("Index type must not be null", () -> draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, null));
        } finally {
            batch.delete();
        }
    }

    // Checks that both draws are refused after the tessellation ends without a GL call
    @Test
    void drawsRejectACallOutsideATessellationWithoutAGlCall() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(1);
        try {
            DrawCommandList draw = list.beginTessellating(tessellation);
            draw.close();
            refusedWithoutGl(IllegalStateException.class, NO_TESSELLATION, () -> draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
            refusedWithoutGl(IllegalStateException.class, NO_TESSELLATION, () -> draw.multiDrawElementsIndirect(0L, 1, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
            list.beginTessellating(tessellation).endTessellating();
            refusedWithoutGl(IllegalStateException.class, NO_TESSELLATION, () -> draw.multiDrawElementsIndirect(4L, 1, GlPrimitiveType.LINES, GlIndexType.UNSIGNED_BYTE));
        } finally {
            batch.delete();
        }
    }

    // Returns aligned indirect offsets and counts at zero and the int and long limits
    static Stream<Arguments> acceptedIndirectDraws() {
        long[] offsets = {0L, 4L, 8L, 20L, Integer.MAX_VALUE - 3L, 1L + Integer.MAX_VALUE, 1L << 32, Long.MAX_VALUE - 3L};
        int[] counts = {0, 1, 2, Integer.MAX_VALUE};
        List<Arguments> cases = new ArrayList<>();
        for (long offset : offsets) {
            for (int count : counts) {
                cases.add(Arguments.of(offset, count));
            }
        }
        return cases.stream();
    }

    // Checks that an aligned indirect draw forwards the offset and count unchanged
    @ParameterizedTest
    @MethodSource("acceptedIndirectDraws")
    void indirectDrawForwardsAlignedOffsetsAndCounts(long offset, int count) {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            draw.multiDrawElementsIndirect(offset, count, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_SHORT);
        }
        Mockito.verify(gl).glMultiDrawElementsIndirect(GlPrimitiveType.TRIANGLES.getId(), GlIndexType.UNSIGNED_SHORT.getFormatId(), offset, count, 0);
    }

    // Returns indirect offsets and counts that every indirect draw refuses with the message
    static Stream<Arguments> refusedIndirectDraws() {
        return Stream.of(
                Arguments.of(-4L, 1, "Offset must not be negative: -4"),
                Arguments.of(-1L, 1, "Offset must not be negative: -1"),
                Arguments.of(Long.MIN_VALUE, 1, "Offset must not be negative: " + Long.MIN_VALUE),
                Arguments.of(1L, 1, "Offset must be a multiple of 4: 1"),
                Arguments.of(2L, 1, "Offset must be a multiple of 4: 2"),
                Arguments.of(3L, 0, "Offset must be a multiple of 4: 3"),
                Arguments.of(5L, 1, "Offset must be a multiple of 4: 5"),
                Arguments.of((long) Integer.MAX_VALUE, 1, "Offset must be a multiple of 4: " + Integer.MAX_VALUE),
                Arguments.of(Long.MAX_VALUE, 1, "Offset must be a multiple of 4: " + Long.MAX_VALUE),
                Arguments.of(0L, -1, "Count must not be negative: -1"),
                Arguments.of(4L, Integer.MIN_VALUE, "Count must not be negative: " + Integer.MIN_VALUE));
    }

    // Checks that a negative count or a negative or misaligned offset is refused without a GL call
    @ParameterizedTest
    @MethodSource("refusedIndirectDraws")
    void indirectDrawRejectsNegativeOrMisalignedArgumentsWithoutAGlCall(long offset, int count, String message) {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            refusedWithoutGl(IllegalArgumentException.class, message, () -> draw.multiDrawElementsIndirect(offset, count, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT));
        }
    }

    // Checks that null indirect draw types are refused without a GL call
    @Test
    void indirectDrawRejectsNullTypesWithoutAGlCall() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        try (DrawCommandList draw = list.beginTessellating(tessellation)) {
            nullRefusedWithoutGl("Primitive type must not be null", () -> draw.multiDrawElementsIndirect(0L, 1, null, GlIndexType.UNSIGNED_INT));
            nullRefusedWithoutGl("Index type must not be null", () -> draw.multiDrawElementsIndirect(-1L, -1, GlPrimitiveType.TRIANGLES, null));
        }
    }

    // Checks that ending with no tessellation in progress is refused without a GL call
    @Test
    void endTessellatingRejectsACallWithNothingInProgressWithoutAGlCall() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        draw.endTessellating();
        refusedWithoutGl(IllegalStateException.class, NO_TESSELLATION, draw::endTessellating);
        Mockito.verify(tessellation).unbind(list);
    }

    // Checks that an end unbinds the tessellation once and a later flush unbinds nothing
    @Test
    void endTessellatingUnbindsOnceAndEnds() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        draw.endTessellating();
        draw.flush();
        draw.close();
        Mockito.verify(tessellation).bind(list);
        Mockito.verify(tessellation).unbind(list);
        Mockito.verifyNoMoreInteractions(tessellation);
    }

    // Checks that a failed unbind keeps the tessellation in progress and the next end unbinds it
    @Test
    void endTessellatingKeepsTheTessellationWhenItsUnbindThrows() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(tessellation).unbind(list);
        DrawCommandList draw = list.beginTessellating(tessellation);
        assertSame(failure, assertThrows(IllegalStateException.class, draw::endTessellating));
        draw.endTessellating();
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }

    // Checks that a draw flush with no tessellation in progress issues no call
    @Test
    void drawFlushWithNothingInProgressDoesNothing() {
        CommandList list = list();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        draw.flush();
        Mockito.clearInvocations(gl, tessellation);
        draw.flush();
        draw.flush();
        Mockito.verifyNoInteractions(gl, tessellation);
    }

    // Checks that an indirect batch uploads its commands once and draws them from its buffer
    @Test
    void indirectBatchesUploadTheirCommandsOnceAndDrawFromTheBuffer() {
        CommandList list = list();
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(3);
        batch.appendDrawCommand(100, 6, 24L);
        batch.appendDrawCommand(200, 0, 0L);
        batch.appendDrawCommand(300, 12, 0L);
        batch.mergeIntoLastCommand(6);
        assertEquals(2, batch.size());
        assertEquals(18, batch.getIndexBufferSize());
        // Reads the count and instance count and first index and base vertex and base instance of each command
        Mockito.doAnswer(inv -> {
            long commands = inv.getArgument(2);
            assertEquals(6, MemoryUtil.memGetInt(commands));
            assertEquals(1, MemoryUtil.memGetInt(commands + 4));
            assertEquals(6, MemoryUtil.memGetInt(commands + 8));
            assertEquals(100, MemoryUtil.memGetInt(commands + 12));
            assertEquals(18, MemoryUtil.memGetInt(commands + 20));
            assertEquals(300, MemoryUtil.memGetInt(commands + 32));
            return null;
        }).when(gl).glBufferData(Mockito.anyInt(), Mockito.eq(40L), Mockito.anyLong(), Mockito.eq(GlBufferUsage.STATIC_DRAW.getId()));
        batch.upload(list);
        Mockito.verify(gl).glBufferData(Mockito.anyInt(), Mockito.eq(40L), Mockito.anyLong(), Mockito.eq(GlBufferUsage.STATIC_DRAW.getId()));
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(gl).glMultiDrawElementsIndirect(GlPrimitiveType.TRIANGLES.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), 0L, 2, 0);
        Mockito.verify(gl).glBindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER.getTargetParameter(), 0);
        Mockito.verify(tessellation).unbind(list);
        batch.delete();
        IndirectMultiDrawBatch neverUploaded = new IndirectMultiDrawBatch(1);
        neverUploaded.delete();
        Mockito.verify(gl, Mockito.times(2)).glDeleteBuffers(Mockito.anyInt());
    }

    // Checks that a null bind after activation or after a vertex array change unbinds the binding point
    @Test
    void nullBindAfterAnUnknownBindingUnbinds() {
        CommandList list = list();
        int indirect = GlBufferTarget.DRAW_INDIRECT_BUFFER.getTargetParameter();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, null);
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer);
        list.bindVertexArray(new GlVertexArray());
        list.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
        list.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
        Mockito.verify(gl).glBindBuffer(ELEMENT, 0);
        Mockito.verify(gl).glBindBuffer(indirect, 0);
    }

    // Checks that an indirect batch drawn through a vertex array tessellation unbinds the indirect buffer afterwards
    @Test
    void indirectBatchThroughAVertexArrayTessellationUnbindsTheIndirectBuffer() {
        CommandList list = list();
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(1);
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), new TessellationBinding[0]);
        try {
            batch.appendDrawCommand(0, 6, 0L);
            batch.upload(list);
            batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
            InOrder order = Mockito.inOrder(gl);
            order.verify(gl).glMultiDrawElementsIndirect(GlPrimitiveType.TRIANGLES.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), 0L, 1, 0);
            order.verify(gl).glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
            order.verify(gl).glBindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER.getTargetParameter(), 0);
        } finally {
            batch.delete();
        }
    }

    // Checks that a failed command array allocation is reported as out of memory
    @Test
    void batchAllocationFailuresAreReported() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(1));
            assertThrows(OutOfMemoryError.class, () -> new IndirectMultiDrawBatch(1));
        }
    }

    // Checks that the map flags and the storage flags that the device compares share their GL bits
    @Test
    void mapAndStorageFlagsShareTheirGlBits() {
        assertEquals(GlBufferMapFlags.READ.getBits(), GlBufferStorageFlags.MAP_READ.getBits());
        assertEquals(GlBufferMapFlags.WRITE.getBits(), GlBufferStorageFlags.MAP_WRITE.getBits());
        assertEquals(GlBufferMapFlags.PERSISTENT.getBits(), GlBufferStorageFlags.PERSISTENT.getBits());
        int checked = GlBufferMapFlags.READ.getBits() | GlBufferMapFlags.WRITE.getBits() | GlBufferMapFlags.PERSISTENT.getBits();
        for (GlBufferMapFlags flag : GlBufferMapFlags.values()) {
            assertEquals(1, Integer.bitCount(flag.getBits()), flag.name());
        }
        assertEquals(3, Integer.bitCount(checked));
    }
}
