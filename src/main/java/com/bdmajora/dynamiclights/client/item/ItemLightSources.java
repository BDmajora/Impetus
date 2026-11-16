package com.bdmajora.dynamiclights.client.item;

import com.bdmajora.dynamiclights.DynamicLights;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Registry of the item light sources.
public final class ItemLightSources {
    // Namespace of the definitions.
    private static final String NAMESPACE = "impetus";
    // Folder of the definitions.
    private static final String DIRECTORY = "dynamiclights/item/";
    // Index file that lists the definitions.
    private static final ResourceLocation INDEX = new ResourceLocation(NAMESPACE, "dynamiclights/index.json");

    // Sources read from resources.
    private static volatile Map<Item, ItemLightSource> loaded = new Reference2ObjectOpenHashMap<>();
    // Sources registered by code.
    private static final Map<Item, ItemLightSource> REGISTERED = new java.util.concurrent.ConcurrentHashMap<>();

    // Prevents instantiation
    private ItemLightSources() {
    }

    // Reloads the sources whenever resources reload.
    public static void registerReloadListener() {
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();

        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(ItemLightSources::load);
        } else {
            DynamicLights.LOGGER.warn("Resource manager is not reloadable; item light sources will be "
                    + "loaded once and never refreshed");
            load(manager);
        }
    }

    // Reads every listed definition and replaces the loaded sources.
    public static void load(IResourceManager resourceManager) {
        Map<Item, ItemLightSource> next = new Reference2ObjectOpenHashMap<>();

        for (String fileName : readIndex(resourceManager)) {
            ResourceLocation location = new ResourceLocation(NAMESPACE, DIRECTORY + fileName);

            try {
                for (IResource resource : resourceManager.getAllResources(location)) {
                    loadOne(location, resource, next);
                }
            } catch (IOException e) {
                DynamicLights.LOGGER.warn("Could not read the item light source at {}", location);
            }
        }

        loaded = next;
    }

    // Returns the file names listed in the indexes.
    private static Set<String> readIndex(IResourceManager resourceManager) {
        Set<String> files = new LinkedHashSet<>();

        try {
            for (IResource resource : resourceManager.getAllResources(INDEX)) {
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonArray entries = json.getAsJsonArray("files");

                    if (entries == null) {
                        continue;
                    }

                    for (JsonElement entry : entries) {
                        files.add(entry.getAsString());
                    }
                } catch (Exception e) {
                    DynamicLights.LOGGER.warn("Could not parse an item light source index", e);
                }
            }
        } catch (IOException e) {
            DynamicLights.LOGGER.error("Could not read {}; no item light sources will be loaded", INDEX, e);
        }

        return files;
    }

    // Reads one definition and stores it unless code registered the item.
    private static void loadOne(ResourceLocation location, IResource resource, Map<Item, ItemLightSource> into) {
        ResourceLocation id = new ResourceLocation(location.getNamespace(),
                location.getPath().replace(".json", ""));

        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            ItemLightSource.fromJson(id, json).ifPresent(data -> {
                if (!REGISTERED.containsKey(data.item())) {
                    into.put(data.item(), data);
                }
            });
        } catch (IOException | RuntimeException e) {
            DynamicLights.LOGGER.warn("Could not load the item light source \"{}\"", id, e);
        }
    }

    // Registers a source and rejects a duplicate item.
    public static void registerItemLightSource(ItemLightSource data) {
        ItemLightSource existing = REGISTERED.get(data.item());

        if (existing != null) {
            DynamicLights.LOGGER.warn("Item light source \"{}\" duplicates \"{}\" for item {}",
                    data.id(), existing.id(), Item.REGISTRY.getNameForObject(data.item()));
            return;
        }

        REGISTERED.put(data.item(), data);
        synchronized (ItemLightSources.class) {
            if (loaded.containsKey(data.item())) {
                Map<Item, ItemLightSource> copy = new Reference2ObjectOpenHashMap<>(loaded);
                copy.remove(data.item());
                loaded = copy;
            }
        }
    }

    // Returns the light level of the stack.
    public static int getLuminance(ItemStack stack, boolean submergedInWater) {
        if (stack.isEmpty()) {
            return 0;
        }

        Item item = stack.getItem();

        ItemLightSource data = REGISTERED.get(item);
        if (data == null) {
            data = loaded.get(item);
        }

        if (data != null) {
            return data.getLuminance(stack, submergedInWater);
        }

        if (item instanceof ItemBlock) {
            return ItemLightSource.BlockItemLightSource.getLuminance(stack,
                    ((ItemBlock) item).getBlock().getDefaultState());
        }

        return 0;
    }
}
