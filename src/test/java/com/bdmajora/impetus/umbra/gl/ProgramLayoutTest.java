package com.bdmajora.impetus.umbra.gl;

import com.bdmajora.impetus.umbra.gl.image.ImageLimits;
import com.bdmajora.impetus.umbra.gl.program.ProgramImages;
import com.bdmajora.impetus.umbra.gl.program.ProgramSamplers;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.SamplerLimits;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.multiplayer.WorldClient;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramLayoutTest {
    private static final int PROGRAM = 5;
    private static final int GL_ACTIVE_UNIFORMS = 0x8B86;

    private int defaultTexUnit;

    @BeforeEach
    void freshLimits() {
        SamplerLimits.reset();
        ImageLimits.reset();
        Mc.client();
        // GlStateManager indexes its unit cache relative to this, which the game sets to GL_TEXTURE0 at startup
        defaultTexUnit = net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit;
        net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
    }

    @AfterEach
    void resetClock() {
        net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit = defaultTexUnit;
        SystemTimeUniforms.COUNTER.reset();
        SamplerLimits.reset();
        ImageLimits.reset();
    }

    // The program declares these uniforms with these GL types, each at location index + 1
    private static void declare(Map<String, Integer> uniforms) {
        List<String> names = List.copyOf(uniforms.keySet());
        when(TestGl.gl().glGetProgrami(PROGRAM, GL_ACTIVE_UNIFORMS)).thenReturn(names.size());
        when(TestGl.gl().glGetActiveUniform(eq(PROGRAM), anyInt(), eq(256), any(IntBuffer.class))).thenAnswer(invocation -> {
            int index = invocation.getArgument(1);
            IntBuffer sizeType = invocation.getArgument(3);
            String name = names.get(index);
            sizeType.put(0, 1);
            sizeType.put(1, uniforms.get(name));
            return name;
        });
        when(TestGl.gl().glGetUniformLocation(eq(PROGRAM), any(CharSequence.class))).thenAnswer(invocation -> {
            String name = invocation.getArgument(1).toString();
            int index = names.indexOf(name);
            if (index < 0) {
                index = names.indexOf(name + "[0]");
            }
            return index < 0 ? -1 : index + 1;
        });
    }

    @Test
    void uniformsAreCheckedAgainstWhatTheProgramDeclared() {
        Map<String, Integer> declared = new LinkedHashMap<>();
        declared.put("f", 0x1406);
        declared.put("i", 0x1404);
        declared.put("framemod8", 0x1404);
        declared.put("worldTime", 0x1406);
        declared.put("vecAsFloat", 0x1406);
        declared.put("v2", 0x8B50);
        declared.put("iv2", 0x8B53);
        declared.put("v3", 0x8B51);
        declared.put("iv3", 0x8B54);
        declared.put("v4", 0x8B52);
        declared.put("iv4", 0x8B55);
        declared.put("m3", 0x8B5B);
        declared.put("m4[0]", 0x8B5C);
        declared.put("entityId", 0x8B56);
        declared.put("sampler", 0x8B5E);
        declared.put("weird", 0x9999);
        declared.put("", 0x1406);
        declare(declared);
        ProgramUniforms.Builder builder = ProgramUniforms.builder("test", PROGRAM);
        assertEquals("test", builder.getName());
        float[] time = {0.4F};
        builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "f", () -> time[0])
                .uniform1i(UniformUpdateFrequency.PER_TICK, "i", () -> 1)
                // A float provider for an int declaration is rounded, an int provider for a float one widened
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "framemod8", () -> 2.6F)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "worldTime", () -> 6000)
                // Any other disagreement drops the uniform
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "vecAsFloat", Vector2f::new)
                .uniform2f(UniformUpdateFrequency.ONCE, "v2", Vector2f::new)
                .uniform2i(UniformUpdateFrequency.ONCE, "iv2", Vector2i::new)
                .uniform3f(UniformUpdateFrequency.ONCE, "v3", Vector3f::new)
                .uniform3i(UniformUpdateFrequency.ONCE, "iv3", Vector3i::new)
                .uniform4f(UniformUpdateFrequency.ONCE, "v4", Vector4f::new)
                .uniform4i(UniformUpdateFrequency.ONCE, "iv4", Vector4i::new)
                .uniformMatrix3(UniformUpdateFrequency.DYNAMIC, "m3", Matrix3f::new)
                .uniformMatrix(UniformUpdateFrequency.DYNAMIC, "m4", Matrix4f::new)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "entityId", () -> 7)
                // Undeclared names never register
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "absent", () -> 1)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "absentInt", () -> 1)
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "absent2", Vector2f::new)
                .uniform2i(UniformUpdateFrequency.PER_FRAME, "absent2i", Vector2i::new)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "absent3", Vector3f::new)
                .uniform3i(UniformUpdateFrequency.PER_FRAME, "absent3i", Vector3i::new)
                .uniform4f(UniformUpdateFrequency.PER_FRAME, "absent4", Vector4f::new)
                .uniform4i(UniformUpdateFrequency.PER_FRAME, "absent4i", Vector4i::new)
                .uniformMatrix3(UniformUpdateFrequency.PER_FRAME, "absentM3", Matrix3f::new)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "absentM4", Matrix4f::new);
        ProgramUniforms uniforms = builder.buildUniforms();

        uniforms.update();
        verify(TestGl.gl()).glUniform1i(3, 3);
        verify(TestGl.gl()).glUniform1f(4, 6000.0F);
        verify(TestGl.gl(), never()).glUniform2f(eq(5), anyFloat(), anyFloat());
        verify(TestGl.gl()).glUniform2f(eq(6), anyFloat(), anyFloat());
        int dynamic = ProgramUniforms.dynamicPass();
        // Nothing is due again within the same frame and tick, except the dynamic bucket
        time[0] = 0.9F;
        uniforms.update();
        assertEquals(dynamic + 1, ProgramUniforms.dynamicPass());
        verify(TestGl.gl(), never()).glUniform1f(1, 0.9F);
        SystemTimeUniforms.COUNTER.beginFrame(System.nanoTime());
        uniforms.update();
        verify(TestGl.gl()).glUniform1f(1, 0.9F);
        // A new world tick re-reads the per-tick bucket
        WorldClient world = mock(WorldClient.class);
        when(world.getTotalWorldTime()).thenReturn(5L);
        net.minecraft.client.Minecraft.getMinecraft().world = world;
        uniforms.update();
        verify(TestGl.gl()).glUniform1i(2, 1);
        net.minecraft.client.Minecraft.getMinecraft().world = null;
        // The per-object bucket holds only the material ids and colours
        uniforms.updatePerObject();
        verify(TestGl.gl(), times(1)).glUniform1i(14, 7);
    }

    @Test
    void samplersGetUnitsOnlyWhenTheProgramDeclaresThem() {
        Map<String, Integer> declared = new LinkedHashMap<>();
        declared.put("gtexture", 0x8B5E);
        declared.put("lightmap", 0x8B5E);
        declared.put("colortex0", 0x8B5E);
        declared.put("gcolor", 0x8B5E);
        declared.put("colortex1", 0x8B5E);
        declared.put("colortex2", 0x8B5F);
        declared.put("colortex3", 0x8B5E);
        declared.put("colortex4", 0x8B5E);
        declare(declared);
        when(TestGl.gl().glGetInteger(0x8872)).thenReturn(4);
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(8);
        assertEquals(4, SamplerLimits.get().getMaxTextureUnits());
        assertEquals(8, SamplerLimits.get().getMaxDrawBuffers());
        // Unit 1 belongs to the lightmap, unit 9 does not exist on this driver
        ProgramSamplers.Builder builder = ProgramSamplers.builder(PROGRAM, Set.of(1, 9));
        assertTrue(builder.hasSampler("gtexture"));
        assertFalse(builder.hasSampler("shadowtex0"));
        builder.addExternalSampler(1, "lightmap");
        builder.addExternalSampler(7, "gtexture", "nothing");
        assertEquals(0, builder.getNextUnit());
        assertTrue(builder.addDynamicSampler(() -> 11, "colortex0", "gcolor"));
        // Reserved unit 1 is skipped
        assertEquals(2, builder.getNextUnit());
        assertFalse(builder.addDynamicSampler(() -> 12, "shadowtex0"));
        assertTrue(builder.addDynamicSampler(GL12.GL_TEXTURE_3D, () -> 13, "colortex2"));
        assertTrue(builder.addDynamicSampler(() -> 14, "colortex1"));
        // Out of units: this and every later one reads unit 0, reported once
        assertFalse(builder.addDynamicSampler(() -> 15, "colortex3"));
        assertFalse(builder.addDynamicSampler(() -> 16, "colortex4"));
        ProgramSamplers samplers = builder.build();
        assertEquals(3, samplers.getActiveSamplers());
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            samplers.update();
            samplers.update();
        }
        verify(TestGl.gl(), times(1)).glUniform1i(2, 1);
        verify(TestGl.gl(), times(1)).glUniform1i(3, 0);
        verify(TestGl.gl(), times(1)).glUniform1i(4, 0);
        verify(TestGl.gl(), times(2)).glBindTexture(GL12.GL_TEXTURE_3D, 13);
        // A program with no samplers of its own only runs its unit assignments
        ProgramSamplers none = ProgramSamplers.builder(PROGRAM, Set.of()).build();
        none.update();
        assertEquals(0, none.getActiveSamplers());
    }

    @Test
    void imagesGetUnitsUpToTheDriverLimit() {
        Map<String, Integer> declared = new LinkedHashMap<>();
        declared.put("voxel_img", 0x904D);
        declared.put("light_img", 0x904D);
        declare(declared);
        when(TestGl.gl().glGetInteger(0x8D57)).thenReturn(1);
        assertEquals(1, ImageLimits.get().getMaxImageUnits());
        ProgramImages.Builder builder = ProgramImages.builder(PROGRAM);
        assertTrue(builder.hasImage("voxel_img"));
        assertFalse(builder.hasImage("other_img"));
        builder.addTextureImage(() -> 21, GL11.GL_RGBA8, "other_img");
        builder.addTextureImage(() -> 21, GL11.GL_RGBA8, "voxel_img");
        builder.addTextureImage(() -> 22, GL11.GL_RGBA8, "light_img");
        ProgramImages images = builder.build();
        assertEquals(1, images.getActiveImages());
        images.update();
        images.update();
        verify(TestGl.gl(), times(1)).glUniform1i(1, 0);
        verify(TestGl.gl(), times(2)).glBindImageTexture(0, 21, 0, true, 0, 0x88BA, GL11.GL_RGBA8);
        // A driver without image support reports zero units
        ImageLimits.reset();
        when(TestGl.gl().glGetInteger(0x8D57)).thenReturn(-1);
        assertEquals(0, ImageLimits.get().getMaxImageUnits());
    }

    @Test
    void shadowSamplersReadRawDepthOnlyWhenDeclaredPlain() {
        Map<String, Integer> none = new LinkedHashMap<>();
        declare(none);
        assertSame(ShadowSamplerKinds.ALL_COMPARE, ShadowSamplerKinds.detect(PROGRAM));

        Map<String, Integer> plain = new LinkedHashMap<>();
        plain.put("shadowtex0", 0x8B5E);
        plain.put("shadowtex1", 0x8B62);
        plain.put("colortex0[0]", 0x8B5E);
        plain.put("", 0x8B5E);
        declare(plain);
        ShadowSamplerKinds kinds = ShadowSamplerKinds.detect(PROGRAM);
        assertTrue(kinds.isRaw(0));
        assertFalse(kinds.isRaw(1));

        // With watershadow declared, `shadow` means shadowtex1
        Map<String, Integer> water = new LinkedHashMap<>();
        water.put("watershadow", 0x8B62);
        water.put("shadow", 0x8B5E);
        declare(water);
        ShadowSamplerKinds waterKinds = ShadowSamplerKinds.detect(PROGRAM);
        assertFalse(waterKinds.isRaw(0));
        assertTrue(waterKinds.isRaw(1));

        Map<String, Integer> compare = new LinkedHashMap<>();
        compare.put("shadow", 0x8B62);
        declare(compare);
        assertSame(ShadowSamplerKinds.ALL_COMPARE, ShadowSamplerKinds.detect(PROGRAM));
        when(TestGl.gl().glGetActiveUniform(eq(PROGRAM), anyInt(), eq(256), any(IntBuffer.class))).thenReturn(null);
        assertSame(ShadowSamplerKinds.ALL_COMPARE, ShadowSamplerKinds.detect(PROGRAM));
    }

    @Test
    void textureUnitsKeepTheStateCacheHonest() {
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            // A negative unit, from an exhausted allocator, is refused rather than corrupting the cache
            GlTextureUnits.selectScratch(-1);
            GlTextureUnits.bindTextureInRun(-1, GL11.GL_TEXTURE_2D, 3);
            GlTextureUnits.selectScratch(2);
            GlTextureUnits.selectScratch(20);
            verify(TestGl.gl(), Mockito.atLeastOnce()).glActiveTexture(GL13.GL_TEXTURE0 + 20);
            GlTextureUnits.bindTextureInRun(2, GL11.GL_TEXTURE_2D, 4);
            GlTextureUnits.bindTextureInRun(2, GL12.GL_TEXTURE_3D, 5);
            GlTextureUnits.bindTextureInRun(20, GL11.GL_TEXTURE_2D, 6);
            verify(TestGl.gl()).glBindTexture(GL12.GL_TEXTURE_3D, 5);
            verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, 6);
            GlTextureUnits.forceBindTexture2D(3, 7);
            verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, 7);
            GlTextureUnits.forceBindTexture2D(12, 8);
            verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, 8);
            GlTextureUnits.bindTexture2D(1, 9);
            GlTextureUnits.releaseScratch();
            GlTextureUnits.resetToUnit0();
        }
    }
}
