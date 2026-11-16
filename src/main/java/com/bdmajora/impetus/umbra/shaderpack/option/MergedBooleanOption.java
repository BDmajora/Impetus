package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Set;

// Boolean option merged across source files.
public class MergedBooleanOption extends MergedOption<BooleanOption, MergedBooleanOption> {
    // Creates a merged boolean option from its option and locations.
    MergedBooleanOption(BooleanOption option, Set<OptionLocation> locations) {
        super(option, locations);
    }

    // Creates a merged boolean option from its location and option.
    public MergedBooleanOption(OptionLocation location, BooleanOption option) {
        super(location, option);
    }

    // Returns whether another option has the same default.
    @Override
    protected boolean sameDefault(BooleanOption other) {
        return this.option.getDefaultValue() == other.getDefaultValue();
    }

    // Creates a merged boolean option.
    @Override
    protected MergedBooleanOption create(BooleanOption option, Set<OptionLocation> locations) {
        return new MergedBooleanOption(option, locations);
    }
}
