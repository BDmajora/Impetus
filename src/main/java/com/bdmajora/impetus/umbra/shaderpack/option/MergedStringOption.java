package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Set;

// String option merged across source files.
public class MergedStringOption extends MergedOption<StringOption, MergedStringOption> {
    // Creates a merged string option from its option and locations.
    MergedStringOption(StringOption option, Set<OptionLocation> locations) {
        super(option, locations);
    }

    // Creates a merged string option from its location and option.
    public MergedStringOption(OptionLocation location, StringOption option) {
        super(location, option);
    }

    // Returns whether another option has the same default.
    @Override
    protected boolean sameDefault(StringOption other) {
        return this.option.getDefaultValue().equals(other.getDefaultValue());
    }

    // Creates a merged string option.
    @Override
    protected MergedStringOption create(StringOption option, Set<OptionLocation> locations) {
        return new MergedStringOption(option, locations);
    }
}
