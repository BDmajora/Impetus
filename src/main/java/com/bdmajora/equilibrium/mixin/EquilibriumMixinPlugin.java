package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.equilibrium.config.Option;
import com.bdmajora.impetus.core.SimpleMixinPlugin;

// Class equilibrium mixin plugin.
public class EquilibriumMixinPlugin extends SimpleMixinPlugin {
    // Package of the Equilibrium mixins.
    private static final String MIXIN_PACKAGE_ROOT = "com.bdmajora.equilibrium.mixin.";

    // System property that turns off every mixin.
    private static final String DISABLE_ALL_MIXINS_PROPERTY = "equilibrium.disable_all_mixins";

    // Whether -Dequilibrium.disable_all_mixins=true turned every Equilibrium mixin off for this launch.
    public static final boolean DISABLE_ALL_MIXINS = Boolean.parseBoolean(System.getProperty(DISABLE_ALL_MIXINS_PROPERTY));

    // Loaded config.
    private static EquilibriumConfig config;

    // Loads the config once and throws when it fails.
    @Override
    public void onLoad(String mixinPackage) {
        if (DISABLE_ALL_MIXINS) {
            Equilibrium.LOGGER.warn("All Equilibrium mixins are disabled via -D{}=true", DISABLE_ALL_MIXINS_PROPERTY);
            return;
        }

        if (config != null) {
            return;
        }

        try {
            config = EquilibriumConfig.load(EquilibriumConfig.defaultFile());
        } catch (Exception e) {
            throw new RuntimeException("Could not load configuration file for Equilibrium", e);
        }

        Equilibrium.setConfig(config);
    }

    // Resolves the mixin's package against the option tree and refuses anything it cannot account for.
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (DISABLE_ALL_MIXINS) {
            return false;
        }

        if (!mixinClassName.startsWith(MIXIN_PACKAGE_ROOT)) {
            Equilibrium.LOGGER.error("Expected mixin '{}' to start with package root '{}', treating as foreign and "
                    + "disabling", mixinClassName, MIXIN_PACKAGE_ROOT);
            return false;
        }

        String mixin = mixinClassName.substring(MIXIN_PACKAGE_ROOT.length());

        Option option = config.getEffectiveOptionForMixin(mixin);

        if (option == null) {
            Equilibrium.LOGGER.error("No rules matched mixin '{}', treating as foreign and disabling", mixin);
            return false;
        }

        return option.isEnabled();
    }
}
