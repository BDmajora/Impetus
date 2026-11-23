package com.bdmajora.impetus.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

// Coremod entry that registers the Impetus mixins.
@IFMLLoadingPlugin.Name("Impetus")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class ImpetusLoadingPlugin implements IFMLLoadingPlugin {

    // Creates an impetus loading plugin with default state.
    public ImpetusLoadingPlugin() {
        TransformerChain.makeConcurrent();
        ImpetusMixinRegistrar.hijackSupersededLighting();
        ImpetusMixinRegistrar.suppressVanillaFrustumFix();
        ImpetusMixinRegistrar.queueImpetusConfigs();
        com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe.prefetch();
    }

    // Receives the loader data.
    @Override
    public void injectData(Map<String, Object> data) { }

    // Returns the asm transformer class.
    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    // Returns the mod container class.
    @Override
    public String getModContainerClass() {
        return null;
    }

    // Returns the setup class.
    @Override
    public String getSetupClass() {
        return null;
    }

    // Returns the access transformer class.
    @Override
    public String getAccessTransformerClass() {
        return null;
    }

}
