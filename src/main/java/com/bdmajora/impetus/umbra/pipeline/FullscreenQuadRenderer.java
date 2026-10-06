package com.bdmajora.impetus.umbra.pipeline;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The full-screen quad every composite, deferred and final pass renders through: one VAO/VBO of four [0,1] position+texcoord vertices as a triangle strip, replacing OptiFine's immediate-mode quad which cannot work with a core-profile VAO current; layout matches FullscreenTransformer's a_Position/a_TexCoord slots
public class FullscreenQuadRenderer {
    public static final int POSITION_SLOT = 0;
    public static final int TEXCOORD_SLOT = 1;
    // Modern packs address the quad via gl_Vertex and gl_MultiTexCoord0, which alias generic locations 0 and 8 on the compatibility profile; POSITION_SLOT already feeds gl_Vertex, and the texcoord is mirrored into slot 8, harmless on the GLSL-120 path
    public static final int MULTITEXCOORD0_SLOT = 8;

    private static final int STRIDE = 4 * Float.BYTES;

    private final int vertexArray;
    private final int vertexBuffer;
    private boolean destroyed;

    public FullscreenQuadRenderer() {
        this.vertexArray = LWJGL.glGenVertexArrays();
        this.vertexBuffer = LWJGL.glGenBuffers();

        LWJGL.glBindVertexArray(this.vertexArray);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);

        try (MemoryStack stack = LWJGL.stackPush()) {
            ByteBuffer data = stack.malloc(4 * STRIDE);
            FloatBuffer floats = data.asFloatBuffer();
            floats.put(new float[]{
                    // x, y, u, v triangle strip in [0,1] (matching Umbra), NOT NDC: packs read gl_Vertex and expect [0,1] (superDuperVanilla does `gl_Vertex.xy * 2.0 - 1.0`), and the [0,1]->[-1,1] mapping lives in pushFullscreenFixedFunctionMatrices and FullscreenTransformer's defines
                    0.0f, 0.0f, 0.0f, 0.0f,
                    1.0f, 0.0f, 1.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 1.0f,
                    1.0f, 1.0f, 1.0f, 1.0f
            });
            LWJGL.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        }

        LWJGL.glEnableVertexAttribArray(POSITION_SLOT);
        LWJGL.glVertexAttribPointer(POSITION_SLOT, 2, GL11.GL_FLOAT, false, STRIDE, 0L);
        LWJGL.glEnableVertexAttribArray(TEXCOORD_SLOT);
        LWJGL.glVertexAttribPointer(TEXCOORD_SLOT, 2, GL11.GL_FLOAT, false, STRIDE, 2L * Float.BYTES);
        // Mirror the texcoord into slot 8 for modern packs' gl_MultiTexCoord0 (see MULTITEXCOORD0_SLOT).
        LWJGL.glEnableVertexAttribArray(MULTITEXCOORD0_SLOT);
        LWJGL.glVertexAttribPointer(MULTITEXCOORD0_SLOT, 2, GL11.GL_FLOAT, false, STRIDE, 2L * Float.BYTES);

        LWJGL.glBindVertexArray(0);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    // Draws against whatever program, framebuffer and samplers are bound, setting none; leaves VAO 0 bound so vanilla's immediate-mode drawing is not fed our arrays
    public void draw() {
        LWJGL.glBindVertexArray(this.vertexArray);
        LWJGL.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        LWJGL.glBindVertexArray(0);
    }

    // Frees the quad's buffer
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        LWJGL.glDeleteBuffers(this.vertexBuffer);
        LWJGL.glDeleteVertexArrays(this.vertexArray);
    }
}
