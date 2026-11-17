package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.engine.impl.render.chunk.shader.TerrainDynamicLights;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat3v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloatArray;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformMatrix4f;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import org.lwjgl.opengl.GL40;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderTextureSlot;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4fc;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;

import java.util.EnumMap;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Terrain shader interface of pack programs.
public class UmbraTerrainShaderInterface implements ChunkShaderInterface {
    // Model view matrix uniform.
    private final GlUniformMatrix4f uModelViewMatrix;
    // Projection matrix uniform.
    private final GlUniformMatrix4f uProjectionMatrix;
    // Region offset uniform.
    private final GlUniformFloat3v uRegionOffset;
    // Chunk age array uniform.
    private final GlUniformFloatArray uChunkAges;
    // Inverse chunk fade time uniform.
    private final GlUniformFloat uChunkFadeInv;
    private final Map<ChunkShaderTextureSlot, GlUniformInt> uTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
    // Draw buffers.
    private final int[] drawBuffers;
    // Blend state.
    private final ProgramBlendState blendState;
    // Alpha test.
    private final ProgramAlphaTest alphaTest;
    // Uniforms of the program.
    private ProgramUniforms uniforms;
    // Shadow sampler kinds of the program.
    private ShadowSamplerKinds shadowSamplerKinds = ShadowSamplerKinds.ALL_COMPARE;

    // Primitive type.
    private GlPrimitiveType primitiveType = GlPrimitiveType.TRIANGLES;
    // Flag set when the program has tessellation stages.
    private final boolean tessellated;
    // Flag set when state must be restored after the draw.
    private boolean restoreAfterDraw;
    // Draw buffer slots in use.
    private int activeDrawBufferSlots;

    // Creates an umbra terrain shader interface from its context and draw buffers and blend state.
    public UmbraTerrainShaderInterface(ShaderBindingContext context, int[] drawBuffers, ProgramBlendState blendState,
                                      ProgramAlphaTest alphaTest) {
        this(context, drawBuffers, blendState, alphaTest, false);
    }

    // Creates an umbra terrain shader interface from its context and draw buffers and blend state.
    public UmbraTerrainShaderInterface(ShaderBindingContext context, int[] drawBuffers, ProgramBlendState blendState,
                                      // Alpha test and tessellation flag.
                                      ProgramAlphaTest alphaTest, boolean tessellated) {
        this.tessellated = tessellated;
        this.drawBuffers = drawBuffers == null ? DrawBuffers.DEFAULT.clone() : drawBuffers.clone();
        this.blendState = blendState;
        this.alphaTest = alphaTest;
        this.uModelViewMatrix = context.bindUniformIfPresent("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uProjectionMatrix = context.bindUniformIfPresent("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uRegionOffset = context.bindUniformIfPresent("u_RegionOffset", GlUniformFloat3v::new);
        this.uChunkAges = context.bindUniformIfPresent("iris_ChunkAgesMs", GlUniformFloatArray::new);
        this.uChunkFadeInv = context.bindUniformIfPresent("iris_ChunkFadeInv", GlUniformFloat::new);
        // Binds the moving light block.
        context.bindUniformBlockIfPresent(TerrainDynamicLights.BLOCK_NAME, TerrainDynamicLights.BINDING);

        GlUniformInt block = firstPresent(context, "tex", "texture", "gtexture", "gcolor");
        if (block != null) {
            this.uTextures.put(ChunkShaderTextureSlot.BLOCK, block);
        }
        GlUniformInt light = firstPresent(context, "lightmap", "texLightmap");
        if (light != null) {
            this.uTextures.put(ChunkShaderTextureSlot.LIGHT, light);
        }
    }

    // Returns the first uniform that exists or null.
    private static GlUniformInt firstPresent(ShaderBindingContext context, String... names) {
        for (String name : names) {
            GlUniformInt uniform = context.bindUniformIfPresent(name, GlUniformInt::new);
            if (uniform != null) {
                return uniform;
            }
        }
        return null;
    }

    // Stores the uniforms.
    public void setUniforms(ProgramUniforms uniforms) {
        this.uniforms = uniforms;
    }

    // Stores the shadow sampler kinds.
    public void setShadowSamplerKinds(ShadowSamplerKinds kinds) {
        this.shadowSamplerKinds = kinds == null ? ShadowSamplerKinds.ALL_COMPARE : kinds;
    }

    // Sets up the state.
    @Override
    public void setupState(TerrainRenderPass pass) {
        if (this.tessellated) {
            this.primitiveType = GlPrimitiveType.PATCHES;
            LWJGL.glPatchParameteri(GL40.GL_PATCH_VERTICES, 3);
        } else {
            this.primitiveType = pass.primitiveType().getGlPrimitiveType();
        }
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        boolean shadowPass = UmbraShadowRenderer.isShadowPass();
        if (pipeline != null && !shadowPass) {
            boolean translucentPass = pass.isReverseOrder();
            if (translucentPass) {
                restoreOptifineWaterState();
            }
            pipeline.onTerrainDraw(this.drawBuffers, this.blendState, this.alphaTest, translucentPass);
            pipeline.applyShadowSamplerKinds(this.shadowSamplerKinds, true);
            this.restoreAfterDraw = true;
            this.activeDrawBufferSlots = this.drawBuffers.length;
        }
        if (pipeline != null) {
            pipeline.bindCustomImages();
        }
        if (this.uniforms != null) {
            this.uniforms.update();
        }
    }

    // Restores the GL state after a draw.
    @Override
    public void restoreState() {
        if (!this.restoreAfterDraw) {
            return;
        }
        this.restoreAfterDraw = false;
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.afterTerrainDraw(this.activeDrawBufferSlots);
        }
    }

    // Restores the water state that OptiFine packs expect.
    private static void restoreOptifineWaterState() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(true);
    }

    // Stores the section ages.
    @Override
    public void setSectionAges(long timestamp, long[] loadTimes) {
        if (this.uChunkAges == null) {
            return;
        }
        float[] ages = new float[loadTimes.length];
        for (int i = 0; i < ages.length; i++) {
            ages[i] = Math.min(30_000.0f, (timestamp - loadTimes[i]) / 1_000_000.0f);
        }
        this.uChunkAges.set(ages);
        if (this.uChunkFadeInv != null) {
            int durationMs = com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.chunkFadeInDuration;
            this.uChunkFadeInv.setFloat(durationMs > 0 ? 1.0f / durationMs : 0.0f);
        }
    }

    // Returns the primitive type.
    @Override
    public GlPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    // Stores the projection matrix.
    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        if (this.uProjectionMatrix != null) {
            this.uProjectionMatrix.set(matrix);
        }
    }

    // Stores the model view matrix.
    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        if (this.uModelViewMatrix != null) {
            this.uModelViewMatrix.set(matrix);
        }
    }

    // Stores the region offset.
    @Override
    public void setRegionOffset(float x, float y, float z) {
        if (this.uRegionOffset != null) {
            this.uRegionOffset.set(x, y, z);
        }
    }

    // Stores the texture slot.
    @Override
    public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        GlUniformInt uniform = this.uTextures.get(slot);
        if (uniform != null) {
            uniform.setInt(val);
        }
    }
}
