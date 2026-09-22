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

// Resolves a pack's block.properties against the 1.12.2 block registry (port of Umbra's BlockMaterialMapping) into a flat array so the mesher does one read; first mapping wins (OptiFine behaviour, Umbra #1327), predicates naming a missing property are ignored, unresolved ids skipped
public final class BlockMaterialMapping {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // 1.12.2 global state ids are blockId | meta << 12: 12 + 4 bits.
    private static final int STATE_ID_SPACE = 1 << 16;

    private BlockMaterialMapping() {
    }

    // Resolves the pack's layer.<rendertype> overrides to concrete blocks; unknown ids are skipped with a warning since packs commonly list blocks from mods the user lacks
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

    // Returns the state-id -> pack-id table, or null when the pack has no block.properties (raw 1.12.2 ids stay the contract then).
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

    // Assigns the pack id to every state matching the entry's predicates; first mapping wins
    private static void addBlockStates(BlockEntry entry, int[] table, int intId) {
        ResourceLocation location = new ResourceLocation(entry.getId().getNamespace(), entry.getId().getName());
        if (!Block.REGISTRY.containsKey(location)) {
            // Normal and expected: modern-only names behind the pack's own MC_VERSION guards, or absent mods.
            return;
        }

        Block block = Block.REGISTRY.getObject(location);
        Map<String, String> predicates = entry.getPropertyPredicates();

        for (IBlockState state : block.getBlockState().getValidStates()) {
            if (!matches(state, predicates, intId)) {
                continue;
            }
            int stateId = Block.getStateId(state) & (STATE_ID_SPACE - 1);
            // First mapping wins (Umbra putIfAbsent / OptiFine parity).
            if (table[stateId] == -1) {
                table[stateId] = intId;
            }
        }
    }

    // All named properties must match; properties the block lacks are ignored
    private static boolean matches(IBlockState state, Map<String, String> predicates, int intId) {
        if (predicates.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> predicate : predicates.entrySet()) {
            IProperty<?> property = findProperty(state, predicate.getKey());
            if (property == null) {
                // Umbra parity: a predicate naming a property the block lacks is dropped, not treated as a mismatch.
                continue;
            }
            if (!valueName(state, property).equals(predicate.getValue())) {
                return false;
            }
        }
        return true;
    }

    // Property by name on this block, or null
    private static IProperty<?> findProperty(IBlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    // The property's string form of the current value, as block.properties spells it
    private static <T extends Comparable<T>> String valueName(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }
}
