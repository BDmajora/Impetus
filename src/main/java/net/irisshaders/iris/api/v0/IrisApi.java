package net.irisshaders.iris.api.v0;

// Iris API v0 answered by Impetus. This must remain an interface for existing mod binaries.
public interface IrisApi {
    static IrisApi getInstance() {
        return IrisApiInternal.INSTANCE;
    }

    int getMinorApiRevision();

    boolean isShaderPackInUse();

    boolean isRenderingShadowPass();

    Object openMainIrisScreenObj(Object parent);

    String getMainScreenLanguageKey();

    IrisApiConfig getConfig();
}
