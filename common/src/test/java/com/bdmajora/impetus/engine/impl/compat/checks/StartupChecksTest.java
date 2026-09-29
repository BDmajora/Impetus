package com.bdmajora.impetus.engine.impl.compat.checks;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.impetus.engine.impl.compat.workarounds.Workarounds;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StartupChecksTest {
    private final OsKind realOs = OsKind.current();
    private final Thread.UncaughtExceptionHandler realHandler = Thread.getDefaultUncaughtExceptionHandler();

    @AfterEach
    void restore() {
        Statics.set(OsKind.class, "CURRENT", realOs);
        Thread.setDefaultUncaughtExceptionHandler(realHandler);
        Workarounds.init(new GlContextInfo("", "", ""), List.of());
    }

    @Test
    void crashDialogWrapsThePreviousHandlerOnce() {
        AtomicInteger previous = new AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> previous.incrementAndGet());
        Statics.set(StartupChecks.class, "CRASH_DIALOG_INSTALLED", new java.util.concurrent.atomic.AtomicBoolean(false));
        Statics.set(StartupChecks.class, "CRASH_DIALOG_SHOWN", new java.util.concurrent.atomic.AtomicBoolean(false));
        StartupChecks.installCrashDialog();
        StartupChecks.installCrashDialog();
        Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
        installed.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));
        installed.uncaughtException(Thread.currentThread(), new RuntimeException("again"));
        assertEquals(2, previous.get());

        Thread.setDefaultUncaughtExceptionHandler(null);
        Statics.set(StartupChecks.class, "CRASH_DIALOG_INSTALLED", new java.util.concurrent.atomic.AtomicBoolean(false));
        StartupChecks.installCrashDialog();
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new RuntimeException("logged"));
    }

    @Test
    void bugChecksWarnForKnownBadSetups() throws InterruptedException {
        GlContextInfo context = new GlContextInfo("Intel", "", "");
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        Workarounds.init(context, List.of());
        List<GraphicsAdapterInfo> adapters = List.of(
                new GraphicsAdapterInfo(GraphicsVendor.AMD, "Radeon", "1.0"),
                new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "GTX", "471.11"));
        Statics.call(StartupChecks.class, "runBugChecks", context, adapters);
        assertTrue(ImpetusNotifications.getVisible().stream().anyMatch(n -> n.title().equals("Outdated NVIDIA driver")));
        assertTrue(ImpetusNotifications.getVisible().stream().anyMatch(n -> n.title().equals("Driver workaround active")));
        Statics.call(StartupChecks.class, "runBugChecks", context, List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX", "560.94")));
        Statics.call(StartupChecks.class, "runBugChecks", context, List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX", "31.0.15.5222")));
        Statics.call(StartupChecks.class, "runBugChecks", context, List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX", "")));
        Statics.call(StartupChecks.class, "runBugChecks", context, List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX", "x.y")));
        Statics.call(StartupChecks.class, "runBugChecks", context, List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX", "31.0.15.99")));
        Statics.call(StartupChecks.class, "scanForFrameHookOverlays");
        Statics.set(OsKind.class, "CURRENT", realOs);
        Statics.call(StartupChecks.class, "scanForFrameHookOverlays");
        assertFalse((boolean) Statics.call(StartupChecks.class, "envPresent", "IMPETUS_DEFINITELY_UNSET_" + System.nanoTime()));
        assertTrue((boolean) Statics.call(StartupChecks.class, "propertyContains", "java.vm.name", "vm"));
        StartupChecks.runAsync(context);
        Statics.call(StartupChecks.class, "run", context);
        Thread.sleep(50);
    }
}
