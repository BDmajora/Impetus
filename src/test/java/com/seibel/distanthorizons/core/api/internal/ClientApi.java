package com.seibel.distanthorizons.core.api.internal;

// Stand-in for Distant Horizons' ClientApi.
public final class ClientApi {
    public static final ClientApi INSTANCE = new ClientApi();
    public static int lods;
    public static int deferredLods;
    public static boolean fail;

    public void renderLods() {
        if (fail) {
            throw new IllegalStateException("LOD render failed");
        }
        lods++;
    }

    public void renderDeferredLodsForShaders() {
        if (fail) {
            throw new IllegalStateException("LOD render failed");
        }
        deferredLods++;
    }
}
