package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.texture.PlainTexture;
import com.bdmajora.impetus.umbra.gl.texture.StubShadowMap;
import com.bdmajora.impetus.umbra.pbr.PBRAtlasManager;
import com.bdmajora.impetus.umbra.pipeline.CustomImageManager;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowColorSettings;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The textures that ride along the frame on fixed units (depth, shadow, noise, overlay, PBR fallbacks, the pack's custom textures and images) and the end-of-frame unit cleanup that hands vanilla a coherent GlStateManager cache
public final class TextureBinder {
    // Pack image writes must be visible to later fetches, framebuffer reads and texture updates
    private static final int PACK_RESOURCE_BARRIERS = 0x00000020 | 0x00000008 | 0x00002000;

    private final UmbraRenderTargets targets;
    private final NoiseTexture noise;
    // Fallback PBR inputs for the gbuffer stage: flat up-normal and black specular (OptiFine's defaults)
    private final PlainTexture defaultNormals;
    private final PlainTexture defaultSpecular;
    // Transparent, so a pack doing `mix(color, overlay.rgb, overlay.a)` gets its colour back unchanged
    private final PlainTexture noOverlay;
    // "Always lit" 1x1 on the shadowtex units when the pack has no shadow pass
    private final StubShadowMap stubShadowMap;
    private final CustomTextureManager customTextures;
    private final CustomImageManager customImages;
    private final ShadowSamplers shadowSamplers;
    private final Supplier<DhCompat> dhCompat;
    private UmbraShadowRenderer shadowRenderer;
    private boolean destroyed;

    public TextureBinder(UmbraRenderTargets targets, NoiseTexture noise, CustomTextureManager customTextures,
                         CustomImageManager customImages, ShadowSamplers shadowSamplers, Supplier<DhCompat> dhCompat) {
        this.targets = targets;
        this.noise = noise;
        this.customTextures = customTextures;
        this.customImages = customImages;
        this.shadowSamplers = shadowSamplers;
        this.dhCompat = dhCompat;
        PlainTexture normals = null;
        PlainTexture specular = null;
        PlainTexture overlay = null;
        try {
            normals = new PlainTexture(127, 127, 255, 255);
            specular = new PlainTexture(0, 0, 0, 0);
            overlay = new PlainTexture(0, 0, 0, 0);
            this.stubShadowMap = new StubShadowMap();
        } catch (RuntimeException e) {
            // The caller never gets this object to destroy, so free what was made before failing
            for (PlainTexture made : new PlainTexture[]{normals, specular, overlay}) {
                if (made != null) {
                    made.destroy();
                }
            }
            throw e;
        }
        this.defaultNormals = normals;
        this.defaultSpecular = specular;
        this.noOverlay = overlay;
    }

    // The shadow pass, once built; null keeps the stub on the shadow units
    public void setShadowRenderer(UmbraShadowRenderer shadowRenderer) {
        this.shadowRenderer = shadowRenderer;
    }

    // Binds one 2D texture leaving the selector on that unit; callers batch several and reset once, which is load-bearing for units past the cached range
    static void bindUnit(int unit, int texture) {
        GlTextureUnits.bindTextureInRun(unit, GL11.GL_TEXTURE_2D, texture);
    }

    // noisetex, the overlay dummy and the stub shadow maps for the whole frame, on units vanilla never binds
    public void bindFrameTextures() {
        bindNoise();
        bindUnit(SamplerLayout.OVERLAY_TEX_UNIT, this.noOverlay.getTextureId());
        bindUnit(SamplerLayout.GBUFFER_OVERLAY_UNIT, this.noOverlay.getTextureId());
        bindUnit(SamplerLayout.SHADOW_TEX_0_UNIT, this.stubShadowMap.getTextureId());
        bindUnit(SamplerLayout.SHADOW_TEX_1_UNIT, this.stubShadowMap.getTextureId());
        GlTextureUnits.resetToUnit0();
    }

    // noisetex; a pack-supplied texture.noise replaces the generated noise (Umbra parity)
    public void bindNoise() {
        int custom = this.customTextures.getNoiseTextureId();
        int texture = custom != -1 ? custom : this.noise.getTextureId();
        bindUnit(SamplerLayout.NOISE_TEX_UNIT, texture);
        bindUnit(SamplerLayout.GBUFFER_NOISE_TEX_UNIT, texture);
        GlTextureUnits.resetToUnit0();
    }

    // depthtex0/1/2 on the fullscreen units and the OptiFine 1.12 gbuffer ones (6/12), plus DH's LOD depth
    public void bindDepth() {
        bindDepthUnit(SamplerLayout.DEPTH_TEX_0_UNIT, this.targets.getDepthTexture().getTextureId());
        bindDepthUnit(SamplerLayout.DEPTH_TEX_1_UNIT, this.targets.getDepthTextureNoTranslucents().getTextureId());
        bindDepthUnit(SamplerLayout.DEPTH_TEX_2_UNIT, this.targets.getDepthTextureNoHand().getTextureId());
        bindDepthUnit(SamplerLayout.GBUFFER_DEPTH_TEX_0_UNIT, this.targets.getDepthTexture().getTextureId());
        bindDepthUnit(SamplerLayout.GBUFFER_DEPTH_TEX_1_UNIT, this.targets.getDepthTextureNoTranslucents().getTextureId());
        // dhDepthTex0/1: DH's LOD depth once it has rendered a frame, else the terrain depth so a DH-aware pack without DH reads sane depth
        DhCompat dh = this.dhCompat.get();
        int lodDepth = dh == null ? -1 : dh.getDepthTex();
        int lodDepthNoTranslucent = dh == null ? -1 : dh.getDepthTexNoTranslucent();
        bindDepthUnit(SamplerLayout.DH_DEPTH_TEX_0_UNIT, lodDepth > 0 ? lodDepth : this.targets.getDepthTexture().getTextureId());
        bindDepthUnit(SamplerLayout.DH_DEPTH_TEX_1_UNIT, lodDepthNoTranslucent > 0 ? lodDepthNoTranslucent
                : this.targets.getDepthTextureNoTranslucents().getTextureId());
        GlTextureUnits.resetToUnit0();
    }

    private static void bindDepthUnit(int unit, int texture) {
        LWJGL.glBindSampler(unit, 0);
        bindUnit(unit, texture);
    }

    // PBR maps on the gbuffer-stage normals/specular units 2/3 through GlStateManager; fullscreen passes overwrite them with colortex2/3, so water and hand need them back
    public void bindPbr() {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 2);
        GlStateManager.bindTexture(PBRAtlasManager.getNormalsAtlas(this.defaultNormals.getTextureId()));
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 3);
        GlStateManager.bindTexture(PBRAtlasManager.getSpecularAtlas(this.defaultSpecular.getTextureId()));
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // shadowtex and shadowcolor, or the stub without a shadow pass; OptiFine 1.12 packs declare sampler2DShadow shadowtex0/1 directly, so the plain names get the comparison sampler and only SEPARATE_HARDWARE_SAMPLERS packs expect raw depth there with comparison on the *HW aliases (a plain-sampler2D program gets the raw flavour from ShadowSamplers.apply)
    public void bindShadow() {
        UmbraShadowRenderer shadow = this.shadowRenderer;
        int depth0 = shadow == null ? this.stubShadowMap.getTextureId() : shadow.getDepthTextureId();
        int depth1 = shadow == null ? this.stubShadowMap.getTextureId() : shadow.getDepthTextureNoTranslucentsId();
        int compare0 = this.shadowSamplers.compareFor(0);
        int compare1 = this.shadowSamplers.compareFor(1);
        boolean separate = this.shadowSamplers.separateHardwareSamplers();
        int plain0 = separate ? 0 : compare0;
        int plain1 = separate ? 0 : compare1;
        bindShadowDepth(SamplerLayout.SHADOW_TEX_0_UNIT, depth0, plain0);
        bindShadowDepth(SamplerLayout.SHADOW_TEX_1_UNIT, depth1, plain1);
        // The *HW aliases exist only for SEPARATE_HARDWARE_SAMPLERS packs; holding two units for everyone else starved Complementary's wsr samplers onto unit 0
        if (separate) {
            bindShadowDepth(SamplerLayout.SHADOW_TEX_0_HW_UNIT, depth0, compare0);
            bindShadowDepth(SamplerLayout.SHADOW_TEX_1_HW_UNIT, depth1, compare1);
        }
        bindShadowDepth(SamplerLayout.GBUFFER_SHADOW_TEX_0_UNIT, depth0, plain0);
        bindShadowDepth(SamplerLayout.GBUFFER_SHADOW_TEX_1_UNIT, depth1, plain1);
        int color0 = shadow == null ? 0 : shadow.getColorTextureId(0);
        int color1 = shadow == null ? 0 : shadow.getColorTextureId(1);
        bindUnit(SamplerLayout.SHADOW_COLOR_0_UNIT, color0);
        bindUnit(SamplerLayout.SHADOW_COLOR_1_UNIT, color1);
        bindUnit(SamplerLayout.GBUFFER_SHADOW_COLOR_0_UNIT, color0);
        bindUnit(SamplerLayout.GBUFFER_SHADOW_COLOR_1_UNIT, color1);
        for (int index = ShadowColorSettings.OPTIFINE_BUFFERS; index < ShadowColorSettings.IRIS_BUFFERS; index++) {
            bindUnit(SamplerLayout.shadowColorUnit(index), shadow == null ? 0 : shadow.getColorTextureId(index));
        }
        GlTextureUnits.resetToUnit0();
    }

    private static void bindShadowDepth(int unit, int texture, int sampler) {
        LWJGL.glBindSampler(unit, sampler);
        bindUnit(unit, texture);
    }

    // The pack's custom textures and images; Umbra binds images at every program use, and the barrier makes the previous program's image writes visible
    public void bindPackResources() {
        if (this.destroyed) {
            return;
        }
        if (!this.customImages.isEmpty()) {
            LWJGL.glMemoryBarrier(PACK_RESOURCE_BARRIERS);
        }
        this.customTextures.bindAll();
        this.customImages.bindAll();
    }

    // Binds each colortex a fullscreen pass samples; units 0..7 through GlStateManager so vanilla's cache stays coherent, colortex8..15 raw since the cache cannot index them and vanilla never touches those units
    public static void bindColorSamplers(int[] samplers) {
        for (int i = samplers.length - 1; i >= 0; i--) {
            if (samplers[i] != 0) {
                LWJGL.glBindSampler(i, 0);
                bindUnit(i, samplers[i]);
            }
        }
        // Not a bare setActiveTexture(GL_TEXTURE0): after a raw bind real GL is on a high unit while the cache reads 0, so the cached call would no-op and strand the selector
        GlTextureUnits.resetToUnit0();
    }

    // Unbinds every unit the frame used and returns to unit 0 with the cache agreeing with real GL
    public void restoreUnits() {
        this.customTextures.unbindAll();
        this.customImages.unbindAll();
        for (int unit = SamplerLayout.DEPTH_TEX_0_UNIT; unit <= SamplerLayout.DEPTH_TEX_2_UNIT; unit++) {
            LWJGL.glBindSampler(unit, 0);
            bindUnit(unit, 0);
        }
        for (int unit : SamplerLayout.FRAME_UNITS) {
            LWJGL.glBindSampler(unit, 0);
            bindUnit(unit, 0);
        }
        // colortex8..15 sit beyond GlStateManager's 8-slot cache, so they are unbound raw; units 0..7 go through it
        for (int i = UmbraRenderTargets.MAX_COLOR_BUFFERS - 1; i >= GlTextureUnits.CACHED_UNITS; i--) {
            LWJGL.glBindSampler(i, 0);
            GlTextureUnits.selectScratch(i);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        // Step through unit 1 so the active-unit cache agrees with real GL before touching 0..7; a bare glActiveTexture desynced it and the screenshot path's cached bind no-op'd forever, a white screen until reload
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        for (int i = GlTextureUnits.CACHED_UNITS - 1; i >= 0; i--) {
            LWJGL.glBindSampler(i, 0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // Frees the fallback textures; the noise texture and the managers belong to the pipeline state
    public void destroy() {
        this.destroyed = true;
        this.defaultNormals.destroy();
        this.defaultSpecular.destroy();
        this.noOverlay.destroy();
        this.stubShadowMap.destroy();
    }
}
