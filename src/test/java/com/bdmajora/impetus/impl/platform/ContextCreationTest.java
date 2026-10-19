package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.mixin.core.ContextCreationMixin;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import com.cleanroommc.client.sdl.SDLException;
import com.cleanroommc.client.sdl.Window;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLVideo;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextCreationTest {
    private static final List<GraphicsAdapterInfo> NVIDIA = List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX", "32.0.15"));
    private final OsKind realOs = OsKind.current();
    private final boolean realDebug = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
    private AtomicReference<CompletableFuture<List<GraphicsAdapterInfo>>> shared;
    private CompletableFuture<List<GraphicsAdapterInfo>> realProbe;
    private MockedStatic<SDLVideo> video;
    private int noError;
    private final List<Integer> attempts = new ArrayList<>();
    private final Object created = new Object();

    @BeforeEach
    void windowsWithANvidiaCard() throws Exception {
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        shared = Statics.get(GraphicsAdapterProbe.class, "SHARED");
        realProbe = shared.get();
        shared.set(CompletableFuture.completedFuture(NVIDIA));
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
        setOption(true);
        video = Mockito.mockStatic(SDLVideo.class);
        video.when(() -> SDLVideo.SDL_GL_SetAttribute(Mockito.eq(SDLVideo.SDL_GL_CONTEXT_NO_ERROR), Mockito.anyInt())).thenAnswer(inv -> {
            noError = inv.getArgument(1);
            return true;
        });
    }

    @AfterEach
    void restore() throws Exception {
        if (video != null) video.close();
        Statics.set(OsKind.class, "CURRENT", realOs);
        shared.set(realProbe);
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = realDebug;
        setOption(true);
        TestGl.reset();
    }

    private static void setOption(boolean value) throws Exception {
        ImpetusGameOptions options = ImpetusGameOptions.load();
        options.performance.useNoErrorGLContext = value;
        ImpetusGameOptions.writeToDisk(options);
    }

    private Object record() {
        attempts.add(noError);
        return created;
    }

    @Test
    void successfulCreationKeepsTheAttributeForSharedContexts() {
        assertTrue(ContextCreation.requestsNoError());
        assertSame(created, ContextCreation.create(this::record));
        assertEquals(List.of(1), attempts);
        assertEquals(1, noError);
    }

    @Test
    void aRefusedContextRetriesWithNormalErrorChecking() {
        assertSame(created, ContextCreation.create(() -> {
            record();
            if (attempts.size() == 1) throw new SDLException("Context refused");
            return created;
        }));
        assertEquals(List.of(1, 0), attempts);
        assertEquals(0, noError);
    }

    @Test
    void aFailedRetryKeepsBothDriverErrors() {
        SDLException failure = assertThrows(SDLException.class, () -> ContextCreation.create(() -> {
            record();
            throw new SDLException("Attempt " + attempts.size());
        }));
        assertEquals("Attempt 2", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(0, noError);
    }

    @Test
    void optionDebugAndProbeGuardsLeaveTheSdlAttributeAlone() throws Exception {
        setOption(false);
        ContextCreation.create(this::record);
        setOption(true);
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = true;
        ContextCreation.create(this::record);
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
        shared.set(CompletableFuture.completedFuture(List.of()));
        ContextCreation.create(this::record);
        assertEquals(List.of(0, 0, 0), attempts);
        video.verifyNoInteractions();
    }

    @Test
    void anUnsupportedAttributeStillCreatesTheWindow() {
        video.when(() -> SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_NO_ERROR, 1)).thenReturn(false);
        noError = 0;
        assertSame(created, ContextCreation.create(this::record));
        assertEquals(List.of(0), attempts);
    }

    @Test
    void nvidiaMitigationsFollowCreationAndTheMixinWrapsTheSdlBuilder() {
        Mockito.doReturn("NVIDIA Corporation").when(TestGl.gl()).glGetString(0x1F00);
        ContextCreation.create(this::record);
        Mockito.verify(TestGl.gl()).glEnable(0x8242);
        Window.Builder builder = Mockito.mock(Window.Builder.class);
        Window window = Mockito.mock(Window.class);
        Operation<Window> original = args -> {
            assertSame(builder, args[0]);
            return window;
        };
        assertSame(window, Mixins.call(Mixins.instance(ContextCreationMixin.class), "impetus$createWindow", builder, original));
    }
}
