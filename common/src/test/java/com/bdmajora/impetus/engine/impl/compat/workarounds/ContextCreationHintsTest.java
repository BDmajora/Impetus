package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextCreationHintsTest {
    private static final List<GraphicsAdapterInfo> MODERN_INTEL = List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc", "31.0.101"));
    private static final List<GraphicsAdapterInfo> LEGACY_INTEL = List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD 4000", "10.18.10.4"));

    @AfterEach
    void reset() {
        TestGl.reset();
    }

    @Test
    void noErrorContextOnlyWhereNothingRulesItOut() {
        assertTrue(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.LINUX, "x11", List.of()));
        assertTrue(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.WINDOWS, null, MODERN_INTEL));
        assertTrue(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.MACOS, "wayland", List.of()));
        // Off by option, and never alongside a debug context
        assertFalse(ContextCreationHints.allowsNoErrorContext(false, false, OsKind.LINUX, "x11", List.of()));
        assertFalse(ContextCreationHints.allowsNoErrorContext(true, true, OsKind.LINUX, "x11", List.of()));
        // Wayland sessions, legacy Intel Windows drivers, and Windows without any adapter information
        assertFalse(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.LINUX, "Wayland", List.of()));
        assertFalse(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.WINDOWS, null, LEGACY_INTEL));
        assertFalse(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.WINDOWS, null, List.of()));
        // The same Intel adapter matters only on Windows
        assertTrue(ContextCreationHints.allowsNoErrorContext(true, false, OsKind.LINUX, null, LEGACY_INTEL));
    }

    @Test
    void synchronousDebugOutputOnlyForNvidiaOnWindows() {
        GlContextInfo nvidia = new GlContextInfo("NVIDIA Corporation", "RTX", "4.6.0 NVIDIA 580");
        assertTrue(ContextCreationHints.applyContextMitigations(nvidia, OsKind.WINDOWS));
        Mockito.verify(TestGl.gl()).glEnable(ContextCreationHints.DEBUG_OUTPUT_SYNCHRONOUS);

        TestGl.reset();
        assertFalse(ContextCreationHints.applyContextMitigations(nvidia, OsKind.LINUX));
        assertFalse(ContextCreationHints.applyContextMitigations(new GlContextInfo("ATI Technologies", "Radeon", "4.6"), OsKind.WINDOWS));
        // Debug output state needs GL 4.3
        Mockito.doReturn(false).when(TestGl.gl()).isOpenGLVersionSupported(4, 3);
        assertFalse(ContextCreationHints.applyContextMitigations(nvidia, OsKind.WINDOWS));
        Mockito.verify(TestGl.gl(), Mockito.never()).glEnable(Mockito.anyInt());
    }
}
