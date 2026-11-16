package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSet;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.util.SafeInt;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Program sources of a pack with their active stages and the targets and images they reference.
public final class PackSources {
    // Program arrays of the fullscreen passes.
    private static final ProgramArrayId[] FULLSCREEN_FAMILIES = {
            ProgramArrayId.Begin, ProgramArrayId.Prepare, ProgramArrayId.Deferred, ProgramArrayId.Composite
    };
    // Pattern of a shadow color or color image reference.
    private static final Pattern REFERENCE = Pattern.compile("\\b(shadowcolor(img)?|colorimg)(\\d{1,2})\\b");
    // Pattern of a 2D sampler declaration.
    private static final Pattern SAMPLED = Pattern.compile("\\b[iu]?sampler2D\\s+(\\w+)");
    // Pattern of a further name in a declaration list.
    private static final Pattern LIST_TAIL = Pattern.compile("\\s*,\\s*(\\w+)");
    // Number of shadow color targets.
    private static final int SHADOW_COLOR_INDICES = 8;

    // Environment defines of the pack.
    private final Map<String, String> defines;
    // Every program source.
    private final List<ProgramSource> all;
    // Sources of the fullscreen passes and the final pass.
    private final List<ProgramSource> fullscreen;
    // Vertex and fragment stages after conditionals by source.
    private final Map<ProgramSource, String[]> active = new IdentityHashMap<>();
    // Color targets used as images.
    private final BitSet renderTargetImages = new BitSet();
    // Shadow color targets referenced by any stage.
    private final BitSet shadowColorReferences = new BitSet();
    // Shadow color targets used as images.
    private final BitSet shadowColorImages = new BitSet();

    // Collects the sources of a program set and scans every stage for references.
    public PackSources(ProgramSet programs, Map<String, String> defines) {
        this.defines = defines;
        this.all = collectAll(programs);
        this.fullscreen = collectFullscreen(programs);
        for (ProgramSource source : this.all) {
            for (String stage : allStages(source)) {
                scanReferences(stage);
            }
        }
    }

    // Returns every source of a program set.
    static List<ProgramSource> collectAll(ProgramSet programs) {
        List<ProgramSource> sources = new ArrayList<>();
        for (ProgramId id : ProgramId.VALUES) {
            programs.get(id).ifPresent(sources::add);
        }
        for (ProgramArrayId id : ProgramArrayId.values()) {
            collectFamily(programs, id, sources);
        }
        return sources;
    }

    // Returns the sources of the fullscreen passes and the final pass.
    static List<ProgramSource> collectFullscreen(ProgramSet programs) {
        List<ProgramSource> sources = new ArrayList<>();
        for (ProgramArrayId id : FULLSCREEN_FAMILIES) {
            collectFamily(programs, id, sources);
        }
        programs.get(ProgramId.Final).ifPresent(sources::add);
        return sources;
    }

    // Adds the present sources of a program array.
    private static void collectFamily(ProgramSet programs, ProgramArrayId id, List<ProgramSource> sources) {
        for (int i = 0; i < id.getNumPrograms(); i++) {
            programs.get(id, i).ifPresent(sources::add);
        }
    }

    // Returns every present stage of a source.
    public static List<String> allStages(ProgramSource source) {
        List<String> stages = new ArrayList<>();
        source.getVertexSource().ifPresent(stages::add);
        source.getGeometrySource().ifPresent(stages::add);
        source.getTessControlSource().ifPresent(stages::add);
        source.getTessEvalSource().ifPresent(stages::add);
        source.getFragmentSource().ifPresent(stages::add);
        for (String compute : source.getComputeSources()) {
            if (compute != null) {
                stages.add(compute);
            }
        }
        return stages;
    }

    // Returns every source.
    public List<ProgramSource> all() {
        return this.all;
    }

    // Returns the fullscreen sources.
    public List<ProgramSource> fullscreen() {
        return this.fullscreen;
    }

    // Returns the macros of a source.
    public Map<String, String> macros(ProgramSource source) {
        return ShaderMacros.forProgram(this.defines, source.getName());
    }

    // Returns the vertex stage after conditionals or null.
    public String activeVertex(ProgramSource source) {
        return resolved(source)[0];
    }

    // Returns the fragment stage after conditionals or null.
    public String activeFragment(ProgramSource source) {
        return resolved(source)[1];
    }

    // Returns the present vertex and fragment stages after conditionals.
    public List<String> activeStages(ProgramSource source) {
        String[] stages = resolved(source);
        List<String> present = new ArrayList<>(2);
        for (String stage : stages) {
            if (stage != null) {
                present.add(stage);
            }
        }
        return present;
    }

    // Returns the stages after conditionals and resolves each source once.
    private String[] resolved(ProgramSource source) {
        return this.active.computeIfAbsent(source, s -> {
            Map<String, String> macros = macros(s);
            return new String[]{
                    s.getVertexSource().map(stage -> GlslPreprocessor.resolveConditionals(stage, macros)).orElse(null),
                    s.getFragmentSource().map(stage -> GlslPreprocessor.resolveConditionals(stage, macros)).orElse(null)
            };
        });
    }

    // Returns the color targets used as images.
    public BitSet renderTargetImages() {
        return this.renderTargetImages;
    }

    // Returns the referenced shadow color targets.
    public BitSet shadowColorReferences() {
        return this.shadowColorReferences;
    }

    // Returns the shadow color targets used as images.
    public BitSet shadowColorImages() {
        return this.shadowColorImages;
    }

    // Records the shadow color and image references of a stage.
    private void scanReferences(String stage) {
        Matcher matcher = REFERENCE.matcher(stage);
        while (matcher.find()) {
            String digits = matcher.group(3);
            int index = SafeInt.parse(digits);
            if (matcher.group(1).startsWith("shadow")) {
                if (digits.length() == 1 && index < SHADOW_COLOR_INDICES) {
                    this.shadowColorReferences.set(index);
                    if (matcher.group(2) != null) {
                        this.shadowColorImages.set(index);
                    }
                }
            } else if (index < UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                this.renderTargetImages.set(index);
            }
        }
    }

    // Returns the color targets the fullscreen stages declare samplers for.
    public BitSet sampledColorTargets() {
        BitSet sampled = new BitSet();
        Map<String, Integer> names = SamplerLayout.colorTargetsByName();
        for (ProgramSource source : this.fullscreen) {
            source.getVertexSource().ifPresent(stage -> markSampled(stage, names, sampled));
            source.getFragmentSource().ifPresent(stage -> markSampled(stage, names, sampled));
        }
        return sampled;
    }

    // Records every sampler of every declaration list of a stage.
    private static void markSampled(String stage, Map<String, Integer> names, BitSet sampled) {
        Matcher declaration = SAMPLED.matcher(stage);
        Matcher tail = LIST_TAIL.matcher(stage);
        while (declaration.find()) {
            mark(declaration.group(1), names, sampled);
            tail.region(declaration.end(), stage.length());
            while (tail.lookingAt()) {
                mark(tail.group(1), names, sampled);
                tail.region(tail.end(), stage.length());
            }
        }
    }

    // Records a color target sampler name.
    private static void mark(String name, Map<String, Integer> names, BitSet sampled) {
        Integer index = names.get(name);
        if (index != null) {
            sampled.set(index);
        }
    }
}
