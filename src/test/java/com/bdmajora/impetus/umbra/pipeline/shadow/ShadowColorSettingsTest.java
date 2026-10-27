package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the per buffer shadow colour directives
class ShadowColorSettingsTest {
    // Checks that each buffer reads its own directives and the shared mipmap switch comes first
    @Test
    void eachBufferReadsItsOwnDirectives() {
        ConstDirectives consts = new ConstDirectives(String.join("\n",
                "const bool generateShadowColorMipmap = true;",
                "const int shadowcolor1Format = RGBA16F;",
                "const int shadowcolor2Format = R32UI;",
                "const int shadowcolor3Format = NOT_A_FORMAT;",
                "const bool shadowcolor1Clear = false;",
                "const vec4 shadowcolor1ClearColor = vec4(0.25);",
                "const bool shadowcolor0Mipmap = false;",
                "const bool shadowColor3Mipmap = false;",
                "const bool shadowcolor0Nearest = true;",
                "const bool shadowColor4Nearest = true;",
                "const bool shadowColor5MinMagNearest = true;"));
        ShadowColorSettings settings = ShadowColorSettings.parse(consts, ShadowColorSettings.IRIS_BUFFERS);
        assertEquals(8, settings.count());
        assertEquals(InternalTextureFormat.RGBA, settings.format(0));
        assertEquals(InternalTextureFormat.RGBA16F, settings.format(1));
        assertEquals(InternalTextureFormat.RGBA, settings.format(3));
        assertTrue(settings.clear(0));
        assertFalse(settings.clear(1));
        assertArrayEquals(new float[] {0.25f, 0.25f, 0.25f, 0.25f}, settings.clearColor(1));
        assertArrayEquals(new float[] {1, 1, 1, 1}, settings.clearColor(2));
        assertFalse(settings.mipmap(0));
        assertTrue(settings.mipmap(1));
        assertFalse(settings.mipmap(3));
        assertTrue(settings.nearest(0));
        assertFalse(settings.nearest(1));
        assertTrue(settings.nearest(2));
        assertTrue(settings.nearest(4));
        assertTrue(settings.nearest(5));
        ShadowColorSettings defaults = ShadowColorSettings.parse(new ConstDirectives(""), ShadowColorSettings.OPTIFINE_BUFFERS);
        assertEquals(2, defaults.count());
        assertFalse(defaults.mipmap(1));
        assertEquals(InternalTextureFormat.RGBA, defaults.format(1));
    }

    // Checks that the later spellings win and an integer format filters nearest even when told otherwise
    @Test
    void laterSpellingsWinAndIntegerFormatsAlwaysFilterNearest() {
        ShadowColorSettings settings = ShadowColorSettings.parse(new ConstDirectives(String.join("\n",
                "const bool shadowcolor0Mipmap = true;",
                "const bool shadowColor0Mipmap = false;",
                "const bool shadowcolor0Nearest = true;",
                "const bool shadowColor0Nearest = true;",
                "const bool shadowColor0MinMagNearest = false;",
                "const int shadowcolor1Format = RGBA32I;",
                "const bool shadowcolor1Nearest = false;")), 2);
        assertFalse(settings.mipmap(0));
        assertFalse(settings.nearest(0));
        assertTrue(settings.nearest(1));
    }

    // Checks that directives past the count are ignored and a zero count has no buffers
    @Test
    void directivesPastTheCountAreIgnoredAndZeroIsEmpty() {
        ConstDirectives consts = new ConstDirectives("const int shadowcolor5Format = R32UI;");
        ShadowColorSettings two = ShadowColorSettings.parse(consts, 2);
        assertEquals(2, two.count());
        ShadowColorSettings none = ShadowColorSettings.parse(consts, 0);
        assertEquals(0, none.count());
        assertThrows(IndexOutOfBoundsException.class, () -> none.format(0));
    }

    // Checks that indices outside the count are rejected by every lookup
    @Test
    void indicesOutsideTheCountAreRejected() {
        ShadowColorSettings settings = ShadowColorSettings.parse(new ConstDirectives(""), 2);
        for (int index : new int[]{-1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> settings.format(index));
            assertThrows(IndexOutOfBoundsException.class, () -> settings.clear(index));
            assertThrows(IndexOutOfBoundsException.class, () -> settings.clearColor(index));
            assertThrows(IndexOutOfBoundsException.class, () -> settings.mipmap(index));
            assertThrows(IndexOutOfBoundsException.class, () -> settings.nearest(index));
        }
    }

    // Checks that each buffer gets its own default clear colour array
    @Test
    void defaultClearColoursAreSeparateArrays() {
        ShadowColorSettings settings = ShadowColorSettings.parse(new ConstDirectives(""), 2);
        assertNotSame(settings.clearColor(0), settings.clearColor(1));
        assertSame(settings.clearColor(0), settings.clearColor(0));
    }

    // Checks that a null directive set and a negative count are rejected
    @Test
    void nullDirectivesAndNegativeCountsAreRejected() {
        assertThrows(NullPointerException.class, () -> ShadowColorSettings.parse(null, 2));
        assertThrows(IllegalArgumentException.class, () -> ShadowColorSettings.parse(new ConstDirectives(""), -1));
        assertThrows(IllegalArgumentException.class, () -> ShadowColorSettings.parse(new ConstDirectives(""), Integer.MIN_VALUE));
    }
}
