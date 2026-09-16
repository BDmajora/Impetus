package com.bdmajora.extras.mixin.text;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.client.text.GlyphBatch;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Replaces the per-glyph immediate-mode draws with the batch; geometry and UVs are vanilla's numbers, colour rides on each vertex since the batch is drawn after setColor has moved on. Flushed at the end of every renderStringAtPos, before the Tessellator-drawn strikethrough/underline, and whenever the glyph page changes
@Mixin(FontRenderer.class)
public abstract class FontRendererBatchMixin {
    @Shadow
    protected float posX;

    @Shadow
    protected float posY;

    @Shadow
    @Final
    private ResourceLocation locationFontTexture;

    @Shadow
    @Final
    protected int[] charWidth;

    @Shadow
    @Final
    protected byte[] glyphWidth;

    @Shadow
    private boolean strikethroughStyle;

    @Shadow
    private boolean underlineStyle;

    @Shadow
    protected abstract ResourceLocation getUnicodePageLocation(int page);

    // Forge's hook, which the splash font overrides to bind its own texture and mods override for custom atlases
    @Shadow(remap = false)
    protected abstract void bindTexture(ResourceLocation location);

    private final GlyphBatch impetus$batch = new GlyphBatch();

    private static boolean impetus$enabled() {
        return Extras.options().text.batchGlyphs;
    }

    @Inject(method = "setColor", at = @At("HEAD"))
    private void impetus$trackColor(float red, float green, float blue, float alpha, CallbackInfo ci) {
        this.impetus$batch.setColor(red, green, blue, alpha);
    }

    @Inject(method = "renderDefaultChar", at = @At("HEAD"), cancellable = true)
    private void impetus$batchDefaultChar(int ch, boolean italic, CallbackInfoReturnable<Float> cir) {
        if (!impetus$enabled()) {
            return;
        }
        int column = ch % 16 * 8;
        int row = ch / 16 * 8;
        float skew = italic ? 1.0F : 0.0F;
        int width = this.charWidth[ch];
        float f = (float) width - 0.01F;
        this.impetus$batch.prepare(this::bindTexture, this.locationFontTexture);
        this.impetus$batch.quad(this.posX, this.posY, f - 1.0F, 7.99F, skew,
                column / 128.0F, row / 128.0F, (column + f - 1.0F) / 128.0F, (row + 7.99F) / 128.0F);
        cir.setReturnValue((float) width);
    }

    @Inject(method = "renderUnicodeChar", at = @At("HEAD"), cancellable = true)
    private void impetus$batchUnicodeChar(char ch, boolean italic, CallbackInfoReturnable<Float> cir) {
        if (!impetus$enabled()) {
            return;
        }
        int packed = this.glyphWidth[ch] & 255;
        if (packed == 0) {
            cir.setReturnValue(0.0F);
            return;
        }
        int page = ch / 256;
        int start = packed >>> 4;
        int end = packed & 15;
        float f = (float) start;
        float f1 = (float) (end + 1);
        float u = (float) (ch % 16 * 16) + f;
        float v = (float) ((ch & 255) / 16 * 16);
        float span = f1 - f - 0.02F;
        float skew = italic ? 1.0F : 0.0F;
        this.impetus$batch.prepare(this::bindTexture, this.getUnicodePageLocation(page));
        this.impetus$batch.quad(this.posX, this.posY, span / 2.0F, 7.99F, skew,
                u / 256.0F, v / 256.0F, (u + span) / 256.0F, (v + 15.98F) / 256.0F);
        cir.setReturnValue((f1 - f) / 2.0F + 1.0F);
    }

    // The decorations draw through the shared Tessellator with texturing off; the glyphs must be on screen before that state changes
    @Inject(method = "doDraw", at = @At("HEAD"))
    private void impetus$flushBeforeDecorations(float advance, CallbackInfo ci) {
        if ((this.strikethroughStyle || this.underlineStyle) && this.impetus$batch.isOpen()) {
            this.impetus$batch.flush(this::bindTexture);
        }
    }

    @Inject(method = "renderStringAtPos", at = @At("RETURN"))
    private void impetus$flushString(String text, boolean shadow, CallbackInfo ci) {
        this.impetus$batch.flush(this::bindTexture);
    }
}
