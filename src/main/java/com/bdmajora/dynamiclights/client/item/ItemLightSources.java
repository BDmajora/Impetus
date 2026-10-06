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

// Loaded item light definitions, rebuilt on every resource reload; code registrations beat pack ones and survive reloads
public final class ItemLightSources {
    private static final String NAMESPACE = "impetus";
    private static final String DIRECTORY = "dynamiclights/item/";
    private static final ResourceLocation INDEX = new ResourceLocation(NAMESPACE, "dynamiclights/index.json");

    private static final Map<Item, ItemLightSource> LOADED = new Reference2ObjectOpenHashMap<>();
    private static final Map<Item, ItemLightSource> REGISTERED = new Reference2ObjectOpenHashMap<>();

    private ItemLightSources() {
    }

    // Hooks the resource manager so #load runs on every pack reload.
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

    // Rereads every definition; 1.12.2 cannot list a directory, so every pack's copy of an index resource is read and merged, letting a pack add sources for items this mod never heard of
    public static void load(IResourceManager resourceManager) {
        LOADED.clear();

        for (String fileName : readIndex(resourceManager)) {
            ResourceLocation location = new ResourceLocation(NAMESPACE, DIRECTORY + fileName);

            try {
                for (IResource resource : resourceManager.getAllResources(location)) {
                    loadOne(location, resource);
                }
            } catch (IOException e) {
                DynamicLights.LOGGER.warn("Could not read the item light source at {}", location);
            }
        }
    }

    // Reads every pack's index.json and unions the listed file names
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

    // Parses one definition; a malformed file is logged and skipped rather than failing the reload
    private static void loadOne(ResourceLocation location, IResource resource) {
        ResourceLocation id = new ResourceLocation(location.getNamespace(),
                location.getPath().replace(".json", ""));

        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            ItemLightSource.fromJson(id, json).ifPresent(data -> {
                if (!REGISTERED.containsKey(data.item())) {
                    LOADED.put(data.item(), data);
                }
            });
        } catch (IOException | IllegalStateException e) {
            DynamicLights.LOGGER.warn("Could not load the item light source \"{}\"", id, e);
        }
    }

    // Registers an item light source in code, the API counterpart of a JSON file: survives reloads and beats anything a pack declares for the same item
    public static void registerItemLightSource(ItemLightSource data) {
        ItemLightSource existing = REGISTERED.get(data.item());

        if (existing != null) {
            DynamicLights.LOGGER.warn("Item light source \"{}\" duplicates \"{}\" for item {}",
                    data.id(), existing.id(), Item.REGISTRY.getNameForObject(data.item()));
            return;
        }

        REGISTERED.put(data.item(), data);
        LOADED.remove(data.item());
    }

    // Luminance of a stack; anything that places a block falls back to that block's light value, so only non-block items (blaze rod, lava bucket) need a definition
    public static int getLuminance(ItemStack stack, boolean submergedInWater) {
        if (stack.isEmpty()) {
            return 0;
        }

        Item item = stack.getItem();

        ItemLightSource data = REGISTERED.get(item);
        if (data == null) {
            data = LOADED.get(item);
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
