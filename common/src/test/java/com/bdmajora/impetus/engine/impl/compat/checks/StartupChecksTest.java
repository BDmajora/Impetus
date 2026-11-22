package com.bdmajora.impetus.engine.impl.compat.checks;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.platform.MessageBoxUtil;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.impetus.engine.impl.compat.workarounds.Workarounds;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.testing.Statics;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Tests the startup compatibility checks
class StartupChecksTest {
    // Title of the crash dialog
    private static final String CRASH_TITLE = "Minecraft client stopped";
    // Log message for an uncaught exception
    private static final String UNHANDLED = "Unhandled exception in thread {}";
    // Log message for missing context strings
    private static final String INCOMPLETE = "OpenGL context strings were incomplete; compatibility checks may be less accurate.";
    // Title of the overlay notification
    private static final String OVERLAY_TITLE = "Incompatible overlay detected";
    // Title of the outdated driver notification
    private static final String NVIDIA_TITLE = "Outdated NVIDIA driver";
    // Shell that runs the fake task list scripts
    private static final Path SHELL = Path.of("/bin/sh");
    // Size of the needle stream buffer in characters
    private static final int STREAM_CHUNK = 4096;
    // Environment variables that mark a PojavLauncher process
    private static final String[] POJAV_VARIABLES = {"POJAV_RENDERER", "POJAVEXEC_EGL", "ANDROID_ROOT"};
    // Characters whose lower case forms are ASCII letters or special cases
    private static final char[] FOLDING_ALPHABET = {
            'a', 'b', 'i', 'k', 's', 'A', 'B', 'I', 'K', 'S', '\u0130', '\u0131', '\u212A', '\u017F',
            '\u0307', '\u03A3', '\u1E9E', '\n', '.', '\uD801', '\uDC00'
    };
    // Class type of a lower case needle
    private static final Class<?> NEEDLE = nested("LowerCaseNeedle");
    // Constructor of a lower case needle
    private static final Constructor<?> NEEDLE_OF = constructor(NEEDLE, String.class);
    // Search of a needle in a string
    private static final Method OCCURS_IN = method(NEEDLE, "occursIn", String.class);
    // Search of a needle in a reader
    private static final Method OCCURS_IN_STREAM = method(NEEDLE, "occursInStream", Reader.class);

    // Directory for pid and marker files
    @TempDir
    Path temp;

    // Operating system detected at startup
    private OsKind realOs;
    // Default uncaught exception handler before the test
    private Thread.UncaughtExceptionHandler realHandler;
    // Logger of the checks before the test
    private Logger realLogger;
    // Task list command before the test
    private String[] realCommand;
    // Runtime name property before the test
    private String realRuntimeName;
    // Virtual machine name property before the test
    private String realVmName;

    // Records global state and clears the crash flags and workarounds and notifications
    @BeforeEach
    void capture() {
        realOs = OsKind.current();
        realHandler = Thread.getDefaultUncaughtExceptionHandler();
        realLogger = Statics.get(StartupChecks.class, "LOGGER");
        realCommand = command().clone();
        realRuntimeName = System.getProperty("java.runtime.name");
        realVmName = System.getProperty("java.vm.name");
        resetCrashFlags();
        Workarounds.init(new GlContextInfo("", "", ""), List.of());
        clearNotifications();
    }

    // Restores global state and clears the crash flags and workarounds and notifications
    @AfterEach
    void restore() {
        Statics.set(OsKind.class, "detected", realOs);
        Thread.setDefaultUncaughtExceptionHandler(realHandler);
        Statics.set(StartupChecks.class, "LOGGER", realLogger);
        System.arraycopy(realCommand, 0, command(), 0, realCommand.length);
        restoreProperty("java.runtime.name", realRuntimeName);
        restoreProperty("java.vm.name", realVmName);
        resetCrashFlags();
        Workarounds.init(new GlContextInfo("", "", ""), List.of());
        clearNotifications();
        Thread.interrupted();
    }

    // Checks the private constructor of the final class
    @Test
    void constructorIsPrivateAndClassIsFinal() throws ReflectiveOperationException {
        Constructor<StartupChecks> constructor = StartupChecks.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertTrue(Modifier.isFinal(StartupChecks.class.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    // Checks that every crash reaches the previous handler with its own thread and throwable
    @Test
    void crashHandlerForwardsEveryCrashToPreviousHandler() {
        List<Object[]> seen = Collections.synchronizedList(new ArrayList<>());
        Thread.UncaughtExceptionHandler previous = (thread, throwable) -> seen.add(new Object[]{thread, throwable});
        Thread.setDefaultUncaughtExceptionHandler(previous);
        StartupChecks.installCrashDialog();
        Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull(installed);
        assertNotSame(previous, installed);

        Thread worker = new Thread(() -> { }, "worker");
        RuntimeException first = new RuntimeException("boom");
        Error second = new AssertionError("again");
        installed.uncaughtException(worker, first);
        installed.uncaughtException(Thread.currentThread(), second);

        assertEquals(2, seen.size());
        assertSame(worker, seen.get(0)[0]);
        assertSame(first, seen.get(0)[1]);
        assertSame(Thread.currentThread(), seen.get(1)[0]);
        assertSame(second, seen.get(1)[1]);
    }

    // Checks that installing again leaves the current default handler alone
    @Test
    void repeatedInstallIsIgnored() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        StartupChecks.installCrashDialog();
        assertNotNull(Thread.getDefaultUncaughtExceptionHandler());
        Thread.UncaughtExceptionHandler replacement = (thread, throwable) -> { };
        Thread.setDefaultUncaughtExceptionHandler(replacement);
        StartupChecks.installCrashDialog();
        StartupChecks.installCrashDialog();
        assertSame(replacement, Thread.getDefaultUncaughtExceptionHandler());
    }

    // Checks that only the first crash opens the dialog with the class name and message
    @Test
    void crashDialogOpensOnceWithClassAndMessage() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> { });
        try (MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            StartupChecks.installCrashDialog();
            Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
            installed.uncaughtException(Thread.currentThread(), new IllegalStateException("boom"));
            installed.uncaughtException(Thread.currentThread(), new IllegalArgumentException("again"));
            boxes.verify(() -> MessageBoxUtil.showError(CRASH_TITLE, crashMessage("IllegalStateException", "boom")));
            boxes.verifyNoMoreInteractions();
        }
    }

    @Test
    void backgroundJsonFailureIsLoggedWithoutClaimingTheClientDialog() {
        Logger logger = mockLogger();
        List<Throwable> forwarded = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> forwarded.add(throwable));
        try (MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            StartupChecks.installCrashDialog();
            Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
            Thread worker = new Thread(() -> { }, "mod update worker");
            JsonSyntaxException invalidJson = new JsonSyntaxException("Expected BEGIN_OBJECT but was STRING");
            installed.uncaughtException(worker, invalidJson);
            verify(logger).error(UNHANDLED, (Object) worker.getName(), (Object) invalidJson);
            assertEquals(List.of(invalidJson), forwarded);
            boxes.verifyNoInteractions();

            installed.uncaughtException(Thread.currentThread(), new IllegalStateException("boom"));
            boxes.verify(() -> MessageBoxUtil.showError(CRASH_TITLE, crashMessage("IllegalStateException", "boom")));
        }
    }

    // Checks that a throwable without a message shows the word null
    @Test
    void crashDialogShowsNullMessage() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> { });
        try (MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            StartupChecks.installCrashDialog();
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new NullPointerException());
            boxes.verify(() -> MessageBoxUtil.showError(CRASH_TITLE, crashMessage("NullPointerException", "null")));
        }
    }

    // Checks that a null throwable leaves the dialog for the next real crash
    @Test
    void nullThrowableDoesNotUseUpTheDialog() {
        List<Throwable> seen = Collections.synchronizedList(new ArrayList<>());
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> seen.add(throwable));
        try (MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            StartupChecks.installCrashDialog();
            Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
            RuntimeException real = new IllegalStateException("real");
            assertDoesNotThrow(() -> installed.uncaughtException(Thread.currentThread(), null));
            installed.uncaughtException(Thread.currentThread(), real);
            boxes.verify(() -> MessageBoxUtil.showError(CRASH_TITLE, crashMessage("IllegalStateException", "real")));
            boxes.verifyNoMoreInteractions();
            assertEquals(Arrays.asList(null, real), seen);
        }
    }

    // Checks that a throwable whose message accessor fails still reaches the previous handler
    @Test
    void failingMessageStillForwards() {
        AtomicInteger forwarded = new AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> forwarded.incrementAndGet());
        RuntimeException broken = new RuntimeException() {
            // Throws instead of returning a message.
            @Override
            public String getMessage() {
                throw new IllegalStateException("message unavailable");
            }
        };
        try (MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            StartupChecks.installCrashDialog();
            assertDoesNotThrow(() -> Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), broken));
            boxes.verifyNoInteractions();
        }
        assertEquals(1, forwarded.get());
    }

    // Checks that a dialog that throws still lets the crash reach the previous handler
    @Test
    void failingDialogStillForwards() {
        AtomicInteger forwarded = new AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> forwarded.incrementAndGet());
        try (MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            boxes.when(() -> MessageBoxUtil.showError(anyString(), anyString())).thenThrow(new IllegalStateException("no display"));
            StartupChecks.installCrashDialog();
            assertDoesNotThrow(() -> Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new RuntimeException("x")));
        }
        assertEquals(1, forwarded.get());
    }

    // Checks that a crash without a previous handler is logged with the thread name and throwable
    @Test
    void crashWithoutPreviousHandlerIsLogged() {
        Logger logger = mockLogger();
        Thread.setDefaultUncaughtExceptionHandler(null);
        StartupChecks.installCrashDialog();
        RuntimeException crash = new RuntimeException("logged");
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(new Thread(() -> { }, "worker"), crash);
        verify(logger).error(UNHANDLED, (Object) "worker", (Object) crash);
    }

    // Checks that a null thread and null throwable without a previous handler are logged without throwing
    @Test
    void nullThreadAndThrowableAreLogged() {
        Logger logger = mockLogger();
        Thread.setDefaultUncaughtExceptionHandler(null);
        StartupChecks.installCrashDialog();
        assertDoesNotThrow(() -> Thread.getDefaultUncaughtExceptionHandler().uncaughtException(null, null));
        verify(logger).error(UNHANDLED, (Object) null, (Object) null);
    }

    // Checks that simultaneous worker failures all reach the previous handler
    @Test
    @Timeout(30)
    void concurrentCrashesAllForward() throws InterruptedException {
        AtomicInteger forwarded = new AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> forwarded.incrementAndGet());
        StartupChecks.installCrashDialog();
        Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                installed.uncaughtException(Thread.currentThread(), new RuntimeException("parallel"));
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(8, forwarded.get());
        assertFalse(((AtomicBoolean) Statics.get(StartupChecks.class, "CRASH_DIALOG_SHOWN")).get());
    }

    // Checks that the asynchronous run returns at once and then runs every check
    @Test
    @Timeout(60)
    void runAsyncRunsChecksInBackground() {
        assumeShell();
        windows();
        fakeTaskList("sleep 1; echo '\"RTSS.exe\",\"4\"'", "tasklist");
        StartupChecks.runAsync(new GlContextInfo("NVIDIA Corporation", "GeForce", "4.6"));
        assertFalse(overlayFlagged());
        await(() -> count(OVERLAY_TITLE) == 1);
        assertTrue(Workarounds.isActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS));
        assertTrue(overlayFlagged());
    }

    // Checks that the asynchronous run with a null context still scans for overlays
    @Test
    @Timeout(60)
    void runAsyncToleratesNullContext() {
        assumeShell();
        windows();
        fakeTaskList("echo rtss", "tasklist");
        assertDoesNotThrow(() -> StartupChecks.runAsync(null));
        await(() -> count(OVERLAY_TITLE) == 1);
        assertTrue(overlayFlagged());
    }

    // Checks that an NVIDIA adapter version no longer triggers a warning
    @Test
    void runSkipsNvidiaDriverVersionWarning() {
        assumeShell();
        windows();
        fakeTaskList("echo rtss", "tasklist");
        List<GraphicsAdapterInfo> adapters = List.of(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "GTX", "471.11"));
        try (MockedStatic<GraphicsAdapterProbe> probe = mockStatic(GraphicsAdapterProbe.class)) {
            probe.when(() -> GraphicsAdapterProbe.adapters(anyLong())).thenReturn(adapters);
            Statics.call(StartupChecks.class, "run", new GlContextInfo("NVIDIA Corporation", "GeForce", "4.6"));
            probe.verify(() -> GraphicsAdapterProbe.adapters(10_000L));
        }
        assertEquals(0, count(NVIDIA_TITLE));
        assertTrue(Workarounds.isActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS));
        assertTrue(overlayFlagged());
    }

    // Checks that a failing probe still lets the workarounds and the overlay scan run
    @Test
    void runSurvivesProbeFailure() {
        assumeShell();
        windows();
        fakeTaskList("echo rtss", "tasklist");
        try (MockedStatic<GraphicsAdapterProbe> probe = mockStatic(GraphicsAdapterProbe.class)) {
            probe.when(() -> GraphicsAdapterProbe.adapters(anyLong())).thenThrow(new IllegalStateException("probe failed"));
            assertDoesNotThrow(() -> Statics.call(StartupChecks.class, "run", new GlContextInfo("NVIDIA Corporation", "GeForce", "4.6")));
        }
        assertTrue(Workarounds.isActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS));
        assertTrue(overlayFlagged());
    }

    // Checks that a null context still lets the later checks run
    @Test
    void runSurvivesNullContext() {
        assumeShell();
        windows();
        fakeTaskList("echo rtss", "tasklist");
        Logger logger = mockLogger();
        try (MockedStatic<GraphicsAdapterProbe> probe = mockStatic(GraphicsAdapterProbe.class)) {
            probe.when(() -> GraphicsAdapterProbe.adapters(anyLong())).thenReturn(List.of());
            assertDoesNotThrow(() -> Statics.call(StartupChecks.class, "run", (Object) null));
        }
        verify(logger).warn(INCOMPLETE);
        assertTrue(overlayFlagged());
    }

    // Checks that an error thrown by a warning dialog stops neither the overlay scan nor run
    @Test
    void runSurvivesErrorsFromChecks() {
        assumeShell();
        windows();
        fakeTaskList("echo rtss", "tasklist");
        try (MockedStatic<GraphicsAdapterProbe> probe = mockStatic(GraphicsAdapterProbe.class);
             MockedStatic<MessageBoxUtil> boxes = mockStatic(MessageBoxUtil.class)) {
            probe.when(() -> GraphicsAdapterProbe.adapters(anyLong()))
                    .thenReturn(List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD Graphics", "10.18.10.3496")));
            boxes.when(() -> MessageBoxUtil.showWarning(anyString(), anyString())).thenThrow(new LinkageError("no dialog"));
            assertDoesNotThrow(() -> Statics.call(StartupChecks.class, "run", new GlContextInfo("Intel", "HD Graphics", "4.6")));
            boxes.verify(() -> MessageBoxUtil.showWarning(eq("Impetus — Driver workaround active"), anyString()));
            boxes.verify(() -> MessageBoxUtil.showWarning(eq("Impetus — Incompatible software detected"), anyString()));
        }
        assertTrue(overlayFlagged());
        assertEquals(1, count(OVERLAY_TITLE));
    }

    // Checks that a healthy setup produces no warning
    @Test
    void bugChecksStayQuietForHealthySetup() {
        assumeDesktop();
        Logger logger = mockLogger();
        Statics.call(StartupChecks.class, "runBugChecks", new GlContextInfo("NVIDIA", "RTX", "4.6"));
        verify(logger, never()).warn(anyString());
        assertTrue(ImpetusNotifications.getVisible().isEmpty());
    }

    // Checks that an active no error context workaround is announced
    @Test
    void bugChecksAnnounceNoErrorContextWorkaround() {
        Workarounds.markActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE);
        Statics.call(StartupChecks.class, "runBugChecks", new GlContextInfo("Intel", "HD", "4.6"));
        assertEquals(1, count("Driver workaround active"));
    }

    // Checks that a missing vendor or renderer is logged once for every shape of missing value
    @Test
    void bugChecksLogIncompleteContext() {
        Logger logger = mockLogger();
        GlContextInfo[] incomplete = {
                new GlContextInfo("", "RTX", "4.6"),
                new GlContextInfo("NVIDIA", "", "4.6"),
                new GlContextInfo("", "", ""),
                new GlContextInfo(null, "RTX", "4.6"),
                new GlContextInfo("NVIDIA", null, "4.6"),
                new GlContextInfo(null, null, null),
                null
        };
        for (GlContextInfo context : incomplete) {
            assertDoesNotThrow(() -> Statics.call(StartupChecks.class, "runBugChecks", context), String.valueOf(context));
        }
        verify(logger, times(incomplete.length)).warn(INCOMPLETE);
    }

    // Checks that a blank but non empty vendor and a missing version are not reported
    @Test
    void bugChecksAcceptBlankVendorAndMissingVersion() {
        Logger logger = mockLogger();
        Statics.call(StartupChecks.class, "runBugChecks", new GlContextInfo(" ", " ", ""));
        Statics.call(StartupChecks.class, "runBugChecks", new GlContextInfo("NVIDIA", "RTX", null));
        verify(logger, never()).warn(INCOMPLETE);
    }

    // Checks that an Android runtime name marks PojavLauncher and warns
    @Test
    void pojavDetectedFromRuntimeName() {
        System.setProperty("java.runtime.name", "Android Runtime");
        assertTrue((boolean) Statics.call(StartupChecks.class, "isPojavLauncher"));
        Statics.call(StartupChecks.class, "warnIfPojavLauncher");
        assertEquals(1, count("Unsupported launcher"));
    }

    // Checks that a Dalvik virtual machine name in upper case marks PojavLauncher
    @Test
    void pojavDetectedFromVmName() {
        System.setProperty("java.vm.name", "DALVIK");
        assertTrue((boolean) Statics.call(StartupChecks.class, "isPojavLauncher"));
    }

    // Checks that a desktop runtime and absent or swapped properties do not mark PojavLauncher
    @Test
    void pojavNotDetectedOnDesktop() {
        assumeDesktop();
        assertFalse((boolean) Statics.call(StartupChecks.class, "isPojavLauncher"));
        Statics.call(StartupChecks.class, "warnIfPojavLauncher");
        assertTrue(ImpetusNotifications.getVisible().isEmpty());

        System.setProperty("java.runtime.name", "Dalvik");
        System.setProperty("java.vm.name", "Android");
        assertFalse((boolean) Statics.call(StartupChecks.class, "isPojavLauncher"));

        System.clearProperty("java.runtime.name");
        System.clearProperty("java.vm.name");
        assertFalse((boolean) Statics.call(StartupChecks.class, "isPojavLauncher"));
    }

    // Checks environment variables that are set or unset or empty
    @Test
    void envPresentRequiresNonEmptyValue() {
        assumeTrue(System.getenv("PATH") != null && !System.getenv("PATH").isEmpty());
        assertTrue((boolean) Statics.call(StartupChecks.class, "envPresent", "PATH"));
        assertFalse((boolean) Statics.call(StartupChecks.class, "envPresent", "IMPETUS_UNSET_" + System.nanoTime()));
        Optional<String> empty = System.getenv().entrySet().stream().filter(e -> e.getValue().isEmpty()).map(Map.Entry::getKey).findFirst();
        empty.ifPresent(key -> assertFalse((boolean) Statics.call(StartupChecks.class, "envPresent", key)));
    }

    // Checks property lookups that are absent or mixed case or folded
    @Test
    void propertyContainsFoldsCase() {
        Object dalvik = needle("dalvik");
        System.setProperty("java.vm.name", "Dalvik VM");
        assertTrue((boolean) Statics.call(StartupChecks.class, "propertyContains", "java.vm.name", dalvik));
        System.setProperty("java.vm.name", "Dalv\u0130k");
        assertFalse((boolean) Statics.call(StartupChecks.class, "propertyContains", "java.vm.name", dalvik));
        System.setProperty("java.vm.name", "DALV\u0131K");
        assertFalse((boolean) Statics.call(StartupChecks.class, "propertyContains", "java.vm.name", dalvik));
        System.setProperty("java.vm.name", "DALVI\u212A");
        assertTrue((boolean) Statics.call(StartupChecks.class, "propertyContains", "java.vm.name", dalvik));
        assertFalse((boolean) Statics.call(StartupChecks.class, "propertyContains", "impetus.unset." + System.nanoTime(), dalvik));
    }

    // Checks every character in every needle position against the Locale.ROOT lower case form
    @Test
    void needleAgreesWithRootLowerCaseForEveryCharacter() {
        for (String word : List.of("android", "dalvik", "rtss", "pi", "ik", "aab")) {
            Object needle = needle(word);
            char[] replaced = word.toCharArray();
            char[] inserted = new char[word.length() + 1];
            for (int c = 0; c <= Character.MAX_VALUE; c++) {
                for (int j = 0; j < replaced.length; j++) {
                    char original = replaced[j];
                    replaced[j] = (char) c;
                    assertAgrees(needle, word, new String(replaced));
                    replaced[j] = original;
                }
                for (int j : new int[]{0, word.length() / 2, word.length()}) {
                    word.getChars(0, j, inserted, 0);
                    inserted[j] = (char) c;
                    word.getChars(j, word.length(), inserted, j + 1);
                    assertAgrees(needle, word, new String(inserted));
                }
            }
        }
    }

    // Checks random needles and texts against the Locale.ROOT lower case form
    @Test
    void needleAgreesWithRootLowerCaseOnRandomText() {
        SplittableRandom random = new SplittableRandom(0x5EEDL);
        char[] letters = {'a', 'b', 'i', 'k', 's'};
        for (int round = 0; round < 200_000; round++) {
            String word = randomText(random, letters, 1, 4);
            String text = randomText(random, FOLDING_ALPHABET, 0, 12);
            Object needle = needle(word);
            boolean expected = referenceContains(text, word);
            assertEquals(expected, occursIn(needle, text), () -> word + " in " + escape(text));
            if (round % 16 == 0) {
                assertEquals(expected, occursInStream(needle, new TrickleReader(text, 1)), () -> word + " streamed in " + escape(text));
            }
        }
    }

    // Checks the dotted capital I which completes a match only as the last needle character
    @Test
    void dottedCapitalIMatchesOnlyAtTheEnd() {
        assertTrue(occursIn(needle("pi"), "P\u0130"));
        assertFalse(occursIn(needle("ip"), "\u0130P"));
        assertFalse(occursIn(needle("pid"), "P\u0130D"));
        assertTrue(occursIn(needle("i"), "\u0130"));
        assertTrue(occursIn(needle("k"), "\u212A"));
        assertFalse(occursIn(needle("i"), "\u0131"));
        assertFalse(occursIn(needle("s"), "\u017F"));
    }

    // Checks needles whose prefixes repeat inside them
    @Test
    void needleRecoversAfterPartialMatch() {
        assertTrue(occursIn(needle("aab"), "aaab"));
        assertTrue(occursIn(needle("abab"), "abaabab"));
        assertTrue(occursIn(needle("aaaa"), "aaabaaaa"));
        assertFalse(occursIn(needle("aaaa"), "aaabaaa"));
        assertTrue(occursIn(needle("abcabd"), "abcabcabd"));
    }

    // Checks empty needles and empty texts
    @Test
    void emptyNeedleAndEmptyText() {
        assertTrue(occursIn(needle(""), ""));
        assertTrue(occursIn(needle(""), "anything"));
        assertTrue(occursInStream(needle(""), new StringReader("")));
        assertFalse(occursIn(needle("rtss"), ""));
        assertFalse(occursInStream(needle("rtss"), new StringReader("")));
        assertTrue(occursIn(needle("rtss"), "RTSS"));
        assertFalse(occursIn(needle("rtss"), "RTS"));
    }

    // Checks that null text or a null reader is rejected
    @Test
    void needleRejectsNullInput() {
        Object needle = needle("rtss");
        assertThrows(NullPointerException.class, () -> occursIn(needle, null));
        assertThrows(NullPointerException.class, () -> occursInStream(needle, null));
        assertThrows(NullPointerException.class, () -> needle(null));
    }

    // Checks matches that straddle a stream chunk boundary or arrive one character at a time
    @Test
    void streamMatchesAcrossChunkBoundaries() {
        Object rtss = needle("rtss");
        for (int offset = STREAM_CHUNK - 4; offset <= STREAM_CHUNK + 1; offset++) {
            String text = "x".repeat(offset) + "RtSs" + "y".repeat(10);
            assertTrue(occursInStream(rtss, new StringReader(text)), "offset " + offset);
            assertTrue(occursInStream(rtss, new TrickleReader(text, 7)), "offset " + offset);
        }
        assertFalse(occursInStream(rtss, new StringReader("x".repeat(STREAM_CHUNK - 2) + "rt\nss" + "x".repeat(STREAM_CHUNK))));
        assertTrue(occursInStream(rtss, new TrickleReader("..rtss..", 1)));
    }

    // Checks that a stream is read to its end after a match and without one
    @Test
    void streamDrainsReader() {
        TrickleReader matched = new TrickleReader("rtss" + "z".repeat(3 * STREAM_CHUNK), 1000);
        assertTrue(occursInStream(needle("rtss"), matched));
        assertTrue(matched.exhausted);
        TrickleReader unmatched = new TrickleReader("z".repeat(3 * STREAM_CHUNK), 1000);
        assertFalse(occursInStream(needle("rtss"), unmatched));
        assertTrue(unmatched.exhausted);
    }

    // Checks that a read failure reaches the caller
    @Test
    void streamPropagatesReadFailure() {
        TrickleReader failing = new TrickleReader("rtss and more", 2);
        failing.failAt = 6;
        assertThrows(IOException.class, () -> occursInStream(needle("rtss"), failing));
    }

    // Checks that the scan runs nothing outside Windows
    @Test
    void scanSkipsOtherSystems() {
        assumeShell();
        Statics.set(OsKind.class, "detected", OsKind.LINUX);
        Path marker = temp.resolve("ran");
        fakeTaskList("touch \"$0\"; echo rtss", marker.toString());
        scan();
        assertFalse(Files.exists(marker));
        assertFalse(overlayFlagged());
    }

    // Checks that RTSS in any case in the task list is reported on every scan
    @Test
    void scanFlagsRtss() {
        assumeShell();
        windows();
        fakeTaskList("echo '\"System\",\"4\"'; echo '\"RtSs.exe\",\"1234\"'", "tasklist");
        scan();
        assertTrue(overlayFlagged());
        assertEquals(1, count(OVERLAY_TITLE));
        scan();
        assertTrue(overlayFlagged());
        assertEquals(1, count(OVERLAY_TITLE));
    }

    // Checks task lists without RTSS or with RTSS split over lines
    @Test
    void scanIgnoresTaskListWithoutRtss() {
        assumeShell();
        windows();
        fakeTaskList("printf 'rt\\nss\\nRTS\\n\"svchost.exe\",\"1\"\\n'", "tasklist");
        scan();
        fakeTaskList("true", "tasklist");
        scan();
        assertFalse(overlayFlagged());
        assertEquals(0, count(OVERLAY_TITLE));
    }

    // Checks that output far larger than a pipe is drained with RTSS first or last
    @Test
    @Timeout(60)
    void scanDrainsLargeTaskList() {
        assumeShell();
        windows();
        fakeTaskList("echo RTSS.exe; yes '\"svchost.exe\",\"1234\",\"Services\",\"0\",\"10,000 K\"' | head -n 20000", "tasklist");
        scan();
        assertTrue(overlayFlagged());
        Workarounds.init(new GlContextInfo("", "", ""), List.of());
        fakeTaskList("yes '\"svchost.exe\",\"1234\"' | head -n 20000; echo RTSS.exe", "tasklist");
        scan();
        assertTrue(overlayFlagged());
    }

    // Checks that a failing exit status still reports RTSS
    @Test
    void scanIgnoresExitStatus() {
        assumeShell();
        windows();
        fakeTaskList("echo rtss; exit 3", "tasklist");
        scan();
        assertTrue(overlayFlagged());
    }

    // Checks that a task list that cannot start is ignored
    @Test
    void scanIgnoresMissingCommand() {
        windows();
        command()[0] = temp.resolve("missing").toString();
        assertDoesNotThrow(this::scan);
        assertFalse(overlayFlagged());
    }

    // Checks that a task list that outlives the timeout is killed and not reported
    @Test
    @Timeout(60)
    void scanKillsTaskListAfterTimeout() throws IOException {
        assumeShell();
        windows();
        Path pidFile = temp.resolve("pid");
        fakeTaskList("echo $$ > \"$0\"; echo rtss; exec >&- 2>&-; exec sleep 60", pidFile.toString());
        scan();
        assertFalse(overlayFlagged());
        assertProcessEnds(pidFile);
    }

    // Checks that an interrupt during the scan kills the task list and stays set
    @Test
    @Timeout(60)
    void scanRestoresInterruptAndKillsTaskList() throws IOException {
        assumeShell();
        windows();
        Path pidFile = temp.resolve("pid");
        fakeTaskList("echo $$ > \"$0\"; echo rtss; exec >&- 2>&-; exec sleep 60", pidFile.toString());
        Thread.currentThread().interrupt();
        scan();
        assertTrue(Thread.interrupted());
        assertFalse(overlayFlagged());
        assertProcessEnds(pidFile);
    }

    // Returns the crash dialog text for a throwable class name and message
    private static String crashMessage(String simpleName, String message) {
        return "The Minecraft client thread stopped after an uncaught exception.\n\n" +
                "Error: " + simpleName + ": " + message + "\n\n" +
                "See the launcher console or logs/latest.log for the stack trace.";
    }

    // Replaces the logger of the checks with a mock
    private static Logger mockLogger() {
        Logger logger = mock(Logger.class);
        Statics.set(StartupChecks.class, "LOGGER", logger);
        return logger;
    }

    // Clears both crash dialog flags
    private static void resetCrashFlags() {
        ((AtomicBoolean) Statics.get(StartupChecks.class, "CRASH_DIALOG_INSTALLED")).set(false);
        ((AtomicBoolean) Statics.get(StartupChecks.class, "CRASH_DIALOG_SHOWN")).set(false);
    }

    // Removes every queued notification
    private static void clearNotifications() {
        List<?> queued = Statics.get(ImpetusNotifications.class, "NOTIFICATIONS");
        synchronized (ImpetusNotifications.class) {
            queued.clear();
        }
    }

    // Returns the number of visible notifications with the title
    private static long count(String title) {
        return ImpetusNotifications.getVisible().stream().filter(n -> n.title().equals(title)).count();
    }

    // Sets a system property or clears it when the value is null
    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    // Skips the test when no PojavLauncher variable may be ruled out
    private static void assumeDesktop() {
        for (String variable : POJAV_VARIABLES) {
            assumeTrue(System.getenv(variable) == null || System.getenv(variable).isEmpty(), variable);
        }
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        assumeTrue(!runtime.contains("android") && !vm.contains("dalvik"));
    }

    // Skips the test when no POSIX shell is available
    private static void assumeShell() {
        assumeTrue(Files.isExecutable(SHELL));
    }

    // Makes the checks see Windows
    private static void windows() {
        Statics.set(OsKind.class, "detected", OsKind.WINDOWS);
    }

    // Returns the live task list command array
    private static String[] command() {
        return Statics.get(StartupChecks.class, "TASKLIST_COMMAND");
    }

    // Replaces the task list command with a shell script and its zeroth argument
    private static void fakeTaskList(String script, String argument) {
        String[] command = command();
        assertEquals(4, command.length);
        command[0] = SHELL.toString();
        command[1] = "-c";
        command[2] = script;
        command[3] = argument;
    }

    // Runs the overlay scan on the current thread
    private void scan() {
        Statics.call(StartupChecks.class, "scanForFrameHookOverlays");
    }

    // Returns whether the overlay workaround is active
    private static boolean overlayFlagged() {
        return Workarounds.isActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
    }

    // Waits up to thirty seconds for the condition
    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "condition not reached");
            pause();
        }
    }

    // Asserts that the process named in the pid file ends within ten seconds
    private static void assertProcessEnds(Path pidFile) throws IOException {
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            assertTrue(System.nanoTime() < deadline, "process " + pid + " still running");
            pause();
        }
    }

    // Sleeps for ten milliseconds
    private static void pause() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    // Asserts that the needle agrees with the Locale.ROOT lower case form of the text
    private static void assertAgrees(Object needle, String word, String text) {
        if (referenceContains(text, word) != occursIn(needle, text)) {
            throw new AssertionError(word + " in " + escape(text));
        }
    }

    // Returns whether the Locale.ROOT lower case form of the text contains the word
    private static boolean referenceContains(String text, String word) {
        return text.toLowerCase(Locale.ROOT).contains(word);
    }

    // Returns random text from the alphabet with a length between the bounds
    private static String randomText(SplittableRandom random, char[] alphabet, int minLength, int maxLength) {
        char[] text = new char[random.nextInt(minLength, maxLength + 1)];
        for (int i = 0; i < text.length; i++) {
            text[i] = alphabet[random.nextInt(alphabet.length)];
        }
        return new String(text);
    }

    // Returns the text with every character outside printable ASCII written as a Unicode escape
    private static String escape(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= ' ' && c < 0x7F) {
                escaped.append(c);
            } else {
                escaped.append(String.format("\\u%04X", (int) c));
            }
        }
        return escaped.toString();
    }

    // Creates a lower case needle
    private static Object needle(String word) {
        return construct(NEEDLE_OF, word);
    }

    // Returns whether the needle occurs in the text
    private static boolean occursIn(Object needle, String text) {
        return (boolean) invoke(OCCURS_IN, needle, text);
    }

    // Returns whether the needle occurs in the reader
    private static boolean occursInStream(Object needle, Reader reader) {
        return (boolean) invoke(OCCURS_IN_STREAM, needle, reader);
    }

    // Returns the nested class of the checks with the simple name
    private static Class<?> nested(String name) {
        try {
            return Class.forName(StartupChecks.class.getName() + "$" + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    // Returns an accessible declared method
    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    // Returns an accessible declared constructor
    private static Constructor<?> constructor(Class<?> owner, Class<?>... parameters) {
        try {
            Constructor<?> constructor = owner.getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    // Invokes a reflected method and rethrows what it threw
    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw StartupChecksTest.<RuntimeException>rethrow(e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // Invokes a reflected constructor and rethrows what it threw
    private static Object construct(Constructor<?> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw StartupChecksTest.<RuntimeException>rethrow(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Throws any throwable without declaring it
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T rethrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    // Reader that hands out a bounded number of characters per read and can fail after a position
    private static final class TrickleReader extends Reader {
        // Text served by the reader
        private final String text;
        // Largest number of characters returned by one read
        private final int chunk;
        // Position of the next character
        private int position;
        // Position at which reads throw or minus one for never
        private int failAt = -1;
        // Flag set once a read reports the end of the text
        private boolean exhausted;

        // Creates a reader over the text with the chunk limit
        TrickleReader(String text, int chunk) {
            this.text = text;
            this.chunk = chunk;
        }

        // Copies up to the chunk limit and reports the end or throws at the failure position
        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (failAt >= 0 && position >= failAt) {
                throw new IOException("read failed at " + position);
            }
            if (position == text.length()) {
                exhausted = true;
                return -1;
            }
            int count = Math.min(Math.min(length, chunk), text.length() - position);
            text.getChars(position, position + count, buffer, offset);
            position += count;
            return count;
        }

        // Closes nothing
        @Override
        public void close() {
        }
    }
}
