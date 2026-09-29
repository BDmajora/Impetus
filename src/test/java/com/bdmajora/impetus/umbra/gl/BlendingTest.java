package com.bdmajora.impetus.umbra.gl;

import com.bdmajora.impetus.umbra.gl.blending.BlendMode;
import com.bdmajora.impetus.umbra.gl.blending.BlendOverrideGuard;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlendingTest {
    @AfterEach
    void releaseGuard() {
        BlendOverrideGuard.release();
        Mixins.set(ProgramBlendState.class, "warnedNoBufferBlend", false);
    }

    @Test
    void blendModesParseEveryFactor() {
        String[] names = {"ZERO", "one", "SRC_COLOR", "ONE_MINUS_SRC_COLOR", "DST_COLOR", "ONE_MINUS_DST_COLOR",
                "SRC_ALPHA", "ONE_MINUS_SRC_ALPHA", "DST_ALPHA", "ONE_MINUS_DST_ALPHA", "SRC_ALPHA_SATURATE",
                "CONSTANT_COLOR", "ONE_MINUS_CONSTANT_COLOR", "CONSTANT_ALPHA", "ONE_MINUS_CONSTANT_ALPHA"};
        int[] values = {GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_SRC_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_DST_COLOR,
                GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_DST_ALPHA,
                GL11.GL_ONE_MINUS_DST_ALPHA, GL11.GL_SRC_ALPHA_SATURATE, com.bdmajora.impetus.lwjgl.GL14.GL_CONSTANT_COLOR,
                com.bdmajora.impetus.lwjgl.GL14.GL_ONE_MINUS_CONSTANT_COLOR, com.bdmajora.impetus.lwjgl.GL14.GL_CONSTANT_ALPHA,
                com.bdmajora.impetus.lwjgl.GL14.GL_ONE_MINUS_CONSTANT_ALPHA};
        for (int i = 0; i < names.length; i++) {
            assertEquals(values[i], BlendMode.parse(names[i] + " ONE").srcRgb(), names[i]);
        }
        assertEquals(new BlendMode(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO), BlendMode.parse(" ONE  ZERO "));
        BlendMode four = BlendMode.parse("SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ZERO");
        assertEquals(GL11.GL_ONE, four.srcAlpha());
        assertEquals(GL11.GL_ZERO, four.dstAlpha());
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, four.dstRgb());
        assertThrows(IllegalArgumentException.class, () -> BlendMode.parse("ONE ZERO ONE"));
        assertThrows(IllegalArgumentException.class, () -> BlendMode.parse("ONE BOGUS"));
    }

    private static ShaderProperties properties(String text) {
        return ShaderProperties.parse(text);
    }

    @Test
    void alphaTestsParseAndRoundTripTheGlState() {
        assertSame(ProgramAlphaTest.empty(), ProgramAlphaTest.from(properties(""), "gbuffers_water"));
        assertFalse(ProgramAlphaTest.empty().hasDirectives());
        assertEquals("", ProgramAlphaTest.empty().toGlslDiscard("a", ""));
        String text = String.join("\n",
                "alphaTest.off = off",
                "alphaTest.false = false",
                "alphaTest.bogus = SOMETIMES 0.5",
                "alphaTest.broken = GREATER half",
                "alphaTest.missing = GREATER",
                "alphaTest.always = ALWAYS",
                "alphaTest.never = never",
                "alphaTest.less = LESS 0.1",
                "alphaTest.equal = EQUAL 0.1",
                "alphaTest.lequal = LEQUAL 0.1",
                "alphaTest.greater = GREATER 0.1",
                "alphaTest.notequal = NOTEQUAL 0.1",
                "alphaTest.gequal = GEQUAL 0.1");
        ShaderProperties props = properties(text);
        ProgramAlphaTest off = ProgramAlphaTest.from(props, "off");
        assertTrue(off.hasDirectives());
        assertEquals("", off.toGlslDiscard("color.a", ""));
        assertEquals(0.0F, off.getReference());
        assertTrue(ProgramAlphaTest.from(props, "false").hasDirectives());
        for (String ignored : new String[] {"bogus", "broken", "missing"}) {
            assertSame(ProgramAlphaTest.empty(), ProgramAlphaTest.from(props, ignored), ignored);
        }
        assertEquals("", ProgramAlphaTest.from(props, "always").toGlslDiscard("a", ""));
        assertEquals("  discard;\n", ProgramAlphaTest.from(props, "never").toGlslDiscard("a", "  "));
        String[][] operators = {{"less", "<"}, {"equal", "=="}, {"lequal", "<="}, {"greater", ">"}, {"notequal", "!="}, {"gequal", ">="}};
        for (String[] operator : operators) {
            ProgramAlphaTest test = ProgramAlphaTest.from(props, operator[0]);
            assertEquals(0.1F, test.getReference());
            assertTrue(test.toGlslDiscard("color.a", "").contains("if (!(color.a " + operator[1] + " 0.1))"), operator[0]);
        }
        // A function with no GLSL counterpart passes everything rather than emitting nonsense
        ProgramAlphaTest odd = Mixins.construct(ProgramAlphaTest.class, true, false, GL11.GL_ALWAYS + 99, 0.5F);
        assertEquals("", odd.toGlslDiscard("a", ""));

        ProgramAlphaTest greater = ProgramAlphaTest.from(props, "greater");
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class)) {
            // Restoring without having applied does nothing
            greater.restore();
            ProgramAlphaTest.empty().apply();
            ProgramAlphaTest.empty().restore();
            when(TestGl.gl().glGetBoolean(0x0BC0)).thenReturn(true);
            when(TestGl.gl().glGetInteger(0x0BC1)).thenReturn(GL11.GL_GREATER);
            when(TestGl.gl().glGetFloat(0x0BC2)).thenReturn(0.5F);
            greater.apply();
            greater.restore();
            gl11.verify(() -> GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F));
            gl11.verify(() -> GL11.glAlphaFunc(GL11.GL_GREATER, 0.5F));
            when(TestGl.gl().glGetBoolean(0x0BC0)).thenReturn(false);
            off.apply();
            off.restore();
        }
    }

    @Test
    void blendStatesApplyPerOutputSlot() {
        ShaderProperties props = properties(String.join("\n",
                "blend.gbuffers_water = SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ZERO",
                "blend.gbuffers_water.colortex1 = off",
                "blend.gbuffers_water.gaux1 = ONE ONE",
                "blend.gbuffers_water.bogus = ONE ONE",
                "blend.gbuffers_hand = off",
                "blend.gbuffers_block = NOT A MODE",
                "blend.composite.colortex2 = ONE ZERO"));
        assertSame(ProgramBlendState.empty(), ProgramBlendState.empty());
        assertFalse(ProgramBlendState.from(props, "final").hasDirectives());
        ProgramBlendState water = ProgramBlendState.from(props, "gbuffers_water");
        assertTrue(water.hasDirectives());
        ProgramBlendState hand = ProgramBlendState.from(props, "gbuffers_hand");
        ProgramBlendState block = ProgramBlendState.from(props, "gbuffers_block");
        ProgramBlendState composite = ProgramBlendState.from(props, "composite");
        BlendMode eyes = new BlendMode(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        ProgramBlendState defaulted = ProgramBlendState.from(props, "gbuffers_spidereyes", eyes);
        // Indexed blending is an optional backend feature, off unless the context has it
        when(TestGl.gl().supportsBufferBlending()).thenReturn(true);
        Mockito.doNothing().when(TestGl.gl()).glEnablei(anyInt(), anyInt());
        Mockito.doNothing().when(TestGl.gl()).glDisablei(anyInt(), anyInt());
        Mockito.doNothing().when(TestGl.gl()).glBlendFuncSeparatei(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL14> gl14 = Mockito.mockStatic(GL14.class)) {
            ProgramBlendState.empty().apply(new int[] {0});
            // Slot 0 writes colortex0 (base mode), slot 1 colortex1 (off), slot 2 colortex4 (its own mode)
            water.apply(new int[] {0, 1, 4});
            verify(TestGl.gl()).glEnablei(GL11.GL_BLEND, 0);
            verify(TestGl.gl()).glDisablei(GL11.GL_BLEND, 1);
            verify(TestGl.gl()).glBlendFuncSeparatei(2, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            // A plain `off` disables blending on every slot, a malformed mode counts as off
            hand.apply(new int[] {0, 1});
            verify(TestGl.gl()).glDisablei(GL11.GL_BLEND, 0);
            block.apply(new int[] {0});
            defaulted.apply(new int[] {0});
            verify(TestGl.gl()).glBlendFuncSeparatei(0, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            // Per-target modes without a base leave the other slots alone
            composite.apply(new int[] {0, 2});
            verify(TestGl.gl()).glBlendFuncSeparatei(1, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            // Without per-buffer blending the global state still applies, and per-target requests are refused once
            when(TestGl.gl().supportsBufferBlending()).thenReturn(false);
            hand.apply(new int[] {0});
            composite.apply(new int[] {0, 2});
            composite.apply(new int[] {0, 2});
            verify(TestGl.gl(), times(1)).glBlendFuncSeparatei(1, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void theGuardHoldsABlendOffOverride() {
        // Nothing captured, nothing suppressed
        BlendOverrideGuard.afterProgramBlendApply();
        assertFalse(BlendOverrideGuard.recordEnableBlend());
        BlendOverrideGuard.beforeProgramBlendApply(true);
        BlendOverrideGuard.afterProgramBlendApply();
        assertTrue(BlendOverrideGuard.recordEnableBlend());
        // A program that left blending on has nothing to enforce
        when(TestGl.gl().glGetInteger(GL11.GL_BLEND)).thenReturn(1);
        BlendOverrideGuard.beforeProgramBlendApply(true);
        BlendOverrideGuard.afterProgramBlendApply();
        assertFalse(BlendOverrideGuard.recordEnableBlend());
        BlendOverrideGuard.beforeProgramBlendApply(false);
        BlendOverrideGuard.afterProgramBlendApply();
        assertFalse(BlendOverrideGuard.recordEnableBlend());
        assertNotNull(Mixins.construct(BlendOverrideGuard.class));
        verify(TestGl.gl(), times(2)).glGetInteger(anyInt());
        verify(TestGl.gl(), never()).glGetBoolean(anyInt());
    }
}
