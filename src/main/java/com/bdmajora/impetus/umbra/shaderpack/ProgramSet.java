package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Programs of a pack folder by id.
public final class ProgramSet {
    private final Map<ProgramId, ProgramSource> programs = new EnumMap<>(ProgramId.class);
    private final Map<ProgramArrayId, ProgramSource[]> programArrays = new EnumMap<>(ProgramArrayId.class);
    // Pack properties.
    private final ShaderProperties properties;

    // Creates a program set from its properties.
    public ProgramSet(ShaderProperties properties) {
        this.properties = properties;
    }

    // Stores a program source.
    void put(ProgramId id, ProgramSource source) {
        this.programs.put(id, source);
    }

    // Stores the array.
    void putArray(ProgramArrayId id, ProgramSource[] sources) {
        this.programArrays.put(id, sources);
    }

    // Returns the properties.
    public ShaderProperties getProperties() {
        return this.properties;
    }

    // Returns the value.
    public Optional<ProgramSource> get(ProgramId id) {
        ProgramId current = id;
        while (current != null) {
            ProgramSource source = this.programs.get(current);
            if (source != null && source.isValid()) {
                return Optional.of(source);
            }
            current = current.getFallback();
        }
        return Optional.empty();
    }

    // Returns a program without fallback.
    public Optional<ProgramSource> getDirect(ProgramId id) {
        ProgramSource source = this.programs.get(id);
        return (source != null && source.isValid()) ? Optional.of(source) : Optional.empty();
    }

    // Returns the value.
    public Optional<ProgramSource> get(ProgramArrayId id, int index) {
        ProgramSource[] arr = this.programArrays.get(id);
        if (arr == null || index < 0 || index >= arr.length) {
            return Optional.empty();
        }
        ProgramSource source = arr[index];
        return (source != null && source.isValid()) ? Optional.of(source) : Optional.empty();
    }

    // Returns the programs of an array id.
    public ProgramSource[] getArray(ProgramArrayId id) {
        return this.programArrays.get(id);
    }

    // Returns every program by name.
    public Map<String, ProgramSource> collectDeclaredPrograms() {
        Map<String, ProgramSource> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<ProgramId, ProgramSource> e : this.programs.entrySet()) {
            if (e.getValue() != null && e.getValue().isValid()) {
                result.put(e.getKey().getSourceName(), e.getValue());
            }
        }
        for (Map.Entry<ProgramArrayId, ProgramSource[]> e : this.programArrays.entrySet()) {
            ProgramSource[] arr = e.getValue();
            if (arr == null) {
                continue;
            }
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null && arr[i].isValid()) {
                    result.put(e.getKey().getSourceName(i), arr[i]);
                }
            }
        }
        return result;
    }

    // Returns the names of every program.
    public List<String> listDeclaredPrograms() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<ProgramId, ProgramSource> e : this.programs.entrySet()) {
            if (e.getValue() != null && e.getValue().isValid()) {
                names.add(e.getKey().getSourceName());
            }
        }
        for (Map.Entry<ProgramArrayId, ProgramSource[]> e : this.programArrays.entrySet()) {
            ProgramSource[] arr = e.getValue();
            if (arr == null) {
                continue;
            }
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null && arr[i].isValid()) {
                    names.add(e.getKey().getSourceName(i));
                }
            }
        }
        return names;
    }
}
