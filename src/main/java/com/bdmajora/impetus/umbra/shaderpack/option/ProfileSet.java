package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.option.values.OptionValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

// The profiles a pack declares in declaration order (worst-to-best, so cycling forward raises quality), plus the reverse scan matching current values to a profile so the screen shows "Profile: High" rather than "Custom". From Iris
public class ProfileSet {
    private final LinkedHashMap<String, Profile> orderedProfiles; // The order that profiles should cycle through
    private final List<Profile> sortedProfiles; // The order that profiles should be scanned through

    public ProfileSet(LinkedHashMap<String, Profile> orderedProfiles) {
        List<Profile> sorted = new ArrayList<>(orderedProfiles.values());

        Comparator<Profile> lowToHigh = Comparator.comparing(p -> p.precedence);
        Comparator<Profile> highToLow = lowToHigh.reversed();

        // Compare profiles with many constraints first, needed for accurate matching when one profile adds a constraint to another but is otherwise the same
        sorted.sort(highToLow);

        this.sortedProfiles = sorted;
        this.orderedProfiles = orderedProfiles;
    }

    // In declaration order
    public void forEach(BiConsumer<String, Profile> action) {
        orderedProfiles.forEach(action);
    }

    // Finds the profile the current values match, plus its neighbours for cycling
    public ProfileResult scan(OptionSet options, OptionValues values) {
        if (sortedProfiles.isEmpty()) {
            return new ProfileResult(null, null, null);
        }

        for (int i = 0; i < sortedProfiles.size(); i++) {
            Profile current = sortedProfiles.get(i);

            if (current.matches(options, values)) {
                Profile next = sortedProfiles.get(Math.floorMod(i + 1, sortedProfiles.size()));
                Profile prev = sortedProfiles.get(Math.floorMod(i - 1, sortedProfiles.size()));

                return new ProfileResult(current, next, prev);
            }
        }

        // Default return if no profiles matched
        Profile next = sortedProfiles.get(0);
        Profile prev = sortedProfiles.get(sortedProfiles.size() - 1);

        return new ProfileResult(null, next, prev);
    }

    // Builds from the parsed profile.<name> lines
    public static ProfileSet fromTree(Map<String, List<String>> tree, OptionSet optionSet) {
        LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();

        for (String name : tree.keySet()) {
            profiles.put(name, parse(name, new ArrayList<>(), tree, optionSet));
        }

        return new ProfileSet(profiles);
    }

    // One profile, resolving profile.<parent> references recursively
    private static Profile parse(String name, List<String> parents, Map<String, List<String>> tree, OptionSet optionSet) throws IllegalArgumentException {
        Profile.Builder builder = new Profile.Builder(name);
        List<String> options = tree.get(name);

        if (options == null) {
            throw new IllegalArgumentException("Profile \"" + name + "\" does not exist!");
        }

        for (String option : options) {
            if (option.startsWith("!program.")) {
                builder.disableProgram(option.substring("!program.".length()));
            } else if (option.startsWith("profile.")) {
                String dependency = option.substring("profile.".length());

                if (parents.contains(dependency)) {
                    throw new IllegalArgumentException("Error parsing profile \"" + name
                            + "\", recursively included by: " + String.join(", ", parents));
                }

                parents.add(dependency);
                builder.addAll(parse(dependency, parents, tree, optionSet));
            } else if (option.startsWith("!")) {
                builder.option(option.substring(1), "false");
            } else if (option.contains("=")) {
                int splitPoint = option.indexOf("=");
                builder.option(option.substring(0, splitPoint), option.substring(splitPoint + 1));
            } else if (option.contains(":")) {
                int splitPoint = option.indexOf(":");
                builder.option(option.substring(0, splitPoint), option.substring(splitPoint + 1));
            } else if (optionSet.isBooleanOption(option)) {
                builder.option(option, "true");
            } else {
                Umbra.logger().warn("Invalid pack option: " + option);
            }
        }

        return builder.build();
    }

    // Profile count
    public int size() {
        return sortedProfiles.size();
    }

    public static class ProfileResult {
        public final Optional<Profile> current;
        public final Profile next;
        public final Profile previous;

        private ProfileResult(Profile current, Profile next, Profile previous) {
            this.current = Optional.ofNullable(current);
            this.next = next;
            this.previous = previous;
        }
    }
}
