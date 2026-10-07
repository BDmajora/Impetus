package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Opaque terrain as one multi-draw by address from a GPU-written command buffer; the main variant draws last frame's commands, the temporal one (Nvidium's temporal coherence) redraws only sections that turned visible this frame
public class TerrainRasterizer {
    // Each command is a uvec2: meshlet count and the section id base
    static final int COMMAND_BYTES = 8;

    private final MeshProgram program;

    // Constants carrying TEMPORAL build the temporal variant
    public TerrainRasterizer(String name, ShaderConstants constants) {
        this.program = MeshProgram.builder(name)
                .constants(constants)
                .stage(ShaderType.TASK, "impetus:mesh/terrain.task")
                .stage(ShaderType.MESH, "impetus:mesh/terrain.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/terrain.frag")
                .link();
    }

    // One glMultiDrawMeshTasksIndirectNV over every region's command
    public void raster(int regionCount, long commandBufferAddress) {
        this.program.bind();
        drawIndirect(regionCount, commandBufferAddress);
    }

    // Frees the program
    public void delete() {
        this.program.delete();
    }

    // Binds a command buffer by address and draws it; stride 0 means tightly packed, which is what the section rasterizer writes
    static void drawIndirect(int regionCount, long commandBufferAddress) {
        LWJGL.glBufferAddressRangeNV(MeshShaderSupport.GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandBufferAddress, (long) regionCount * COMMAND_BYTES);
        LWJGL.glMultiDrawMeshTasksIndirectNV(0L, regionCount, 0);
    }
}
