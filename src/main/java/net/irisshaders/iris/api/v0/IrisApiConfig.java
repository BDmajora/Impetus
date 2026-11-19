package net.irisshaders.iris.api.v0;

// Iris API v0 configuration contract implemented by Umbra.
public interface IrisApiConfig {
    boolean areShadersEnabled();

    void setShadersEnabledAndApply(boolean enabled);
}
