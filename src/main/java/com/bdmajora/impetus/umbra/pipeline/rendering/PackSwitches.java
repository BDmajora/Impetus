package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.BlockMaterialMapping;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;

// Switches read from the pack properties.
public final class PackSwitches {
    // Back face keys.
    private static final String[] BACK_FACE_KEYS = {"solid", "cutoutMipped", "cutout", "translucent"};

    // Prepare before shadow.
    public final boolean prepareBeforeShadow;
    // Allow concurrent compute.
    public final boolean allowConcurrentCompute;
    // Rain depth.
    public final boolean rainDepth;
    // Beacon beam depth.
    public final boolean beaconBeamDepth;
    // Frustum culling.
    public final boolean frustumCulling;
    // Occlusion culling.
    public final boolean occlusionCulling;
    // Skip all rendering.
    public final boolean skipAllRendering;
    // Separate entity draws.
    public final boolean separateEntityDraws;
    // Particle ordering.
    public final String particleOrdering;
    // Back face culling.
    private final boolean[] backFaceCulling = new boolean[BACK_FACE_KEYS.length];

    // Creates a pack switches from its pack.
    public PackSwitches(ShaderPack pack) {
        ShaderProperties properties = pack.getProperties();
        this.prepareBeforeShadow = properties.getPrepareBeforeShadow().orElse(Boolean.FALSE);
        this.allowConcurrentCompute = properties.getAllowConcurrentCompute().orElse(Boolean.FALSE);
        this.rainDepth = properties.getRainDepth().orElse(Boolean.FALSE);
        this.beaconBeamDepth = properties.getBeaconBeamDepth().orElse(Boolean.FALSE);
        this.frustumCulling = properties.getFrustumCulling().orElse(Boolean.TRUE);
        this.occlusionCulling = properties.getOcclusionCulling().orElse(Boolean.TRUE);
        this.skipAllRendering = properties.getSkipAllRendering().orElse(Boolean.FALSE);
        this.separateEntityDraws = properties.getSeparateEntityDraws().orElse(Boolean.FALSE);
        this.particleOrdering = properties.getParticleOrdering()
                .orElseGet(() -> hasDeferredChain(pack) && !this.separateEntityDraws ? "after" : "mixed");
        for (int i = 0; i < BACK_FACE_KEYS.length; i++) {
            this.backFaceCulling[i] = properties.getBackFaceCulling(BACK_FACE_KEYS[i]).orElse(Boolean.TRUE);
        }
    }

    // Returns whether the pack has deferred passes.
    private static boolean hasDeferredChain(ShaderPack pack) {
        for (int i = 0; i < ProgramArrayId.Deferred.getNumPrograms(); i++) {
            if (pack.getProgramSet().get(ProgramArrayId.Deferred, i).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // Returns whether a block layer culls back faces.
    public boolean cullsBackFaces(int layerOrdinal) {
        return layerOrdinal < 0 || layerOrdinal >= this.backFaceCulling.length || this.backFaceCulling[layerOrdinal];
    }

    // Publishes the world settings of a pack.
    public static void publishWorldSettings(ShaderPack pack, int renderDistanceChunks) {
        ShaderProperties properties = pack.getProperties();
        WorldRenderingSettings.setBlockStateIds(BlockMaterialMapping.createBlockStateIdTable(pack.getIdMap()));
        WorldRenderingSettings.setBlockRenderLayers(BlockMaterialMapping.createBlockRenderLayerTable(pack.getIdMap()));
        WorldRenderingSettings.setItemIds(pack.getIdMap().getItemIdMap());
        WorldRenderingSettings.setEntityIds(pack.getIdMap().getEntityIdMap());
        WorldRenderingSettings.setVoxelRenderDistanceChunks(renderDistanceChunks);
        WorldRenderingSettings.setDynamicHandLight(properties.getDynamicHandLight().orElse(Boolean.TRUE));
        WorldRenderingSettings.setSeparateAo(properties.getSeparateAo().orElse(Boolean.FALSE));
        WorldRenderingSettings.setOldLighting(properties.getOldLighting().orElse(Boolean.TRUE));
        WorldRenderingSettings.setOldHandLight(properties.getOldHandLight().orElse(Boolean.TRUE));
        WorldRenderingSettings.setVoxelizeLightBlocks(properties.getVoxelizeLightBlocks().orElse(Boolean.FALSE));
    }

    // Restores the default world settings.
    public static void resetWorldSettings() {
        WorldRenderingSettings.setBlockStateIds(null);
        WorldRenderingSettings.setBlockRenderLayers(null);
        WorldRenderingSettings.setItemIds(null);
        WorldRenderingSettings.setEntityIds(null);
        WorldRenderingSettings.setVoxelRenderDistanceChunks(0);
        WorldRenderingSettings.setDynamicHandLight(true);
        WorldRenderingSettings.setSeparateAo(false);
        WorldRenderingSettings.setOldLighting(true);
        WorldRenderingSettings.setOldHandLight(true);
        WorldRenderingSettings.setVoxelizeLightBlocks(false);
    }
}
