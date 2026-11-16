package com.bdmajora.equilibrium.config;

import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// One switch of the configuration with its dependencies.
public class Option {
    // Name of the switch.
    private final String name;

    // Switches that must hold a value or null.
    private Object2BooleanLinkedOpenHashMap<Option> dependencies;

    // Mods that set the value or null.
    private Set<String> modDefined = null;

    // Current value.
    private boolean enabled;

    // Whether the player set the value.
    private boolean userDefined;

    // Creates a switch.
    public Option(String name, boolean enabled, boolean userDefined) {
        this.name = name;
        this.enabled = enabled;
        this.userDefined = userDefined;
    }

    // Stores the value.
    public void setEnabled(boolean enabled, boolean userDefined) {
        this.enabled = enabled;
        this.userDefined = userDefined;
    }

    // Stores a value set by a mod.
    public void addModOverride(boolean enabled, String modId) {
        this.enabled = enabled;

        if (this.modDefined == null) {
            this.modDefined = new LinkedHashSet<>();
        }

        this.modDefined.add(modId);
    }

    // Returns the value.
    public boolean isEnabled() {
        return this.enabled;
    }

    // Returns whether the value and every parent value are on.
    public boolean isEnabledRecursive(EquilibriumConfig config) {
        return this.enabled && (config.getParent(this) == null || config.getParent(this).isEnabledRecursive(config));
    }

    // Returns whether the player or a mod set the value.
    public boolean isOverridden() {
        return this.isUserDefined() || this.isModDefined();
    }

    // Returns whether the player set the value.
    public boolean isUserDefined() {
        return this.userDefined;
    }

    // Returns whether a mod set the value.
    public boolean isModDefined() {
        return this.modDefined != null;
    }

    // Returns the name.
    public String getName() {
        return this.name;
    }

    // Forgets the mods that set the value.
    public void clearModsDefiningValue() {
        this.modDefined = null;
    }

    // Returns the mods that set the value.
    public Collection<String> getDefiningMods() {
        return this.modDefined != null ? Collections.unmodifiableCollection(this.modDefined) : Collections.<String>emptyList();
    }

    // Requires another switch to hold a value.
    public void addDependency(Option dependencyOption, boolean requiredValue) {
        if (this.dependencies == null) {
            this.dependencies = new Object2BooleanLinkedOpenHashMap<>(1);
        }
        this.dependencies.put(dependencyOption, requiredValue);
    }

    // Turns the switch off when a dependency fails and returns whether it changed.
    public boolean disableIfDependenciesNotMet(EquilibriumConfig config) {
        if (this.dependencies != null && this.isEnabled()) {
            for (Object2BooleanMap.Entry<Option> dependency : this.dependencies.object2BooleanEntrySet()) {
                Option option = dependency.getKey();
                boolean requiredValue = dependency.getBooleanValue();
                boolean enabledRecursive = option.isEnabledRecursive(config);

                if (enabledRecursive != requiredValue) {
                    this.enabled = false;
                    return true;
                }
            }
        }

        return false;
    }
}
