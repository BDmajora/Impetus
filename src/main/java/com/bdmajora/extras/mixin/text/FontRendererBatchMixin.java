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

// Mixin that changes FontRenderer.
@Mixin(FontRenderer.class)
public abstract class FontRendererBatchMixin {
    // Reference to the field pos x of the target.
    @Shadow
    protected float posX;

    // Reference to the field pos y of the target.
    @Shadow
    protected float posY;

    // Reference to the field location font texture of the target.
    @Shadow
    @Final
    private ResourceLocation locationFontTexture;

    // Reference to the field char width of the target.
    @Shadow
    @Final
    protected int[] charWidth;

    // Reference to the field glyph width of the target.
    @Shadow
    @Final
    protected byte[] glyphWidth;

    // Reference to the field strikethrough style of the target.
    @Shadow
    private boolean strikethroughStyle;

    // Reference to the field underline style of the target.
    @Shadow
    private boolean underlineStyle;

    // Reference to the method get unicode page location of the target.
    @Shadow
    protected abstract ResourceLocation getUnicodePageLocation(int page);

    // Reference to the method bind texture of the target.
    @Shadow(remap = false)
    protected abstract void bindTexture(ResourceLocation location);

    // Batch of queued glyphs.
    private final GlyphBatch impetus$batch = new GlyphBatch();

    // Handles enabled.
    private static boolean impetus$enabled() {
        return Extras.options().text.batchGlyphs;
    }

    // Runs at the start of setColor.
    @Inject(method = "setColor", at = @At("HEAD"))
    private void impetus$trackColor(float red, float green, float blue, float alpha, CallbackInfo ci) {
        this.impetus$batch.setColor(red, green, blue, alpha);
    }

    // Runs at the start of renderDefaultChar and can cancel it.
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

    // Runs at the start of renderUnicodeChar and can cancel it.
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

    // Runs at the start of doDraw.
    @Inject(method = "doDraw", at = @At("HEAD"))
    private void impetus$flushBeforeDecorations(float advance, CallbackInfo ci) {
        if ((this.strikethroughStyle || this.underlineStyle) && this.impetus$batch.isOpen()) {
            this.impetus$batch.flush(this::bindTexture);
        }
    }

    // Runs before every return of renderStringAtPos.
    @Inject(method = "renderStringAtPos", at = @At("RETURN"))
    private void impetus$flushString(String text, boolean shadow, CallbackInfo ci) {
        this.impetus$batch.flush(this::bindTexture);
    }
}
