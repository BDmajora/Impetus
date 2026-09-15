package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat3v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformMatrix4f;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderTextureSlot;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;

import java.util.EnumMap;
import java.util.Map;

// The ChunkShaderInterface for a pack terrain program: feeds the engine's per-draw state (u_ModelViewMatrix, u_ProjectionMatrix, u_RegionOffset, block/lightmap units) the generated prologue declares, and binds the pipeline's gbuffer so terrain lands there for the deferred chain
public class UmbraTerrainShaderInterface implements ChunkShaderInterface {
    private final GlUniformMatrix4f uModelViewMatrix;
    private final GlUniformMatrix4f uProjectionMatrix;
    private final GlUniformFloat3v uRegionOffset;
    private final Map<ChunkShaderTextureSlot, GlUniformInt> uTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
    // The program's sanitised DRAWBUFFERS mask, re-applied to the gbuffer FBO on every bind; sanitised since a pack can name a buffer this driver lacks and an out-of-range slot fails the whole draw
    private final int[] drawBuffers;
    private final ProgramBlendState blendState;
    private final ProgramAlphaTest alphaTest;
    // The pack's OptiFine uniform set (gbufferModelView and inverse, cameraPosition, time counters) uploaded every bind; without it the world-space round trip multiplies by a zero matrix and the world disappears. Attached after link, hence not final
    private ProgramUniforms uniforms;
    // How this program declared shadowtex0/1; attached after link like the uniforms, comparison-only until then
    private ShadowSamplerKinds shadowSamplerKinds = ShadowSamplerKinds.ALL_COMPARE;

    private GlPrimitiveType primitiveType = GlPrimitiveType.TRIANGLES;
    private boolean restoreAfterDraw;
    private int activeDrawBufferSlots;

    public UmbraTerrainShaderInterface(ShaderBindingContext context, int[] drawBuffers, ProgramBlendState blendState,
                                      ProgramAlphaTest alphaTest) {
        this.drawBuffers = drawBuffers == null ? DrawBuffers.DEFAULT.clone() : drawBuffers.clone();
        this.blendState = blendState;
        this.alphaTest = alphaTest;
        this.uModelViewMatrix = context.bindUniformIfPresent("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uProjectionMatrix = context.bindUniformIfPresent("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uRegionOffset = context.bindUniformIfPresent("u_RegionOffset", GlUniformFloat3v::new);

        GlUniformInt block = firstPresent(context, "tex", "texture", "gtexture", "gcolor");
        if (block != null) {
            this.uTextures.put(ChunkShaderTextureSlot.BLOCK, block);
        }
        GlUniformInt light = firstPresent(context, "lightmap", "texLightmap");
        if (light != null) {
            this.uTextures.put(ChunkShaderTextureSlot.LIGHT, light);
        }
    }

    // Binds the first of several possible uniform spellings, since packs and transforms name them differently
    private static GlUniformInt firstPresent(ShaderBindingContext context, String... names) {
        for (String name : names) {
            GlUniformInt uniform = context.bindUniformIfPresent(name, GlUniformInt::new);
            if (uniform != null) {
                return uniform;
            }
        }
        return null;
    }

    // Uploads the pack's uniform set for this frame
    public void setUniforms(ProgramUniforms uniforms) {
        this.uniforms = uniforms;
    }

    // Records the declared shadow sampler types the bind path applies
    public void setShadowSamplerKinds(ShadowSamplerKinds kinds) {
        this.shadowSamplerKinds = kinds == null ? ShadowSamplerKinds.ALL_COMPARE : kinds;
    }

    // Binds the gbuffer framebuffer and per-pass state before drawing terrain
    @Override
    public void setupState(TerrainRenderPass pass) {
        this.primitiveType = pass.primitiveType() == QuadPrimitiveType.DIRECT
                ? GlPrimitiveType.QUADS : GlPrimitiveType.TRIANGLES;
        // Terrain draws into the frame pipeline's gbuffer; point its draw-buffer mask at this program's DRAWBUFFERS so iris_FragData[k] lands in the requested colortex (skipped in the shadow pass, where onTerrainDraw would rebind the gbuffer over the shadow framebuffer)
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        boolean shadowPass = UmbraShadowRenderer.isShadowPass();
        if (pipeline != null && !shadowPass) {
            boolean translucentPass = pass.isReverseOrder();
            if (translucentPass) {
                restoreOptifineWaterState();
            }
            pipeline.onTerrainDraw(this.drawBuffers, this.blendState, this.alphaTest, translucentPass);
            // After onTerrainDraw, which rebinds the comparison default on the shadow units; skipped in the shadow pass where those units hold the map being rendered and nothing samples them
            pipeline.applyShadowSamplerKinds(this.shadowSamplerKinds, true);
            this.restoreAfterDraw = true;
            this.activeDrawBufferSlots = this.drawBuffers.length;
        }
        if (pipeline != null) {
            pipeline.bindCustomImages();
        }
        // ShaderChunkRenderer.begin binds the program before setupState, so uniform uploads land on this program.
        if (this.uniforms != null) {
            this.uniforms.update();
        }
    }

    // Returns to the main framebuffer and default state
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

    // Undoes the blend and depth tweaks the water pass applied
    private static void restoreOptifineWaterState() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(true);
    }

    // Triangles, as the mesher emits
    @Override
    public GlPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    // Engine projection into the generated prologue's uniform
    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        if (this.uProjectionMatrix != null) {
            this.uProjectionMatrix.set(matrix);
        }
    }

    // Engine model-view into the generated prologue's uniform
    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        if (this.uModelViewMatrix != null) {
            this.uModelViewMatrix.set(matrix);
        }
    }

    // Region origin for the prologue's position decode
    @Override
    public void setRegionOffset(float x, float y, float z) {
        if (this.uRegionOffset != null) {
            this.uRegionOffset.set(x, y, z);
        }
    }

    // Block atlas or lightmap unit into the prologue's sampler
    @Override
    public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        GlUniformInt uniform = this.uTextures.get(slot);
        if (uniform != null) {
            uniform.setInt(val);
        }
    }
}
