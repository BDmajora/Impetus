package com.bdmajora.extras.client.text;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

// Collects glyph quads and draws them together.
public final class GlyphBatch {
    // Uploader of the vertex data.
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    // Vertex data of the batch.
    private final BufferBuilder buffer = new BufferBuilder(4096);
    // Texture of the batch.
    private ResourceLocation texture;
    // Whether the batch holds a drawing.
    private boolean open;
    // Red part of the color.
    private float red = 1.0F;
    // Green part of the color.
    private float green = 1.0F;
    // Blue part of the color.
    private float blue = 1.0F;
    // Alpha part of the color.
    private float alpha = 1.0F;

    // Stores the color of the next quads.
    public void setColor(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    // Opens the batch and flushes it first when the texture differs.
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

    // Adds one glyph quad.
    public void quad(float x, float y, float width, float height, float skew, float u0, float v0, float u1, float v1) {
        BufferBuilder b = this.buffer;
        b.pos(x + skew, y, 0.0D).tex(u0, v0).color(this.red, this.green, this.blue, this.alpha).endVertex();
        b.pos(x - skew, y + height, 0.0D).tex(u0, v1).color(this.red, this.green, this.blue, this.alpha).endVertex();
        b.pos(x + width - skew, y + height, 0.0D).tex(u1, v1).color(this.red, this.green, this.blue, this.alpha).endVertex();
        b.pos(x + width + skew, y, 0.0D).tex(u1, v0).color(this.red, this.green, this.blue, this.alpha).endVertex();
    }

    // Draws the collected quads.
    public void flush(Consumer<ResourceLocation> binder) {
        if (!this.open) {
            return;
        }
        this.open = false;
        binder.accept(this.texture);
        this.buffer.finishDrawing();
        UPLOADER.draw(this.buffer);
    }

    // Returns whether the batch holds a drawing.
    public boolean isOpen() {
        return this.open;
    }
}
