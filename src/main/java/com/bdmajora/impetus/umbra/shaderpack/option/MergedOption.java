package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// One option deduplicated across every file and line it was declared in, so the config screen shows ONE control and the writer can patch every copy; from Iris, with ImmutableSet replaced by an unmodifiable LinkedHashSet that keeps declaration order stable
public abstract class MergedOption<O extends BaseOption, M extends MergedOption<O, M>> {
    protected final O option;
    private final Set<OptionLocation> locations;

    protected MergedOption(O option, Set<OptionLocation> locations) {
        this.option = option;
        this.locations = locations;
    }

    protected MergedOption(OptionLocation location, O option) {
        this.option = option;
        Set<OptionLocation> set = new LinkedHashSet<>();
        set.add(location);
        this.locations = Collections.unmodifiableSet(set);
    }

    // Whether the two declarations agree on their default; disagreeing defaults make the option genuinely ambiguous, with no single value the screen could show, and picking one would change what half the includes compile with
    protected abstract boolean sameDefault(O other);

    protected abstract M create(O option, Set<OptionLocation> locations);

    // Merges another declaration of the same option, or null when irreconcilable; null rather than an exception since an ambiguous option just drops from the config screen and the pack still runs
    public M merge(M other) {
        if (!sameDefault(other.option)) {
            return null;
        }

        // Keep whichever declaration carried a comment, the human-readable label the screen displays, since usually only one of several identical declarations has one
        O merged = this.option.getComment().isPresent() ? this.option : other.option;

        Set<OptionLocation> mergedLocations = new LinkedHashSet<>(this.locations);
        mergedLocations.addAll(other.getLocations());

        return create(merged, Collections.unmodifiableSet(mergedLocations));
    }

    // The option
    public O getOption() {
        return option;
    }

    // Every file and line that defines it
    public Set<OptionLocation> getLocations() {
        return locations;
    }
}
