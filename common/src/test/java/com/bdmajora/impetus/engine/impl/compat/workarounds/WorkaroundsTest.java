package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkaroundsTest {
    private final OsKind realOs = OsKind.current();

    @AfterEach
    void restore() {
        Statics.set(OsKind.class, "CURRENT", realOs);
        Workarounds.init(new GlContextInfo("", "", ""), List.of());
    }

    @Test
    void issuesDependOnVendorOsAndDriver() {
        GlContextInfo nvidia = new GlContextInfo("NVIDIA", "", "");
        GlContextInfo intel = new GlContextInfo("Intel", "", "");
        Workarounds.init(nvidia, List.of());
        assertFalse(Workarounds.isActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS));
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        Workarounds.init(nvidia, List.of());
        assertTrue(Workarounds.isActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS));
        Workarounds.init(intel, List.of());
        assertTrue(Workarounds.isActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE));
        Workarounds.init(intel, List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD", "10.18.10.4")));
        assertTrue(Workarounds.isActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE));
        Workarounds.init(intel, List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "GTX", "9.1"), new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc", "31.0.1")));
        assertFalse(Workarounds.isActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE));
        Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
        Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
        assertTrue(Workarounds.isActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT));
    }
}
