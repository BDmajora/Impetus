package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

// Probe that lists display adapters from sysfs on Linux and from Win32_VideoController on Windows and returns an empty list on any failure
public final class GraphicsAdapterProbe {
    // Time limit of the PowerShell query in nanoseconds
    private static final long PROCESS_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    // PowerShell command that prints the compatibility and name and driver version of each adapter joined by bars
    private static final String[] CIM_COMMAND = {
            "powershell", "-NoProfile", "-NonInteractive", "-Command",
            "Get-CimInstance Win32_VideoController | ForEach-Object { $_.AdapterCompatibility + '|' + $_.Name + '|' + $_.DriverVersion }"};
    // Separator between the fields of a CIM line
    private static final char CIM_SEPARATOR = '|';
    // Directory with one entry per DRM device on Linux
    private static final Path DRM_ROOT = Path.of("/sys/class/drm");
    // Name prefix of a DRM card entry
    private static final String CARD_PREFIX = "card";
    // Path of the version file of the driver module below a card device
    private static final String MODULE_VERSION = "driver/module/version";
    // Name prefix of an adapter found in sysfs
    private static final String PCI_NAME_PREFIX = "PCI ";
    // Smallest number of hex digits of the vendor id in an adapter name
    private static final int VENDOR_HEX_DIGITS = 4;
    // Bit that turns an upper case ASCII letter into its lower case form
    private static final int ASCII_LOWER_CASE_BIT = 0x20;

    // Shared probe of this launch
    private static final AtomicReference<CompletableFuture<List<GraphicsAdapterInfo>>> SHARED = new AtomicReference<>();

    // Prevents instantiation
    private GraphicsAdapterProbe() {
    }

    // Returns the shared probe and starts it on a virtual thread on the first call
    public static CompletableFuture<List<GraphicsAdapterInfo>> prefetch() {
        var existing = SHARED.get();
        if (existing != null) {
            return existing;
        }

        var future = new CompletableFuture<List<GraphicsAdapterInfo>>();
        var witness = SHARED.compareAndExchange(null, future);
        if (witness != null) {
            return witness;
        }

        Thread.ofVirtual().name("Impetus Adapter Probe").start(() -> future.complete(probe()));
        return future;
    }

    // Returns the adapters of the shared probe or an empty list when the probe fails or outlasts the timeout in milliseconds
    public static List<GraphicsAdapterInfo> adapters(long timeoutMillis) {
        try {
            var found = prefetch().get(timeoutMillis, TimeUnit.MILLISECONDS);
            return found != null ? found : Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | CancellationException e) {
            // Treats a failed or unfinished or cancelled probe as no adapters
        }

        return Collections.emptyList();
    }

    // Returns the adapters from sysfs on Linux or from CIM on Windows and an empty list elsewhere or on failure
    public static List<GraphicsAdapterInfo> probe() {
        try {
            return switch (OsKind.current()) {
                case LINUX -> probeLinuxSysFs(DRM_ROOT);
                case WINDOWS -> parseCimLines(runProcess(PROCESS_TIMEOUT_NANOS, CIM_COMMAND));
                default -> Collections.emptyList();
            };
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    // Returns one adapter for each card entry of the DRM directory whose device reports a nonzero PCI vendor id
    private static List<GraphicsAdapterInfo> probeLinuxSysFs(Path drm) {
        var results = new ArrayList<GraphicsAdapterInfo>();

        try (DirectoryStream<Path> cards = Files.newDirectoryStream(drm, GraphicsAdapterProbe::isCardNode)) {
            for (var card : cards) {
                var device = card.resolve("device");
                var vendorId = parsePciId(readTrimmed(device.resolve("vendor")));

                if (vendorId == 0) {
                    continue;
                }

                results.add(new GraphicsAdapterInfo(GraphicsVendor.fromPciVendorId(vendorId),
                        pciName(vendorId, readTrimmed(device.resolve("device"))),
                        readTrimmed(device.resolve(MODULE_VERSION))));
            }
        } catch (Exception e) {
            // Keeps the adapters collected before the failure
        }

        return frozen(results);
    }

    // Returns true when the entry name is card followed by one or more ASCII digits
    private static boolean isCardNode(Path entry) {
        var name = entry.getFileName().toString();
        int length = name.length();

        if (length <= CARD_PREFIX.length() || !name.startsWith(CARD_PREFIX)) {
            return false;
        }

        for (int i = CARD_PREFIX.length(); i < length; i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }

        return true;
    }

    // Returns the file decoded as UTF 8 without leading or trailing bytes at or below the space char or an empty string when the read fails
    private static String readTrimmed(Path path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (Exception e) {
            return "";
        }

        int begin = 0;
        int end = bytes.length;
        while (begin < end && (bytes[begin] & 0xFF) <= ' ') {
            begin++;
        }
        while (end > begin && (bytes[end - 1] & 0xFF) <= ' ') {
            end--;
        }

        return new String(bytes, begin, end - begin, StandardCharsets.UTF_8);
    }

    // Returns the int that Integer.decode reads from the text or zero when the text holds none
    private static int parsePciId(String value) {
        if (value.isEmpty()) {
            return 0;
        }

        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Returns PCI and the vendor id as lower case hex of at least four digits and a colon and the device id without its 0x pairs
    private static String pciName(int vendorId, String deviceId) {
        int digits = Math.max(VENDOR_HEX_DIGITS, (Integer.SIZE - Integer.numberOfLeadingZeros(vendorId) + 3) >> 2);
        int length = deviceId.length();
        var name = new StringBuilder(PCI_NAME_PREFIX.length() + digits + 1 + length).append(PCI_NAME_PREFIX);

        for (int shift = (digits - 1) << 2; shift >= 0; shift -= 4) {
            name.append(Character.forDigit((vendorId >>> shift) & 0xF, 16));
        }
        name.append(':');

        for (int i = 0; i < length; i++) {
            char c = deviceId.charAt(i);
            // Skips a 0x pair and continues after its x
            if (c == '0' && i + 1 < length && deviceId.charAt(i + 1) == 'x') {
                i++;
            } else {
                name.append(c);
            }
        }

        return name.toString();
    }

    // Returns one adapter for each line with two separators and a name that is not blank
    private static List<GraphicsAdapterInfo> parseCimLines(List<String> lines) {
        var results = new ArrayList<GraphicsAdapterInfo>(lines.size());

        for (var line : lines) {
            int first = line.indexOf(CIM_SEPARATOR);
            int second = first < 0 ? -1 : line.indexOf(CIM_SEPARATOR, first + 1);
            if (second < 0) {
                continue;
            }

            var name = trimmed(line, first + 1, second);
            if (name.isEmpty()) {
                continue;
            }

            results.add(new GraphicsAdapterInfo(classifyWindowsVendor(line, first, second), name,
                    trimmed(line, second + 1, line.length())));
        }

        return frozen(results);
    }

    // Returns the text between begin and end without leading or trailing chars at or below the space char
    private static String trimmed(String text, int begin, int end) {
        while (begin < end && text.charAt(begin) <= ' ') {
            begin++;
        }
        while (end > begin && text.charAt(end - 1) <= ' ') {
            end--;
        }

        return text.substring(begin, end);
    }

    // Returns the vendor named before the end of the line with the bar at the separator read as a space and NVIDIA ahead of AMD and AMD ahead of Intel
    private static GraphicsVendor classifyWindowsVendor(String line, int separator, int end) {
        boolean amd = false;
        boolean intel = false;

        for (int i = 0; i < end; i++) {
            switch (line.charAt(i) | ASCII_LOWER_CASE_BIT) {
                case 'n' -> {
                    if (matches(line, i, separator, end, "nvidia")) {
                        return GraphicsVendor.NVIDIA;
                    }
                }
                case 'a' -> amd = amd || matches(line, i, separator, end, "amd") || matches(line, i, separator, end, "ati ");
                case 'r' -> amd = amd || matches(line, i, separator, end, "radeon");
                case 'i' -> intel = intel || matches(line, i, separator, end, "intel");
            }
        }

        return amd ? GraphicsVendor.AMD : intel ? GraphicsVendor.INTEL : GraphicsVendor.OTHER;
    }

    // Returns true when the pattern starts at the index and fits before the end with ASCII case ignored for letters and the separator read as a space
    private static boolean matches(String line, int index, int separator, int end, String pattern) {
        int length = pattern.length();

        if (index < 0 || (long) end - index < length) {
            return false;
        }

        for (int j = 0; j < length; j++) {
            int at = index + j;
            char expected = pattern.charAt(j);
            boolean equal = expected == ' '
                    ? at == separator || line.charAt(at) == ' '
                    : (line.charAt(at) | ASCII_LOWER_CASE_BIT) == expected;
            if (!equal) {
                return false;
            }
        }

        return true;
    }

    // Returns the trimmed nonempty output lines of the command or an empty list when it fails or outlasts the timeout in nanoseconds
    private static List<String> runProcess(long timeoutNanos, String... command) {
        long deadline = System.nanoTime() + Math.max(0, timeoutNanos);
        Process process = null;

        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            // Closes the standard input of the command
            process.getOutputStream().close();

            var stdout = process.getInputStream();
            var output = new FutureTask<List<String>>(() -> readLines(stdout));
            Thread.ofVirtual().start(output);

            var lines = output.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            return process.waitFor(deadline - System.nanoTime(), TimeUnit.NANOSECONDS) ? lines : Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            // Ends the command and closes its streams
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    // Returns the trimmed nonempty lines of the stream decoded as UTF 8 and closes the stream
    private static List<String> readLines(InputStream stream) throws IOException {
        var lines = new ArrayList<String>();

        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        }

        return lines;
    }

    // Returns a read only view of the adapters or the shared empty list when there are none
    private static List<GraphicsAdapterInfo> frozen(ArrayList<GraphicsAdapterInfo> adapters) {
        return adapters.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(adapters);
    }
}
