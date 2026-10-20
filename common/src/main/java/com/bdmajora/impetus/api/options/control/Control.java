package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.FontMetricsProvider;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Widget type that edits one option on the options screen
public interface Control<T> {
    // Returns the option this control edits and never returns null
    Option<T> getOption();

    // Creates a new element at exactly the given bounds on every call
    ControlElement<T> createElement(Dim2i dim);

    // Returns the maximum width of the control in pixels
    int getMaxWidth();

    // Returns the maximum width for the given fonts and defaults to getMaxWidth()
    default int getMaxWidth(FontMetricsProvider fonts) {
        return this.getMaxWidth();
    }

    // Returns the class of the values this control reads and writes or Object when the type argument is open
    default Class<?> getValueType() {
        return ControlValueTypes.declared(this.getClass());
    }

    // Returns the control's column width clamped between zero and the row width
    static int columnWidth(Control<?> control, FontMetricsProvider fonts, int rowWidth) {
        Objects.requireNonNull(control, "Control must not be null");
        Objects.requireNonNull(fonts, "Fonts must not be null");
        // Treats a row of negative width as zero width
        return Math.clamp(control.getMaxWidth(fonts), 0, Math.max(0, rowWidth));
    }

    // Creates the option's element at the given bounds and checks that the option and control and element match
    static <T> ControlElement<T> createElementFor(Option<T> option, Dim2i dim) {
        Objects.requireNonNull(option, "Option must not be null");
        Objects.requireNonNull(dim, "Dimensions must not be null");
        Control<T> control = option.getControl();
        if (control == null) {
            throw new IllegalStateException("Option " + describe(option) + " has no control");
        }
        // Rejects a control that belongs to another option
        if (control.getOption() != option) {
            throw new IllegalStateException("Control of option " + describe(option) + " edits a different option");
        }
        // Rejects a control whose value type does not match the value type of the option
        Class<?> controlType = control.getValueType();
        Class<?> optionType = option.getValueType();
        if (!ControlValueTypes.related(controlType, optionType)) {
            throw new IllegalStateException("Control of option " + describe(option) + " edits " + controlType.getName() + " values but the option holds " + optionType.getName());
        }
        ControlElement<T> element = control.createElement(dim);
        if (element == null) {
            throw new IllegalStateException("Control of option " + describe(option) + " created no element");
        }
        if (element.getOption() != option) {
            throw new IllegalStateException("Element of option " + describe(option) + " edits a different option");
        }
        Dim2i bounds = element.getDimensions();
        // Rejects an element whose bounds differ from the given bounds
        if (bounds != dim && !dim.equals(bounds)) {
            throw new IllegalStateException("Element of option " + describe(option) + " has bounds " + bounds + " instead of " + dim);
        }
        return element;
    }

    // Creates the option's element or a greyed out placeholder row when its control fails a check or throws
    static <T> ControlElement<T> createElementOrPlaceholder(Option<T> option, Dim2i dim) {
        Objects.requireNonNull(option, "Option must not be null");
        Objects.requireNonNull(dim, "Dimensions must not be null");
        // Returns a placeholder immediately for an option already marked broken
        if (PlaceholderControlElement.isBroken(option)) {
            return new PlaceholderControlElement<>(option, dim, describe(option));
        }
        try {
            return createElementFor(option, dim);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable broken) {
            return placeholder(option, dim, broken);
        }
    }

    // Returns a placeholder row for an element that threw on screen and reports the failure once per option
    static <T> ControlElement<T> placeholderFor(ControlElement<T> element, Throwable failure) {
        Objects.requireNonNull(element, "Element must not be null");
        Objects.requireNonNull(failure, "Failure must not be null");
        // Returns an existing placeholder unchanged
        if (element instanceof PlaceholderControlElement<T>) {
            return element;
        }
        // Reads the option and bounds from the fields instead of the getters
        return placeholder(element.option, element.dim, failure);
    }

    // Reports a failure in the option's own code once and marks the option so later rows become placeholders
    static void reportBroken(Option<?> option, String during, Throwable failure) {
        Objects.requireNonNull(option, "Option must not be null");
        Objects.requireNonNull(during, "Activity must not be null");
        Objects.requireNonNull(failure, "Failure must not be null");
        // Restores the interrupt flag after an interruption
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        PlaceholderControlElement.reportOption(option, describe(option), during, failure);
    }

    // Reports the failure and makes the placeholder for the option at the given bounds
    private static <T> ControlElement<T> placeholder(Option<T> option, Dim2i dim, Throwable failure) {
        // Restores the interrupt flag after an interruption
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        String name = describe(option);
        PlaceholderControlElement.report(option, name, failure);
        return new PlaceholderControlElement<>(option, dim, name);
    }

    // Returns the option's id or its name when it has no id for use in failure messages
    private static String describe(Option<?> option) {
        // Falls back to the class name when reading the id or name throws anything other than a virtual machine error
        try {
            OptionIdentifier<?> id = option.getId();
            return OptionIdentifier.isPresent(id) ? id.toString() : String.valueOf(option.getName());
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable unreadable) {
            // Restores the interrupt flag after an interruption
            if (unreadable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return option.getClass().getName();
        }
    }
}