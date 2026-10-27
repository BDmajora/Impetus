package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

// Registry of the driver and environment issues found on this machine once the GL context exists
public final class Workarounds {
    // Logger of the issues found at startup
    private static final Logger LOGGER = LogManager.getLogger("Impetus-Workarounds");
    // Empty set of issues stored whenever no issue is active
    private static final EnumSet<Issue> NONE = EnumSet.noneOf(Issue.class);
    // Set of the active issues that stays unchanged once stored
    private static final AtomicReference<EnumSet<Issue>> ACTIVE = new AtomicReference<>(NONE);

    // Prevents instantiation
    private Workarounds() {
    }

    // Driver or environment issue with a known mitigation or diagnostic
    public enum Issue {
        // Threaded optimization of the NVIDIA driver under Windows
        NVIDIA_THREADED_OPTIMIZATIONS,
        // No error context that is unsafe on a legacy Intel driver under Windows
        NO_ERROR_CONTEXT_UNSAFE,
        // Overlay such as RivaTuner Statistics Server that hooks the frame path
        FRAME_HOOK_OVERLAY_PRESENT
    }

    // Replaces the active issues with those that apply to the context and adapters under the current operating system
    public static void init(GlContextInfo context, List<GraphicsAdapterInfo> adapters) {
        // Rejects a null context or adapter list
        Objects.requireNonNull(context, "Context must not be null");
        Objects.requireNonNull(adapters, "Adapters must not be null");

        var issues = NONE;

        // Classifies the vendor under Windows only and picks the issue of an NVIDIA context or of an Intel context with a legacy adapter
        if (OsKind.current() == OsKind.WINDOWS) {
            var vendor = GraphicsVendor.fromContext(context);

            if (vendor == GraphicsVendor.NVIDIA) {
                issues = EnumSet.of(Issue.NVIDIA_THREADED_OPTIMIZATIONS);
            } else if (vendor == GraphicsVendor.INTEL && hasLegacyIntelAdapter(adapters)) {
                issues = EnumSet.of(Issue.NO_ERROR_CONTEXT_UNSAFE);
            }
        }

        ACTIVE.set(issues);

        if (issues != NONE) {
            LOGGER.warn("Detected environment issues, mitigations/diagnostics enabled: {}", issues);
        }
    }

    // Adds the issue to the active issues and leaves them unchanged when the issue is already active
    public static void markActive(Issue issue) {
        // Rejects a null issue
        Objects.requireNonNull(issue, "Issue must not be null");

        EnumSet<Issue> current;
        EnumSet<Issue> updated;

        // Stores a copy with the issue added and retries when another update replaced the active issues first
        do {
            current = ACTIVE.get();

            if (current.contains(issue)) {
                return;
            }

            updated = current.clone();
            updated.add(issue);
        } while (!ACTIVE.compareAndSet(current, updated));
    }

    // Returns true when the issue is active and false for a null issue
    public static boolean isActive(Issue issue) {
        return ACTIVE.get().contains(issue);
    }

    // Returns true under Windows when the adapter list is empty or holds an Intel adapter with a legacy driver
    static boolean hasLegacyIntelWindowsAdapter(OsKind os, List<GraphicsAdapterInfo> adapters) {
        // Rejects a null operating system or adapter list
        Objects.requireNonNull(os, "Operating system must not be null");
        Objects.requireNonNull(adapters, "Adapters must not be null");

        return os == OsKind.WINDOWS && hasLegacyIntelAdapter(adapters);
    }

    // Returns true when the adapter list is empty or an Intel adapter reports a driver version with major part 9 or 10
    private static boolean hasLegacyIntelAdapter(List<GraphicsAdapterInfo> adapters) {
        // Treats an empty adapter list as a legacy adapter
        if (adapters.isEmpty()) {
            return true;
        }

        for (var adapter : adapters) {
            if (adapter.vendor() != GraphicsVendor.INTEL) {
                continue;
            }

            var version = adapter.driverVersion();

            // Matches a version that starts with 9 or 10 and a dot and skips a null version
            if (version != null && (version.startsWith("9.") || version.startsWith("10."))) {
                return true;
            }
        }

        return false;
    }
}
