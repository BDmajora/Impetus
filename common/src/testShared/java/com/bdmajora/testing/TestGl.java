package com.bdmajora.testing;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import org.lwjgl.system.MemoryUtil;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// The unit-test GL: one Mockito mock of LWJGLService any test may stub or verify, whose unstubbed calls answer like a permissive driver
public final class TestGl {
    private static final int GL_COMPILE_STATUS = 35713;
    private static final int GL_LINK_STATUS = 35714;
    private static final int GL_VALIDATE_STATUS = 35715;
    private static final int GL_FRAMEBUFFER_COMPLETE = 36053;
    private static final int GL_SIGNALED = 0x9119;
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final AtomicLong SYNCS = new AtomicLong();
    private static final LWJGLService SERVICE = Mockito.mock(LWJGLService.class, Mockito.withSettings().name("gl").defaultAnswer(TestGl::answer));

    private TestGl() {}

    // LWJGLServiceProvider reaches this reflectively through the impetus.lwjgl.service property
    public static LWJGLService create() {
        return SERVICE;
    }

    public static LWJGLService gl() {
        return SERVICE;
    }

    // Drops every stubbing and handle counter so tests do not see each other's GL state
    public static void reset() {
        Mockito.reset(SERVICE);
        NAMES.set(0);
        SYNCS.set(0);
        for (long mapping : MAPPINGS.values()) {
            MemoryUtil.nmemFree(mapping);
        }
        MAPPINGS.clear();
    }

    private static final Map<Integer, Long> MAPPINGS = new ConcurrentHashMap<>();

    // Stubs the DSA, bindless and sparse entry points the mesh backend needs; persistent maps get real memory
    public static void meshCapable() {
        Mockito.doAnswer(inv -> NAMES.incrementAndGet()).when(SERVICE).glCreateBuffers();
        Mockito.doNothing().when(SERVICE).glNamedBufferStorage(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glMakeNamedBufferResidentNV(Mockito.anyInt(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glMakeNamedBufferNonResidentNV(Mockito.anyInt());
        Mockito.doAnswer(inv -> 0x1000L * (long) (Integer) inv.getArgument(0)).when(SERVICE).glGetNamedBufferGpuAddressNV(Mockito.anyInt());
        Mockito.doAnswer(inv -> {
            int id = inv.getArgument(0);
            long size = inv.getArgument(2);
            long address = MemoryUtil.nmemCalloc(1, size);
            MAPPINGS.put(id, address);
            return address;
        }).when(SERVICE).nglMapNamedBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glUnmapNamedBuffer(Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glFlushMappedNamedBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong());
        Mockito.doNothing().when(SERVICE).glCopyNamedBufferSubData(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong());
        Mockito.doNothing().when(SERVICE).glClearNamedBufferSubDataZero(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glClearNamedBufferDataZero(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glBufferAddressRangeNV(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong());
        Mockito.doNothing().when(SERVICE).glEnableClientState(Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glDisableClientState(Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glDrawMeshTasksNV(Mockito.anyInt(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glMultiDrawMeshTasksIndirectNV(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doNothing().when(SERVICE).glBufferPageCommitmentARB(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyBoolean());
    }

    private static Object answer(InvocationOnMock invocation) throws Throwable {
        Method method = invocation.getMethod();
        String name = method.getName();
        Object[] args = invocation.getArguments();
        switch (name) {
            case "isOpenGLVersionSupported":
            case "isExtensionSupported":
            case "supportsBufferBlending": return true;
            case "glFenceSync": return SYNCS.incrementAndGet();
            // Fences answer as signalled with the one result value GlFence expects, so staging rings reclaim without per-test stubbing
            case "glGetSynci": {
                java.nio.IntBuffer count = (java.nio.IntBuffer) args[2];
                if (count != null) {
                    count.put(0, 1);
                }
                return GL_SIGNALED;
            }
            case "glCheckFramebufferStatus": return GL_FRAMEBUFFER_COMPLETE;
            case "glGetUniformLocation":
            case "glGetAttribLocation":
            case "glGetUniformBlockIndex": return -1;
            case "glGetShaderi":
            case "glGetProgrami": {
                int pname = (Integer) args[1];
                return pname == GL_COMPILE_STATUS || pname == GL_LINK_STATUS || pname == GL_VALIDATE_STATUS ? 1 : 0;
            }
            case "glMapBufferRange": return ByteBuffer.allocateDirect((int) (long) (Long) args[2]).order(ByteOrder.nativeOrder());
            case "glMapBuffer": return ByteBuffer.allocateDirect(1 << 16).order(ByteOrder.nativeOrder());
            default: break;
        }
        if (name.startsWith("glGen") || name.startsWith("glCreate")) {
            if (args.length == 1 && args[0] instanceof int[] names) {
                for (int i = 0; i < names.length; i++) {
                    names[i] = NAMES.incrementAndGet();
                }
                return null;
            }
            if (method.getReturnType() == int.class) {
                return NAMES.incrementAndGet();
            }
        }
        if (method.getReturnType() == String.class) {
            return "";
        }
        return Mockito.RETURNS_DEFAULTS.answer(invocation);
    }
}
