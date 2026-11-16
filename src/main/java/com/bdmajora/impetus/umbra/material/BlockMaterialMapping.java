package com.bdmajora.impetus.umbra.material;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.BlockEntry;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.IdMap;

import java.util.Arrays;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.NamespacedId;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Mapping of blocks to pack material ids.
public final class BlockMaterialMapping {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Size of the block state id space.
    private static final int STATE_ID_SPACE = 1 << 16;

    // Prevents instantiation
    private BlockMaterialMapping() {
    }

    // Returns the render layer overrides by block.
    public static Map<Block, BlockRenderLayer> createBlockRenderLayerTable(IdMap idMap) {
        Map<NamespacedId, BlockRenderLayer> declared = idMap.getBlockRenderLayerMap();
        if (declared.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Block, BlockRenderLayer> resolved = new HashMap<>();
        declared.forEach((id, layer) -> {
            Block block = Block.REGISTRY.getObject(new ResourceLocation(id.getNamespace(), id.getName()));
            if (block == null || block == Blocks.AIR) {
                LOGGER.warn("[Umbra] block.properties: unknown block \"{}\" in a render-layer override", id);
                return;
            }
            resolved.put(block, layer);
        });
        return resolved;
    }

    // Returns the material id of every block state id.
    public static int[] createBlockStateIdTable(IdMap idMap) {
        if (!idMap.hasBlockProperties()) {
            return null;
        }

        int[] table = new int[STATE_ID_SPACE];
        Arrays.fill(table, -1);

        idMap.getBlockProperties().forEach((intId, entries) -> {
            for (BlockEntry entry : entries) {
                addBlockStates(entry, table, intId);
            }
        });

        return table;
    }

    // Writes the material id of the states that match an entry.
    private static void addBlockStates(BlockEntry entry, int[] table, int intId) {
        ResourceLocation location = new ResourceLocation(entry.getId().getNamespace(), entry.getId().getName());
        if (!Block.REGISTRY.containsKey(location)) {
            return;
        }

        Block block = Block.REGISTRY.getObject(location);
        Map<String, String> predicates = entry.getPropertyPredicates();

        for (IBlockState state : block.getBlockState().getValidStates()) {
            if (!matches(state, predicates, intId)) {
                continue;
            }
            int stateId = Block.getStateId(state) & (STATE_ID_SPACE - 1);
            if (table[stateId] == -1) {
                table[stateId] = intId;
            }
        }
    }

    // Returns whether a state has the given property values.
    private static boolean matches(IBlockState state, Map<String, String> predicates, int intId) {
        if (predicates.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> predicate : predicates.entrySet()) {
            IProperty<?> property = findProperty(state, predicate.getKey());
            if (property == null) {
                continue;
            }
            if (!valueName(state, property).equals(predicate.getValue())) {
                return false;
            }
        }
        return true;
    }

    // Returns the property of a state by name or null.
    private static IProperty<?> findProperty(IBlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    // Values the name.
    private static <T extends Comparable<T>> String valueName(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }
}
