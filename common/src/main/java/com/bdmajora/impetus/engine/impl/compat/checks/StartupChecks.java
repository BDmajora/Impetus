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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Platform compatibility checks run once at game startup
public final class StartupChecks {
    // Logger for compatibility findings
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    // Flag set once the crash dialog handler is installed
    private static final AtomicBoolean CRASH_DIALOG_INSTALLED = new AtomicBoolean(false);
    // Flag set once a crash has claimed the crash dialog
    private static final AtomicBoolean CRASH_DIALOG_SHOWN = new AtomicBoolean(false);
    // Longest wait in milliseconds for the adapter probe result
    private static final long ADAPTER_WAIT_MILLIS = 10_000;
    // Longest wait in seconds for the task list process to exit
    private static final long OVERLAY_SCAN_TIMEOUT_SECONDS = 10;
    // Command that lists running processes as CSV without a header on Windows
    private static final String[] TASKLIST_COMMAND = {"tasklist", "/fo", "csv", "/nh"};
    // Oldest Windows NVIDIA driver without the terrain corruption bug
    private static final DriverVersion MINIMUM_NVIDIA_WINDOWS_DRIVER = new DriverVersion(536, 23);
    // Oldest NVIDIA driver of the same branch on Linux and the BSD family
    private static final DriverVersion MINIMUM_NVIDIA_LINUX_DRIVER = new DriverVersion(535, 54);
    // Runtime name fragment reported by Android Java runtimes
    private static final LowerCaseNeedle ANDROID = new LowerCaseNeedle("android");
    // Virtual machine name fragment reported by Dalvik
    private static final LowerCaseNeedle DALVIK = new LowerCaseNeedle("dalvik");
    // Process name fragment of RivaTuner Statistics Server
    private static final LowerCaseNeedle RTSS = new LowerCaseNeedle("rtss");

    // Prevents instantiation
    private StartupChecks() {
    }

    // Installs a default uncaught exception handler that shows one crash dialog and delegates to the previous handler
    public static void installCrashDialog() {
        if (!CRASH_DIALOG_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Claims the single dialog only for a crash that has a throwable
            if (throwable != null && CRASH_DIALOG_SHOWN.compareAndSet(false, true)) {
                try {
                    MessageBoxUtil.showError("Impetus — Minecraft crashed",
                            "Minecraft encountered an unrecoverable error while Impetus was loaded.\n\n" +
                            "Error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage() + "\n\n" +
                            "The full crash details are still written to the game log/crash report.");
                } catch (Throwable dialogFailure) {
                    // Ignores a dialog that fails to build or open
                }
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                LOGGER.error("Unhandled exception in thread {}", thread == null ? null : thread.getName(), throwable);
            }
        });
    }

    // Starts the checks on a virtual thread
    public static void runAsync(GlContextInfo context) {
        Thread.ofVirtual().name("Impetus Compatibility Checks").start(() -> run(context));
    }

    // Runs the adapter probe and each check and discards the failure of any one step
    private static void run(GlContextInfo context) {
        List<GraphicsAdapterInfo> adapters;
        try {
            adapters = GraphicsAdapterProbe.adapters(ADAPTER_WAIT_MILLIS);
        } catch (Throwable t) {
            adapters = List.of();
        }

        try {
            Workarounds.init(context, adapters);
        } catch (Throwable t) {
            // Leaves the workaround registry unchanged
        }

        try {
            runBugChecks(context, adapters);
        } catch (Throwable t) {
            // Skips the remaining driver and launcher checks
        }

        try {
            scanForFrameHookOverlays();
        } catch (Throwable t) {
            // Skips the overlay scan
        }
    }

    // Warns about known bad launchers and drivers and logs incomplete context strings
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

        if (context == null || isMissing(context.vendor()) || isMissing(context.renderer())) {
            LOGGER.warn("OpenGL context strings were incomplete; compatibility checks may be less accurate.");
        }
    }

    // Warns when PojavLauncher is detected
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

    // Returns whether a PojavLauncher environment variable or an Android runtime property is present
    private static boolean isPojavLauncher() {
        return envPresent("POJAV_RENDERER")
                || envPresent("POJAVEXEC_EGL")
                || envPresent("ANDROID_ROOT")
                || propertyContains("java.runtime.name", ANDROID)
                || propertyContains("java.vm.name", DALVIK);
    }

    // Returns whether the environment variable holds a non empty value
    private static boolean envPresent(String key) {
        return !isMissing(System.getenv(key));
    }

    // Returns whether the text is null or empty
    private static boolean isMissing(String text) {
        return text == null || text.isEmpty();
    }

    // Returns whether the lower case form of the system property contains the needle
    private static boolean propertyContains(String key, LowerCaseNeedle needle) {
        return needle.occursIn(System.getProperty(key, ""));
    }

    // Warns about the first NVIDIA adapter whose driver is older than the minimum of the running system
    private static void warnIfOutdatedNvidiaDriver(List<GraphicsAdapterInfo> adapters) {
        var minimum = OsKind.current() == OsKind.LINUX ? MINIMUM_NVIDIA_LINUX_DRIVER : MINIMUM_NVIDIA_WINDOWS_DRIVER;

        for (var adapter : adapters) {
            if (adapter.vendor() != GraphicsVendor.NVIDIA) {
                continue;
            }

            String driver = adapter.driverVersion();
            DriverVersion version = DriverVersion.parseNvidia(driver);
            if (version != null && version.compareTo(minimum) < 0) {
                String recommended = minimum.major() + "." + minimum.minor();
                ImpetusNotifications.warn("Outdated NVIDIA driver",
                        "Detected driver: " + driver,
                        "Recommended: " + recommended + " or newer.");
                MessageBoxUtil.showWarning("Impetus — Outdated NVIDIA driver",
                        "Your NVIDIA graphics driver appears to be out of date.\n\n" +
                        "Detected driver: " + driver + "\n" +
                        "Recommended driver: " + recommended + " or newer\n\n" +
                        "Older NVIDIA drivers are known to cause severe performance issues and crashes\n" +
                        "with modern chunk renderers. Please update your graphics driver.");
                return;
            }
        }
    }

    // Major and minor parts of an NVIDIA driver version
    private record DriverVersion(int major, int minor) implements Comparable<DriverVersion> {
        // Marker for a segment that is not a valid int
        private static final long INVALID = Long.MIN_VALUE;

        // Returns the version read from the packed Windows form or the dotted major and minor form or null when neither parses
        static DriverVersion parseNvidia(String raw) {
            if (raw == null) {
                return null;
            }

            int begin = 0;
            int end = raw.length();
            // Skips spaces and control characters at both ends
            while (begin < end && raw.charAt(begin) <= ' ') {
                begin++;
            }
            while (end > begin && raw.charAt(end - 1) <= ' ') {
                end--;
            }

            int firstDot = raw.indexOf('.', begin);
            if (firstDot < 0) {
                return null;
            }

            int secondDot = raw.indexOf('.', firstDot + 1);
            if (secondDot >= 0) {
                int thirdDot = raw.indexOf('.', secondDot + 1);
                // Reads the packed Windows form whose third segment is 15
                if (thirdDot == secondDot + 3 && raw.charAt(secondDot + 1) == '1' && raw.charAt(secondDot + 2) == '5') {
                    int fourthDot = raw.indexOf('.', thirdDot + 1);
                    long packed = parseInt(raw, thirdDot + 1, fourthDot < 0 ? end : fourthDot);
                    if (packed >= 1000) {
                        return new DriverVersion(500 + (int) (packed / 100), (int) (packed % 100));
                    }
                }
            }

            long major = parseInt(raw, begin, firstDot);
            if (major == INVALID) {
                return null;
            }

            long minor = parseInt(raw, firstDot + 1, secondDot < 0 ? end : secondDot);
            return minor == INVALID ? null : new DriverVersion((int) major, (int) minor);
        }

        // Returns the signed decimal int between begin and end or INVALID for text that Integer.parseInt rejects
        private static long parseInt(String text, int begin, int end) {
            boolean negative = false;
            if (begin < end) {
                char sign = text.charAt(begin);
                if (sign == '-' || sign == '+') {
                    negative = sign == '-';
                    begin++;
                }
            }

            if (begin == end) {
                return INVALID;
            }

            long magnitude = 0;
            for (int i = begin; i < end; i++) {
                int digit = Character.digit(text.charAt(i), 10);
                if (digit < 0 || (magnitude = magnitude * 10 + digit) > 1L << 31) {
                    return INVALID;
                }
            }

            if (negative) {
                return -magnitude;
            }
            return magnitude <= Integer.MAX_VALUE ? magnitude : INVALID;
        }

        // Compares the major parts and then the minor parts
        @Override
        public int compareTo(DriverVersion other) {
            int majorCmp = Integer.compare(this.major, other.major);
            return majorCmp != 0 ? majorCmp : Integer.compare(this.minor, other.minor);
        }
    }

    // Marks the overlay workaround and warns when the Windows task list names RTSS
    private static void scanForFrameHookOverlays() {
        if (OsKind.current() != OsKind.WINDOWS) {
            return;
        }

        try {
            if (isOverlayListed()) {
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
        } catch (InterruptedException e) {
            // Restores the interrupt status
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Skips the warning when the task list cannot be read
        }
    }

    // Returns whether the task list names RTSS and exits within the timeout
    private static boolean isOverlayListed() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(TASKLIST_COMMAND)
                .redirectErrorStream(true)
                .start();

        try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            boolean listed = RTSS.occursInStream(reader);
            return process.waitFor(OVERLAY_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS) && listed;
        } finally {
            // Kills a task list that is still running after a timeout or failure
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    // Lower case ASCII text searched for in the Locale.ROOT lower case form of other text
    private static final class LowerCaseNeedle {
        // Number of characters read from a stream at a time
        private static final int STREAM_BUFFER_CHARS = 4096;

        // Characters of the needle
        private final char[] chars;
        // Length of the longest proper border of each needle prefix
        private final int[] borders;

        // Creates a needle from lower case ASCII text and computes its prefix borders
        LowerCaseNeedle(String text) {
            chars = text.toCharArray();
            borders = new int[chars.length];
            for (int i = 1, border = 0; i < chars.length; i++) {
                while (border > 0 && chars[i] != chars[border]) {
                    border = borders[border - 1];
                }
                if (chars[i] == chars[border]) {
                    border++;
                }
                borders[i] = border;
            }
        }

        // Returns whether the lower case form of the text contains the needle
        boolean occursIn(String text) {
            int matched = 0;
            for (int i = 0, length = text.length(); i < length && matched < chars.length; i++) {
                matched = advance(matched, text.charAt(i));
            }
            return matched == chars.length;
        }

        // Reads the reader to its end and returns whether its lower case form contains the needle
        boolean occursInStream(Reader reader) throws IOException {
            char[] buffer = new char[STREAM_BUFFER_CHARS];
            int matched = 0;
            for (int read; (read = reader.read(buffer)) >= 0; ) {
                for (int i = 0; i < read && matched < chars.length; i++) {
                    matched = advance(matched, buffer[i]);
                }
            }
            return matched == chars.length;
        }

        // Returns the matched length after one more character of the original text
        private int advance(int matched, char c) {
            if (c == '\u0130') {
                // Feeds the two characters i and combining dot above that the capital dotted I lowers to
                matched = step(matched, 'i');
                return matched == chars.length ? matched : step(matched, '\u0307');
            }
            return step(matched, Character.toLowerCase(c));
        }

        // Returns the matched length after one more lower case character
        private int step(int matched, char c) {
            while (matched > 0 && chars[matched] != c) {
                matched = borders[matched - 1];
            }
            return chars[matched] == c ? matched + 1 : matched;
        }
    }
}
