package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.option.values.MutableOptionValues;
import com.bdmajora.impetus.umbra.shaderpack.option.values.OptionValues;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Options and values of a pack with the edited sources.
public class ShaderPackOptions {
    // Option set.
    private final OptionSet optionSet;
    // Option values.
    private final OptionValues optionValues;
    private final Map<AbsolutePackPath, String> editedSources;

    // Creates a shader pack options from its sources and changed configs.
    public ShaderPackOptions(Map<AbsolutePackPath, String> sources, Map<String, String> changedConfigs) {
        Map<AbsolutePackPath, OptionAnnotatedSource> allAnnotations = new HashMap<>();
        Set<String> referencedBooleanDefines = new HashSet<>();

        sources.forEach((path, source) -> {
            OptionAnnotatedSource annotatedSource = new OptionAnnotatedSource(source);
            allAnnotations.put(path, annotatedSource);
            referencedBooleanDefines.addAll(annotatedSource.getBooleanDefineReferences().keySet());
        });

        Set<String> referencedBooleanDefinesU = Collections.unmodifiableSet(referencedBooleanDefines);

        OptionSet.Builder setBuilder = OptionSet.builder();
        allAnnotations.forEach((path, annotatedSource) -> {
            OptionSet set = annotatedSource.getOptionSet(path, referencedBooleanDefinesU);
            setBuilder.addAll(set);
        });

        this.optionSet = setBuilder.build();
        this.optionValues = new MutableOptionValues(optionSet, changedConfigs);

        Map<AbsolutePackPath, String> edited = new HashMap<>();
        allAnnotations.forEach((path, annotatedSource) -> edited.put(path, annotatedSource.apply(optionValues)));
        this.editedSources = Collections.unmodifiableMap(edited);
    }

    // Returns the option set.
    public OptionSet getOptionSet() {
        return optionSet;
    }

    // Returns the option values.
    public OptionValues getOptionValues() {
        return optionValues;
    }

    // Returns the edited sources.
    public Map<AbsolutePackPath, String> getEditedSources() {
        return editedSources;
    }
}
