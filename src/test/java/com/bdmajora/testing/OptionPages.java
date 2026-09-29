package com.bdmajora.testing;

import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;

import java.util.List;

// Walks built option pages the way the options screen does: reads every label, asks every enable gate, formats every slider stop and writes every value back through its binding
public final class OptionPages {
    private OptionPages() {}

    // The number of options visited, so a test can tell a page list was not empty
    public static int exercise(List<OptionPage> pages) {
        int count = 0;
        for (OptionPage page : pages) {
            page.getName();
            for (OptionGroup group : page.getGroups()) {
                group.getName();
                for (Option<?> option : group.getOptions()) {
                    exercise(option);
                    count++;
                }
            }
        }
        return count;
    }

    public static <T> void exercise(Option<T> option) {
        option.getName();
        option.getTooltip();
        option.getImpact();
        option.getFlags();
        option.isAvailable();
        Control<T> control = option.getControl();
        if (control instanceof SliderControl) {
            int min = Mixins.get(control, "min");
            int max = Mixins.get(control, "max");
            int interval = Mixins.get(control, "interval");
            ControlValueFormatter formatter = Mixins.get(control, "mode");
            for (int value = min; value <= max; value += interval) {
                formatter.format(value);
            }
        }
        // Writing the current value back runs the setter without changing anything
        option.setValue(option.getValue());
        option.hasChanged();
        option.applyChanges();
    }
}
