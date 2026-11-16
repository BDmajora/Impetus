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

// Binds the frame and pack textures of a pipeline to their units.
public final class TextureBinder {
    // Barriers that make image writes visible to the next pass.
    private static final int PACK_RESOURCE_BARRIERS = 0x00000020 | 0x00000008 | 0x00002000;

    // Color targets of the pack.
    private final UmbraRenderTargets targets;
    // Noise texture of the pack.
    private final NoiseTexture noise;
    // Flat normal map used without a resource pack normal map.
    private final PlainTexture defaultNormals;
    // Empty specular map used without a resource pack specular map.
    private final PlainTexture defaultSpecular;
    // Empty overlay texture.
    private final PlainTexture noOverlay;
    // Depth texture bound when the pack has no shadow pass.
    private final StubShadowMap stubShadowMap;
    // Custom textures of the pack.
    private final CustomTextureManager customTextures;
    // Custom images of the pack.
    private final CustomImageManager customImages;
    // Samplers of the shadow maps.
    private final ShadowSamplers shadowSamplers;
    // Source of the Distant Horizons bridge.
    private final Supplier<DhCompat> dhCompat;
    // Shadow renderer or null.
    private UmbraShadowRenderer shadowRenderer;
    // Released state of the binder.
    private boolean destroyed;

    // Creates the default textures and deletes them when one fails.
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

    // Stores the shadow renderer.
    public void setShadowRenderer(UmbraShadowRenderer shadowRenderer) {
        this.shadowRenderer = shadowRenderer;
    }

    // Binds a texture to a unit.
    static void bindUnit(int unit, int texture) {
        GlTextureUnits.bindTextureInRun(unit, GL11.GL_TEXTURE_2D, texture);
    }

    // Binds the textures that change each frame.
    public void bindFrameTextures() {
        bindNoise();
        bindUnit(SamplerLayout.OVERLAY_TEX_UNIT, this.noOverlay.getTextureId());
        bindUnit(SamplerLayout.GBUFFER_OVERLAY_UNIT, this.noOverlay.getTextureId());
        bindUnit(SamplerLayout.SHADOW_TEX_0_UNIT, this.stubShadowMap.getTextureId());
        bindUnit(SamplerLayout.SHADOW_TEX_1_UNIT, this.stubShadowMap.getTextureId());
        GlTextureUnits.resetToUnit0();
    }

    // Binds the noise texture.
    public void bindNoise() {
        int custom = this.customTextures.getNoiseTextureId();
        int texture = custom != -1 ? custom : this.noise.getTextureId();
        bindUnit(SamplerLayout.NOISE_TEX_UNIT, texture);
        bindUnit(SamplerLayout.GBUFFER_NOISE_TEX_UNIT, texture);
        GlTextureUnits.resetToUnit0();
    }

    // Binds the depth textures.
    public void bindDepth() {
        bindDepthUnit(SamplerLayout.DEPTH_TEX_0_UNIT, this.targets.getDepthTexture().getTextureId());
        bindDepthUnit(SamplerLayout.DEPTH_TEX_1_UNIT, this.targets.getDepthTextureNoTranslucents().getTextureId());
        bindDepthUnit(SamplerLayout.DEPTH_TEX_2_UNIT, this.targets.getDepthTextureNoHand().getTextureId());
        bindDepthUnit(SamplerLayout.GBUFFER_DEPTH_TEX_0_UNIT, this.targets.getDepthTexture().getTextureId());
        bindDepthUnit(SamplerLayout.GBUFFER_DEPTH_TEX_1_UNIT, this.targets.getDepthTextureNoTranslucents().getTextureId());
        DhCompat dh = this.dhCompat.get();
        int lodDepth = dh == null ? -1 : dh.getDepthTex();
        int lodDepthNoTranslucent = dh == null ? -1 : dh.getDepthTexNoTranslucent();
        bindDepthUnit(SamplerLayout.DH_DEPTH_TEX_0_UNIT, lodDepth > 0 ? lodDepth : this.targets.getDepthTexture().getTextureId());
        bindDepthUnit(SamplerLayout.DH_DEPTH_TEX_1_UNIT, lodDepthNoTranslucent > 0 ? lodDepthNoTranslucent
                : this.targets.getDepthTextureNoTranslucents().getTextureId());
        GlTextureUnits.resetToUnit0();
    }

    // Binds a depth texture to a unit.
    private static void bindDepthUnit(int unit, int texture) {
        LWJGL.glBindSampler(unit, 0);
        bindUnit(unit, texture);
    }

    // Binds the normal and specular atlases or the defaults.
    public void bindPbr() {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 2);
        GlStateManager.bindTexture(PBRAtlasManager.getNormalsAtlas(this.defaultNormals.getTextureId()));
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 3);
        GlStateManager.bindTexture(PBRAtlasManager.getSpecularAtlas(this.defaultSpecular.getTextureId()));
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // Binds the shadow maps and their samplers.
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

    // Binds a shadow depth texture to a unit.
    private static void bindShadowDepth(int unit, int texture, int sampler) {
        LWJGL.glBindSampler(unit, sampler);
        bindUnit(unit, texture);
    }

    // Binds the custom textures and images and makes earlier image writes visible.
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

    // Binds the color targets under the current flips.
    public static void bindColorSamplers(int[] samplers) {
        for (int i = samplers.length - 1; i >= 0; i--) {
            if (samplers[i] != 0) {
                LWJGL.glBindSampler(i, 0);
                bindUnit(i, samplers[i]);
            }
        }
        GlTextureUnits.resetToUnit0();
    }

    // Unbinds the pack units and restores the default unit.
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
        for (int i = UmbraRenderTargets.MAX_COLOR_BUFFERS - 1; i >= GlTextureUnits.CACHED_UNITS; i--) {
            LWJGL.glBindSampler(i, 0);
            GlTextureUnits.selectScratch(i);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        for (int i = GlTextureUnits.CACHED_UNITS - 1; i >= 0; i--) {
            LWJGL.glBindSampler(i, 0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // Deletes the default textures once and continues after a failure.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        try {
            this.defaultNormals.destroy();
        } finally {
            try {
                this.defaultSpecular.destroy();
            } finally {
                try {
                    this.noOverlay.destroy();
                } finally {
                    this.stubShadowMap.destroy();
                }
            }
        }
    }
}
