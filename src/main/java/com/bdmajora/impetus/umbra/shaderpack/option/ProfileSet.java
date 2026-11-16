package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.option.values.OptionValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

// Profiles of a pack in file order and in precedence order.
public class ProfileSet {
    // Profiles in file order.
    private final LinkedHashMap<String, Profile> orderedProfiles;
    // Profiles from highest to lowest precedence.
    private final List<Profile> sortedProfiles;

    // Creates a set and sorts the profiles by precedence.
    public ProfileSet(LinkedHashMap<String, Profile> orderedProfiles) {
        List<Profile> sorted = new ArrayList<>(orderedProfiles.values());

        Comparator<Profile> lowToHigh = Comparator.comparing(p -> p.precedence);
        Comparator<Profile> highToLow = lowToHigh.reversed();

        sorted.sort(highToLow);

        this.sortedProfiles = sorted;
        this.orderedProfiles = orderedProfiles;
    }

    // Visits every profile in file order.
    public void forEach(BiConsumer<String, Profile> action) {
        orderedProfiles.forEach(action);
    }

    // Returns the first matching profile by precedence with its neighbors or the ends of the list.
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

        Profile next = sortedProfiles.get(0);
        Profile prev = sortedProfiles.get(sortedProfiles.size() - 1);

        return new ProfileResult(null, next, prev);
    }

    // Parses every profile of a tree and parses each included profile once.
    public static ProfileSet fromTree(Map<String, List<String>> tree, OptionSet optionSet) {
        LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();
        // Profiles already parsed so a shared include is parsed once
        Map<String, Profile> parsed = new HashMap<>();

        for (String name : tree.keySet()) {
            profiles.put(name, parse(name, new ArrayList<>(), tree, optionSet, parsed));
        }

        return new ProfileSet(profiles);
    }

    // Parses a profile and its includes and rejects a missing or cyclic profile.
    private static Profile parse(String name, List<String> parents, Map<String, List<String>> tree, OptionSet optionSet,
                                 Map<String, Profile> parsed) throws IllegalArgumentException {
        Profile done = parsed.get(name);
        if (done != null) {
            return done;
        }
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
                try {
                    builder.addAll(parse(dependency, parents, tree, optionSet, parsed));
                } finally {
                    parents.remove(parents.size() - 1);
                }
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

        Profile profile = builder.build();
        parsed.put(name, profile);
        return profile;
    }

    // Returns the number of profiles.
    public int size() {
        return sortedProfiles.size();
    }

    // Matching profile with the next and previous profiles.
    public static class ProfileResult {
        // Matching profile if any.
        public final Optional<Profile> current;
        // Profile after the match.
        public final Profile next;
        // Profile before the match.
        public final Profile previous;

        // Creates a result.
        private ProfileResult(Profile current, Profile next, Profile previous) {
            this.current = Optional.ofNullable(current);
            this.next = next;
            this.previous = previous;
        }
    }
}
