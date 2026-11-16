package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Pass that draws the held items with the hand programs and the light at the eye.
public final class HandPass {
    // Render stage of the solid hand.
    public static final int SOLID_STAGE = 16;
    // Render stage of the translucent hand.
    public static final int TRANSLUCENT_STAGE = 23;
    // Packed light used without a world.
    private static final int FULL_BRIGHT_LIGHTMAP = 0x00F000F0;
    // Scale of the lightmap texture matrix.
    private static final float LIGHTMAP_TEXTURE_SCALE = 1.0f / 256.0f;
    // Offset of the lightmap texture matrix.
    private static final float LIGHTMAP_TEXTURE_OFFSET = 8.0f / 256.0f;

    // Resources of the pack.
    private final PipelineState state;
    // Program phase selection.
    private final PhaseController phases;
    // Scratch of the eye position.
    private final BlockPos.MutableBlockPos eye = new BlockPos.MutableBlockPos();

    // Creates the hand pass of a pipeline.
    public HandPass(PipelineState state, PhaseController phases) {
        this.state = state;
        this.phases = phases;
    }

    // Prepares the gbuffer and lightmap and binds the hand program and returns whether the pass runs.
    public boolean begin(ProgramId programId, int renderStage) {
        if (this.state.destroyed || !this.state.active) {
            return false;
        }
        this.state.gbuffers.current.bind();
        LWJGL.glViewport(0, 0, this.state.targets.getWidth(), this.state.targets.getHeight());
        this.phases.resetDepthRange();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlState.resyncTextureUnitZero();
        GlState.resetVanillaVertexArrayState();
        GbufferPrograms.Entry entry = this.state.gbufferPrograms.get(programId);
        this.phases.current = programId;
        int packedLight = handPackedLight();
        setupHandLightmap(packedLight);
        this.state.binder.bindPbr();
        CapturedRenderingState.INSTANCE.setRenderStage(renderStage);
        if (entry == null) {
            LWJGL.glUseProgram(0);
            this.state.gbuffers.drawBuffers(this.state.gbuffers.current, GbufferTargets.FIXED_FUNCTION_MASK);
            GlState.resyncTextureUnitZero();
            return true;
        }
        entry.getProgram().bind();
        int[] drawBuffers = entry.drawBuffersReadOnly();
        this.state.gbuffers.drawBuffers(this.state.gbuffers.current, drawBuffers);
        entry.getBlendState().apply(drawBuffers);
        entry.setHandLightmap(blockLight(packedLight), skyLight(packedLight));
        this.state.binder.bindPackResources();
        entry.getUniforms().update();
        this.state.shadowSamplers.apply(entry.getShadowSamplerKinds(), true);
        GlState.resyncTextureUnitZero();
        return true;
    }

    // Unbinds the program and restores the depth test and render stage.
    public void end() {
        LWJGL.glUseProgram(0);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        CapturedRenderingState.INSTANCE.setRenderStage(0);
    }

    // Sets the lightmap coordinate and texture matrix of the hand.
    private static void setupHandLightmap(int packedLight) {
        LWJGL.glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, blockLight(packedLight), skyLight(packedLight));
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.matrixMode(GlState.GL_TEXTURE_MODE);
        GlStateManager.loadIdentity();
        GlStateManager.translate(LIGHTMAP_TEXTURE_OFFSET, LIGHTMAP_TEXTURE_OFFSET, LIGHTMAP_TEXTURE_OFFSET);
        GlStateManager.scale(LIGHTMAP_TEXTURE_SCALE, LIGHTMAP_TEXTURE_SCALE, LIGHTMAP_TEXTURE_SCALE);
        GlStateManager.matrixMode(GlState.GL_MODELVIEW_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // Returns the packed light at the eye or full bright without a world.
    private int handPackedLight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return FULL_BRIGHT_LIGHTMAP;
        }
        this.eye.setPos(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
        return mc.world.getCombinedLight(this.eye, 0);
    }

    // Returns the block light of a packed light.
    static float blockLight(int packedLight) {
        return packedLight & 0xFFFF;
    }

    // Returns the sky light of a packed light.
    static float skyLight(int packedLight) {
        return (packedLight >>> 16) & 0xFFFF;
    }
}
