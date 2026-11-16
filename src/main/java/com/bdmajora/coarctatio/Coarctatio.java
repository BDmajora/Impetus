package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
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

// Entry points of the memory features.
public final class Coarctatio {
    // Logger of the module.
    public static final Logger LOGGER = LogManager.getLogger("Coarctatio");

    // Prevents instantiation
    private Coarctatio() {
    }

    // Opens the pools that live for one reload.
    public static void onResourceReloadStart() {
        ModelCaches.open();
        TransformCaches.open();
        ConditionCanonicalizer.open();
    }

    // Closes the pools that live for one reload.
    public static void onResourceReloadFinish() {
        ModelCaches.close();
        TransformCaches.close();
        ConditionCanonicalizer.close();
    }

    // Clears the pools that belong to a world.
    public static void onWorldLeave() {
        StringPool.NBT_KEYS.clear();
        ResourceLocationCaches.PATHS.open();

    }

    // Returns the statistics of every enabled feature as text lines.
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
            lines.addAll(DynamicModels.statistics());
        }

        return lines;
    }

    // Returns the line shown in the debug overlay.
    public static String debugOverlayLine() {
        return String.format("Coarctatio: ~%s saved (/coarctatio for detail)", MemoryReport.summary());
    }
}
