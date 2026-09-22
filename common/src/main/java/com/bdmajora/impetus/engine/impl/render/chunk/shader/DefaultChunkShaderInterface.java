package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat3v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloatArray;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformMatrix4f;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.joml.Matrix4fc;
import com.bdmajora.impetus.lwjgl.MemoryStack;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// The engine's own forward-rendering chunk shader interface, used when no shader pack is active; Umbra swaps in UmbraTerrainShaderInterface, hence the interface
public class DefaultChunkShaderInterface implements ChunkShaderInterface {
    private static final long MAX_CHUNK_AGE = TimeUnit.SECONDS.toMillis(30);

    private final Map<ChunkShaderTextureSlot, GlUniformInt> uniformTextures;

    private final GlUniformMatrix4f uniformModelViewMatrix;
    private final GlUniformMatrix4f uniformProjectionMatrix;
    private final GlUniformFloat3v uniformRegionOffset;
    private final GlUniformFloatArray uniformChunkAges;

    // The additional shader components used by this program in order to setup the appropriate GL state
    private final List<? extends ChunkShaderComponent> components;

    private GlPrimitiveType primitiveType;

    public DefaultChunkShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        this.uniformModelViewMatrix = context.bindUniform("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniform("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformRegionOffset = context.bindUniform("u_RegionOffset", GlUniformFloat3v::new);
        this.uniformChunkAges = context.bindUniformIfPresent("impetus_ChunkAges", GlUniformFloatArray::new);

        this.uniformTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
        this.uniformTextures.put(ChunkShaderTextureSlot.BLOCK, context.bindUniform("u_BlockTex", GlUniformInt::new));
        if (!options.pass().hasNoLightmap()) {
            this.uniformTextures.put(ChunkShaderTextureSlot.LIGHT, context.bindUniform("u_LightTex", GlUniformInt::new));
        }

        this.components = options.components().stream().map(c -> c.create(context)).toList();
    }

    // Binds textures and applies the pass's blend and cull state
    @Deprecated // the shader interface should not modify pipeline state
    public void setupState(TerrainRenderPass pass) {
        this.primitiveType = pass.primitiveType().getGlPrimitiveType();

        for (var c : this.components) {
            c.setup();
        }
    }

    // Triangles
    @Override
    public GlPrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    // Uploads u_ProjectionMatrix
    public void setProjectionMatrix(Matrix4fc matrix) {
        this.uniformProjectionMatrix.set(matrix);
    }

    // Uploads u_ModelViewMatrix
    public void setModelViewMatrix(Matrix4fc matrix) {
        this.uniformModelViewMatrix.set(matrix);
    }

    // Uploads u_RegionOffset
    public void setRegionOffset(float x, float y, float z) {
        this.uniformRegionOffset.set(x, y, z);
    }

    // Points a sampler at a unit
    public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        var uniform = this.uniformTextures.get(slot);

        if (uniform != null) {
            uniform.setInt(val);
        }
    }

    // Uploads per-section ages for the fade-in
    @Override
    public void setSectionAges(long timestamp, long[] loadTimes) {
        var uniform = this.uniformChunkAges;

        if (uniform != null) {
            try (MemoryStack stack = LWJGL.stackPush()) {
                FloatBuffer buf = stack.callocFloat(loadTimes.length);
                long ptr = LWJGL.memAddress(buf);
                for (long loadTime : loadTimes) {
                    LWJGL.memPutFloat(ptr, (float) Math.min(MAX_CHUNK_AGE, (timestamp - loadTime) / (1000000L)));
                    ptr += 4;
                }
                uniform.set(buf);
            }
        }
    }
}
