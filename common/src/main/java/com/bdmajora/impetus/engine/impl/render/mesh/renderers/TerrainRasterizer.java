package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Draws the opaque terrain from a command buffer written by the GPU.
public class TerrainRasterizer {
    // Bytes of one draw command.
    static final int COMMAND_BYTES = 8;

    // Program of the draw.
    private final MeshProgram program;

    // Creates the program and builds the temporal variant when the constants ask for it.
    public TerrainRasterizer(String name, ShaderConstants constants) {
        this.program = MeshProgram.builder(name)
                .constants(constants)
                .stage(ShaderType.TASK, "impetus:mesh/terrain.task")
                .stage(ShaderType.MESH, "impetus:mesh/terrain.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/terrain.frag")
                .link();
    }

    // Binds the program and draws the commands of every region.
    public void raster(int regionCount, long commandBufferAddress) {
        this.program.bind();
        drawIndirect(regionCount, commandBufferAddress);
    }

    // Binds the program.
    public void bind() {
        this.program.bind();
    }

    // Deletes the program.
    public void delete() {
        this.program.delete();
    }

    // Draws the commands at an address and rejects a negative count.
    public static void drawIndirect(int regionCount, long commandBufferAddress) {
        if (regionCount < 0) {
            throw new IllegalArgumentException("Negative region count " + regionCount);
        }
        LWJGL.glBufferAddressRangeNV(MeshShaderSupport.GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandBufferAddress, (long) regionCount * COMMAND_BYTES);
        LWJGL.glMultiDrawMeshTasksIndirectNV(0L, regionCount, 0);
    }
}
