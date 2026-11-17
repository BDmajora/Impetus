package com.bdmajora.testing;

import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Walks built option pages the way the options screen does.
public final class OptionPages {
    // A full-width options row
    private static final Dim2i ROW = new Dim2i(0, 0, 200, 18);
    // Keys of the English lang files.
    private static Set<String> langKeys;

    private OptionPages() {}

    // Returns the keys of both English lang files and fails when either is missing from the classpath
    private static synchronized Set<String> langKeys() {
        if (langKeys == null) {
            Set<String> keys = new HashSet<>();
            readLang("/assets/impetus/lang/en_US.lang", keys);
            readLang("/assets/minecraft/lang/en_us.lang", keys);
            langKeys = keys;
        }
        return langKeys;
    }

    // Adds every key of one lang file
    private static void readLang(String path, Set<String> keys) {
        try (InputStream in = OptionPages.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new AssertionError("Lang file " + path + " is not on the test classpath");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                int split = line.indexOf('=');
                if (split > 0 && !line.startsWith("#")) {
                    keys.add(line.substring(0, split).trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Fails when a translatable label has no English entry.
    public static void assertTranslated(TextComponent text, Object owner) {
        if (text instanceof TextComponent.Translatable translatable) {
            if (translatable.keys().stream().noneMatch(langKeys()::contains)) {
                throw new AssertionError("No en_US entry for " + translatable.keys() + " on " + owner);
            }
            for (Object arg : translatable.args()) {
                if (arg instanceof TextComponent nested) {
                    assertTranslated(nested, owner);
                }
            }
        } else if (text != null) {
            for (TextComponent child : text.children()) {
                assertTranslated(child, owner);
            }
        }
    }

    // Exercises every option and returns the count.
    public static int exercise(List<OptionPage> pages) {
        int count = 0;
        for (OptionPage page : pages) {
            assertTranslated(page.getName(), page.getId());
            for (OptionGroup group : page.getGroups()) {
                assertTranslated(group.getName(), group.getId());
                for (Option<?> option : group.getOptions()) {
                    exercise(option);
                    count++;
                }
            }
        }
        return count;
    }

    // Exercises an option of a real page and checks its labels against the lang files
    public static <T> void exercise(Option<T> option) {
        exercise(option, true);
    }

    // Exercises a test-built option whose keys are made up and so have no lang entry
    public static <T> void exerciseFixture(Option<T> option) {
        exercise(option, false);
    }

    private static <T> void exercise(Option<T> option, boolean lang) {
        if (lang) {
            assertTranslated(option.getName(), option.getId());
            assertTranslated(option.getTooltip(), option.getId());
        }
        option.getImpact();
        option.getFlags();
        option.isAvailable();
        Control<T> control = option.getControl();
        // Builds the row through the strict control checks.
        Control.createElementFor(option, ROW);
        if (lang && control instanceof CyclingControl<T> cycling) {
            for (TextComponent name : cycling.getNames()) {
                assertTranslated(name, option.getId());
            }
        }
        if (control instanceof SliderControl) {
            int min = Mixins.get(control, "min");
            int max = Mixins.get(control, "max");
            int interval = Mixins.get(control, "interval");
            ControlValueFormatter formatter = Mixins.get(control, "mode");
            for (int value = min; value <= max; value += interval) {
                TextComponent label = formatter.format(value);
                if (lang) {
                    assertTranslated(label, option.getId());
                }
            }
        }
        // Writing the current value back runs the setter without changing anything
        option.setValue(option.getValue());
        option.hasChanged();
        option.applyChanges();
    }
}
