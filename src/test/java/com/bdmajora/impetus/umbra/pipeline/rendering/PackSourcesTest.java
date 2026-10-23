package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackSourcesTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";

    @AfterEach
    void noLegacyPrograms() {
        ShaderMacros.setPackLegacyPrograms(Set.of());
    }

    static ShaderPack pack(String... pathsAndSources) {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        for (int i = 0; i < pathsAndSources.length; i += 2) {
            sources.put(AbsolutePackPath.fromAbsolutePath(pathsAndSources[i]), pathsAndSources[i + 1]);
        }
        return new ShaderPack(sources);
    }

    private static List<String> names(List<ProgramSource> sources) {
        return sources.stream().map(ProgramSource::getName).toList();
    }

    @Test
    void fullscreenSourcesComeInPassOrderAndEverythingKeepsTheFallbackDuplicates() {
        ShaderPack pack = pack("/final.vsh", VERTEX, "/final.fsh", "void main() {}",
                "/composite2.vsh", VERTEX, "/composite2.fsh", "void main() {}",
                "/composite.vsh", VERTEX, "/composite.fsh", "void main() {}",
                "/deferred1.vsh", VERTEX, "/deferred1.fsh", "void main() {}",
                "/begin.vsh", VERTEX, "/begin.fsh", "void main() {}",
                "/prepare.vsh", VERTEX, "/prepare.fsh", "void main() {}",
                "/gbuffers_textured.vsh", VERTEX, "/gbuffers_textured.fsh", "void main() {}");
        PackSources sources = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        assertEquals(List.of("begin", "prepare", "deferred1", "composite", "composite2", "final"), names(sources.fullscreen()));
        List<String> all = names(sources.all());
        // gbuffers_textured answers for itself and for every id that falls back onto it, once per id
        assertTrue(all.stream().filter("gbuffers_textured"::equals).count() > 1, all.toString());
        // Every family slot follows the gbuffer ids, final included once more from its family list order
        assertEquals(List.of("begin", "prepare", "deferred1", "composite", "composite2"),
                all.subList(all.size() - 5, all.size()));
        assertEquals(PackSources.collectAll(pack.getProgramSet()).size(), all.size());
        assertTrue(new PackSources(pack("/shaders.properties", "").getProgramSet(), Map.of()).all().isEmpty());
    }

    @Test
    void activeStagesResolveAgainstEachProgramsOwnMacrosOnceAndInVertexFragmentOrder() {
        ShaderPack pack = pack("/composite.vsh", "#ifdef IS_IRIS\nvoid iris() {}\n#else\nvoid legacy() {}\n#endif",
                "/composite.fsh", "#ifdef IS_IRIS\nconst int a = 1;\n#endif",
                "/composite1.fsh", "void main() {}", "/composite1.csh", "void main() {}");
        PackSources sources = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        ProgramSource composite = pack.getProgramSet().get(ProgramArrayId.Composite, 0).orElseThrow();
        List<String> stages = sources.activeStages(composite);
        assertEquals(2, stages.size());
        assertTrue(stages.get(0).contains("iris()") && !stages.get(0).contains("legacy()"), stages.get(0));
        assertTrue(stages.get(1).contains("const int a"), stages.get(1));
        // Cached: the same resolved text comes back
        assertSame(sources.activeFragment(composite), sources.activeFragment(composite));
        assertSame(stages.get(0), sources.activeVertex(composite));
        // A compute program with a fragment but no vertex stage answers null for it and only lists its fragment
        ProgramSource fragmentOnly = pack.getProgramSet().get(ProgramArrayId.Composite, 1).orElseThrow();
        assertNull(sources.activeVertex(fragmentOnly));
        assertEquals(1, sources.activeStages(fragmentOnly).size());
        // A legacy program compiles without IS_IRIS, so its directives resolve the other branch
        ShaderMacros.setPackLegacyPrograms(Set.of("composite"));
        PackSources legacy = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        assertTrue(legacy.activeVertex(composite).contains("legacy()"));
        assertFalse(legacy.macros(composite).containsKey("IS_IRIS"));
    }

    @Test
    void oneScanFindsImageAndShadowColourReferencesInEveryStage() {
        ShaderPack pack = pack("/shaders.properties", "iris.features.optional = TESSELLATION_SHADERS",
                "/composite.vsh", VERTEX,
                "/composite.fsh", "uniform sampler2D shadowcolor3; uniform sampler2D shadowcolor12; uniform sampler2D xcolorimg5;",
                "/composite.csh", "layout(rgba8) uniform image2D colorimg4; void main() { imageStore(shadowcolorimg2, ivec2(0), vec4(0)); }",
                "/composite_a.csh", "colorimg16 colorimg15 colorimg07 shadowcolorimg9",
                "/gbuffers_entities.vsh", VERTEX,
                "/gbuffers_entities.fsh", "void main() {}",
                "/gbuffers_entities.tes", "#version 400\nlayout(triangles) in;\nvoid main() { shadowcolor6; }");
        PackSources sources = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        assertEquals(bits(4, 7, 15), sources.renderTargetImages());
        assertEquals(bits(2, 3, 6), sources.shadowColorReferences());
        assertEquals(bits(2), sources.shadowColorImages());
    }

    @Test
    void sampledTargetsCoverCommaListsIntegerSamplersAndBothNamesButOnlyInFullscreenPrograms() {
        ShaderPack pack = pack(
                "/composite.vsh", "uniform sampler2D colortex1, gaux2 , foo;",
                "/composite.fsh", String.join("\n",
                        "uniform usampler2D colortex9;",
                        "uniform isampler2D colortex10;",
                        "vec4 f(sampler2D a, sampler2D gaux4) { return vec4(0); }",
                        "uniform sampler2DShadow colortex11;",
                        "uniform sampler3D colortex12;",
                        "uniform sampler2D colortex13Extra;"),
                "/gbuffers_basic.vsh", VERTEX,
                "/gbuffers_basic.fsh", "uniform sampler2D colortex14;");
        PackSources sources = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        assertEquals(bits(1, 5, 7, 9, 10), sources.sampledColorTargets());
    }

    @Test
    void allStagesListsEveryPresentStageComputeVariantsIncluded() {
        ShaderPack pack = pack("/composite.vsh", "v", "/composite.gsh", "g", "/composite.fsh", "f",
                "/composite.csh", "c", "/composite_a.csh", "cb");
        ProgramSource source = pack.getProgramSet().get(ProgramArrayId.Composite, 0).orElseThrow();
        assertEquals(List.of("v", "g", "f", "c", "cb"), PackSources.allStages(source).stream().map(String::trim).toList());
        assertTrue(pack.getProgramSet().get(ProgramId.Final).isEmpty());
    }

    private static BitSet bits(int... indices) {
        BitSet set = new BitSet();
        for (int index : indices) {
            set.set(index);
        }
        return set;
    }
}
