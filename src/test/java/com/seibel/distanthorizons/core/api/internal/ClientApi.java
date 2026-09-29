package com.seibel.distanthorizons.core.api.internal;

// Stands in for Distant Horizons' internal ClientApi, whose render entry points the shadow pass reaches by handle
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
