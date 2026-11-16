package com.bdmajora.impetus.engine.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.shader.*;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.*;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Chunk renderer that draws with chunk shader programs.
public abstract class ShaderChunkRenderer implements ChunkRenderer {
    // Logger of shader problems.
    private static final Logger LOGGER = LogManager.getLogger(ShaderChunkRenderer.class);

    // Compiled programs by options where a failed compile is stored as null.
    private final Map<ChunkShaderOptions, @Nullable GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    // Configuration of the render passes.
    protected final RenderPassConfiguration<?> renderPassConfiguration;

    // Device the renderer draws on.
    protected final RenderDevice device;

    // Program bound for the current pass.
    protected GlProgram<ChunkShaderInterface> activeProgram;

    // Legacy GLSL patch state of the shaders.
    protected final boolean enableLegacyGLPatches;

    // Services of the shader components.
    protected final ChunkShaderEnvironment environment;

    // Creates the renderer and rejects null arguments.
    public ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        this.device = Objects.requireNonNull(device, "device");
        this.environment = new ChunkShaderEnvironment(Objects.requireNonNull(fogService, "fogService"));
        this.renderPassConfiguration = Objects.requireNonNull(renderPassConfiguration, "renderPassConfiguration");
        this.enableLegacyGLPatches = ShaderLoader.useLegacyGlsl();
        if (this.enableLegacyGLPatches) {
            LOGGER.warn("System does not support modern GLSL, will attempt to patch terrain shaders");
        }
    }

    // Returns the program of the options and compiles it once and returns null after a failure.
    protected @Nullable GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
        GlProgram<ChunkShaderInterface> program = this.programs.get(options);

        if (program == null && !this.programs.containsKey(options)) {
            try {
                program = this.createShader("blocks/block_layer_opaque", options);
            } catch(Exception e) {
                LOGGER.error("There was an error creating a chunk program. Terrain will not render until this is fixed.", e);
            }
            this.programs.put(options, program);
        }

        return program;
    }

    // Compiles and links a program and deletes the shaders on every path.
    protected GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        List<GlShader> loadedShaders = new ArrayList<>();

        try {
            loadedShaders.add(ShaderLoader.loadShader(ShaderType.VERTEX,
                    "impetus:" + path + ".vsh", constants));

            loadedShaders.add(ShaderLoader.loadShader(ShaderType.FRAGMENT,
                    "impetus:" + path + ".fsh", constants));

            var builder = GlProgram.builder("impetus:chunk_shader");
            loadedShaders.forEach(builder::attachShader);
            int i = 0;
            for (var attr : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }
            if (!this.enableLegacyGLPatches) {
                builder.bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR);
            }
            return builder.link((shader) -> new DefaultChunkShaderInterface(shader, options, this.environment));
        } finally {
            deleteAll(loadedShaders);
        }
    }

    // Deletes every shader and rethrows the first failure after the rest.
    private static void deleteAll(List<GlShader> shaders) {
        RuntimeException failure = null;
        for (GlShader shader : shaders) {
            try {
                shader.delete();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else if (e != failure) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    // Returns the factories of the shader components.
    protected List<ChunkShaderComponent.Factory<?>> getShaderComponents() {
        var componentFactories = new ArrayList<ChunkShaderComponent.Factory<?>>(4);
        componentFactories.add(this.environment.fogService().getFogMode());
        return componentFactories;
    }

    // Binds the program of a pass.
    protected void begin(TerrainRenderPass pass) {
        Objects.requireNonNull(pass, "pass");
        pass.startDrawing();

        ChunkShaderOptions options = new ChunkShaderOptions(getShaderComponents(), pass);

        this.activeProgram = this.compileProgram(options);

        if (this.activeProgram != null) {
            this.activeProgram.bind();
            this.activeProgram.getInterface()
                    .setupState(pass);
        }
    }

    // Restores the state and unbinds the program and ends the pass on every path.
    protected void end(TerrainRenderPass pass) {
        GlProgram<ChunkShaderInterface> program = this.activeProgram;
        this.activeProgram = null;
        try {
            if (program != null) {
                try {
                    program.getInterface().restoreState();
                } finally {
                    program.unbind();
                }
            }
        } finally {
            pass.endDrawing();
        }
    }

    // Deletes every program and continues after a failure.
    @Override
    public void delete(CommandList commandList) {
        RuntimeException failure = null;

        for (GlProgram<ChunkShaderInterface> program : this.programs.values()) {
            try {
                if (program != null) {
                    program.delete();
                }
            } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }

        this.programs.clear();
        this.activeProgram = null;

        if (failure != null) {
            throw failure;
        }
    }

    // Returns the render pass configuration.
    @Override
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderPassConfiguration;
    }
}
