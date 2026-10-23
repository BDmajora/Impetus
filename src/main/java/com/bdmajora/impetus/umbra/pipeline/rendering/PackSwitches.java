package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.BlockMaterialMapping;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;

// The pack's shaders.properties switches the frame consults, plus the world-rendering settings it publishes for the chunk meshers
public final class PackSwitches {
    // backFace.<layer> keys in BlockRenderLayer order on 1.12.2: SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT
    private static final String[] BACK_FACE_KEYS = {"solid", "cutoutMipped", "cutout", "translucent"};

    // prepareBeforeShadow: the prepare family runs before the shadow map, for packs whose shadow pass samples what prepare produces
    public final boolean prepareBeforeShadow;
    // allowConcurrentCompute: no full barrier between consecutive dispatches, only safe when the pack says they are independent
    public final boolean allowConcurrentCompute;
    // rain.depth / beacon.beam.depth: whether those draws write the depth buffer
    public final boolean rainDepth;
    public final boolean beaconBeamDepth;
    // frustum.culling / occlusion.culling, both on by default
    public final boolean frustumCulling;
    public final boolean occlusionCulling;
    // skipAllRendering: no world geometry at all, only the composite chain
    public final boolean skipAllRendering;
    // separateEntityDraws: entities render in their own pass after the deferred chain
    public final boolean separateEntityDraws;
    // particles.ordering = mixed | after | before, relative to the deferred chain
    public final String particleOrdering;
    // Vanilla culls every layer; a pack shading both sides or reading geometry from the light's side keeps back faces
    private final boolean[] backFaceCulling = new boolean[BACK_FACE_KEYS.length];

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
        // Umbra's order: an explicit directive wins, else a pack with a deferred chain and no separate entity draws gets `after` (particles not lit by deferred), else `mixed`; any deferred slot counts, a chain starting at deferred1 included
        this.particleOrdering = properties.getParticleOrdering()
                .orElseGet(() -> hasDeferredChain(pack) && !this.separateEntityDraws ? "after" : "mixed");
        for (int i = 0; i < BACK_FACE_KEYS.length; i++) {
            this.backFaceCulling[i] = properties.getBackFaceCulling(BACK_FACE_KEYS[i]).orElse(Boolean.TRUE);
        }
    }

    private static boolean hasDeferredChain(ShaderPack pack) {
        for (int i = 0; i < ProgramArrayId.Deferred.getNumPrograms(); i++) {
            if (pack.getProgramSet().get(ProgramArrayId.Deferred, i).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // false when the pack wants that terrain layer's back faces drawn; an ordinal outside the four layers culls as vanilla does
    public boolean cullsBackFaces(int layerOrdinal) {
        return layerOrdinal < 0 || layerOrdinal >= this.backFaceCulling.length || this.backFaceCulling[layerOrdinal];
    }

    // Publishes the pack's block.properties mapping (null keeps raw 1.12.2 ids) and its lighting switches; registry resolution needs the game fully initialised, so this runs at pipeline build
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

    // Vanilla's values once no pack renders; oldLighting goes back to TRUE, its default, since false left vanilla terrain without face shading after shaders were turned off
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
