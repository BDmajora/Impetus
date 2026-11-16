package com.bdmajora.impetus.umbra.pipeline;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Vertex array of a quad that covers the screen.
public class FullscreenQuadRenderer {
    // Attribute index of the position.
    public static final int POSITION_SLOT = 0;
    // Attribute index of the texture coordinate.
    public static final int TEXCOORD_SLOT = 1;
    // Attribute index of the legacy texture coordinate.
    public static final int MULTITEXCOORD0_SLOT = 8;

    // Bytes of one vertex.
    private static final int STRIDE = 4 * Float.BYTES;

    // GL name of the vertex array.
    private final int vertexArray;
    // GL name of the vertex buffer.
    private final int vertexBuffer;
    // Deleted state of the quad.
    private boolean destroyed;

    // Creates the quad and deletes its objects when the setup fails.
    public FullscreenQuadRenderer() {
        this.vertexArray = LWJGL.glGenVertexArrays();
        this.vertexBuffer = LWJGL.glGenBuffers();
        // Deletes both objects when the setup fails
        try {
            this.upload();
        } catch (RuntimeException | Error e) {
            LWJGL.glBindVertexArray(0);
            LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            LWJGL.glDeleteBuffers(this.vertexBuffer);
            LWJGL.glDeleteVertexArrays(this.vertexArray);
            throw e;
        }
    }

    // Uploads the quad and records its attribute layout.
    private void upload() {
        LWJGL.glBindVertexArray(this.vertexArray);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(4 * STRIDE);
            FloatBuffer floats = data.asFloatBuffer();
            floats.put(new float[]{
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
        LWJGL.glEnableVertexAttribArray(MULTITEXCOORD0_SLOT);
        LWJGL.glVertexAttribPointer(MULTITEXCOORD0_SLOT, 2, GL11.GL_FLOAT, false, STRIDE, 2L * Float.BYTES);

        LWJGL.glBindVertexArray(0);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    // Draws the quad and rejects a destroyed quad.
    public void draw() {
        if (this.destroyed) {
            throw new IllegalStateException("Fullscreen quad was destroyed");
        }
        LWJGL.glBindVertexArray(this.vertexArray);
        LWJGL.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        LWJGL.glBindVertexArray(0);
    }

    // Deletes the buffer and the vertex array once.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        try {
            LWJGL.glDeleteBuffers(this.vertexBuffer);
        } finally {
            LWJGL.glDeleteVertexArrays(this.vertexArray);
        }
    }
}
