package com.bdmajora.impetus.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

// Impetus' single coremod entry point (FMLCorePlugin takes one class); Cleanroom has Mixin running before any coremod is built, so all that is left here is registering Impetus' configs
@IFMLLoadingPlugin.Name("Impetus")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class ImpetusLoadingPlugin implements IFMLLoadingPlugin {

    // As early as Impetus gets control: the blacklist must precede the superseded mods' own registration, and Impetus' configs must be queued before anything loads their targets
    public ImpetusLoadingPlugin() {
        ImpetusMixinRegistrar.hijackSupersededLighting();
        ImpetusMixinRegistrar.queueImpetusConfigs();
        // The window's no-error decision needs the adapter list; on Windows that is a PowerShell round trip, which this lets run alongside the rest of the launch
        com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe.prefetch();
        // Cleanroom picks X11 or Wayland once, when its Display first starts GLFW, which is long after this
        com.bdmajora.linuxextras.LinuxExtras.chooseWindowSystem();
    }

    // injectData runs once every coremod jar has joined the classpath, which DH's resource lookup needs
    @Override
    public void injectData(Map<String, Object> data) {
        ImpetusMixinRegistrar.restoreDistantHorizonsDepthTexture();
    }

    // Required by IFMLLoadingPlugin; left blank as Impetus has no ASM transformers
    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    // Required by IFMLLoadingPlugin; left blank as Impetus does not use a mod container
    @Override
    public String getModContainerClass() {
        return null;
    }

    // Required by IFMLLoadingPlugin; left blank as Impetus does not use a setup class
    @Override
    public String getSetupClass() {
        return null;
    }

    // Required by IFMLLoadingPlugin; the access transformer is declared through FMLAT in the manifest
    @Override
    public String getAccessTransformerClass() {
        return null;
    }

}
