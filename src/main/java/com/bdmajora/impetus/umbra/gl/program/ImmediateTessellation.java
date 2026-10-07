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

// A program with tessellation stages only accepts GL_PATCHES, so while one is bound, vanilla's immediate-mode quads and triangles are resubmitted as three-vertex patches (Iris does the same for triangles on modern versions; 1.12.2 draws quads, which go through a shared quad-to-triangle index buffer)
public final class ImmediateTessellation {
    // GL_CURRENT_PROGRAM
    static final int CURRENT_PROGRAM = 0x8B8D;
    // Quads the shared index buffer holds at first; it doubles on demand
    static final int INITIAL_QUADS = 1024;

    // The tessellated program last bound through Umbra, 0 when none
    private static int tessellatedProgram;

    private static int quadIndexBuffer;
    private static int quadCapacity;

    private ImmediateTessellation() {
    }

    // Called on every Umbra program bind; a tessellated program also gets the three-vertex patch size its control stage expects
    static void programBound(int handle, boolean tessellated) {
        tessellatedProgram = tessellated ? handle : 0;

        if (tessellated) {
            LWJGL.glPatchParameteri(GL40.GL_PATCH_VERTICES, 3);
        }
    }

    // Whether the program GL has bound right now is a tessellated Umbra program; checked against GL itself, since the engine and vanilla bind programs Umbra never hears about, and only queried while a tessellated pack is active
    public static boolean isActive() {
        return tessellatedProgram != 0 && LWJGL.glGetInteger(CURRENT_PROGRAM) == tessellatedProgram;
    }

    // Draws an immediate-mode batch as patches when a tessellated program is bound; false leaves the draw to the caller unchanged, which is also the answer for lines and points
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

        // Vanilla draws immediate mode without a VAO, so the element binding lives on the default one and goes back to 0 for whatever draws next
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadIndexBuffer);
        LWJGL.glDrawElementsBaseVertex(GL40.GL_PATCHES, quads * 6, GL11.GL_UNSIGNED_INT, 0L, first);
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        return true;
    }

    // Grows the shared (0,1,2)(2,3,0) index pattern to cover at least this many quads
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

    // A model part's display list holds GL_QUADS a tessellated program cannot take, so while one is bound the part's boxes are drawn immediately instead, which routes them through drawArrays above
    public static void callModelList(ModelRenderer model, int displayList, float scale) {
        if (isActive()) {
            drawModelBoxes(model, scale);
        } else {
            GlStateManager.callList(displayList);
        }
    }

    // The boxes a model part's display list was compiled from, drawn through the Tessellator one quad at a time just as the list recorded them
    public static void drawModelBoxes(ModelRenderer model, float scale) {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        for (ModelBox box : model.cubeList) {
            box.render(buffer, scale);
        }
    }

    // Frees the index buffer when the pipeline goes away
    public static void reset() {
        if (quadIndexBuffer != 0) {
            LWJGL.glDeleteBuffers(quadIndexBuffer);
            quadIndexBuffer = 0;
        }
        quadCapacity = 0;
        tessellatedProgram = 0;
    }
}
