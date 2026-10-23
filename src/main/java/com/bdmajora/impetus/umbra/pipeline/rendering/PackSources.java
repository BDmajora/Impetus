package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSet;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The pack's program sources for directive scanning, collected once per pipeline with each stage's conditional-resolved text cached, since the format, shadow and mipmap scans all read the same stages
public final class PackSources {
    // The numbered families that render full-screen quads into the colour targets, in run order; Setup is compute-only, so it samples and writes no targets
    private static final ProgramArrayId[] FULLSCREEN_FAMILIES = {
            ProgramArrayId.Begin, ProgramArrayId.Prepare, ProgramArrayId.Deferred, ProgramArrayId.Composite
    };
    // colorimgN, shadowcolorN and shadowcolorimgN in one pass over every stage
    private static final Pattern REFERENCE = Pattern.compile("\\b(shadowcolor(img)?|colorimg)(\\d{1,2})\\b");
    private static final Pattern SAMPLED = Pattern.compile("\\b[iu]?sampler2D\\s+(\\w+)");
    // The rest of a `sampler2D a, b, c;` declaration list
    private static final Pattern LIST_TAIL = Pattern.compile("\\s*,\\s*(\\w+)");
    private static final int SHADOW_COLOR_INDICES = 8;

    private final Map<String, String> defines;
    private final List<ProgramSource> all;
    private final List<ProgramSource> fullscreen;
    // Per source, {vertex, fragment} with conditionals resolved, null for an absent stage
    private final Map<ProgramSource, String[]> active = new IdentityHashMap<>();
    private final BitSet renderTargetImages = new BitSet();
    private final BitSet shadowColorReferences = new BitSet();
    private final BitSet shadowColorImages = new BitSet();

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

    // Every present program (all gbuffer ids through their fallbacks, then every numbered family) in a fixed order; duplicates from the fallback chain are kept, since directives are applied in this order and the last one wins
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

    // Every begin, prepare, deferred, composite and final program, in pass order
    static List<ProgramSource> collectFullscreen(ProgramSet programs) {
        List<ProgramSource> sources = new ArrayList<>();
        for (ProgramArrayId id : FULLSCREEN_FAMILIES) {
            collectFamily(programs, id, sources);
        }
        programs.get(ProgramId.Final).ifPresent(sources::add);
        return sources;
    }

    private static void collectFamily(ProgramSet programs, ProgramArrayId id, List<ProgramSource> sources) {
        for (int i = 0; i < id.getNumPrograms(); i++) {
            programs.get(id, i).ifPresent(sources::add);
        }
    }

    // Every stage of a program that has source, compute included
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

    public List<ProgramSource> all() {
        return this.all;
    }

    public List<ProgramSource> fullscreen() {
        return this.fullscreen;
    }

    // The macro set one program compiles with (a pass in impetus.umbra.legacyPrograms has no IS_IRIS)
    public Map<String, String> macros(ProgramSource source) {
        return ShaderMacros.forProgram(this.defines, source.getName());
    }

    // The vertex stage with conditionals resolved, or null
    public String activeVertex(ProgramSource source) {
        return resolved(source)[0];
    }

    // The fragment stage with conditionals resolved, or null
    public String activeFragment(ProgramSource source) {
        return resolved(source)[1];
    }

    // The stages a directive may be declared in, vertex first and fragment last since Umbra trusts the fragment stage; raw scans took the LAST textual match, so a directive under a disabled #if won (Body Camera whited the screen)
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

    private String[] resolved(ProgramSource source) {
        return this.active.computeIfAbsent(source, s -> {
            Map<String, String> macros = macros(s);
            return new String[]{
                    s.getVertexSource().map(stage -> GlslPreprocessor.resolveConditionals(stage, macros)).orElse(null),
                    s.getFragmentSource().map(stage -> GlslPreprocessor.resolveConditionals(stage, macros)).orElse(null)
            };
        });
    }

    // colorimgN anywhere: a buffer a compute writes through the image API must exist even if nothing samples it
    public BitSet renderTargetImages() {
        return this.renderTargetImages;
    }

    // shadowcolorN or shadowcolorimgN anywhere
    public BitSet shadowColorReferences() {
        return this.shadowColorReferences;
    }

    // shadowcolorimgN anywhere
    public BitSet shadowColorImages() {
        return this.shadowColorImages;
    }

    private void scanReferences(String stage) {
        Matcher matcher = REFERENCE.matcher(stage);
        while (matcher.find()) {
            String digits = matcher.group(3);
            int index = Integer.parseInt(digits);
            if (matcher.group(1).startsWith("shadow")) {
                // Only single digits name a shadowcolor buffer
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

    // Every colour target the fullscreen programs declare a sampler for, comma lists and integer samplers included, so per-pass snapshots can bind them even when the writing pass comes later
    public BitSet sampledColorTargets() {
        BitSet sampled = new BitSet();
        Map<String, Integer> names = SamplerLayout.colorTargetsByName();
        for (ProgramSource source : this.fullscreen) {
            source.getVertexSource().ifPresent(stage -> markSampled(stage, names, sampled));
            source.getFragmentSource().ifPresent(stage -> markSampled(stage, names, sampled));
        }
        return sampled;
    }

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

    private static void mark(String name, Map<String, Integer> names, BitSet sampled) {
        Integer index = names.get(name);
        if (index != null) {
            sampled.set(index);
        }
    }
}
