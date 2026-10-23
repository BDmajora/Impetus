package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.features.FeatureFlags;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowColorSettings;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Builds the shadow renderer when the pack declares a shadow program, with OptiFine's shadow projection directives read from anywhere in the sources (packs put them in includes flattened into every program)
public final class ShadowSettings {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // OptiFine's pre-const spellings (`#define SHADOWRES 2048`); Umbra accepts both and shaders.properties overrides either
    private static final Pattern SHADOWRES = Pattern.compile("(?m)^\\s*#define\\s+SHADOWRES\\s+(\\d+)");
    private static final Pattern SHADOWHPL = Pattern.compile("(?m)^\\s*#define\\s+SHADOWHPL\\s+([0-9.]+)");
    private static final Pattern SHADOWFOV = Pattern.compile("(?m)^\\s*#define\\s+SHADOWFOV\\s+([0-9.]+)");
    private static final ProgramId[] SCANNED = {ProgramId.Shadow, ProgramId.Terrain, ProgramId.Water, ProgramId.Final};
    public static final int DEFAULT_RESOLUTION = 1024;
    public static final float DEFAULT_DISTANCE = 160.0f;

    private ShadowSettings() {
    }

    // null when the pack draws no shadow map or the renderer failed to build; the sun path tilt is set either way, since the celestial uniforms need it without shadows too
    public static UmbraShadowRenderer createRenderer(ShaderPack pack, PackSources sources, ShadowSamplers samplers,
                                                     Map<String, String> defines, Map<String, Integer> overrideUnits,
                                                     Runnable resourceRestorer) {
        List<String> raw = new ArrayList<>();
        List<String> active = new ArrayList<>();
        for (ProgramId id : SCANNED) {
            pack.getProgramSet().get(id).ifPresent(source -> {
                add(raw, active, source.getVertexSource(), sources.activeVertex(source));
                add(raw, active, source.getFragmentSource(), sources.activeFragment(source));
            });
        }
        for (ProgramSource source : sources.fullscreen()) {
            add(raw, active, source.getFragmentSource(), sources.activeFragment(source));
        }
        // Consts come from the active text: Complementary declares shadowMapResolution 4096 under one branch and 2048 under #else, and the wrong size sent every texelFetch light shaft into one quadrant of cleared depth
        ConstDirectives consts = new ConstDirectives(active);
        float sunPathRotation = consts.getFloat("sunPathRotation", 0.0f);
        CelestialUniforms.setSunPathRotation(sunPathRotation);

        Optional<ProgramSource> shadowSource = pack.getProgramSet().get(ProgramId.Shadow);
        if (shadowSource.isEmpty()) {
            return null;
        }
        ShaderProperties properties = pack.getProperties();
        int resolution = properties.getShadowMapResolution()
                .orElse(consts.getInt("shadowMapResolution", defineInt(raw, SHADOWRES, DEFAULT_RESOLUTION)));
        resolution = validResolution(resolution);
        float distance = properties.getShadowDistance().isPresent() ? properties.getShadowDistance().getAsInt()
                : consts.getFloat("shadowDistance", defineFloat(raw, SHADOWHPL, DEFAULT_DISTANCE));
        // With Distant Horizons LODs casting into the map and no declared planes, the depth range spans the LOD distance like Iris's -1 "auto" planes, or a mountain LOD a few hundred blocks along the light is clipped out
        boolean dhShadows = DhCompat.hasRenderingEnabled()
                && pack.getProgramSet().get(ProgramId.DhShadow).isPresent()
                && properties.getDhShadowEnabled().orElse(Boolean.TRUE);
        float nearPlane = consts.getFloat("shadowNearPlane",
                dhShadows ? -DhCompat.getRenderDistance() : UmbraShadowRenderer.DEFAULT_NEAR_PLANE);
        float farPlane = consts.getFloat("shadowFarPlane",
                dhShadows ? DhCompat.getRenderDistance() : UmbraShadowRenderer.DEFAULT_FAR_PLANE);
        float intervalSize = consts.getFloat("shadowIntervalSize", UmbraShadowRenderer.DEFAULT_INTERVAL_SIZE);
        Float fov = consts.getFloat("shadowMapFov");
        if (fov == null) {
            fov = defineFloat(raw, SHADOWFOV, null);
        }
        // `const float voxelDistance` overrides the shadow distance for voxelization only, since colored-lighting packs want a tighter radius than their shadow map (Umbra PackShadowDirectives)
        float voxelDistance = consts.getFloat("voxelDistance", 0.0f);
        // `shadowDistanceRenderMul` scales the shadow pass's CULLING distance, not the projection; unset or negative means no scaling
        float renderMul = consts.getFloat("shadowDistanceRenderMul", -1.0f);
        float cullDistance = renderMul >= 0.0f ? distance * renderMul : distance;
        float voxelRadius = voxelDistance > 0.0f ? voxelDistance : distance;
        WorldRenderingSettings.setVoxelRenderDistanceChunks(Math.max(1, Math.round(voxelRadius / 16.0f)));
        samplers.parse(consts);
        // The fixed-function shadow program (entities/block entities) belongs to the gbuffers custom-texture stage
        Map<String, Integer> samplerUnits = new LinkedHashMap<>(SamplerLayout.gbuffer());
        samplerUnits.putAll(overrideUnits);
        try {
            ShadowContentSettings content = ShadowContentSettings.from(properties);
            // Umbra's voxelization detection: a shadow geometry stage, or the pack declaring custom images
            boolean packVoxelizes = shadowSource.get().getGeometrySource().isPresent()
                    || !properties.getUmbraCustomImages().isEmpty();
            // Both resolve through the ProgramSet fallback chain, so a pack shipping only `shadow` hands the renderer one ProgramSource three times and it compiles once
            return new UmbraShadowRenderer(resolution, distance, nearPlane, farPlane, intervalSize, fov, sunPathRotation,
                    shadowSource.get(),
                    pack.getProgramSet().get(ProgramId.ShadowEntities).orElse(shadowSource.get()),
                    pack.getProgramSet().get(ProgramId.ShadowBlock).orElse(shadowSource.get()),
                    samplerUnits, defines, samplers.hardwareFiltering(), samplers.mipmap(), samplers.nearest(),
                    samplers.separateHardwareSamplers(), resourceRestorer, content, voxelDistance, cullDistance,
                    packVoxelizes, ShadowColorSettings.parse(consts, colorBufferCount(pack)),
                    usedColorBuffers(pack, sources));
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to create the shadow renderer; shadows disabled", e);
            return null;
        }
    }

    private static void add(List<String> raw, List<String> active, Optional<String> stage, String resolved) {
        if (stage.isPresent()) {
            raw.add(stage.get());
            active.add(resolved);
        }
    }

    // A non-positive size cannot be allocated (E-LITE declares 10 in its shadows-off branch, which conditional resolution hides), and one past the driver's limit fails allocation; checked after shaders.properties overrides, which used to skip the check
    static int validResolution(int resolution) {
        if (resolution <= 0) {
            LOGGER.warn("[Umbra] Pack declares shadowMapResolution={}; falling back to {}", resolution, DEFAULT_RESOLUTION);
            return DEFAULT_RESOLUTION;
        }
        int limit = LWJGL.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (limit > 0 && resolution > limit) {
            LOGGER.warn("[Umbra] Pack declares shadowMapResolution={}, past this GPU's {}; clamping", resolution, limit);
            return limit;
        }
        return resolution;
    }

    // The first `#define NAME <int>` across the stages in order; a value too long for an int keeps the fallback rather than failing the whole pipeline
    static int defineInt(List<String> stages, Pattern pattern, int fallback) {
        for (String stage : stages) {
            Matcher matcher = pattern.matcher(stage);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    // The first `#define NAME <float>` across the stages in order, fallback when absent or malformed (1.2.3)
    static Float defineFloat(List<String> stages, Pattern pattern, Float fallback) {
        for (String stage : stages) {
            Matcher matcher = pattern.matcher(stage);
            if (matcher.find()) {
                try {
                    return Float.parseFloat(matcher.group(1));
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    // Two shadowcolor buffers like OptiFine, or Iris's eight for a pack declaring HIGHER_SHADOWCOLOR
    static int colorBufferCount(ShaderPack pack) {
        return pack.hasFeature(FeatureFlags.HIGHER_SHADOWCOLOR) ? ShadowColorSettings.IRIS_BUFFERS : ShadowColorSettings.OPTIFINE_BUFFERS;
    }

    // Every shadowcolor index the pack touches: sampled or bound as an image by name anywhere, or written by the shadow program's or a shadowcomp's DRAWBUFFERS
    static BitSet usedColorBuffers(ShaderPack pack, PackSources sources) {
        BitSet used = (BitSet) sources.shadowColorReferences().clone();
        List<ProgramSource> writers = new ArrayList<>();
        pack.getProgramSet().get(ProgramId.Shadow).ifPresent(writers::add);
        for (int i = 0; i < ProgramArrayId.ShadowComposite.getNumPrograms(); i++) {
            pack.getProgramSet().get(ProgramArrayId.ShadowComposite, i).ifPresent(writers::add);
        }
        for (ProgramSource writer : writers) {
            writer.getFragmentSource().ifPresent(fragment -> {
                for (int buffer : DrawBuffers.parseActive(fragment, sources.macros(writer))) {
                    used.set(buffer);
                }
            });
        }
        return used;
    }
}
