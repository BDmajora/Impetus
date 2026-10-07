package com.bdmajora.impetus.engine.impl.compat.checks;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.platform.MessageBoxUtil;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.impetus.engine.impl.compat.workarounds.Workarounds;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

// Platform-compatibility startup sequence: GL context capture is done by the caller on the render thread, everything else on a daemon thread, and no failure may break launch
public final class StartupChecks {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    private static final AtomicBoolean CRASH_DIALOG_INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean CRASH_DIALOG_SHOWN = new AtomicBoolean(false);
    // Longer than the probe's own process timeout, so this thread only gives up on a probe that has hung
    private static final long ADAPTER_WAIT_MILLIS = 10_000;

    private StartupChecks() {
    }

    // Hooks the uncaught exception handler to show a dialog before the game dies
    public static void installCrashDialog() {
        if (!CRASH_DIALOG_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (CRASH_DIALOG_SHOWN.compareAndSet(false, true)) {
                try {
                    MessageBoxUtil.showError("Impetus — Minecraft crashed",
                            "Minecraft encountered an unrecoverable error while Impetus was loaded.\n\n" +
                            "Error: " + throwable.getClass().getSimpleName() + ": " +
                            String.valueOf(throwable.getMessage()) + "\n\n" +
                            "The full crash details are still written to the game log/crash report.");
                } catch (Throwable dialogFailure) {
                    // The dialog is a courtesy on top of the crash report; if it cannot be shown the crash handling below still runs
                }
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                LOGGER.error("Unhandled exception in thread " + thread.getName(), throwable);
            }
        });
    }

    // Runs the checks off the render thread; they probe the OS and can block
    public static void runAsync(GlContextInfo context) {
        Thread.ofVirtual().name("Impetus Compatibility Checks").start(() -> run(context));
    }

    // The checks themselves, each tolerant of the others failing
    private static void run(GlContextInfo context) {
        try {
            var adapters = GraphicsAdapterProbe.adapters(ADAPTER_WAIT_MILLIS);
            Workarounds.init(context, adapters);
            runBugChecks(context, adapters);
            scanForFrameHookOverlays();
        } catch (Throwable t) {
            // Best-effort: these only pick workarounds and advisory dialogs, so a probe that blows up must not take the load down
        }
    }

    // Known bad driver and launcher combinations
    private static void runBugChecks(GlContextInfo context, List<GraphicsAdapterInfo> adapters) {
        warnIfPojavLauncher();
        warnIfOutdatedNvidiaDriver(adapters);

        if (Workarounds.isActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE)) {
            ImpetusNotifications.warn("Driver workaround active",
                    "Older Intel Windows driver detected.",
                    "No-error GL context is not requested.");
            MessageBoxUtil.showWarning("Impetus — Driver workaround active",
                    "Impetus detected an older Intel Windows graphics driver.\n\n" +
                    "The no-error OpenGL context is unsafe on this driver, so Impetus does not request\n" +
                    "one whatever the No Error Context option says. If you still see black screens or\n" +
                    "driver crashes, update your Intel graphics driver.");
        }

        if (context.vendor().isEmpty() || context.renderer().isEmpty()) {
            LOGGER.warn("OpenGL context strings were incomplete; compatibility checks may be less accurate.");
        }
    }

    // Pojav's GL translation layer is unsupported
    private static void warnIfPojavLauncher() {
        if (!isPojavLauncher()) {
            return;
        }

        ImpetusNotifications.warn("Unsupported launcher",
                "PojavLauncher is not supported.",
                "Expect severe rendering issues.");
        MessageBoxUtil.showWarning("Impetus — Unsupported launcher",
                "PojavLauncher appears to be running.\n\n" +
                "PojavLauncher is not supported with Impetus and is very likely to hit severe\n" +
                "performance problems, graphical bugs, or crashes.");
    }

    // Detected from the environment variables Pojav sets
    private static boolean isPojavLauncher() {
        return envPresent("POJAV_RENDERER")
                || envPresent("POJAVEXEC_EGL")
                || envPresent("ANDROID_ROOT")
                || propertyContains("java.runtime.name", "android")
                || propertyContains("java.vm.name", "dalvik");
    }

    // Non-empty environment variable
    private static boolean envPresent(String key) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty();
    }

    // System property substring check
    private static boolean propertyContains(String key, String needle) {
        return System.getProperty(key, "").toLowerCase(Locale.ROOT).contains(needle);
    }

    // Drivers before the known-good version have a threading bug that corrupts terrain
    private static void warnIfOutdatedNvidiaDriver(List<GraphicsAdapterInfo> adapters) {
        for (var adapter : adapters) {
            if (adapter.vendor() != GraphicsVendor.NVIDIA) {
                continue;
            }

            DriverVersion version = DriverVersion.parseNvidia(adapter.driverVersion());
            if (version != null && version.compareTo(new DriverVersion(536, 23)) < 0) {
                ImpetusNotifications.warn("Outdated NVIDIA driver",
                        "Detected driver: " + adapter.driverVersion(),
                        "Recommended: 536.23 or newer.");
                MessageBoxUtil.showWarning("Impetus — Outdated NVIDIA driver",
                        "Your NVIDIA graphics driver appears to be out of date.\n\n" +
                        "Detected driver: " + adapter.driverVersion() + "\n" +
                        "Recommended driver: 536.23 or newer\n\n" +
                        "Older NVIDIA drivers are known to cause severe performance issues and crashes\n" +
                        "with modern chunk renderers. Please update your graphics driver.");
                return;
            }
        }
    }

    private record DriverVersion(int major, int minor) implements Comparable<DriverVersion> {
        // Parses the major.minor form; null when unparseable
        static DriverVersion parseNvidia(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }

            String[] parts = raw.trim().split("\\.");

            if (parts.length >= 4 && "15".equals(parts[2])) {
                Integer packed = parseInt(parts[3]);
                if (packed != null && packed >= 1000) {
                    return new DriverVersion(500 + (packed / 100), packed % 100);
                }
            }

            if (parts.length >= 2) {
                Integer major = parseInt(parts[0]);
                Integer minor = parseInt(parts[1]);
                if (major != null && minor != null) {
                    return new DriverVersion(major, minor);
                }
            }

            return null;
        }

        // Null rather than an exception
        private static Integer parseInt(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Major then minor
        @Override
        public int compareTo(DriverVersion other) {
            int majorCmp = Integer.compare(this.major, other.major);
            return majorCmp != 0 ? majorCmp : Integer.compare(this.minor, other.minor);
        }
    }

    // Detects frame-hooking overlays in the GL presentation path, a leading cause of "crashes only on my machine" reports with a replaced render loop
    private static void scanForFrameHookOverlays() {
        if (OsKind.current() != OsKind.WINDOWS) {
            return;
        }

        try {
            var process = new ProcessBuilder("tasklist", "/fo", "csv", "/nh")
                    .redirectErrorStream(true)
                    .start();

            boolean rtssFound = false;

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase(Locale.ROOT).contains("rtss")) {
                        rtssFound = true;
                    }
                }
            }

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }

            if (rtssFound) {
                Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
                ImpetusNotifications.warn("Incompatible overlay detected",
                        "RTSS appears to be running.",
                        "Close it if rendering breaks.");
                MessageBoxUtil.showWarning("Impetus — Incompatible software detected",
                        "RivaTuner Statistics Server (RTSS) appears to be running.\n\n" +
                        "RTSS hooks the OpenGL frame path and is known to cause crashes and rendering\n" +
                        "corruption with modified renderers such as Impetus. If the game crashes or\n" +
                        "renders incorrectly, close RTSS (and overlays built on it, e.g. MSI Afterburner's\n" +
                        "on-screen display) and try again.");
            }
        } catch (Exception e) {
            // The RTSS check is advisory; failing to detect it is not worth reporting
        }
    }
}
