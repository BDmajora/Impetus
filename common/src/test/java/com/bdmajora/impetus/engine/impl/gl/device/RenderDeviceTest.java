package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.state.GlStateTracker;
import com.bdmajora.impetus.engine.impl.gpu.device.GpuDevice;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

// Tests of the shared render device and of the managed code scope methods of the render device interface
class RenderDeviceTest {
    // Message of every refusal outside managed code
    private static final String UNMANAGED = "Tried to access device from unmanaged context";
    // GL enum of the array buffer binding point
    private static final int ARRAY = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
    // GL enum of the element buffer binding point
    private static final int ELEMENT = GlBufferTarget.ELEMENT_BUFFER.getTargetParameter();
    // GL enum of the vendor string query
    private static final int GL_VENDOR = 0x1F00;
    // GL enum of the renderer string query
    private static final int GL_RENDERER = 0x1F01;
    // GL enum of the version string query
    private static final int GL_VERSION = 0x1F02;
    // Number of managed code scopes that the repeated scope test opens and closes
    private static final int SCOPES = 1000;

    // Mocked LWJGL service that records every GL call
    private final LWJGLService gl = TestGl.gl();
    // Shared render device under test
    private final GLRenderDevice device = (GLRenderDevice) RenderDevice.INSTANCE;
    // Activation flag of the shared device before the test
    private boolean wasActive;
    // Capability description of the shared device before the test or null when it had none
    private GpuDevice savedInfo;

    // Stores the state of the shared device and starts it inactive with a description and no recorded GL call
    @BeforeEach
    void isolate() {
        wasActive = active();
        savedInfo = (GpuDevice) read("deviceInfo");
        if (savedInfo == null) {
            device.makeActive();
        }
        write("isActive", false);
        tracker().clear();
        Mockito.clearInvocations(gl);
    }

    // Restores the activation flag and the description of the shared device and forgets the bindings of the test
    @AfterEach
    void restore() {
        tracker().clear();
        write("deviceInfo", savedInfo);
        write("isActive", wasActive);
    }

    // Returns the private field of the render device with access granted
    private static Field field(String name) {
        try {
            Field field = GLRenderDevice.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            throw new AssertionError(missing);
        }
    }

    // Returns the value of the private field of the shared device
    private Object read(String name) {
        try {
            return field(name).get(device);
        } catch (IllegalAccessException denied) {
            throw new AssertionError(denied);
        }
    }

    // Stores the value in the private field of the shared device
    private void write(String name, Object value) {
        try {
            field(name).set(device, value);
        } catch (IllegalAccessException denied) {
            throw new AssertionError(denied);
        }
    }

    // Returns the binding cache of the shared device
    private GlStateTracker tracker() {
        return (GlStateTracker) read("stateTracker");
    }

    // Returns true when the shared device is active
    private boolean active() {
        return (boolean) read("isActive");
    }

    // Asserts that the call throws the type with the message and issues no GL call
    private <T extends Throwable> T refusedWithoutGl(Class<T> type, String message, Executable call) {
        Mockito.clearInvocations(gl);
        T thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        Mockito.verifyNoInteractions(gl);
        return thrown;
    }

    // Returns the throwable that the body throws
    private static Throwable thrownBy(Runnable body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            return thrown;
        }
        throw new AssertionError("Body did not throw");
    }

    // Checks that the shared device is one render device that every read returns
    @Test
    void instanceIsOneSharedGlRenderDevice() throws ReflectiveOperationException {
        RenderDevice first = RenderDevice.INSTANCE;
        assertNotNull(first);
        assertSame(GLRenderDevice.class, first.getClass());
        assertSame(first, RenderDevice.INSTANCE);
        assertSame(first, RenderDevice.class.getField("INSTANCE").get(null));
    }

    // Checks that the shared device is the only field and a public static final constant of the interface type
    @Test
    void instanceIsTheOnlyFieldAndAConstantOfTheInterfaceType() throws NoSuchFieldException {
        Field instance = RenderDevice.class.getDeclaredField("INSTANCE");
        int modifiers = instance.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertSame(RenderDevice.class, instance.getType());
        List<String> fields = new ArrayList<>();
        for (Field field : RenderDevice.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                fields.add(field.getName());
            }
        }
        assertEquals(List.of("INSTANCE"), fields);
    }

    // Checks that the interface declares five abstract methods and two static methods with no parameter and no default method
    @Test
    void interfaceDeclaresFiveAbstractAndTwoStaticMethods() {
        assertTrue(RenderDevice.class.isInterface());
        assertTrue(Modifier.isPublic(RenderDevice.class.getModifiers()));
        assertEquals(0, RenderDevice.class.getInterfaces().length);
        Map<String, Class<?>> abstracts = new TreeMap<>();
        Map<String, Class<?>> statics = new TreeMap<>();
        for (Method method : RenderDevice.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            assertFalse(method.isDefault(), method.getName());
            assertTrue(Modifier.isPublic(method.getModifiers()), method.getName());
            assertEquals(0, method.getParameterCount(), method.getName());
            assertEquals(0, method.getExceptionTypes().length, method.getName());
            if (Modifier.isStatic(method.getModifiers())) {
                statics.put(method.getName(), method.getReturnType());
            } else {
                assertTrue(Modifier.isAbstract(method.getModifiers()), method.getName());
                abstracts.put(method.getName(), method.getReturnType());
            }
        }
        assertEquals(Map.of(
                "createCommandList", CommandList.class,
                "makeActive", void.class,
                "makeInactive", void.class,
                "getDeviceFunctions", DeviceFunctions.class,
                "getGpuDevice", GpuDevice.class), abstracts);
        assertEquals(Map.of("enterManagedCode", void.class, "exitManagedCode", void.class), statics);
    }

    // Checks that entering managed code unbinds the array buffer once and activates the shared device
    @Test
    void enterManagedCodeUnbindsTheArrayBufferAndActivatesTheSharedDevice() {
        GpuDevice captured = device.getGpuDevice();
        RenderDevice.enterManagedCode();
        Mockito.verify(gl).glBindBuffer(ARRAY, 0);
        Mockito.verifyNoMoreInteractions(gl);
        assertTrue(active());
        CommandList list = RenderDevice.INSTANCE.createCommandList();
        assertNotNull(list);
        assertSame(list, RenderDevice.INSTANCE.createCommandList());
        assertSame(captured, RenderDevice.INSTANCE.getGpuDevice());
    }

    // Checks that entering managed code while active issues no GL call and keeps the command list
    @Test
    void enterManagedCodeWhileActiveIssuesNoGlCall() {
        RenderDevice.enterManagedCode();
        CommandList list = RenderDevice.INSTANCE.createCommandList();
        Mockito.clearInvocations(gl);
        RenderDevice.enterManagedCode();
        RenderDevice.enterManagedCode();
        Mockito.verifyNoInteractions(gl);
        assertTrue(active());
        assertSame(list, RenderDevice.INSTANCE.createCommandList());
    }

    // Checks that entering managed code while active keeps the bindings cached in the open scope
    @Test
    void enterManagedCodeWhileActiveKeepsTheCachedBindings() {
        RenderDevice.enterManagedCode();
        CommandList list = RenderDevice.INSTANCE.createCommandList();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        RenderDevice.enterManagedCode();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        Mockito.verify(gl).glBindBuffer(ELEMENT, buffer.handle());
        list.deleteBuffer(buffer);
    }

    // Checks that one exit closes a scope that was entered several times
    @Test
    void oneExitClosesAScopeEnteredSeveralTimes() {
        for (int i = 0; i < 3; i++) {
            RenderDevice.enterManagedCode();
        }
        RenderDevice.exitManagedCode();
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ARRAY, 0);
        Mockito.verifyNoMoreInteractions(gl);
        assertFalse(active());
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::createCommandList);
    }

    // Checks that exiting managed code unbinds the array buffer once and deactivates the shared device
    @Test
    void exitManagedCodeUnbindsTheArrayBufferAndDeactivatesTheSharedDevice() {
        RenderDevice.enterManagedCode();
        Mockito.clearInvocations(gl);
        RenderDevice.exitManagedCode();
        Mockito.verify(gl).glBindBuffer(ARRAY, 0);
        Mockito.verifyNoMoreInteractions(gl);
        assertFalse(active());
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::createCommandList);
    }

    // Checks that exiting managed code without an open scope issues no GL call and keeps the device inactive
    @Test
    void exitManagedCodeWithoutAScopeIssuesNoGlCall() {
        for (int i = 0; i < 3; i++) {
            RenderDevice.exitManagedCode();
        }
        Mockito.verifyNoInteractions(gl);
        assertFalse(active());
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::createCommandList);
    }

    // Checks that a second exit after a closed scope issues no GL call
    @Test
    void secondExitAfterAClosedScopeIssuesNoGlCall() {
        RenderDevice.enterManagedCode();
        RenderDevice.exitManagedCode();
        Mockito.clearInvocations(gl);
        RenderDevice.exitManagedCode();
        Mockito.verifyNoInteractions(gl);
        assertFalse(active());
    }

    // Checks that a scope forgets the bindings cached in the previous scope
    @Test
    void scopeForgetsTheBindingsCachedInThePreviousScope() {
        RenderDevice.enterManagedCode();
        CommandList list = RenderDevice.INSTANCE.createCommandList();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        RenderDevice.exitManagedCode();
        RenderDevice.enterManagedCode();
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ELEMENT, buffer.handle());
        list.deleteBuffer(buffer);
    }

    // Checks that the shared device hands out the same command list in every scope
    @Test
    void everyScopeHandsOutTheSameCommandList() {
        RenderDevice.enterManagedCode();
        CommandList list = RenderDevice.INSTANCE.createCommandList();
        RenderDevice.exitManagedCode();
        RenderDevice.enterManagedCode();
        assertSame(list, RenderDevice.INSTANCE.createCommandList());
    }

    // Checks that repeated scopes unbind the array buffer on every entry and exit and never capture again
    @Test
    void repeatedScopesUnbindOnEveryEntryAndExitAndNeverCaptureAgain() {
        GpuDevice captured = device.getGpuDevice();
        for (int i = 0; i < SCOPES; i++) {
            RenderDevice.enterManagedCode();
            RenderDevice.exitManagedCode();
        }
        Mockito.verify(gl, Mockito.times(2 * SCOPES)).glBindBuffer(ARRAY, 0);
        Mockito.verifyNoMoreInteractions(gl);
        assertFalse(active());
        assertSame(captured, device.getGpuDevice());
    }

    // Checks that the first scope of a device without a description captures the context once after the unbind
    @Test
    void firstScopeCapturesTheContextOnceAfterTheUnbind() {
        write("deviceInfo", null);
        Mockito.doReturn("Vendor").when(gl).glGetString(GL_VENDOR);
        Mockito.doReturn("Renderer").when(gl).glGetString(GL_RENDERER);
        Mockito.doReturn("4.6").when(gl).glGetString(GL_VERSION);
        RenderDevice.enterManagedCode();
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glBindBuffer(ARRAY, 0);
        order.verify(gl).glGetString(GL_VENDOR);
        GpuDevice info = RenderDevice.INSTANCE.getGpuDevice();
        assertEquals("Vendor", info.vendor());
        assertEquals("Renderer", info.renderer());
        assertEquals("4.6", info.version());
        RenderDevice.exitManagedCode();
        RenderDevice.enterManagedCode();
        Mockito.verify(gl).glGetString(GL_VENDOR);
        Mockito.verify(gl).glGetString(GL_RENDERER);
        Mockito.verify(gl).glGetString(GL_VERSION);
        assertSame(info, RenderDevice.INSTANCE.getGpuDevice());
    }

    // Checks that a device that never entered managed code refuses its description without a GL call
    @Test
    void getGpuDeviceRefusesASharedDeviceThatNeverEnteredManagedCode() {
        write("deviceInfo", null);
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::getGpuDevice);
        RenderDevice.exitManagedCode();
        Mockito.verifyNoInteractions(gl);
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::getGpuDevice);
    }

    // Checks that a failed capture propagates unchanged and leaves the shared device inactive until the next entry captures again
    @Test
    void failedCaptureLeavesTheSharedDeviceInactiveAndTheNextEntryRetries() {
        write("deviceInfo", null);
        IllegalStateException failure = new IllegalStateException("capture");
        Mockito.doThrow(failure).doReturn("Vendor").when(gl).glGetString(GL_VENDOR);
        assertSame(failure, assertThrows(IllegalStateException.class, RenderDevice::enterManagedCode));
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(active());
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::createCommandList);
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::getGpuDevice);
        RenderDevice.exitManagedCode();
        Mockito.verifyNoInteractions(gl);
        RenderDevice.enterManagedCode();
        assertTrue(active());
        assertNotNull(RenderDevice.INSTANCE.createCommandList());
        assertEquals("Vendor", RenderDevice.INSTANCE.getGpuDevice().vendor());
    }

    // Checks that a failed unbind on entry propagates unchanged and skips the capture and leaves the device inactive
    @Test
    void failedEntryUnbindPropagatesAndSkipsTheCapture() {
        write("deviceInfo", null);
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(gl).glBindBuffer(ARRAY, 0);
        assertSame(failure, assertThrows(IllegalStateException.class, RenderDevice::enterManagedCode));
        Mockito.verify(gl, Mockito.never()).glGetString(Mockito.anyInt());
        assertFalse(active());
        assertNull(read("deviceInfo"));
        Mockito.clearInvocations(gl);
        RenderDevice.exitManagedCode();
        Mockito.verifyNoInteractions(gl);
        RenderDevice.enterManagedCode();
        assertTrue(active());
        assertNotNull(RenderDevice.INSTANCE.getGpuDevice());
    }

    // Checks that a failed unbind on exit propagates unchanged and keeps the shared device active until the next exit
    @Test
    void failedExitUnbindPropagatesAndKeepsTheSharedDeviceActive() {
        RenderDevice.enterManagedCode();
        CommandList list = RenderDevice.INSTANCE.createCommandList();
        IllegalStateException failure = new IllegalStateException("unbind");
        Mockito.doThrow(failure).doNothing().when(gl).glBindBuffer(ARRAY, 0);
        assertSame(failure, assertThrows(IllegalStateException.class, RenderDevice::exitManagedCode));
        assertTrue(active());
        assertSame(list, RenderDevice.INSTANCE.createCommandList());
        RenderDevice.exitManagedCode();
        assertFalse(active());
        refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::createCommandList);
    }

    // Checks that an error and an undeclared checked exception of the GL service pass through both scope methods unchanged
    @Test
    void errorsAndUndeclaredCheckedExceptionsPassThroughUnchanged() {
        StackOverflowError error = new StackOverflowError("enter");
        IOException checked = new IOException("exit");
        Mockito.doThrow(error).doNothing().doAnswer(invocation -> {
            throw checked;
        }).doNothing().when(gl).glBindBuffer(ARRAY, 0);
        assertSame(error, thrownBy(RenderDevice::enterManagedCode));
        assertFalse(active());
        RenderDevice.enterManagedCode();
        assertSame(checked, thrownBy(RenderDevice::exitManagedCode));
        assertTrue(active());
        RenderDevice.exitManagedCode();
        assertFalse(active());
    }

    // Checks that the description stays readable outside managed code without a GL call
    @Test
    void getGpuDeviceStaysReadableOutsideManagedCode() {
        RenderDevice.enterManagedCode();
        GpuDevice info = RenderDevice.INSTANCE.getGpuDevice();
        RenderDevice.exitManagedCode();
        Mockito.clearInvocations(gl);
        assertSame(info, RenderDevice.INSTANCE.getGpuDevice());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that the functions of the shared device need no scope and stay the same object without a GL call
    @Test
    void getDeviceFunctionsNeedsNoScopeAndStaysTheSame() {
        DeviceFunctions functions = RenderDevice.INSTANCE.getDeviceFunctions();
        assertNotNull(functions);
        assertNotNull(functions.bufferCopyFunctions());
        assertNotNull(functions.bufferMapRangeFunctions());
        RenderDevice.enterManagedCode();
        assertSame(functions, RenderDevice.INSTANCE.getDeviceFunctions());
        RenderDevice.exitManagedCode();
        Mockito.clearInvocations(gl);
        assertSame(functions, RenderDevice.INSTANCE.getDeviceFunctions());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that another render device keeps its activation apart from the shared device
    @Test
    void anotherDeviceKeepsItsActivationApartFromTheSharedDevice() {
        GLRenderDevice other = new GLRenderDevice();
        try {
            other.makeActive();
            assertFalse(active());
            refusedWithoutGl(IllegalStateException.class, UNMANAGED, RenderDevice.INSTANCE::createCommandList);
            RenderDevice.enterManagedCode();
            other.makeInactive();
            assertTrue(active());
            assertNotSame(other.getDeviceFunctions(), RenderDevice.INSTANCE.getDeviceFunctions());
            RenderDevice.exitManagedCode();
            assertFalse(active());
            refusedWithoutGl(IllegalStateException.class, UNMANAGED, other::createCommandList);
        } finally {
            other.makeInactive();
        }
    }
}
