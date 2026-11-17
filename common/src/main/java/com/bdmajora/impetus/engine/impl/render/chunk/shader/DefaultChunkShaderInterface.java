package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat3v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloatArray;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformMatrix4f;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.lwjgl.system.MemoryUtil;

// Chunk shader interface of the engine that is used without a shader pack.
public class DefaultChunkShaderInterface implements ChunkShaderInterface {
    // Largest chunk age in milliseconds.
    private static final long MAX_CHUNK_AGE = TimeUnit.SECONDS.toMillis(30);

    // Texture uniforms by slot.
    private final Map<ChunkShaderTextureSlot, GlUniformInt> uniformTextures;

    // Uniform of the model view matrix.
    private final GlUniformMatrix4f uniformModelViewMatrix;
    // Uniform of the projection matrix.
    private final GlUniformMatrix4f uniformProjectionMatrix;
    // Uniform of the region offset.
    private final GlUniformFloat3v uniformRegionOffset;
    // Uniform of the section ages or null.
    private final GlUniformFloatArray uniformChunkAges;

    // Components of the program.
    private final List<? extends ChunkShaderComponent> components;

    // Primitive type of the pass.
    private GlPrimitiveType primitiveType;

    // Binds the uniforms and creates the components.
    public DefaultChunkShaderInterface(ShaderBindingContext context, ChunkShaderOptions options, ChunkShaderEnvironment environment) {
        this.uniformModelViewMatrix = context.bindUniform("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniform("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformRegionOffset = context.bindUniform("u_RegionOffset", GlUniformFloat3v::new);
        this.uniformChunkAges = context.bindUniformIfPresent("impetus_ChunkAges", GlUniformFloatArray::new);

        this.uniformTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
        this.uniformTextures.put(ChunkShaderTextureSlot.BLOCK, context.bindUniform("u_BlockTex", GlUniformInt::new));
        if (!options.pass().hasNoLightmap()) {
            this.uniformTextures.put(ChunkShaderTextureSlot.LIGHT, context.bindUniform("u_LightTex", GlUniformInt::new));
        }

        // Binds the moving light block.
        context.bindUniformBlockIfPresent(TerrainDynamicLights.BLOCK_NAME, TerrainDynamicLights.BINDING);

        this.components = options.components().stream().map(c -> c.create(context, environment)).toList();
    }

    // Stores the primitive type of the pass and sets up the components.
    @Deprecated
    public void setupState(TerrainRenderPass pass) {
        this.primitiveType = pass.primitiveType().getGlPrimitiveType();

        for (var c : this.components) {
            c.setup();
        }
    }

    // Returns the primitive type of the pass.
    @Override
    public GlPrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    // Uploads the projection matrix.
    public void setProjectionMatrix(Matrix4fc matrix) {
        this.uniformProjectionMatrix.set(matrix);
    }

    // Uploads the model view matrix.
    public void setModelViewMatrix(Matrix4fc matrix) {
        this.uniformModelViewMatrix.set(matrix);
    }

    // Uploads the region offset.
    public void setRegionOffset(float x, float y, float z) {
        this.uniformRegionOffset.set(x, y, z);
    }

    // Sets the unit of a texture slot when the shader has it.
    public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        var uniform = this.uniformTextures.get(slot);

        if (uniform != null) {
            uniform.setInt(val);
        }
    }

    // Uploads the age of every section limited to the maximum.
    @Override
    public void setSectionAges(long timestamp, long[] loadTimes) {
        Objects.requireNonNull(loadTimes, "loadTimes");
        var uniform = this.uniformChunkAges;

        if (uniform != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buf = stack.mallocFloat(loadTimes.length);
                long ptr = MemoryUtil.memAddress(buf);
                for (long loadTime : loadTimes) {
                    MemoryUtil.memPutFloat(ptr, (float) Math.max(0L, Math.min(MAX_CHUNK_AGE, (timestamp - loadTime) / (1000000L))));
                    ptr += 4;
                }
                uniform.set(buf);
            }
        }
    }
}
