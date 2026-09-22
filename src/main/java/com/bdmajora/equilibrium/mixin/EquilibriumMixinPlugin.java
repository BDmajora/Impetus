package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.equilibrium.config.Option;
import com.bdmajora.impetus.booter.mixin.SimpleMixinPlugin;

// Decides which mixins apply by resolving each package path against the option tree; the package is the switch, so adding a mixin under an existing option needs no change here
public class EquilibriumMixinPlugin extends SimpleMixinPlugin {
    // Everything below this prefix is ours; anything else is treated as foreign and refused
    private static final String MIXIN_PACKAGE_ROOT = "com.bdmajora.equilibrium.mixin.";

    // Kill switch for bisecting a startup crash without editing the config, mirroring lithium.test.disable_all_mixins; answers "is this us" in one launch argument
    private static final String DISABLE_ALL_MIXINS_PROPERTY = "equilibrium.disable_all_mixins";

    // Read once at class init, before any mixin is considered
    public static final boolean DISABLE_ALL_MIXINS = Boolean.parseBoolean(System.getProperty(DISABLE_ALL_MIXINS_PROPERTY));

    // Loaded on first onLoad and shared by every subsequent shouldApplyMixin call
    private static EquilibriumConfig config;

    // Loads the config once; a failure here is fatal because every later decision depends on it
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

    // Resolves the mixin's package against the option tree; refuses anything it cannot account for
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

        // An unmatched mixin means a package was added without its option; refusing to apply is the safe reading, and the log line says exactly what to add
        if (option == null) {
            Equilibrium.LOGGER.error("No rules matched mixin '{}', treating as foreign and disabling", mixin);
            return false;
        }

        return option.isEnabled();
    }
}
