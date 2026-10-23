package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramCacheTest {
    private static final int GL_LINK_STATUS = 35714;
    private static final String LEGACY_VERTEX = "#version 120\nvarying vec2 uv;\nvoid main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.xy; }";
    private static final String LEGACY_FRAGMENT = "#version 120\n/* DRAWBUFFERS:13 */\nvoid main() { gl_FragData[0] = vec4(1.0); }";
    private PipelineGl gl;
    private final List<TextureStage> askedStages = new ArrayList<>();

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
    }

    @AfterEach
    void close() {
        this.gl.close();
    }

    private ProgramCache cache(Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides) {
        return new ProgramCache(Map.of(), stage -> {
            this.askedStages.add(stage);
            return overrides.apply(stage);
        }, new BitSet());
    }

    private static ProgramSource source(String name, String vertex, String fragment) {
        return new ProgramSource(name, vertex, null, null, null, fragment, null);
    }

    @Test
    void eachFamilyNameMapsOntoItsCustomTextureStage() {
        assertEquals(TextureStage.BEGIN, ProgramCache.stageOf("begin3"));
        assertEquals(TextureStage.PREPARE, ProgramCache.stageOf("prepare"));
        assertEquals(TextureStage.DEFERRED, ProgramCache.stageOf("deferred12"));
        assertEquals(TextureStage.SHADOWCOMP, ProgramCache.stageOf("shadowcomp1"));
        assertEquals(TextureStage.COMPOSITE_AND_FINAL, ProgramCache.stageOf("composite2"));
        assertEquals(TextureStage.COMPOSITE_AND_FINAL, ProgramCache.stageOf("final"));
    }

    @Test
    void aProgramIsCompiledOnceAndEveryLaterRequestGetsTheSameResult() {
        ProgramCache cache = cache(stage -> Map.of());
        ProgramSource source = source("deferred", LEGACY_VERTEX, LEGACY_FRAGMENT);
        ProgramCache.Compiled first = cache.get(source);
        assertNotNull(first);
        assertSame(first, cache.get(source));
        verify(TestGl.gl(), times(1)).glCreateProgram();
        assertArrayEquals(new int[]{1, 3}, first.program().getDrawBuffers());
        assertNotNull(first.uniforms());
        assertNotNull(first.shadowSamplerKinds());
        assertFalse(first.modern());
        assertEquals(List.of(TextureStage.DEFERRED), this.askedStages);
        // The program is bound while its samplers are assigned and unbound afterwards
        assertEquals(0, this.gl.program);
    }

    @Test
    void aModernProgramIsFlaggedSoOnlyItGetsTheFixedFunctionMatrices() {
        ProgramCache.Compiled modern = cache(stage -> Map.of()).get(source("composite",
                "#version 330 compatibility\nvoid main() { gl_Position = ftransform(); }",
                "#version 330 compatibility\nlayout(location = 0) out vec4 color;\nvoid main() { color = vec4(1.0); }"));
        assertTrue(modern.modern());
    }

    @Test
    void aProgramMissingAStageIsCachedAsAbsentWithoutCompiling() {
        ProgramCache cache = cache(stage -> Map.of());
        ProgramSource vertexOnly = source("composite", LEGACY_VERTEX, null);
        assertNull(cache.get(vertexOnly));
        assertNull(cache.get(vertexOnly));
        assertNull(cache.get(source("composite1", null, LEGACY_FRAGMENT)));
        verify(TestGl.gl(), never()).glCreateProgram();
    }

    @Test
    void aLinkFailureIsReportedOnceAndNeverRetried() {
        when(TestGl.gl().glGetProgrami(anyInt(), eq(GL_LINK_STATUS))).thenReturn(0);
        ProgramCache cache = cache(stage -> Map.of());
        ProgramSource source = source("composite", LEGACY_VERTEX, LEGACY_FRAGMENT);
        assertThrows(RuntimeException.class, () -> cache.get(source));
        assertNull(cache.get(source));
        verify(TestGl.gl(), times(1)).glCreateProgram();
        // Both shaders are freed whatever happened
        verify(TestGl.gl(), times(2)).glDeleteShader(anyInt());
    }

    @Test
    void aProgramThatLinkedButCouldNotBeSetUpIsFreedNotLeaked() {
        ProgramCache cache = cache(stage -> {
            throw new IllegalStateException("overrides broke");
        });
        ProgramSource source = source("composite", LEGACY_VERTEX, LEGACY_FRAGMENT);
        assertThrows(IllegalStateException.class, () -> cache.get(source));
        verify(TestGl.gl(), times(1)).glDeleteProgram(anyInt());
        assertNull(cache.get(source));
        // Nothing left for destroy to free twice
        Mockito.clearInvocations(TestGl.gl());
        cache.destroy();
        verify(TestGl.gl(), never()).glDeleteProgram(anyInt());
    }

    @Test
    void destroyFreesEveryCompiledProgramOnceAndForgetsThem() {
        ProgramCache cache = cache(stage -> Map.of());
        cache.get(source("composite", LEGACY_VERTEX, LEGACY_FRAGMENT));
        cache.get(source("composite1", LEGACY_VERTEX, LEGACY_FRAGMENT));
        cache.get(source("composite2", LEGACY_VERTEX, null));
        cache.destroy();
        verify(TestGl.gl(), times(2)).glDeleteProgram(anyInt());
        cache.destroy();
        verify(TestGl.gl(), times(2)).glDeleteProgram(anyInt());
        // Forgotten: asking again compiles again
        cache.get(source("composite", LEGACY_VERTEX, LEGACY_FRAGMENT));
        verify(TestGl.gl(), times(3)).glCreateProgram();
    }

    @Test
    void aShaderThatDoesNotCompileFailsThePassAndLeavesNothingBehind() {
        when(TestGl.gl().glGetShaderi(anyInt(), eq(35713))).thenReturn(0);
        ProgramCache cache = cache(stage -> Map.of());
        assertThrows(RuntimeException.class, () -> cache.get(source("composite", LEGACY_VERTEX, LEGACY_FRAGMENT)));
        verify(TestGl.gl(), never()).glCreateProgram();
        assertNull(cache.get(source("composite", LEGACY_VERTEX, LEGACY_FRAGMENT)));
    }
}
