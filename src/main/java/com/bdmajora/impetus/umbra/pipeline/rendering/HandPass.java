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

// The first-person hand passes: solid into the PRE-deferred gbuffer like OptiFine's renderHand0 (deferred packs light it there, a later hand stays unlit raw gbuffer data), translucent after translucent geometry like renderHand1
public final class HandPass {
    // MC_RENDER_STAGE_HAND_SOLID and MC_RENDER_STAGE_HAND_TRANSLUCENT
    public static final int SOLID_STAGE = 16;
    public static final int TRANSLUCENT_STAGE = 23;
    private static final int FULL_BRIGHT_LIGHTMAP = 0x00F000F0;
    private static final float LIGHTMAP_TEXTURE_SCALE = 1.0f / 256.0f;
    private static final float LIGHTMAP_TEXTURE_OFFSET = 8.0f / 256.0f;

    private final PipelineState state;
    private final PhaseController phases;
    private final BlockPos.MutableBlockPos eye = new BlockPos.MutableBlockPos();

    public HandPass(PipelineState state, PhaseController phases) {
        this.state = state;
        this.phases = phases;
    }

    // Binds the hand program and depth setup; true means call end(), and with no hand program it still renders fixed-function into colortex0
    public boolean begin(ProgramId programId, int renderStage) {
        if (this.state.destroyed || !this.state.active) {
            return false;
        }
        this.state.gbuffers.current.bind();
        LWJGL.glViewport(0, 0, this.state.targets.getWidth(), this.state.targets.getHeight());
        this.phases.resetDepthRange();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        // The hand draws into a populated depth buffer, and with the camera pressed into geometry even the squeezed projection can fail LEQUAL; it wins for this pass, restored in end()
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlState.resyncTextureUnitZero();
        GlState.resetVanillaVertexArrayState();
        GbufferPrograms.Entry entry = this.state.gbufferPrograms.get(programId);
        // This binds a gbuffer program without a phase switch, and the held item draws through RenderItem underneath, so the phase must name the hand program or the per-object refresh skips currentRenderedItemId
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

    // Restores state after the hand
    public void end() {
        LWJGL.glUseProgram(0);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        CapturedRenderingState.INSTANCE.setRenderStage(0);
    }

    // BSL's gbuffers_hand derives all brightness from gl_TextureMatrix[1] * gl_MultiTexCoord1, and Sodium can leave the coord stale, so the legacy texcoord and the bridge uniform get the player's combined light (Umbra's getPackedLightCoords)
    private static void setupHandLightmap(int packedLight) {
        LWJGL.glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, blockLight(packedLight), skyLight(packedLight));
        // Vanilla's lightmap texture matrix, which packs expect on unit 1
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.matrixMode(GlState.GL_TEXTURE_MODE);
        GlStateManager.loadIdentity();
        GlStateManager.translate(LIGHTMAP_TEXTURE_OFFSET, LIGHTMAP_TEXTURE_OFFSET, LIGHTMAP_TEXTURE_OFFSET);
        GlStateManager.scale(LIGHTMAP_TEXTURE_SCALE, LIGHTMAP_TEXTURE_SCALE, LIGHTMAP_TEXTURE_SCALE);
        GlStateManager.matrixMode(GlState.GL_MODELVIEW_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // Light at the player's eye, full bright with no world
    private int handPackedLight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return FULL_BRIGHT_LIGHTMAP;
        }
        this.eye.setPos(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
        return mc.world.getCombinedLight(this.eye, 0);
    }

    // Block light half of packed light as a lightmap coordinate
    static float blockLight(int packedLight) {
        return packedLight & 0xFFFF;
    }

    // Sky light half
    static float skyLight(int packedLight) {
        return (packedLight >>> 16) & 0xFFFF;
    }
}
