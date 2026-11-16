package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// Option declared in one or more source locations with the same default.
public abstract class MergedOption<O extends BaseOption, M extends MergedOption<O, M>> {
    // Option whose comment and default are kept.
    protected final O option;
    // Source locations of the option.
    private final Set<OptionLocation> locations;

    // Creates a merged option over a location set.
    protected MergedOption(O option, Set<OptionLocation> locations) {
        this.option = option;
        this.locations = locations;
    }

    // Creates a merged option with one location.
    protected MergedOption(OptionLocation location, O option) {
        this.option = option;
        Set<OptionLocation> set = new LinkedHashSet<>();
        set.add(location);
        this.locations = Collections.unmodifiableSet(set);
    }

    // Returns whether another option has the same default.
    protected abstract boolean sameDefault(O other);

    // Creates a merged option of the subclass.
    protected abstract M create(O option, Set<OptionLocation> locations);

    // Joins the locations of two options or returns null when their defaults differ.
    public M merge(M other) {
        if (!sameDefault(other.option)) {
            return null;
        }

        O merged = this.option.getComment().isPresent() ? this.option : other.option;

        Set<OptionLocation> mergedLocations = new LinkedHashSet<>(this.locations);
        mergedLocations.addAll(other.getLocations());

        return create(merged, Collections.unmodifiableSet(mergedLocations));
    }

    // Returns the kept option.
    public O getOption() {
        return option;
    }

    // Returns the source locations.
    public Set<OptionLocation> getLocations() {
        return locations;
    }
}
