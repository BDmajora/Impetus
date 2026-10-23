package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComputeBuilderTest {
    private static final int GL_LINK_STATUS = 35714;
    private PipelineGl gl;
    private ShaderStorageBufferHolder storage;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.storage = mock(ShaderStorageBufferHolder.class);
        when(this.storage.getBufferId(anyInt())).thenReturn(-1);
        when(this.storage.getBufferId(1)).thenReturn(33);
    }

    @AfterEach
    void close() {
        this.gl.close();
    }

    private ComputeBuilder builder(int[] volume, Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides) {
        return new ComputeBuilder(Map.of(), overrides, new BitSet(),
                Map.of("deferred_a", new ComputeDirectives.IndirectPointer(1, 16L), "deferred_b", new ComputeDirectives.IndirectPointer(7, 0L)),
                this.storage, volume);
    }

    private static ProgramSource computes(String name, String... sources) {
        return new ProgramSource(name, null, null, null, null, null, sources);
    }

    private static int[] field(ComputePass pass, String name) {
        return new int[]{Mixins.<Integer>get(pass, name + "X"), Mixins.<Integer>get(pass, name + "Y")};
    }

    @Test
    void aProgramWithoutComputesBuildsNothing() {
        assertTrue(builder(null, stage -> Map.of()).build(computes("composite"), TextureStage.COMPOSITE_AND_FINAL).isEmpty());
        assertTrue(builder(null, stage -> Map.of()).build(new ProgramSource("composite", "v", null, null, null, "f", null),
                TextureStage.COMPOSITE_AND_FINAL).isEmpty());
        verify(TestGl.gl(), never()).glCreateProgram();
    }

    @Test
    void eachVariantLinksOnItsOwnAndAGapIsSkipped() {
        List<ComputePass> built = builder(null, stage -> Map.of()).build(computes("deferred",
                "#version 430\nlayout(local_size_x = 8) in;\nconst ivec3 workGroups = ivec3(2, 3, 4);\nvoid main() {}",
                null,
                "#version 430\nlayout(local_size_x = 16, local_size_y = 16) in;\nconst vec2 workGroupsRender = vec2(0.5, 0.5);\nvoid main() {}"),
                TextureStage.DEFERRED);
        assertEquals(2, built.size());
        assertArrayEquals(new int[]{2, 3}, field(built.get(0), "groups"));
        assertEquals(4, (int) Mixins.<Integer>get(built.get(0), "groupsZ"));
        assertTrue(Float.isNaN(Mixins.<Float>get(built.get(0), "renderScaleX")));
        assertEquals(0.5f, Mixins.<Float>get(built.get(1), "renderScaleX"));
        assertArrayEquals(new int[]{16, 16}, field(built.get(1), "localSize"));
        verify(TestGl.gl(), times(2)).glCreateProgram();
        // Linked programs are left unbound
        assertEquals(0, this.gl.program);
    }

    @Test
    void anUndeclaredDispatchCoversTheScreenOnePixelPerInvocationUnlessItIsIndirect() {
        ComputeBuilder builder = builder(null, stage -> Map.of());
        ComputePass screen = builder.build(computes("prepare", "#version 430\nlayout(local_size_x = 8, local_size_y = 4) in;\nvoid main() {}"),
                TextureStage.PREPARE).get(0);
        assertEquals(1.0f, Mixins.<Float>get(screen, "renderScaleX"));
        assertEquals(1.0f, Mixins.<Float>get(screen, "renderScaleY"));
        // deferred_a reads its counts from bufferObject.1 at byte 16, so no screen fallback is invented
        ComputePass indirect = builder.build(computes("deferred", null, "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}"),
                TextureStage.DEFERRED).get(0);
        assertEquals(33, (int) Mixins.<Integer>get(indirect, "indirectBuffer"));
        assertEquals(16L, (long) Mixins.<Long>get(indirect, "indirectOffset"));
        assertTrue(Float.isNaN(Mixins.<Float>get(indirect, "renderScaleX")));
        // deferred_b names a bufferObject the pack never declared: reported, and it dispatches directly
        ComputePass undeclared = builder.build(computes("deferred", null, null, "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}"),
                TextureStage.DEFERRED).get(0);
        assertEquals(-1, (int) Mixins.<Integer>get(undeclared, "indirectBuffer"));
        assertEquals(1.0f, Mixins.<Float>get(undeclared, "renderScaleX"));
    }

    @Test
    void theVoxelVolumeFallbackOnlyWidensShadowcompDispatchesThatFallShort() {
        int[] volume = {16, 16, 16};
        ComputeBuilder builder = builder(volume, stage -> Map.of());
        String shortDispatch = "#version 430\nlayout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}";
        ComputePass widened = builder.build(computes("shadowcomp", shortDispatch), TextureStage.SHADOWCOMP).get(0);
        assertArrayEquals(new int[]{2, 2}, field(widened, "groups"));
        assertEquals(16, (int) Mixins.<Integer>get(widened, "groupsZ"));
        // Umbra never second-guesses a declared dispatch elsewhere (Photon's deferred4_a reduction is 1x1x1 on purpose)
        ComputePass declared = builder.build(computes("deferred4", shortDispatch), TextureStage.DEFERRED).get(0);
        assertArrayEquals(new int[]{1, 1}, field(declared, "groups"));
        // A shadowcomp with no declaration covers the volume; one that already covers it keeps its own
        ComputePass undeclared = builder.build(computes("shadowcomp", "#version 430\nlayout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;\nvoid main() {}"),
                TextureStage.SHADOWCOMP).get(0);
        assertArrayEquals(new int[]{4, 4}, field(undeclared, "groups"));
        assertTrue(Float.isNaN(Mixins.<Float>get(undeclared, "renderScaleX")));
        ComputePass covering = builder.build(computes("shadowcomp", "#version 430\nlayout(local_size_x = 8, local_size_y = 8) in;\nconst ivec3 workGroups = ivec3(4, 4, 16);\nvoid main() {}"),
                TextureStage.SHADOWCOMP).get(0);
        assertArrayEquals(new int[]{4, 4}, field(covering, "groups"));
        // Without a local size there is nothing to divide the volume by, so the stage's own rule applies
        ComputePass noLocal = builder.build(computes("shadowcomp", "#version 430\nvoid main() {}"), TextureStage.SHADOWCOMP).get(0);
        assertEquals(1.0f, Mixins.<Float>get(noLocal, "renderScaleX"));
    }

    @Test
    void aVariantThatFailsIsSkippedAndALinkedOneThatCannotBeSetUpIsFreed() {
        when(TestGl.gl().glGetProgrami(anyInt(), eq(GL_LINK_STATUS))).thenReturn(0, 1);
        List<ComputePass> built = builder(null, stage -> Map.of()).build(computes("deferred",
                "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}",
                "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}"), TextureStage.DEFERRED);
        assertEquals(1, built.size());
        verify(TestGl.gl(), times(2)).glDeleteShader(anyInt());
        Mockito.clearInvocations(TestGl.gl());
        List<ComputePass> broken = builder(null, stage -> {
            throw new IllegalStateException("overrides broke");
        }).build(computes("deferred", "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}"), TextureStage.DEFERRED);
        assertTrue(broken.isEmpty());
        verify(TestGl.gl(), times(1)).glDeleteProgram(anyInt());
    }
}
