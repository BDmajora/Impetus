package com.bdmajora.impetus.umbra.gl.program;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Shared vertex array for immediate pipeline draws.
public final class ImmediateTessellation {
    // GL enum of the current program.
    static final int CURRENT_PROGRAM = 0x8B8D;
    // Starting quad capacity of the index buffer.
    static final int INITIAL_QUADS = 1024;

    // Name of the bound tessellated program or zero.
    private static int tessellatedProgram;

    // Quad index buffer name.
    private static int quadIndexBuffer;
    // Quads the index buffer holds.
    private static int quadCapacity;

    // Prevents instantiation
    private ImmediateTessellation() {
    }

    // Records the bound program and whether it tessellates.
    static void programBound(int handle, boolean tessellated) {
        tessellatedProgram = tessellated ? handle : 0;

        if (tessellated) {
            LWJGL.glPatchParameteri(GL40.GL_PATCH_VERTICES, 3);
        }
    }

    // Returns whether active.
    public static boolean isActive() {
        return tessellatedProgram != 0 && LWJGL.glGetInteger(CURRENT_PROGRAM) == tessellatedProgram;
    }

    // Draws arrays as patches when a tessellated program is bound and returns whether it did.
    public static boolean drawArrays(int mode, int first, int count) {
        if (count <= 0 || (mode != GL11.GL_TRIANGLES && mode != GL11.GL_QUADS) || !isActive()) {
            return false;
        }

        if (mode == GL11.GL_TRIANGLES) {
            LWJGL.glDrawArrays(GL40.GL_PATCHES, first, count);
            return true;
        }

        int quads = count >> 2;
        ensureQuadIndices(quads);

        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadIndexBuffer);
        LWJGL.glDrawElementsBaseVertex(GL40.GL_PATCHES, quads * 6, GL11.GL_UNSIGNED_INT, 0L, first);
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        return true;
    }

    // Grows the quad index buffer to a quad count.
    private static void ensureQuadIndices(int quads) {
        if (quads <= quadCapacity) {
            return;
        }

        int capacity = Math.max(INITIAL_QUADS, Integer.highestOneBit(quads - 1) << 1);
        ByteBuffer indices = ByteBuffer.allocateDirect(capacity * 6 * Integer.BYTES).order(ByteOrder.nativeOrder());
        for (int quad = 0; quad < capacity; quad++) {
            int base = quad << 2;
            indices.putInt(base).putInt(base + 1).putInt(base + 2)
                    .putInt(base + 2).putInt(base + 3).putInt(base);
        }
        indices.flip();

        if (quadIndexBuffer == 0) {
            quadIndexBuffer = LWJGL.glGenBuffers();
        }
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadIndexBuffer);
        LWJGL.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        quadCapacity = capacity;
    }

    // Draws a model display list.
    public static void callModelList(ModelRenderer model, int displayList, float scale) {
        if (isActive()) {
            drawModelBoxes(model, scale);
        } else {
            GlStateManager.callList(displayList);
        }
    }

    // Draws the boxes of a model as patches.
    public static void drawModelBoxes(ModelRenderer model, float scale) {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        for (ModelBox box : model.cubeList) {
            box.render(buffer, scale);
        }
    }

    // Deletes the shared vertex array.
    public static void reset() {
        if (quadIndexBuffer != 0) {
            LWJGL.glDeleteBuffers(quadIndexBuffer);
            quadIndexBuffer = 0;
        }
        quadCapacity = 0;
        tessellatedProgram = 0;
    }
}
