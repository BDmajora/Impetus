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

// Settings of the world rendering group.
public final class WorldRenderingSettings {
    // Choice of the block state ids option.
    private static volatile int[] blockStateIds;
    private static volatile Map<ResourceLocation, Integer> itemIds;
    private static volatile Map<ResourceLocation, Integer> entityIds;
    // Choice of the voxel render distance chunks option.
    private static volatile int voxelRenderDistanceChunks;

    // Prevents instantiation
    private WorldRenderingSettings() {
    }

    // Returns the block state ids.
    public static int[] getBlockStateIds() {
        return blockStateIds;
    }

    // Stores the block state ids.
    public static void setBlockStateIds(int[] table) {
        blockStateIds = table;
    }

    // Stores the item ids.
    public static void setItemIds(Map<NamespacedId, Integer> table) {
        itemIds = byResourceLocation(table);
    }

    // Stores the entity ids.
    public static void setEntityIds(Map<NamespacedId, Integer> table) {
        entityIds = byResourceLocation(table);
    }

    // Bies the resource location.
    private static Map<ResourceLocation, Integer> byResourceLocation(Map<NamespacedId, Integer> table) {
        if (table == null) {
            return null;
        }
        Map<ResourceLocation, Integer> keyed = new HashMap<>(table.size() * 2);
        table.forEach((id, packId) -> keyed.put(new ResourceLocation(id.getNamespace(), id.getName()), packId));
        return keyed;
    }

    // Choice of the dynamic hand light option.
    private static boolean dynamicHandLight = true;
    // Choice of the separate ao option.
    private static boolean separateAo;
    // Choice of the old lighting option.
    private static volatile boolean oldLighting = true;
    // Choice of the old hand light option.
    private static boolean oldHandLight = true;
    // Choice of the voxelize light blocks option.
    private static boolean voxelizeLightBlocks;

    // Returns whether old hand light.
    public static boolean isOldHandLight() {
        return oldHandLight;
    }

    // Stores the old hand light.
    public static void setOldHandLight(boolean value) {
        oldHandLight = value;
    }

    private static Map<Block, BlockRenderLayer> blockRenderLayers;

    // Stores the block render layers.
    public static void setBlockRenderLayers(
            Map<Block, BlockRenderLayer> table) {
        blockRenderLayers = table == null || table.isEmpty() ? null : table;
    }

    // Returns the forced render layer.
    public static BlockRenderLayer getForcedRenderLayer(Block block) {
        Map<Block, BlockRenderLayer> table = blockRenderLayers;
        return table == null ? null : table.get(block);
    }

    // Returns whether voxelize light blocks.
    public static boolean isVoxelizeLightBlocks() {
        return voxelizeLightBlocks;
    }

    // Stores the voxelize light blocks.
    public static void setVoxelizeLightBlocks(boolean value) {
        voxelizeLightBlocks = value;
    }

    // Returns whether dynamic hand light.
    public static boolean isDynamicHandLight() {
        return dynamicHandLight;
    }

    // Stores the dynamic hand light.
    public static void setDynamicHandLight(boolean value) {
        dynamicHandLight = value;
    }

    // Stores the separate ao.
    public static void setSeparateAo(boolean value) {
        separateAo = value;
        ChunkColorWriter.SeparateAoState.set(value);
    }

    // Returns whether old lighting.
    public static boolean isOldLighting() {
        return oldLighting;
    }

    // Returns whether disable directional shading.
    public static boolean shouldDisableDirectionalShading() {
        return !oldLighting;
    }

    // Stores the old lighting.
    public static void setOldLighting(boolean value) {
        oldLighting = value;
    }

    // Choice of the ambient occlusion level option.
    private static volatile float ambientOcclusionLevel = 1.0f;

    // Returns the ambient occlusion level.
    public static float getAmbientOcclusionLevel() {
        return ambientOcclusionLevel;
    }

    // Stores the ambient occlusion level.
    public static void setAmbientOcclusionLevel(float value) {
        ambientOcclusionLevel = Math.max(0.0f, Math.min(1.0f, value));
    }

    // Applies the ambient occlusion level.
    public static float applyAmbientOcclusionLevel(float vanillaAo) {
        float level = ambientOcclusionLevel;
        if (level == 1.0f) {
            return vanillaAo;
        }
        return 1.0f - level * (1.0f - vanillaAo);
    }

    // Returns the voxel render distance chunks.
    public static int getVoxelRenderDistanceChunks() {
        return voxelRenderDistanceChunks;
    }

    // Stores the voxel render distance chunks.
    public static void setVoxelRenderDistanceChunks(int chunks) {
        voxelRenderDistanceChunks = Math.max(0, chunks);
    }

    // Returns the block state id.
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

    // Returns the item id.
    public static int getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        Item item = stack.getItem();
        ResourceLocation key = item.getRegistryName();
        int rawId = Item.getIdFromItem(item);
        return mappedOrRaw(itemIds, key, rawId);
    }

    // Returns the entity id.
    public static int getEntityId(Entity entity) {
        if (entity == null) {
            return -1;
        }

        ResourceLocation key = EntityList.getKey(entity);
        int rawId = EntityList.getID(entity.getClass());
        return mappedOrRaw(entityIds, key, rawId);
    }

    // Returns the block entity id.
    public static int getBlockEntityId(TileEntity tileEntity) {
        if (tileEntity == null) {
            return -1;
        }

        ResourceLocation key = TileEntity.getKey(tileEntity.getClass());
        return mappedOrRaw(entityIds, key, -1);
    }

    // Returns the mapped id of a name or the raw id.
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
