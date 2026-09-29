package com.bdmajora.extras.client.text;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.text.FontRendererBatchMixin;
import com.bdmajora.testing.Mixins;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlyphBatchTest {
    private static final ResourceLocation ASCII = new ResourceLocation("textures/font/ascii.png");
    private static final ResourceLocation PAGE = new ResourceLocation("textures/font/unicode_page_00.png");

    private ExtrasConfig config;

    @BeforeEach
    void freshConfig() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
    }

    @Test
    void glyphsOnOnePageDrawTogether() {
        GlyphBatch batch = new GlyphBatch();
        List<ResourceLocation> bound = new ArrayList<>();
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class);
             MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            // Nothing open, nothing drawn
            batch.flush(bound::add);
            assertFalse(batch.isOpen());
            batch.setColor(1.0F, 0.5F, 0.25F, 1.0F);
            batch.prepare(bound::add, ASCII);
            batch.quad(0, 0, 5, 8, 0, 0, 0, 0.05F, 0.06F);
            batch.prepare(bound::add, ASCII);
            batch.quad(6, 0, 5, 8, 1, 0.05F, 0, 0.1F, 0.06F);
            assertTrue(batch.isOpen());
            assertTrue(bound.isEmpty());
            // A page change sends what is queued before starting on the new texture
            batch.prepare(bound::add, PAGE);
            assertEquals(List.of(ASCII), bound);
            batch.quad(12, 0, 4, 8, 0, 0, 0, 0.1F, 0.1F);
            batch.flush(bound::add);
            assertEquals(List.of(ASCII, PAGE), bound);
            assertFalse(batch.isOpen());
            gl11.verify(() -> GL11.glDrawArrays(GL11.GL_QUADS, 0, 8));
            gl11.verify(() -> GL11.glDrawArrays(GL11.GL_QUADS, 0, 4));
        }
    }

    private static <R> CallbackInfoReturnable<R> call(Object font, String handler, Object... leading) {
        CallbackInfoReturnable<R> cir = Mixins.cir();
        Object[] args = Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = cir;
        Mixins.call(font, handler, args);
        return cir;
    }

    @Test
    void theFontRendererQueuesGlyphsInsteadOfDrawingEach() {
        FontRendererBatchMixin font = Mixins.instance(FontRendererBatchMixin.class);
        Mixins.set(font, "impetus$batch", new GlyphBatch());
        int[] widths = new int[256];
        Arrays.fill(widths, 6);
        Mixins.set(font, "charWidth", widths);
        byte[] glyphs = new byte[65536];
        glyphs['é'] = 0x2E;
        Mixins.set(font, "glyphWidth", glyphs);
        Mixins.set(font, "locationFontTexture", ASCII);
        Mixins.stub(font, "getUnicodePageLocation", invocation -> PAGE);
        // Off, vanilla draws each glyph itself
        config.text.batchGlyphs = false;
        assertFalse(call(font, "impetus$batchDefaultChar", (int) 'A', false).isCancelled());
        assertFalse(call(font, "impetus$batchUnicodeChar", 'é', false).isCancelled());
        config.text.batchGlyphs = true;
        GlyphBatch batch = Mixins.get(font, "impetus$batch");
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class);
             MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            Mixins.call(font, "impetus$trackColor", 1.0F, 1.0F, 1.0F, 1.0F, Mixins.ci());
            assertEquals(6.0F, call(font, "impetus$batchDefaultChar", (int) 'A', true).getReturnValue());
            assertTrue(batch.isOpen());
            // An empty unicode glyph advances nothing and queues nothing
            assertEquals(0.0F, call(font, "impetus$batchUnicodeChar", '\u0001', false).getReturnValue());
            // Start column 2, end 14: half of 13 pixels plus the one-pixel gap
            assertEquals(7.5F, call(font, "impetus$batchUnicodeChar", 'é', false).getReturnValue());
            gl11.verify(() -> GL11.glDrawArrays(GL11.GL_QUADS, 0, 4));
            // Decorations flush first only when there are any and something is queued
            Mixins.call(font, "impetus$flushBeforeDecorations", 6.0F, Mixins.ci());
            assertTrue(batch.isOpen());
            Mixins.set(font, "strikethroughStyle", true);
            Mixins.call(font, "impetus$flushBeforeDecorations", 6.0F, Mixins.ci());
            assertFalse(batch.isOpen());
            Mixins.call(font, "impetus$flushBeforeDecorations", 6.0F, Mixins.ci());
            Mixins.set(font, "strikethroughStyle", false);
            Mixins.set(font, "underlineStyle", true);
            Mixins.call(font, "impetus$flushBeforeDecorations", 6.0F, Mixins.ci());
            // The end of every string sends whatever is left
            call(font, "impetus$batchDefaultChar", (int) 'B', false);
            Mixins.call(font, "impetus$flushString", "AB", false, (CallbackInfo) Mixins.ci());
            assertFalse(batch.isOpen());
        }
    }
}
