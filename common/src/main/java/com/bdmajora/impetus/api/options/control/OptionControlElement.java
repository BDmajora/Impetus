package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;

// Row on the options screen that edits one option of type T
public interface OptionControlElement<T> {
    // Returns the same option instance on every call and never returns null
    Option<T> getOption();
}