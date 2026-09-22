package com.bdmajora.impetus.umbra.material;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.NamespacedId;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkColorWriter;
import net.minecraft.util.BlockRenderLayer;
import java.util.HashMap;
import java.util.Map;

// Settings the pipeline publishes on construction and clears on destroy, read by chunk-build workers; a much smaller counterpart of Iris's WorldRenderingSettings
public final class WorldRenderingSettings {
    // block.properties mapping as a flat table indexed by global state id (blockId | meta << 12), holding the pack's id or -1 (Umbra parity for unmapped); null without a pack or block.properties, then the mesher uses raw block id/meta
    private static volatile int[] blockStateIds;
    // The pack's item/entity tables keyed the way vanilla hands the key over (ResourceLocation, equal by value), converted once at publish so the per-object lookups below allocate nothing
    private static volatile Map<ResourceLocation, Integer> itemIds;
    private static volatile Map<ResourceLocation, Integer> entityIds;
    private static volatile int voxelRenderDistanceChunks;

    private WorldRenderingSettings() {
    }

    // State id to pack id table, indexed by the raw state id
    public static int[] getBlockStateIds() {
        return blockStateIds;
    }

    // Published by the pipeline
    public static void setBlockStateIds(int[] table) {
        blockStateIds = table;
    }

    // Published by the pipeline
    public static void setItemIds(Map<NamespacedId, Integer> table) {
        itemIds = byResourceLocation(table);
    }

    // Published by the pipeline
    public static void setEntityIds(Map<NamespacedId, Integer> table) {
        entityIds = byResourceLocation(table);
    }

    // Re-keys a pack table by ResourceLocation; null stays null so the lookups keep their "no pack" path
    private static Map<ResourceLocation, Integer> byResourceLocation(Map<NamespacedId, Integer> table) {
        if (table == null) {
            return null;
        }
        Map<ResourceLocation, Integer> keyed = new HashMap<>(table.size() * 2);
        table.forEach((id, packId) -> keyed.put(new ResourceLocation(id.getNamespace(), id.getName()), packId));
        return keyed;
    }

    // dynamicHandLight: when false the pack doesn't want the held item to emit light, so heldBlockLightValue/Color report "nothing held".
    private static boolean dynamicHandLight = true;
    // separateAo: vanilla AO is fed as its own vertex channel instead of being baked into the light channel.
    private static boolean separateAo;
    // oldLighting keeps vanilla's fixed-function face shading (top 1.0, sides 0.8/0.6, bottom 0.5); packs lighting from the normal set it false to avoid double-shading (Body Camera). Volatile, read on chunk-build workers
    private static volatile boolean oldLighting = true;
    // oldHandLight (default true): when the offhand emits more light than the mainhand, heldItemId/heldBlockLightValue report the offhand, mirroring OptiFine's swap before uploading
    private static boolean oldHandLight = true;
    // voxelizeLightBlocks: emit geometry for light-emitting blocks so the shadow pass can voxelize them.
    private static boolean voxelizeLightBlocks;

    // Pack's oldHandLight directive
    public static boolean isOldHandLight() {
        return oldHandLight;
    }

    // Published by the pipeline
    public static void setOldHandLight(boolean value) {
        oldHandLight = value;
    }

    // layer.<rendertype> overrides from block.properties, block -> forced chunk render layer over canRenderInLayer; null when the pack declares none
    private static Map<Block, BlockRenderLayer> blockRenderLayers;

    public static void setBlockRenderLayers(
            Map<Block, BlockRenderLayer> table) {
        blockRenderLayers = table == null || table.isEmpty() ? null : table;
    }

    // The layer the pack forces for this block, or null to keep vanilla's; a fast null check since it runs for every block in every rebuild
    public static BlockRenderLayer getForcedRenderLayer(Block block) {
        Map<Block, BlockRenderLayer> table = blockRenderLayers;
        return table == null ? null : table.get(block);
    }

    // Whether emissive blocks are voxelised for the pack
    public static boolean isVoxelizeLightBlocks() {
        return voxelizeLightBlocks;
    }

    // Published by the pipeline
    public static void setVoxelizeLightBlocks(boolean value) {
        voxelizeLightBlocks = value;
    }

    // Pack's dynamicHandLight directive
    public static boolean isDynamicHandLight() {
        return dynamicHandLight;
    }

    // Published by the pipeline
    public static void setDynamicHandLight(boolean value) {
        dynamicHandLight = value;
    }

    // Published by the pipeline
    public static void setSeparateAo(boolean value) {
        separateAo = value;
        // The mesher bakes this into every chunk's vertex colour; selecting a pack already calls RenderGlobal.loadRenderers(), so the re-encoding rebuild is already scheduled
        ChunkColorWriter.SeparateAoState.set(value);
    }

    // Pack's oldLighting directive, which changes how the mesher writes light
    public static boolean isOldLighting() {
        return oldLighting;
    }

    // Whether vanilla's per-face directional shading must be suppressed (Umbra forces the shade lookup to UP, OptiFine sets its shade constants to 1.0; VintageDiffuseProvider is the site here); Impetus keeps OptiFine's true default since every pack here targets OptiFine
    public static boolean shouldDisableDirectionalShading() {
        return !oldLighting;
    }

    // Published by the pipeline
    public static void setOldLighting(boolean value) {
        // Baked into chunk vertex colour, so a change needs the rebuild that selecting a pack already schedules, same as setSeparateAo
        oldLighting = value;
    }

    // How much of vanilla's baked per-block AO to keep (1.0 vanilla, 0.0 removed so a pack supplies its own without double-darkening); volatile for chunk-build workers, and applyAmbientOcclusionLevel is the 1.12.2 equivalent of Umbra's shade rewrite
    private static volatile float ambientOcclusionLevel = 1.0f;

    // Pack's ambientOcclusionLevel, applied by the mesher
    public static float getAmbientOcclusionLevel() {
        return ambientOcclusionLevel;
    }

    // Published by the pipeline
    public static void setAmbientOcclusionLevel(float value) {
        // Terrain meshes bake AO into vertex colour, so a change needs the rebuild that selecting a pack already schedules
        ambientOcclusionLevel = Math.max(0.0f, Math.min(1.0f, value));
    }

    // Scales one block's vanilla AO by the pack's level (Umbra's 1.0 - level*(1.0-original)): level 1 passes through, level 0 flattens to fully unoccluded
    public static float applyAmbientOcclusionLevel(float vanillaAo) {
        float level = ambientOcclusionLevel;
        if (level == 1.0f) {
            return vanillaAo;
        }
        return 1.0f - level * (1.0f - vanillaAo);
    }

    // How far the voxel data extends
    public static int getVoxelRenderDistanceChunks() {
        return voxelRenderDistanceChunks;
    }

    // Published by the pipeline
    public static void setVoxelRenderDistanceChunks(int chunks) {
        voxelRenderDistanceChunks = Math.max(0, chunks);
    }

    // Pack id for a state, or the raw block id when the pack does not map it
    public static int getBlockStateId(IBlockState state) {
        if (state == null) {
            return -1;
        }
        int rawStateId = Block.getStateId(state) & 0xFFFF;
        int[] table = blockStateIds;
        if (table == null) {
            return Block.getIdFromBlock(state.getBlock());
        }
        return rawStateId < table.length ? table[rawStateId] : -1;
    }

    // Pack id for an item, or the raw item id
    public static int getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        Item item = stack.getItem();
        ResourceLocation key = item.getRegistryName();
        int rawId = Item.getIdFromItem(item);
        return mappedOrRaw(itemIds, key, rawId);
    }

    // Pack id for an entity, or the raw entity id
    public static int getEntityId(Entity entity) {
        if (entity == null) {
            return -1;
        }

        ResourceLocation key = EntityList.getKey(entity);
        int rawId = EntityList.getID(entity.getClass());
        return mappedOrRaw(entityIds, key, rawId);
    }

    // Pack id for a tile entity via its registry key
    public static int getBlockEntityId(TileEntity tileEntity) {
        if (tileEntity == null) {
            return -1;
        }

        ResourceLocation key = TileEntity.getKey(tileEntity.getClass());
        return mappedOrRaw(entityIds, key, -1);
    }

    // Shared lookup: mapped id if present, else the raw one
    private static int mappedOrRaw(Map<ResourceLocation, Integer> map, ResourceLocation key, int rawId) {
        if (map == null || map.isEmpty()) {
            return rawId;
        }
        if (key == null) {
            return -1;
        }
        Integer mapped = map.get(key);
        return mapped != null ? mapped : -1;
    }
}
