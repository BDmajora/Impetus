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

// Tables of every model location the game can ask for.
public final class ModelLocations {
    // Every block and item model location.
    public static final Set<ModelResourceLocation> ALL_KNOWN = new ObjectOpenHashSet<>();
    // Inventory model locations of items.
    public static final Set<ModelResourceLocation> ITEM_VARIANTS = new ObjectOpenHashSet<>();
    // Model file of each item variant.
    public static final Map<ModelResourceLocation, ResourceLocation> ITEM_VARIANT_FILES = new Object2ObjectOpenHashMap<>();
    // Variants of each block state file.
    public static final Map<ResourceLocation, Collection<ModelResourceLocation>> VARIANTS_BY_BLOCKSTATE = new Object2ObjectOpenHashMap<>();

    // Block of each block state file.
    private static final Map<ResourceLocation, Block> BLOCK_BY_BLOCKSTATE = new Object2ObjectOpenHashMap<>();
    // Count of logged errors by namespace.
    private static final Object2IntOpenHashMap<String> ERRORS_BY_NAMESPACE = new Object2IntOpenHashMap<>();
    // Number of errors logged for one namespace.
    private static final int ERROR_LIMIT = 6;

    // Item variant names reported by the model loader.
    private static Map<Item, List<String>> variantNames;
    // Whether the tables were filled.
    private static boolean tablesBuilt;

    // Prevents instantiation
    private ModelLocations() {
    }

    // Reads the variant names and fills the tables once.
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
        ALL_KNOWN.add(new ModelResourceLocation("item_frame", "normal"));
        ALL_KNOWN.add(new ModelResourceLocation("item_frame", "map"));
        for (Collection<ModelResourceLocation> variants : VARIANTS_BY_BLOCKSTATE.values()) {
            ((ObjectOpenHashSet<ModelResourceLocation>) variants).trim();
        }
        tablesBuilt = true;
    }

    // Returns the variant names of the item or its registry name when none are known.
    public static List<String> variantNames(Item item) {
        List<String> names = variantNames == null ? null : variantNames.get(item);
        return names != null ? names : Collections.singletonList(Item.REGISTRY.getNameForObject(item).toString());
    }

    // Returns the model file of an item variant name.
    public static ResourceLocation itemFile(String name) {
        ResourceLocation stripped = new ResourceLocation(name.replaceAll("#.*", ""));
        return new ResourceLocation(stripped.getNamespace(), "item/" + stripped.getPath());
    }

    // Returns the model location of a variant name.
    public static ModelResourceLocation inventoryVariant(String name) {
        return name.contains("#") ? new ModelResourceLocation(name) : new ModelResourceLocation(name, "inventory");
    }

    // Returns the model file of an item variant.
    public static ResourceLocation itemFileFor(ModelResourceLocation variant) {
        synchronized (ITEM_VARIANT_FILES) {
            return ITEM_VARIANT_FILES.get(variant);
        }
    }

    // Stores the model file of an item variant.
    public static void addItemVariantFile(ModelResourceLocation variant, ResourceLocation file) {
        synchronized (ITEM_VARIANT_FILES) {
            ITEM_VARIANT_FILES.put(variant, file);
        }
    }

    // Returns whether the block state file lists the variant.
    public static boolean isKnownVariant(ResourceLocation blockstate, ModelResourceLocation variant) {
        Collection<ModelResourceLocation> variants = VARIANTS_BY_BLOCKSTATE.get(blockstate);
        return variants != null && variants.contains(variant);
    }

    // Returns the block of a block state file.
    public static Block blockFor(ResourceLocation blockstate) {
        return BLOCK_BY_BLOCKSTATE.get(blockstate);
    }

    // Returns the definition read from every pack that has the block state file.
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

    // Returns the definition parsed from one resource.
    private static ModelBlockDefinition parseDefinition(ResourceLocation location, IResource resource) {
        try (InputStream in = resource.getInputStream()) {
            return ModelBlockDefinition.parseFromReader(new InputStreamReader(in, StandardCharsets.UTF_8), location);
        } catch (Exception e) {
            throw new RuntimeException("Encountered an exception when loading model definition of '" + location + "' from: '" + resource.getResourceLocation() + "' in resourcepack: '" + resource.getResourcePackName() + "'", e);
        }
    }

    // Returns whether another error of the namespace may be logged.
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
