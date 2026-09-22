package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Set;

// The string-valued form of MergedOption
public class MergedStringOption extends MergedOption<StringOption, MergedStringOption> {
    MergedStringOption(StringOption option, Set<OptionLocation> locations) {
        super(option, locations);
    }

    public MergedStringOption(OptionLocation location, StringOption option) {
        super(location, option);
    }

    @Override
    protected boolean sameDefault(StringOption other) {
        return this.option.getDefaultValue().equals(other.getDefaultValue());
    }

    @Override
    protected MergedStringOption create(StringOption option, Set<OptionLocation> locations) {
        return new MergedStringOption(option, locations);
    }
}
