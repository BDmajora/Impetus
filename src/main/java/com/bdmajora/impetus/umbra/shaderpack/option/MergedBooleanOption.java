package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Set;

// The boolean form of MergedOption
public class MergedBooleanOption extends MergedOption<BooleanOption, MergedBooleanOption> {
    MergedBooleanOption(BooleanOption option, Set<OptionLocation> locations) {
        super(option, locations);
    }

    public MergedBooleanOption(OptionLocation location, BooleanOption option) {
        super(location, option);
    }

    @Override
    protected boolean sameDefault(BooleanOption other) {
        return this.option.getDefaultValue() == other.getDefaultValue();
    }

    @Override
    protected MergedBooleanOption create(BooleanOption option, Set<OptionLocation> locations) {
        return new MergedBooleanOption(option, locations);
    }
}
