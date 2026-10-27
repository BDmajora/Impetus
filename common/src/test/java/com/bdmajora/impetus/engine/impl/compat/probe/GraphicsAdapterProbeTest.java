package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.testing.FreshClasses;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Tests the display adapter probe GraphicsAdapterProbe
class GraphicsAdapterProbeTest {
    // DRM directory of the running machine
    private static final Path DRM = Path.of("/sys/class/drm");
    // Time limit in nanoseconds of the commands that finish
    private static final long FINISH = TimeUnit.SECONDS.toNanos(30);
    // Time limit in nanoseconds of the commands that outlast it
    private static final long SHORT = TimeUnit.MILLISECONDS.toNanos(300);
    // Upper bound in nanoseconds on the return of a command that outlasts its time limit
    private static final long PROMPT = TimeUnit.SECONDS.toNanos(3);
    // Wait in milliseconds for anything a test expects to finish
    private static final long PATIENCE_MILLIS = 10_000;
    // Patterns of the vendor classification with ati standing for an ati that ends at the separator
    private static final String[] PATTERNS = {"nvidia", "amd", "ati ", "ati", "radeon", "intel"};
    // Fragments the random CIM lines are joined from
    private static final String[] FRAGMENTS = {
            "nvidia", "NVIDIA", "nVidia", "nv", "idia", "amd", "AMD", "am", "d", "ati", "ATI", "at", "i", " ", "  ",
            "|", "radeon", "RADEON", "rad", "eon", "intel", "INTEL", "int", "el", "x", "1.0", "\t", "\u0000",
            "\u00A0", "\u0130", "\u0131", "\u212A", "\u0307"};
    // Fragments the random device ids are joined from
    private static final String[] DEVICE_FRAGMENTS = {"", "0", "x", "0x", "X", "0X", "00", "xx", "2f04", "a", "\u00E9"};
    // Vendor ids at the hex digit boundaries and the int limits
    private static final int[] VENDOR_IDS = {
            0, 1, 0xF, 0x10, 0xFFF, 0x1000, 0x10DE, 0xFFFF, 0x10000, 0x12345, 0xFFFFFFF, 0x10000000,
            Integer.MAX_VALUE, Integer.MIN_VALUE, -1};
    // Number of random inputs compared with each reference
    private static final int RANDOM_INPUTS = 200_000;
    // Adapter of a typical Linux NVIDIA card
    private static final GraphicsAdapterInfo LINUX_NVIDIA = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "PCI 10de:2f04", "615.71.09");

    // Classification of the running JVM restored after each test
    private final OsKind realOs = OsKind.current();
    // Shared probe restored after each test
    private CompletableFuture<List<GraphicsAdapterInfo>> realShared;

    // Stores the shared probe of the launch
    @BeforeEach
    void saveShared() {
        realShared = shared().get();
    }

    // Restores the shared probe and the classification and clears the interrupt flag
    @AfterEach
    void restore() {
        shared().set(realShared);
        Statics.set(OsKind.class, "detected", realOs);
        Thread.interrupted();
    }

    // Returns the holder of the shared probe
    private static AtomicReference<CompletableFuture<List<GraphicsAdapterInfo>>> shared() {
        return Statics.get(GraphicsAdapterProbe.class, "SHARED");
    }

    // Returns the result of the private static method of the probe
    private static <T> T call(String name, Object... args) {
        return Statics.call(GraphicsAdapterProbe.class, name, args);
    }

    // Returns the method of a fresh uninstrumented copy of the probe
    private static Method fresh(String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = new FreshClasses(GraphicsAdapterProbe.class).load(GraphicsAdapterProbe.class).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    // Returns the adapters the sysfs walk finds below the directory
    private static List<GraphicsAdapterInfo> sysFs(Path drm) {
        return call("probeLinuxSysFs", drm);
    }

    // Returns the output lines of the command run with the time limit in nanoseconds
    private static List<String> run(long timeoutNanos, String... command) {
        return call("runProcess", timeoutNanos, command);
    }

    // Returns the adapters parsed from the CIM lines
    private static List<GraphicsAdapterInfo> cim(String... lines) {
        return call("parseCimLines", Arrays.asList(lines));
    }

    // Returns the lines read from the UTF 8 bytes of the text
    private static List<String> lines(String text) {
        return call("readLines", new ByteArrayInputStream(text.getBytes(UTF_8)));
    }

    // Returns the file contents the probe reads
    private static String readTrimmed(Path path) {
        return call("readTrimmed", path);
    }

    // Returns true when the probe takes the entry for a card
    private static boolean isCard(Path entry) {
        return call("isCardNode", entry);
    }

    // Returns the vendor the lower case reference finds in the text
    private static GraphicsVendor referenceVendor(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("nvidia")) {
            return GraphicsVendor.NVIDIA;
        } else if (lower.contains("amd") || lower.contains("ati ") || lower.contains("radeon")) {
            return GraphicsVendor.AMD;
        } else if (lower.contains("intel")) {
            return GraphicsVendor.INTEL;
        }
        return GraphicsVendor.OTHER;
    }

    // Returns the adapters the split reference parses from the lines
    private static List<GraphicsAdapterInfo> referenceCim(List<String> lines) {
        List<GraphicsAdapterInfo> results = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3 || parts[1].trim().isEmpty()) {
                continue;
            }
            results.add(new GraphicsAdapterInfo(referenceVendor(parts[0] + " " + parts[1]), parts[1].trim(), parts[2].trim()));
        }
        return results;
    }

    // Returns the name the format reference builds from the ids
    private static String referenceName(int vendorId, String deviceId) {
        return String.format(Locale.ROOT, "PCI %04x:%s", vendorId, deviceId.replace("0x", ""));
    }

    // Returns the int the decode reference reads or zero when it throws
    private static int referenceId(String value) {
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Creates a card entry with the device files whose text is not null
    private static void card(Path drm, String name, String vendor, String device, String version) throws IOException {
        Path dir = Files.createDirectories(drm.resolve(name).resolve("device"));
        if (vendor != null) {
            Files.writeString(dir.resolve("vendor"), vendor);
        }
        if (device != null) {
            Files.writeString(dir.resolve("device"), device);
        }
        if (version != null) {
            Files.writeString(Files.createDirectories(dir.resolve("driver/module")).resolve("version"), version);
        }
    }

    // Returns true when a directory of the PATH holds an executable of the name
    private static boolean onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            try {
                if (!dir.isEmpty() && (Files.isExecutable(Path.of(dir, name)) || Files.isExecutable(Path.of(dir, name + ".exe")))) {
                    return true;
                }
            } catch (InvalidPathException e) {
                // Skips an entry that is not a path
            }
        }
        return false;
    }

    // Returns the nanoseconds since the start
    private static long since(long start) {
        return System.nanoTime() - start;
    }

    // Verifies the class is final with one private constructor and exactly three public static methods
    @Test
    void classShapeIsStable() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(GraphicsAdapterProbe.class.getModifiers()));
        Constructor<?>[] constructors = GraphicsAdapterProbe.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
        Set<String> publicMethods = new TreeSet<>();
        for (Method method : GraphicsAdapterProbe.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertTrue(Modifier.isStatic(method.getModifiers()), method::getName);
                publicMethods.add(method.getName() + Arrays.toString(method.getParameterTypes()));
            }
        }
        assertEquals(Set.of("adapters[long]", "prefetch[]", "probe[]"), publicMethods);
    }

    // Verifies prefetch stores one future and returns it on every later call
    @Test
    void prefetchReturnsOneFutureOnRepeatedCalls() throws Exception {
        shared().set(null);
        CompletableFuture<List<GraphicsAdapterInfo>> first = GraphicsAdapterProbe.prefetch();
        assertSame(first, shared().get());
        for (int i = 0; i < 100; i++) {
            assertSame(first, GraphicsAdapterProbe.prefetch());
        }
        assertEquals(Set.copyOf(GraphicsAdapterProbe.probe()), Set.copyOf(first.get(PATIENCE_MILLIS, TimeUnit.MILLISECONDS)));
    }

    // Verifies prefetch returns a stored probe and leaves it unfinished
    @Test
    void prefetchKeepsAStoredProbe() {
        CompletableFuture<List<GraphicsAdapterInfo>> stored = new CompletableFuture<>();
        shared().set(stored);
        assertSame(stored, GraphicsAdapterProbe.prefetch());
        assertSame(stored, GraphicsAdapterProbe.prefetch());
        assertFalse(stored.isDone());
    }

    // Verifies concurrent first calls of prefetch all receive the one stored future
    @Test
    void prefetchRacesToOneFuture() throws Exception {
        int threads = 32;
        shared().set(null);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<CompletableFuture<List<GraphicsAdapterInfo>>>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(pool.submit(() -> {
                    barrier.await();
                    return GraphicsAdapterProbe.prefetch();
                }));
            }
            Set<CompletableFuture<List<GraphicsAdapterInfo>>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var call : calls) {
                seen.add(call.get(PATIENCE_MILLIS, TimeUnit.MILLISECONDS));
            }
            assertEquals(1, seen.size());
            assertSame(shared().get(), seen.iterator().next());
            assertNotNull(shared().get().get(PATIENCE_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    // Verifies adapters returns the list of a finished probe by identity at every timeout
    @Test
    void adaptersReturnsAFinishedProbeAtEveryTimeout() {
        List<GraphicsAdapterInfo> list = List.of(LINUX_NVIDIA);
        shared().set(CompletableFuture.completedFuture(list));
        for (long timeout : new long[]{Long.MIN_VALUE, -1, 0, 1, PATIENCE_MILLIS, Long.MAX_VALUE}) {
            assertSame(list, GraphicsAdapterProbe.adapters(timeout), Long.toString(timeout));
        }
    }

    // Verifies adapters returns the shared empty list for an unfinished probe at every short timeout
    @Test
    void adaptersGivesUpOnAnUnfinishedProbe() {
        shared().set(new CompletableFuture<>());
        assertTimeoutPreemptively(Duration.ofMillis(PATIENCE_MILLIS), () -> {
            for (long timeout : new long[]{Long.MIN_VALUE, -1, 0, 1, 50}) {
                assertSame(Collections.emptyList(), GraphicsAdapterProbe.adapters(timeout), Long.toString(timeout));
            }
        });
    }

    // Verifies adapters waits for a probe that finishes within the timeout
    @Test
    void adaptersWaitsForALateProbe() {
        CompletableFuture<List<GraphicsAdapterInfo>> late = new CompletableFuture<>();
        List<GraphicsAdapterInfo> list = List.of(LINUX_NVIDIA);
        shared().set(late);
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> late.complete(list));
        assertSame(list, GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));
    }

    // Verifies an interrupted wait keeps the interrupt flag and a finished probe answers regardless
    @Test
    void adaptersKeepsTheInterruptFlag() {
        shared().set(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        assertSame(Collections.emptyList(), GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));
        assertTrue(Thread.interrupted());

        List<GraphicsAdapterInfo> list = List.of(LINUX_NVIDIA);
        shared().set(CompletableFuture.completedFuture(list));
        Thread.currentThread().interrupt();
        assertSame(list, GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));
        assertTrue(Thread.interrupted());
    }

    // Verifies a failed or cancelled probe or one finished with null reads as the shared empty list
    @Test
    void adaptersTreatsBrokenProbesAsNoAdapters() {
        shared().set(CompletableFuture.failedFuture(new IllegalStateException("probe")));
        assertSame(Collections.emptyList(), GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));
        shared().set(CompletableFuture.failedFuture(new OutOfMemoryError("probe")));
        assertSame(Collections.emptyList(), GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));

        CompletableFuture<List<GraphicsAdapterInfo>> cancelled = new CompletableFuture<>();
        cancelled.cancel(true);
        shared().set(cancelled);
        assertSame(Collections.emptyList(), GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));

        shared().set(CompletableFuture.completedFuture(null));
        assertSame(Collections.emptyList(), GraphicsAdapterProbe.adapters(PATIENCE_MILLIS));
    }

    // Verifies the Linux probe walks the machine DRM directory and returns a read only list
    @Test
    void probeOnLinuxWalksTheMachineDrmDirectory() {
        Statics.set(OsKind.class, "detected", OsKind.LINUX);
        List<GraphicsAdapterInfo> first = GraphicsAdapterProbe.probe();
        assertEquals(Set.copyOf(sysFs(DRM)), Set.copyOf(first));
        assertEquals(Set.copyOf(first), Set.copyOf(GraphicsAdapterProbe.probe()));
        assertThrows(UnsupportedOperationException.class, () -> first.add(LINUX_NVIDIA));
        if (first.isEmpty()) {
            assertSame(Collections.emptyList(), first);
        }
    }

    // Verifies the machine adapters carry driver module versions instead of kernel driver names
    @Test
    void machineAdaptersCarryModuleVersionsInsteadOfDriverNames() throws IOException {
        assumeTrue(Files.isDirectory(DRM));
        Set<String> drivers = new HashSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(DRM)) {
            for (Path entry : entries) {
                Path uevent = entry.resolve("device/uevent");
                if (!Files.isReadable(uevent)) {
                    continue;
                }
                for (String line : Files.readAllLines(uevent)) {
                    if (line.startsWith("DRIVER=")) {
                        drivers.add(line.substring("DRIVER=".length()));
                    }
                }
            }
        }
        for (GraphicsAdapterInfo adapter : sysFs(DRM)) {
            assertFalse(drivers.contains(adapter.driverVersion()), adapter::toString);
        }
    }

    // Verifies the Windows probe without a PowerShell binary returns the shared empty list
    @Test
    void probeOnWindowsWithoutPowerShellIsEmpty() {
        assumeFalse(onPath("powershell"));
        Statics.set(OsKind.class, "detected", OsKind.WINDOWS);
        assertSame(Collections.emptyList(), GraphicsAdapterProbe.probe());
    }

    // Verifies every other classification and a missing one return the shared empty list
    @Test
    void probeElsewhereIsEmpty() {
        for (OsKind kind : new OsKind[]{OsKind.MACOS, OsKind.UNKNOWN, null}) {
            Statics.set(OsKind.class, "detected", kind);
            assertSame(Collections.emptyList(), GraphicsAdapterProbe.probe(), String.valueOf(kind));
        }
    }

    // Verifies the walk reads the vendor and device ids and the driver module version of each card
    @Test
    void sysFsWalkReadsIdsAndModuleVersions(@TempDir Path drm) throws IOException {
        card(drm, "card0", "0x10de\n", "0x2f04\n", "615.71.09\n");
        card(drm, "card1", "0x8086\n", "0x56a0\n", "  6.10.5 \n");
        card(drm, "card2", "0x1002\n", "0x744c\n", null);
        List<GraphicsAdapterInfo> adapters = sysFs(drm);
        assertEquals(3, adapters.size());
        assertEquals(Set.of(
                LINUX_NVIDIA,
                new GraphicsAdapterInfo(GraphicsVendor.INTEL, "PCI 8086:56a0", "6.10.5"),
                new GraphicsAdapterInfo(GraphicsVendor.AMD, "PCI 1002:744c", "")), Set.copyOf(adapters));
    }

    // Verifies the walk takes card entries with any number of ASCII digits and nothing else
    @Test
    void sysFsWalkTakesOnlyCardEntries(@TempDir Path drm) throws IOException {
        List<String> cards = List.of("card0", "card9", "card10", "card99", "card100", "card007");
        for (String name : cards) {
            card(drm, name, "0x10de", "0x" + name, "1");
        }
        for (String name : List.of("card", "card0-DP-1", "card1-HDMI-A-1", "renderD128", "controlD64", "Card0", "CARD0",
                "cardx", "card0a", "card-1", "card+1", "card\u0661", "card\uFF10", " card0", "card0 ", "xcard0")) {
            card(drm, name, "0x10de", "0xskipped", "1");
        }
        Files.writeString(drm.resolve("version"), "drm 1.1.0 20060810");
        Files.createFile(drm.resolve("card5"));
        Set<String> names = new TreeSet<>();
        for (GraphicsAdapterInfo adapter : sysFs(drm)) {
            names.add(adapter.name());
        }
        Set<String> expected = new TreeSet<>();
        for (String name : cards) {
            expected.add("PCI 10de:" + name);
        }
        assertEquals(expected, names);
    }

    // Verifies the walk skips devices that report no nonzero vendor id
    @Test
    void sysFsWalkSkipsDevicesWithoutAVendorId(@TempDir Path drm) throws IOException {
        Files.createDirectories(drm.resolve("card0"));
        card(drm, "card1", null, "0x1", "1");
        card(drm, "card2", "", "0x1", "1");
        card(drm, "card3", "0x0000\n", "0x1", "1");
        card(drm, "card4", "junk", "0x1", "1");
        card(drm, "card5", "0x", "0x1", "1");
        card(drm, "card6", "0x80000000", "0x1", "1");
        card(drm, "card7", " \t\n", "0x1", "1");
        Files.createDirectories(drm.resolve("card8/device/vendor"));
        assertSame(Collections.emptyList(), sysFs(drm));
    }

    // Verifies the walk keeps a device whose device id or module version cannot be read
    @Test
    void sysFsWalkKeepsDevicesWithMissingFiles(@TempDir Path drm) throws IOException {
        card(drm, "card0", "0x10de", null, null);
        card(drm, "card1", "0x8086", "0x1", null);
        Files.createDirectories(drm.resolve("card1/device/driver/module"));
        card(drm, "card2", "0x1002", "0x2", null);
        Files.createDirectories(drm.resolve("card2/device/driver/module/version"));
        card(drm, "card3", "0x1022", "0x3", "");
        assertEquals(Set.of(
                new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "PCI 10de:", ""),
                new GraphicsAdapterInfo(GraphicsVendor.INTEL, "PCI 8086:1", ""),
                new GraphicsAdapterInfo(GraphicsVendor.AMD, "PCI 1002:2", ""),
                new GraphicsAdapterInfo(GraphicsVendor.AMD, "PCI 1022:3", "")), Set.copyOf(sysFs(drm)));
    }

    // Verifies the walk follows symbolic links laid out like the cards and drivers of sysfs
    @Test
    void sysFsWalkFollowsSymbolicLinks(@TempDir Path root) throws IOException {
        Path drm = Files.createDirectories(root.resolve("class/drm"));
        Path pci = Files.createDirectories(root.resolve("devices/pci0000:00/0000:01:00.0"));
        Path card = Files.createDirectories(pci.resolve("drm/card1"));
        Files.writeString(pci.resolve("vendor"), "0x10de\n");
        Files.writeString(pci.resolve("device"), "0x2f04\n");
        Path module = Files.createDirectories(root.resolve("module/nvidia"));
        Files.writeString(module.resolve("version"), "615.71.09\n");
        Path driver = Files.createDirectories(root.resolve("bus/pci/drivers/nvidia"));
        Files.createSymbolicLink(driver.resolve("module"), module);
        Files.createSymbolicLink(pci.resolve("driver"), driver);
        Files.createSymbolicLink(card.resolve("device"), pci);
        Files.createSymbolicLink(drm.resolve("card1"), card);
        assertEquals(List.of(LINUX_NVIDIA), sysFs(drm));
    }

    // Verifies a missing or regular file or empty or null root returns the shared empty list
    @Test
    void sysFsWalkWithoutCardsIsEmpty(@TempDir Path dir) throws IOException {
        assertSame(Collections.emptyList(), sysFs(dir.resolve("missing")));
        assertSame(Collections.emptyList(), sysFs(Files.writeString(dir.resolve("file"), "card0")));
        assertSame(Collections.emptyList(), sysFs(Files.createDirectories(dir.resolve("empty"))));
        assertSame(Collections.emptyList(), call("probeLinuxSysFs", (Object) null));
    }

    // Verifies the walk returns a read only list that equals the list of a repeated walk
    @Test
    void sysFsWalkResultIsReadOnly(@TempDir Path drm) throws IOException {
        card(drm, "card0", "0x10de", "0x2f04", "615.71.09");
        List<GraphicsAdapterInfo> first = sysFs(drm);
        assertThrows(UnsupportedOperationException.class, () -> first.add(LINUX_NVIDIA));
        assertThrows(UnsupportedOperationException.class, () -> first.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> first.set(0, LINUX_NVIDIA));
        assertThrows(UnsupportedOperationException.class, first::clear);
        List<GraphicsAdapterInfo> second = sysFs(drm);
        assertEquals(List.of(LINUX_NVIDIA), first);
        assertEquals(first, second);
        assertNotSame(first, second);
    }

    // Verifies the walk returns every card of a large directory across the digit count boundaries
    @Test
    void sysFsWalkReadsManyCards(@TempDir Path drm) throws IOException {
        int count = 300;
        for (int i = 0; i < count; i++) {
            card(drm, "card" + i, "0x10de", "0x" + Integer.toHexString(i), Integer.toString(i));
        }
        Set<String> versions = new HashSet<>();
        for (GraphicsAdapterInfo adapter : sysFs(drm)) {
            assertEquals("PCI 10de:" + Integer.toHexString(Integer.parseInt(adapter.driverVersion())), adapter.name());
            versions.add(adapter.driverVersion());
        }
        assertEquals(count, versions.size());
        assertTrue(versions.containsAll(Set.of("0", "9", "10", "99", "100", "299")));
    }

    // Verifies the walk keeps vendor ids at the int limits and skips ids beyond them
    @Test
    void sysFsWalkHandlesVendorIdsAtIntLimits(@TempDir Path drm) throws IOException {
        card(drm, "card0", "0x7fffffff", "0x1", "a");
        card(drm, "card1", "-0x80000000", "0x2", "b");
        card(drm, "card2", "-1", "0x3", "c");
        card(drm, "card3", "0x80000000", "0x4", "d");
        card(drm, "card4", "0x100000000", "0x5", "e");
        assertEquals(Set.of(
                new GraphicsAdapterInfo(GraphicsVendor.OTHER, "PCI 7fffffff:1", "a"),
                new GraphicsAdapterInfo(GraphicsVendor.OTHER, "PCI 80000000:2", "b"),
                new GraphicsAdapterInfo(GraphicsVendor.OTHER, "PCI ffffffff:3", "c")), Set.copyOf(sysFs(drm)));
    }

    // Verifies card entry names are card and one or more ASCII digits and a path without a name is rejected
    @Test
    void cardNodeNamesAreCardAndAsciiDigits() {
        for (String name : List.of("card0", "card9", "card10", "card0000", "card2147483648", "/sys/class/drm/card3")) {
            assertTrue(isCard(Path.of(name)), name);
        }
        for (String name : List.of("", "card", "car", "Card0", "CARD0", "card0-DP-1", "cardx", "card0a", "card-1",
                "card+1", "card\u0661", "card\uFF10", " card0", "card0 ", "xcard0", "renderD128", "card0/device")) {
            assertFalse(isCard(Path.of(name)), name);
        }
        assertThrows(NullPointerException.class, () -> isCard(Path.of("/")));
        assertThrows(NullPointerException.class, () -> call("isCardNode", (Object) null));
    }

    // Verifies readTrimmed equals the decode and trim reference for every byte at either end and inside
    @Test
    void readTrimmedMatchesTheReferenceForEveryByte(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("value");
        for (int b = 0; b < 256; b++) {
            byte value = (byte) b;
            byte[][] contents = {{value}, {' ', value, ' '}, {'a', value}, {value, 'a'}, {value, 'a', value},
                    {(byte) 0xC3, value}, {value, (byte) 0xA9}, {'a', (byte) 0xE2, (byte) 0x82, value}};
            for (byte[] bytes : contents) {
                Files.write(file, bytes);
                assertEquals(new String(bytes, UTF_8).trim(), readTrimmed(file), () -> Arrays.toString(bytes));
            }
        }
    }

    // Verifies readTrimmed returns an empty string for an empty or blank or missing or unreadable file or a directory or null
    @Test
    void readTrimmedReturnsEmptyWhenThereIsNothingToRead(@TempDir Path dir) throws IOException {
        assertEquals("", readTrimmed(Files.createFile(dir.resolve("empty"))));
        assertEquals("", readTrimmed(Files.writeString(dir.resolve("blank"), " \t\r\n\u0000\u000B\f")));
        assertEquals("", readTrimmed(dir.resolve("missing")));
        assertEquals("", readTrimmed(dir));
        assertEquals("", call("readTrimmed", (Object) null));
        Path locked = Files.writeString(dir.resolve("locked"), "0x10de");
        assertTrue(locked.toFile().setReadable(false));
        if (!Files.isReadable(locked)) {
            assertEquals("", readTrimmed(locked));
        }
    }

    // Verifies readTrimmed keeps the inner text and every char above the space char and reads large files whole
    @Test
    void readTrimmedKeepsTextAndCharsAboveTheSpaceChar(@TempDir Path dir) throws IOException {
        assertEquals("0x10de", readTrimmed(Files.writeString(dir.resolve("vendor"), "  0x10de\n")));
        assertEquals("615.71.09", readTrimmed(Files.writeString(dir.resolve("version"), "615.71.09\r\n")));
        assertEquals("a b\tc", readTrimmed(Files.writeString(dir.resolve("inner"), "\na b\tc\n")));
        assertEquals("\u00A0x\u00A0", readTrimmed(Files.writeString(dir.resolve("nbsp"), "\u00A0x\u00A0")));
        assertEquals("\u00FCber \u20AC", readTrimmed(Files.writeString(dir.resolve("utf8"), "\u00FCber \u20AC\n")));
        assertEquals("\uFFFD", readTrimmed(Files.write(dir.resolve("invalid"), new byte[]{(byte) 0xFF, '\n'})));
        String large = "x".repeat(1 << 20);
        Path big = Files.writeString(dir.resolve("large"), "\n" + large + "\n");
        assertEquals(large, readTrimmed(big));
        assertEquals(readTrimmed(big), readTrimmed(big));
    }

    // Verifies parsePciId equals the decode reference for prefixes and signs and radixes and the int limits
    @Test
    void parsePciIdFollowsIntegerDecode() {
        for (String value : List.of("0x10de", "0X10DE", "#10de", "4318", "010", "08", "0", "00", "-0x1", "+0x1", "--1",
                "-", "+", "0x", "#", "0x7fffffff", "-0x80000000", "0x80000000", "0xffffffff", "0x100000000",
                "2147483647", "-2147483648", "2147483648", "", " ", " 0x1", "0x1 ", "junk", "0x-1", "0x+1",
                "\u0661", "0x\u0661")) {
            assertEquals(referenceId(value), (int) call("parsePciId", value), value);
        }
        assertEquals(0x10DE, (int) call("parsePciId", "0x10de"));
        assertEquals(Integer.MIN_VALUE, (int) call("parsePciId", "-0x80000000"));
        assertEquals(0, (int) call("parsePciId", ""));
        assertThrows(NullPointerException.class, () -> call("parsePciId", (Object) null));
    }

    // Verifies pciName equals the format reference for vendor ids at the digit boundaries and random device ids
    @Test
    void pciNameMatchesTheFormatReference() throws ReflectiveOperationException {
        Method pciName = fresh("pciName", int.class, String.class);
        for (int vendorId : VENDOR_IDS) {
            for (String device : List.of("", "0x2f04", "2f04", "0x", "0", "x", "00xx", "0x0x", "0X2F04", "x0x", "0xx", "\u00E90x1")) {
                assertEquals(referenceName(vendorId, device), pciName.invoke(null, vendorId, device), device);
            }
        }
        Random random = new Random(0x10DE2F04L);
        StringBuilder device = new StringBuilder();
        for (int round = 0; round < RANDOM_INPUTS; round++) {
            device.setLength(0);
            for (int count = random.nextInt(7); count > 0; count--) {
                device.append(DEVICE_FRAGMENTS[random.nextInt(DEVICE_FRAGMENTS.length)]);
            }
            int vendorId = random.nextBoolean() ? random.nextInt() : VENDOR_IDS[random.nextInt(VENDOR_IDS.length)];
            String text = device.toString();
            assertEquals(referenceName(vendorId, text), pciName.invoke(null, vendorId, text), text);
        }
        assertEquals("PCI 10de:2f04", call("pciName", 0x10DE, "0x2f04"));
        assertEquals("PCI 0001:0x", call("pciName", 1, "00xx"));
        assertThrows(NullPointerException.class, () -> call("pciName", 1, null));
    }

    // Verifies typical CIM lines parse into adapters with their vendors
    @Test
    void cimLinesParseTypicalAdapters() {
        assertEquals(List.of(
                new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "NVIDIA GeForce RTX 5070", "32.0.15.8142"),
                new GraphicsAdapterInfo(GraphicsVendor.AMD, "AMD Radeon RX 7800 XT", "31.0.24027.1012"),
                new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Intel(R) UHD Graphics 770", "31.0.101.5186"),
                new GraphicsAdapterInfo(GraphicsVendor.AMD, "Radeon HD 5450", "8.970.100.1100"),
                new GraphicsAdapterInfo(GraphicsVendor.OTHER, "Microsoft Basic Display Adapter", "10.0.22621.1")), cim(
                "NVIDIA|NVIDIA GeForce RTX 5070|32.0.15.8142",
                "Advanced Micro Devices, Inc.|AMD Radeon RX 7800 XT|31.0.24027.1012",
                "Intel Corporation|Intel(R) UHD Graphics 770|31.0.101.5186",
                "ATI Technologies Inc.|Radeon HD 5450|8.970.100.1100",
                "(Standard display types)|Microsoft Basic Display Adapter|10.0.22621.1"));
    }

    // Verifies lines without two separators or with a blank name are skipped and extra separators stay in the driver
    @Test
    void cimLinesSkipMalformedLines() {
        assertSame(Collections.emptyList(), cim("", "nothing", "a|b", "|", "||", "a||1", "a| \t |1", " | | "));
        assertEquals(List.of(new GraphicsAdapterInfo(GraphicsVendor.OTHER, "n", "")), cim("|n|"));
        assertEquals(List.of(new GraphicsAdapterInfo(GraphicsVendor.OTHER, "n", "1|2|3")), cim("a|n|1|2|3"));
        assertEquals(List.of(new GraphicsAdapterInfo(GraphicsVendor.OTHER, "n", "|")), cim("a|n||"));
    }

    // Verifies the name and the driver lose surrounding chars at or below the space char
    @Test
    void cimLinesTrimNameAndDriver() {
        assertEquals(List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc A770", "31.0.101")),
                cim("Intel | Arc A770 |  31.0.101  "));
        assertEquals(List.of(new GraphicsAdapterInfo(GraphicsVendor.OTHER, "a\u00A0b", "1")),
                cim("x|\t\u0000a\u00A0b\r|\n1\u0001"));
    }

    // Verifies ati ending the compatibility meets the separator and the driver never names a vendor
    @Test
    void cimLinesClassifyAcrossTheSeparator() {
        assertEquals(GraphicsVendor.AMD, cim("ATI|x|1").get(0).vendor());
        assertEquals(GraphicsVendor.AMD, cim("x|ati |1").get(0).vendor());
        assertEquals(GraphicsVendor.OTHER, cim("x|ati|1").get(0).vendor());
        assertEquals(GraphicsVendor.OTHER, cim("AT|Ix|1").get(0).vendor());
        assertEquals(GraphicsVendor.OTHER, cim("nvi|dia|1").get(0).vendor());
        assertEquals(GraphicsVendor.OTHER, cim("x|y|NVIDIA AMD Intel").get(0).vendor());
    }

    // Verifies NVIDIA wins over AMD and AMD wins over Intel
    @Test
    void cimLinesPreferNvidiaThenAmdThenIntel() {
        assertEquals(GraphicsVendor.NVIDIA, cim("Intel AMD|Radeon NVIDIA|1").get(0).vendor());
        assertEquals(GraphicsVendor.AMD, cim("Intel|Radeon|1").get(0).vendor());
        assertEquals(GraphicsVendor.AMD, cim("Intel|amd|1").get(0).vendor());
        assertEquals(GraphicsVendor.INTEL, cim("x|INTEL|1").get(0).vendor());
    }

    // Verifies parseCimLines agrees with the split reference for random lines joined from overlapping fragments
    @Test
    void cimLinesMatchTheReferenceForRandomLines() throws ReflectiveOperationException {
        Method parse = fresh("parseCimLines", List.class);
        Random random = new Random(0xC1A5L);
        StringBuilder line = new StringBuilder();
        for (int round = 0; round < RANDOM_INPUTS; round++) {
            line.setLength(0);
            for (int count = random.nextInt(9); count > 0; count--) {
                line.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]);
            }
            List<String> lines = List.of(line.toString());
            assertEquals(referenceCim(lines), parse.invoke(null, lines), lines::toString);
        }
    }

    // Verifies an empty list gives the shared empty list and many lines keep their order
    @Test
    void cimLinesHandleEmptyAndSingleAndManyLines() {
        assertSame(Collections.emptyList(), call("parseCimLines", List.of()));
        assertEquals(List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc", "1")), cim("Intel|Arc|1"));
        int count = 100_000;
        List<String> many = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            many.add("NVIDIA|GPU " + i + "|" + i);
        }
        List<GraphicsAdapterInfo> adapters = call("parseCimLines", many);
        assertEquals(count, adapters.size());
        for (int i = 0; i < count; i += 9_999) {
            assertEquals(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "GPU " + i, Integer.toString(i)), adapters.get(i));
        }
    }

    // Verifies parseCimLines returns a read only list and rejects a null list or line
    @Test
    void cimLinesResultIsReadOnlyAndNullIsRejected() {
        List<GraphicsAdapterInfo> adapters = cim("Intel|Arc|1");
        assertThrows(UnsupportedOperationException.class, () -> adapters.add(LINUX_NVIDIA));
        assertThrows(UnsupportedOperationException.class, adapters::clear);
        assertThrows(NullPointerException.class, () -> call("parseCimLines", (Object) null));
        assertThrows(NullPointerException.class, () -> cim("Intel|Arc|1", null));
    }

    // Verifies the classification agrees with the lower case reference for every UTF 16 unit in every pattern slot
    @Test
    void classifyMatchesTheReferenceForEveryCharInEveryPatternSlot() throws ReflectiveOperationException {
        Method classify = fresh("classifyWindowsVendor", String.class, int.class, int.class);
        for (String pattern : PATTERNS) {
            char[] chars = pattern.toCharArray();
            for (int slot = 0; slot < chars.length; slot++) {
                for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
                    chars[slot] = (char) c;
                    String text = new String(chars);
                    for (String line : new String[]{text + "|x|1", "x|" + text + "|1"}) {
                        int separator = line.indexOf('|');
                        int end = line.indexOf('|', separator + 1);
                        String reference = line.substring(0, separator) + " " + line.substring(separator + 1, end);
                        assertEquals(referenceVendor(reference), classify.invoke(null, line, separator, end), line);
                    }
                }
                chars[slot] = pattern.charAt(slot);
            }
        }
    }

    // Verifies the classification stops at the end and reads the separator as a space only for the space slot
    @Test
    void classifyHonoursItsBounds() {
        assertEquals(GraphicsVendor.OTHER, call("classifyWindowsVendor", "nvidia", 6, 0));
        assertEquals(GraphicsVendor.NVIDIA, call("classifyWindowsVendor", "x|nvidia|1", 1, 8));
        assertEquals(GraphicsVendor.OTHER, call("classifyWindowsVendor", "x|nvidia|1", 1, 7));
        assertEquals(GraphicsVendor.AMD, call("classifyWindowsVendor", "ati|x", 3, 5));
        assertEquals(GraphicsVendor.OTHER, call("classifyWindowsVendor", "ati|x", -1, 5));
        assertEquals(GraphicsVendor.OTHER, call("classifyWindowsVendor", "ati|x", Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertThrows(StringIndexOutOfBoundsException.class, () -> call("classifyWindowsVendor", "ati", -1, 4));
    }

    // Verifies matches folds letter case and needs a real space or the separator for the space slot and survives int limits
    @Test
    void matchesComparesPatternsWithinBounds() {
        assertTrue((boolean) call("matches", "nvidia", 0, -1, 6, "nvidia"));
        assertTrue((boolean) call("matches", "NViDiA", 0, -1, 6, "nvidia"));
        assertTrue((boolean) call("matches", "xATI y", 1, -1, 6, "ati "));
        assertTrue((boolean) call("matches", "ati|x", 0, 3, 5, "ati "));
        assertFalse((boolean) call("matches", "ati|x", 0, -1, 5, "ati "));
        assertFalse((boolean) call("matches", "ati\u0000x", 0, -1, 5, "ati "));
        assertFalse((boolean) call("matches", "n|idia", 0, 1, 6, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", 0, -1, 5, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", 1, -1, 6, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", -1, -1, 6, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", Integer.MIN_VALUE, -1, Integer.MAX_VALUE, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", 0, -1, Integer.MIN_VALUE, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", 1, -1, Integer.MIN_VALUE, "nvidia"));
        assertFalse((boolean) call("matches", "nvidia", Integer.MAX_VALUE, -1, Integer.MAX_VALUE, "nvidia"));
        assertTrue((boolean) call("matches", "", 0, -1, 0, ""));
        assertThrows(StringIndexOutOfBoundsException.class, () -> call("matches", "nvi", 0, -1, 6, "nvidia"));
    }

    // Verifies trimmed equals String trim of the range for every UTF 16 unit at either end
    @Test
    void trimmedMatchesStringTrim() throws ReflectiveOperationException {
        Method trimmed = fresh("trimmed", String.class, int.class, int.class);
        for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
            String text = (char) c + "a" + (char) c + "b" + (char) c;
            assertEquals(text.trim(), trimmed.invoke(null, text, 0, text.length()), Integer.toHexString(c));
            assertEquals(text.substring(1, 4).trim(), trimmed.invoke(null, text, 1, 4), Integer.toHexString(c));
        }
        String whole = "whole";
        assertSame(whole, call("trimmed", whole, 0, whole.length()));
        assertEquals("", call("trimmed", "abc", 1, 1));
        assertEquals("", call("trimmed", "   ", 0, 3));
        assertThrows(StringIndexOutOfBoundsException.class, () -> call("trimmed", "abc", 2, 1));
        assertThrows(StringIndexOutOfBoundsException.class, () -> call("trimmed", "abc", -1, 2));
        assertThrows(StringIndexOutOfBoundsException.class, () -> call("trimmed", "abc", 0, 4));
        assertThrows(NullPointerException.class, () -> call("trimmed", null, 0, 1));
    }

    // Verifies runProcess returns the trimmed nonempty lines and keeps the output of a failing command
    @Test
    void runProcessReturnsTrimmedNonEmptyLines() {
        assumeTrue(onPath("printf") && onPath("sh"));
        assertEquals(List.of("a", "b", "c"), run(FINISH, "printf", "  a  \\n\\n\\t\\nb\\r\\nc"));
        assertEquals(List.of("x"), run(FINISH, "sh", "-c", "echo x; exit 3"));
        assertEquals(List.of("\u00E9", "\uFFFD"), run(FINISH, "printf", "\\303\\251\\n\\377\\n"));
        assertEquals(List.of(), run(FINISH, "printf", ""));
    }

    // Verifies runProcess leaves standard error out of the lines
    @Test
    void runProcessDiscardsStandardError() {
        assumeTrue(onPath("sh"));
        assertEquals(List.of("out"), run(FINISH, "sh", "-c", "echo out; echo err 1>&2"));
        assertEquals(List.of(), run(FINISH, "sh", "-c", "echo only 1>&2"));
    }

    // Verifies a command that reads its standard input sees the end of it
    @Test
    void runProcessClosesStandardInput() {
        assumeTrue(onPath("cat") && onPath("sh"));
        assertTimeoutPreemptively(Duration.ofMillis(PATIENCE_MILLIS), () -> {
            assertEquals(List.of(), run(FINISH, "cat"));
            assertEquals(List.of("eof"), run(FINISH, "sh", "-c", "read line || echo eof"));
        });
    }

    // Verifies a command that outlasts the time limit is killed and yields the shared empty list
    @Test
    void runProcessKillsACommandThatOutlastsTheTimeout(@TempDir Path dir) throws Exception {
        assumeTrue(onPath("sh") && onPath("sleep"));
        Path pidFile = dir.resolve("pid");
        long start = System.nanoTime();
        assertSame(Collections.emptyList(), run(TimeUnit.SECONDS.toNanos(1), "sh", "-c", "echo $$ > \"$0\"; exec sleep 30", pidFile.toString()));
        assertTrue(since(start) < PROMPT + TimeUnit.SECONDS.toNanos(1));
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PATIENCE_MILLIS);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            assertTrue(System.nanoTime() < deadline, "the timed out command is still running");
            Thread.sleep(10);
        }
    }

    // Verifies a child that keeps the output open past the time limit yields the shared empty list
    @Test
    void runProcessGivesUpOnAChildHoldingTheOutput() {
        assumeTrue(onPath("sh") && onPath("sleep"));
        long start = System.nanoTime();
        assertSame(Collections.emptyList(), run(SHORT, "sh", "-c", "sleep 3 & echo hi"));
        assertTrue(since(start) < PROMPT);
    }

    // Verifies zero and negative time limits give up at once and the largest one waits for the command
    @Test
    void runProcessHandlesTimeoutLimits() {
        assumeTrue(onPath("sleep") && onPath("echo"));
        for (long timeout : new long[]{0, -1, Long.MIN_VALUE}) {
            long start = System.nanoTime();
            assertSame(Collections.emptyList(), run(timeout, "sleep", "5"), Long.toString(timeout));
            assertTrue(since(start) < PROMPT, Long.toString(timeout));
        }
        assertEquals(List.of("hello"), run(Long.MAX_VALUE, "echo", "hello"));
    }

    // Verifies an interrupted caller gets the shared empty list and keeps the interrupt flag
    @Test
    void runProcessKeepsTheInterruptFlag() {
        assumeTrue(onPath("echo"));
        Thread.currentThread().interrupt();
        assertSame(Collections.emptyList(), run(FINISH, "echo", "x"));
        assertTrue(Thread.interrupted());
        assertEquals(List.of("x"), run(FINISH, "echo", "x"));
    }

    // Verifies a missing or null or empty command or one naming a directory yields the shared empty list
    @Test
    void runProcessRejectsBadCommands(@TempDir Path dir) {
        assertSame(Collections.emptyList(), run(FINISH, "definitely-not-a-command-" + System.nanoTime()));
        assertSame(Collections.emptyList(), run(FINISH, (String[]) null));
        assertSame(Collections.emptyList(), run(FINISH, new String[]{null}));
        assertSame(Collections.emptyList(), run(FINISH));
        assertSame(Collections.emptyList(), run(FINISH, dir.toString()));
    }

    // Verifies runProcess reads a large output whole and agrees across repeated calls
    @Test
    void runProcessReadsLargeOutputAndRepeats() {
        assumeTrue(onPath("seq") && onPath("echo"));
        List<String> numbers = run(FINISH, "seq", "1", "200000");
        assertEquals(200_000, numbers.size());
        assertEquals("1", numbers.get(0));
        assertEquals("200000", numbers.get(numbers.size() - 1));
        for (int i = 0; i < 20; i++) {
            assertEquals(List.of("x"), run(FINISH, "echo", "x"));
        }
    }

    // Verifies readLines splits on every line terminator and drops blank lines
    @Test
    void readLinesSplitsTrimsAndDropsBlankLines() {
        assertEquals(List.of(), lines(""));
        assertEquals(List.of(), lines("\n\r\n\r \t\n"));
        assertEquals(List.of("a"), lines("a"));
        assertEquals(List.of("a", "b", "c", "d"), lines("a\rb\nc\r\nd"));
        assertEquals(List.of("x y"), lines("  x y \t\n"));
        assertEquals(List.of("\u2028"), lines("\u2028\n"));
        assertEquals(List.of("\uFFFD"), call("readLines", new ByteArrayInputStream(new byte[]{(byte) 0xFF, '\n'})));
    }

    // Verifies readLines closes the stream after reading and after a read failure
    @Test
    void readLinesClosesTheStream() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new ByteArrayInputStream("a\n".getBytes(UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        assertEquals(List.of("a"), call("readLines", stream));
        assertTrue(closed.get());

        AtomicBoolean brokenClosed = new AtomicBoolean();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken");
            }

            @Override
            public void close() {
                brokenClosed.set(true);
            }
        };
        RuntimeException failure = assertThrows(RuntimeException.class, () -> call("readLines", broken));
        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(brokenClosed.get());
    }

    // Verifies readLines rejects a closed stream and a null stream
    @Test
    void readLinesRejectsClosedAndNullStreams(@TempDir Path dir) throws IOException {
        FileInputStream stream = new FileInputStream(Files.writeString(dir.resolve("lines"), "a\n").toFile());
        stream.close();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> call("readLines", stream));
        assertInstanceOf(IOException.class, failure.getCause());
        assertThrows(NullPointerException.class, () -> call("readLines", (Object) null));
    }

    // Verifies frozen shares the empty list and wraps a nonempty list in a read only view
    @Test
    void frozenSharesTheEmptyListAndWrapsTheRest() {
        assertSame(Collections.emptyList(), call("frozen", new ArrayList<GraphicsAdapterInfo>()));
        ArrayList<GraphicsAdapterInfo> backing = new ArrayList<>(List.of(LINUX_NVIDIA));
        List<GraphicsAdapterInfo> view = call("frozen", backing);
        assertEquals(backing, view);
        assertThrows(UnsupportedOperationException.class, () -> view.add(LINUX_NVIDIA));
        backing.add(LINUX_NVIDIA);
        assertEquals(2, view.size());
        assertThrows(NullPointerException.class, () -> call("frozen", (Object) null));
    }
}
