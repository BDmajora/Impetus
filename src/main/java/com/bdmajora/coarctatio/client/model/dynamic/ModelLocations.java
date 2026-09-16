package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelBakeryAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.resources.IResource;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// What vanilla's ModelBakery learns while loading every model, gathered without loading any: which locations exist, which block each blockstate file belongs to, and which variants each block can ask for
public final class ModelLocations {
    // Every location a block state or item variant maps to; what ModelBakeEvent listeners see when they iterate the registry's keys
    public static final Set<ModelResourceLocation> ALL_KNOWN = new ObjectOpenHashSet<>();
    // Item inventory variants, so a lookup miss for one answers the missing model rather than null the way vanilla's registry would
    public static final Set<ModelResourceLocation> ITEM_VARIANTS = new ObjectOpenHashSet<>();
    // Inventory variant to the item model file it is loaded from when the blockstate has no such variant
    public static final Map<ModelResourceLocation, ResourceLocation> ITEM_VARIANT_FILES = new Object2ObjectOpenHashMap<>();
    // Blockstate file location to the variants the state mapper produces for it; the multipart membership check vanilla did in loadBlock
    public static final Map<ResourceLocation, Collection<ModelResourceLocation>> VARIANTS_BY_BLOCKSTATE = new Object2ObjectOpenHashMap<>();
    // Completed once the tables above are filled, so the texture scan on the worker pool can wait for them
    public static final CompletableFuture<Void> READY = new CompletableFuture<>();

    private static final Map<ResourceLocation, Block> BLOCK_BY_BLOCKSTATE = new Object2ObjectOpenHashMap<>();
    private static final Object2IntOpenHashMap<String> ERRORS_BY_NAMESPACE = new Object2IntOpenHashMap<>();
    private static final int ERROR_LIMIT = 6;

    private static Map<Item, List<String>> variantNames;
    private static boolean tablesBuilt;

    private ModelLocations() {
    }

    // Rebuilds the item variant names each reload (mods add to them), the location tables only once since registries are frozen by the first bake that matters
    public static void init(ModelLoader loader, BlockStateMapper mapper) {
        ModelBakeryAccessor bakery = (ModelBakeryAccessor) (Object) loader;
        bakery.coarctatio$registerVariantNames();
        variantNames = bakery.coarctatio$variantNames();
        ERRORS_BY_NAMESPACE.clear();
        if (tablesBuilt) {
            return;
        }
        for (Item item : Item.REGISTRY) {
            for (String name : variantNames(item)) {
                ModelResourceLocation variant = inventoryVariant(name);
                ITEM_VARIANTS.add(variant);
                ALL_KNOWN.add(variant);
                ITEM_VARIANT_FILES.put(variant, itemFile(name));
            }
        }
        for (Block block : Block.REGISTRY) {
            for (ResourceLocation location : mapper.getBlockstateLocations(block)) {
                BLOCK_BY_BLOCKSTATE.put(location, block);
            }
            for (ModelResourceLocation location : mapper.getVariants(block).values()) {
                ALL_KNOWN.add(location);
                ResourceLocation file = new ResourceLocation(location.getNamespace(), location.getPath());
                ((ObjectOpenHashSet<ModelResourceLocation>) VARIANTS_BY_BLOCKSTATE.computeIfAbsent(file, k -> new ObjectOpenHashSet<>())).add(location);
            }
        }
        // The item frame's models are loaded by the entity renderer, not through any block or item
        ALL_KNOWN.add(new ModelResourceLocation("item_frame", "normal"));
        ALL_KNOWN.add(new ModelResourceLocation("item_frame", "map"));
        for (Collection<ModelResourceLocation> variants : VARIANTS_BY_BLOCKSTATE.values()) {
            ((ObjectOpenHashSet<ModelResourceLocation>) variants).trim();
        }
        tablesBuilt = true;
        READY.complete(null);
    }

    public static List<String> variantNames(Item item) {
        List<String> names = variantNames.get(item);
        return names != null ? names : Collections.singletonList(Item.REGISTRY.getNameForObject(item).toString());
    }

    // ModelBakery.getItemLocation: the model file under models/item for a variant name
    public static ResourceLocation itemFile(String name) {
        ResourceLocation stripped = new ResourceLocation(name.replaceAll("#.*", ""));
        return new ResourceLocation(stripped.getNamespace(), "item/" + stripped.getPath());
    }

    // ModelLoader.getInventoryVariant: a name with its own variant keeps it, otherwise "inventory"
    public static ModelResourceLocation inventoryVariant(String name) {
        return name.contains("#") ? new ModelResourceLocation(name) : new ModelResourceLocation(name, "inventory");
    }

    public static ResourceLocation itemFileFor(ModelResourceLocation variant) {
        synchronized (ITEM_VARIANT_FILES) {
            return ITEM_VARIANT_FILES.get(variant);
        }
    }

    // An override model discovered inside an item model is another inventory variant to remember
    public static void addItemVariantFile(ModelResourceLocation variant, ResourceLocation file) {
        synchronized (ITEM_VARIANT_FILES) {
            ITEM_VARIANT_FILES.put(variant, file);
        }
    }

    public static boolean isKnownVariant(ResourceLocation blockstate, ModelResourceLocation variant) {
        Collection<ModelResourceLocation> variants = VARIANTS_BY_BLOCKSTATE.get(blockstate);
        return variants != null && variants.contains(variant);
    }

    public static Block blockFor(ResourceLocation blockstate) {
        return BLOCK_BY_BLOCKSTATE.get(blockstate);
    }

    // ModelBakery.loadMultipartMBD plus the state container vanilla set in loadBlock, which a multipart selector needs to build its predicates
    public static ModelBlockDefinition loadDefinition(ResourceLocation location) {
        ResourceLocation file = new ResourceLocation(location.getNamespace(), "blockstates/" + location.getPath() + ".json");
        List<ModelBlockDefinition> parts = new ArrayList<>();
        try {
            for (IResource resource : Minecraft.getMinecraft().getResourceManager().getAllResources(file)) {
                parts.add(parseDefinition(location, resource));
            }
        } catch (IOException e) {
            throw new RuntimeException("Encountered an exception when loading model definition of model " + file, e);
        }
        ModelBlockDefinition definition = new ModelBlockDefinition(parts);
        if (definition.hasMultipartData()) {
            Block block = blockFor(location);
            if (block != null) {
                definition.getMultipartData().setStateContainer(block.getBlockState());
            }
        }
        return definition;
    }

    private static ModelBlockDefinition parseDefinition(ResourceLocation location, IResource resource) {
        try (InputStream in = resource.getInputStream()) {
            return ModelBlockDefinition.parseFromReader(new InputStreamReader(in, StandardCharsets.UTF_8), location);
        } catch (Exception e) {
            throw new RuntimeException("Encountered an exception when loading model definition of '" + location + "' from: '" + resource.getResourceLocation() + "' in resourcepack: '" + resource.getResourcePackName() + "'", e);
        }
    }

    // A namespace gets a handful of logged failures per reload, then silence; a broken mod otherwise fills the log one model at a time
    public static boolean canLogError(String namespace) {
        synchronized (ERRORS_BY_NAMESPACE) {
            int errors = ERRORS_BY_NAMESPACE.getInt(namespace);
            if (errors > ERROR_LIMIT) {
                return false;
            }
            if (errors == ERROR_LIMIT) {
                Coarctatio.LOGGER.error("Suppressing further model loading errors for namespace '{}'", namespace);
            }
            ERRORS_BY_NAMESPACE.put(namespace, errors + 1);
            return errors < ERROR_LIMIT;
        }
    }
}
