package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.coarctatio.dedup.TransformCaches;
import com.bdmajora.coarctatio.state.CompactPropertyMaps;
import com.bdmajora.coarctatio.state.ConditionCanonicalizer;
import com.bdmajora.coarctatio.state.PropertyValueMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// Backport of Hydrogen plus the FoamFix, LoliASM and FerriteCore parts solving the same problems on 1.12.2; not an FML entry point, it owns the logger and pool lifecycle while mixins do the work
public final class Coarctatio {
    public static final Logger LOGGER = LogManager.getLogger("Coarctatio");

    private Coarctatio() {
    }

    // Re-opens (not clears) the bake-scoped pools: survivors still reference the previous canonical arrays, and the new pool should converge on the new pack's geometry
    public static void onResourceReloadStart() {
        ModelCaches.open();
        TransformCaches.open();
        ConditionCanonicalizer.open();
    }

    // Closing releases the tracking sets, not the pooled contents; quads baked afterwards skip dedup (rare, and likely to be mutated later)
    public static void onResourceReloadFinish() {
        ModelCaches.close();
        TransformCaches.close();
        ConditionCanonicalizer.close();
    }

    // FoamFix's clClearCachesOnUnload equivalent: NBT keys and resource paths grow with play and never shrink, so reset them; model/state pools are resource-keyed and survive a world change
    public static void onWorldLeave() {
        int freed = StringPool.NBT_KEYS.size() + ResourceLocationCaches.PATHS.size();

        StringPool.NBT_KEYS.clear();
        ResourceLocationCaches.PATHS.open();

    }

    // Shared by the log and the F3 overlay.
    public static List<String> statistics() {
        List<String> lines = new ArrayList<>();
        lines.add("Coarctatio memory statistics");
        lines.add("  Resource domains: " + ResourceLocationCaches.DOMAINS);
        lines.add("  Resource paths:   " + ResourceLocationCaches.PATHS);
        lines.add("  Model variants:   " + ModelCaches.VARIANTS);
        lines.add("  Quad vertex data: " + ModelCaches.QUADS);
        lines.add("  Quads not pooled: " + ModelCaches.skippedSummary());
        lines.add("  NBT keys:         " + StringPool.NBT_KEYS);
        lines.add("  Camera transforms:" + TransformCaches.TRANSFORMS);
        lines.add("  Multipart preds:  " + ConditionCanonicalizer.statistics());

        if (CoarctatioConfig.get().optimizeBlockStates) {
            lines.add("  Block states:     " + PropertyValueMapper.statistics());
            lines.add("  Property maps:    " + CompactPropertyMaps.statistics());
        }

        if (CoarctatioConfig.get().dynamicModels) {
            lines.addAll(com.bdmajora.coarctatio.client.model.dynamic.DynamicModels.statistics());
        }

        return lines;
    }

    // Counts read low on purpose (what Coarctatio kept, not what it stands in for); QUADS.size() survives the pool closing after bake
    public static String debugOverlayLine() {
        return String.format("Coarctatio: ~%s saved (/coarctatio for detail)", MemoryReport.summary());
    }
}
