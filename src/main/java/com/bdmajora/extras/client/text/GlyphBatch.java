package com.bdmajora.extras.client.text;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

// Collects a string's glyph quads and submits them in one draw (ImmediatelyFast's text batching, on 1.12.2 where every glyph is its own glBegin/glEnd): a line of chat is ~40 glyphs, the F3 screen several hundred, each costing four immediate-mode vertex calls plus a texture bind. Owned per FontRenderer, with its own BufferBuilder so the strikethrough and underline draws, which use the shared Tessellator, cannot collide with it; binds through the renderer's own bindTexture hook since Forge's splash font has no texture manager at all
public final class GlyphBatch {
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private final BufferBuilder buffer = new BufferBuilder(4096);
    private ResourceLocation texture;
    private boolean open;
    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float alpha = 1.0F;

    public void setColor(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    // Begins or continues the batch on `texture`, flushing first if a different page is bound
    public void prepare(Consumer<ResourceLocation> binder, ResourceLocation texture) {
        if (this.open && this.texture != texture) {
            this.flush(binder);
        }
        if (!this.open) {
            this.buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            this.texture = texture;
            this.open = true;
        }
    }

    // One glyph as a quad in vanilla's strip order (top-left, bottom-left, bottom-right, top-right); `skew` is the italic shear
    public void quad(float x, float y, float width, float height, float skew, float u0, float v0, float u1, float v1) {
        BufferBuilder b = this.buffer;
        b.pos(x + skew, y, 0.0D).tex(u0, v0).color(this.red, this.green, this.blue, this.alpha).endVertex();
        b.pos(x - skew, y + height, 0.0D).tex(u0, v1).color(this.red, this.green, this.blue, this.alpha).endVertex();
        b.pos(x + width - skew, y + height, 0.0D).tex(u1, v1).color(this.red, this.green, this.blue, this.alpha).endVertex();
        b.pos(x + width + skew, y, 0.0D).tex(u1, v0).color(this.red, this.green, this.blue, this.alpha).endVertex();
    }

    public void flush(Consumer<ResourceLocation> binder) {
        if (!this.open) {
            return;
        }
        this.open = false;
        binder.accept(this.texture);
        this.buffer.finishDrawing();
        UPLOADER.draw(this.buffer);
    }

    public boolean isOpen() {
        return this.open;
    }
}
