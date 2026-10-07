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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.common.ForgeEarlyConfig;
import net.minecraftforge.common.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private final boolean realNoError = ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR;
    private final boolean realDebug = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
    private AtomicReference<CompletableFuture<List<GraphicsAdapterInfo>>> shared;
    private CompletableFuture<List<GraphicsAdapterInfo>> realProbe;
    private MockedStatic<ConfigManager> configs;
    // The hint each createDisplay attempt saw
    private final List<Boolean> attempts = new ArrayList<>();

    @BeforeEach
    void windowsWithANvidiaCard() throws Exception {
        // Windows, so the Wayland rule cannot depend on how the test JVM was started
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        shared = Statics.get(GraphicsAdapterProbe.class, "SHARED");
        realProbe = shared.get();
        shared.set(CompletableFuture.completedFuture(NVIDIA));
        ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = false;
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
        setOption(true);
        configs = Mockito.mockStatic(ConfigManager.class);
    }

    @AfterEach
    void restore() throws Exception {
        configs.close();
        Statics.set(OsKind.class, "CURRENT", realOs);
        shared.set(realProbe);
        ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = realNoError;
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = realDebug;
        setOption(true);
        TestGl.reset();
    }

    private static void setOption(boolean value) throws Exception {
        ImpetusGameOptions options = ImpetusGameOptions.load();
        options.performance.useNoErrorGLContext = value;
        ImpetusGameOptions.writeToDisk(options);
    }

    private void record() {
        attempts.add(ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR);
    }

    @Test
    void theHintIsSetForTheCreateAndTakenBackAfterwards() {
        assertTrue(ContextCreation.requestsNoError());
        ContextCreation.create(this::record);
        assertEquals(List.of(true), attempts);
        // Cleanroom saved it along with the GL version; the file is rewritten with the user's value
        assertFalse(ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR);
        configs.verify(() -> ConfigManager.sync(ForgeEarlyConfig.class));
    }

    @Test
    void aRefusedWindowIsRetriedWithoutTheHint() {
        ContextCreation.create(() -> {
            record();
            if (attempts.size() == 1) {
                throw new IllegalStateException("Failed to create Display window.");
            }
        });
        assertEquals(List.of(true, false), attempts);
        assertFalse(ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR);
        configs.verify(() -> ConfigManager.sync(ForgeEarlyConfig.class));
    }

    @Test
    void nothingIsAddedOrSavedWhenTheHintIsNotImpetusToGive() throws Exception {
        // The user's own Cleanroom setting stays on and is not rewritten
        ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = true;
        ContextCreation.create(this::record);
        assertTrue(ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR);

        // Option off, a debug context, and a Windows machine with no adapter information each leave it off
        ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = false;
        setOption(false);
        ContextCreation.create(this::record);
        setOption(true);
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = true;
        ContextCreation.create(this::record);
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
        shared.set(CompletableFuture.completedFuture(List.of()));
        ContextCreation.create(this::record);

        assertEquals(List.of(true, false, false, false), attempts);
        configs.verifyNoInteractions();
    }

    @Test
    void nvidiaOnWindowsGetsSynchronousDebugOutput() {
        Mockito.doReturn("NVIDIA Corporation").when(TestGl.gl()).glGetString(0x1F00);
        ContextCreation.create(this::record);
        Mockito.verify(TestGl.gl()).glEnable(0x8242);
    }

    @Test
    void theMixinHandsVanillasMethodToTheWrapper() {
        ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = true;
        @SuppressWarnings("unchecked")
        Operation<Void> original = Mockito.mock(Operation.class);
        Mixins.call(Mixins.instance(ContextCreationMixin.class), "impetus$createDisplay", original);
        Mockito.verify(original).call();
    }
}
