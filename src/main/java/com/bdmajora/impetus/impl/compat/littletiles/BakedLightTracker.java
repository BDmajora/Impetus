package com.bdmajora.impetus.impl.compat.littletiles;

// Added to LittleTiles' TileEntityRenderManager by LittleTilesRenderManagerMixin, and only called under that manager's monitor
public interface BakedLightTracker {
    // Records the light fingerprint the mesher just read; true when it differs from the last one, or there was none
    boolean impetus$lightChanged(int fingerprint);
}
