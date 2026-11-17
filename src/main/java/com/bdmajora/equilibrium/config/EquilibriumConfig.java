package com.bdmajora.equilibrium.config;

import com.bdmajora.equilibrium.Equilibrium;
import net.minecraft.launchwrapper.Launch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

// Switches of the general performance patches loaded from a properties file.
public class EquilibriumConfig {
    // Name of the properties file.
    private static final String FILE_NAME = "equilibrium.properties";

    // Switches by name.
    private final Map<String, Option> options = new HashMap<>();

    // Switches that have dependencies.
    private final Set<Option> optionsWithDependencies = new LinkedHashSet<>();

    // File of the settings.
    private Path file;

    // Master switch of every patch outside the switches by name.
    private final Option master = new Option("enabled", true, false);

    // Creates the switches with their defaults and dependencies.
    private EquilibriumConfig() {
        for (EquilibriumOptions.Entry entry : EquilibriumOptions.entries().values()) {
            this.addMixinRule(entry.name(), entry.enabledByDefault());
        }

        for (EquilibriumOptions.Entry entry : EquilibriumOptions.entries().values()) {
            for (Map.Entry<String, Boolean> dependency : entry.dependencies().entrySet()) {
                this.addRuleDependency(entry.name(), dependency.getKey(), dependency.getValue());
            }
        }
    }

    // Returns the settings read from the file and writes a default file when it is missing.
    public static EquilibriumConfig load(Path file) {
        java.util.Objects.requireNonNull(file, "file");
        EquilibriumConfig config = new EquilibriumConfig();
        config.file = file;

        if (Files.isRegularFile(file)) {
            Properties props = new Properties();

            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                Equilibrium.LOGGER.error("Could not read {}, falling back to defaults", file, e);
            }

            config.readProperties(props);
        } else {
            config.save();
        }

        for (ModCompatibility.Override override : ModCompatibility.detect()) {
            config.applyModOverride(override);
        }

        config.applyDependencies();

        return config;
    }

    // Returns the settings file inside the config folder.
    public static Path defaultFile() {
        File home = Launch.minecraftHome;
        Path dir = (home == null ? Paths.get(".") : home.toPath()).resolve("config");

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Equilibrium.LOGGER.warn("Could not create {}, configuration will not persist", dir, e);
        }

        return dir.resolve(FILE_NAME);
    }

    // Adds a switch and rejects a duplicate.
    private void addMixinRule(String mixin, boolean enabled) {
        if (this.options.put(mixin, new Option(mixin, enabled, false)) != null) {
            throw new IllegalStateException("Mixin rule already defined: " + mixin);
        }
    }

    // Links a switch to the value that another switch must hold.
    private void addRuleDependency(String rule, String dependency, boolean requiredValue) {
        Option option = this.options.get(rule);
        Option dependencyOption = this.options.get(dependency);

        if (option == null || dependencyOption == null) {
            Equilibrium.LOGGER.error("Dependency '{} depends on {}={}' names an option that does not exist, skipping",
                    rule, dependency, requiredValue);
            return;
        }

        option.addDependency(dependencyOption, requiredValue);
        this.optionsWithDependencies.add(option);
    }

    // Applies every valid line of the file.
    private void readProperties(Properties props) {
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            Option option = "enabled".equals(key) ? this.master : this.options.get(key);

            if (option == null) {
                Equilibrium.LOGGER.warn("No configuration key exists with name '{}', ignoring", key);
                continue;
            }

            boolean enabled;

            if (value.equalsIgnoreCase("true")) {
                enabled = true;
            } else if (value.equalsIgnoreCase("false")) {
                enabled = false;
            } else {
                Equilibrium.LOGGER.warn("Invalid value '{}' encountered for configuration key '{}', ignoring",
                        value, key);
                continue;
            }

            option.setEnabled(enabled, true);
        }
    }

    // Applies a value forced by a mod unless the player set the switch.
    void applyModOverride(ModCompatibility.Override override) {
        Option option = this.options.get(override.option());

        if (option == null && !override.option().startsWith("mixin.")) {
            option = this.options.get("mixin." + override.option());
        }

        if (option == null) {
            Equilibrium.LOGGER.warn("Mod '{}' attempted to override option '{}', which doesn't exist, ignoring",
                    override.modId(), override.option());
            return;
        }

        if (option.isUserDefined()) {
            Equilibrium.LOGGER.warn("{} is installed and wants '{}={}', but the config file sets it to {}. "
                            + "Leaving the configured value; expect problems if it turns out to be wrong.",
                    override.modId(), override.option(), override.enabled(), option.isEnabled());
            return;
        }

        if (!override.enabled() && option.isEnabled()) {
            option.clearModsDefiningValue();
        }

        if (!override.enabled() || option.isEnabled() || option.getDefiningMods().isEmpty()) {
            option.addModOverride(override.enabled(), override.modId());
        }
    }

    // Returns the switch that governs the mixin class.
    public Option getEffectiveOptionForMixin(String mixinClassName) {
        if (!this.master.isEnabled()) {
            return this.master;
        }

        int lastSplit = 0;
        int nextSplit;

        Option rule = null;

        while ((nextSplit = mixinClassName.indexOf('.', lastSplit)) != -1) {
            String key = "mixin." + mixinClassName.substring(0, nextSplit);

            Option candidate = this.options.get(key);

            if (candidate != null) {
                rule = candidate;

                if (!rule.isEnabled()) {
                    return rule;
                }
            }

            lastSplit = nextSplit + 1;
        }

        return rule;
    }

    // Returns whether the switch and its parents are on.
    public boolean isOptionEnabled(String optionName) {
        Option option = this.options.get(optionName);
        return this.master.isEnabled() && option != null && option.isEnabledRecursive(this);
    }

    // Returns whether the master switch is on.
    public boolean isEnabled() {
        return this.master.isEnabled();
    }

    // Stores the master switch value the player chose.
    public void setEnabled(boolean enabled) {
        this.master.setEnabled(enabled, true);
    }

    // Stores a value that the player chose.
    public void setOptionEnabled(String optionName, boolean enabled) {
        Option option = this.options.get(optionName);

        if (option != null) {
            option.setEnabled(enabled, true);
        }
    }

    // Returns the switch of the name or null.
    public Option getOption(String optionName) {
        return this.options.get(optionName);
    }

    // Returns the switch that governs the option or null.
    public Option getParent(Option option) {
        String optionName = option.getName();
        int split = optionName.lastIndexOf('.');

        return split == -1 ? null : this.options.get(optionName.substring(0, split));
    }

    // Returns the number of switches.
    public int getOptionCount() {
        return this.options.size();
    }

    // Returns the number of switches set by the player or a mod.
    public int getOptionOverrideCount() {
        int count = 0;

        for (Option option : this.options.values()) {
            if (option.isOverridden()) {
                count++;
            }
        }

        return count;
    }

    // Turns off switches until every dependency holds.
    private void applyDependencies() {
        while (this.applyDependenciesOnce()) {
        }
    }

    // Runs one pass and returns whether a switch changed.
    private boolean applyDependenciesOnce() {
        boolean changed = false;

        for (Option optionWithDependency : this.optionsWithDependencies) {
            changed |= optionWithDependency.disableIfDependenciesNotMet(this);
        }

        return changed;
    }

    // Writes the settings to the file.
    public void save() {
        if (this.file == null) {
            return;
        }

        try {
            Path parent = this.file.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");

            try (OutputStream stream = Files.newOutputStream(temp);
                 Writer writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                this.write(writer);
            }

            try {
                Files.move(temp, this.file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, this.file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Equilibrium.LOGGER.warn("Could not write {}", this.file, e);
        }
    }

    // Writes the commented settings file.
    private void write(Writer writer) throws IOException {
        writer.write("# Impetus game logic patches.\n");
        writer.write("#\n");
        writer.write("# Every key below turns one group of patches on or off. A key covers its own\n");
        writer.write("# patches and every key beneath it, so mixin.world=false turns off all of the\n");
        writer.write("# world patches whatever the keys under it say.\n");
        writer.write("#\n");
        writer.write("# Changes take effect on the next launch. Deleting a line restores its default.\n");
        writer.write("# To find a patch that breaks something, set a category key to false and work down.\n");
        writer.write("\n");
        writer.write("# Turns every patch on. Off keeps the keys below but runs none of them.\n");
        writer.write("# Default: true\n");
        writer.write("enabled=" + this.master.isEnabled() + "\n");

        String category = null;

        for (EquilibriumOptions.Entry entry : EquilibriumOptions.entries().values()) {
            Option option = this.options.get(entry.name());

            if (!entry.category().equals(category)) {
                category = entry.category();
                writer.write("\n");
                writer.write("# ---------------------------------------------------------------------------\n");
                writer.write("# " + category + "\n");
                writer.write("# ---------------------------------------------------------------------------\n");
            }

            writer.write("\n");

            for (String line : wrap(entry.description(), 74)) {
                writer.write("# " + line + "\n");
            }

            if (entry.nonVanillaBehaviour() != null) {
                writer.write("#\n");
                writer.write("# Differs from vanilla:\n");

                for (String line : wrap(entry.nonVanillaBehaviour(), 72)) {
                    writer.write("#   " + line + "\n");
                }
            }

            for (Map.Entry<String, Boolean> dependency : entry.dependencies().entrySet()) {
                writer.write("# Requires: " + dependency.getKey() + "=" + dependency.getValue() + "\n");
            }

            writer.write("# Default: " + entry.enabledByDefault() + "\n");

            if (option != null && option.isUserDefined()) {
                writer.write(entry.name() + "=" + option.isEnabled() + "\n");
            } else {
                writer.write("#" + entry.name() + "=" + entry.enabledByDefault() + "\n");
            }
        }
    }

    // Returns the text split into lines of at most the width.
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }

            if (line.length() > 0) {
                line.append(' ');
            }

            line.append(word);
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return lines;
    }
}
