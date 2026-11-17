package com.bdmajora.dynamiclights.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Switches that disable light sources by type.
public final class LightSourceSettings {
    // Single instance.
    private static final LightSourceSettings INSTANCE = new LightSourceSettings();

    // Identifiers of disabled entity types.
    private final Set<String> disabledEntities = ConcurrentHashMap.newKeySet();
    // Identifiers of disabled block entity types.
    private final Set<String> disabledBlockEntities = ConcurrentHashMap.newKeySet();

    // Identifier of each entity class.
    private final Map<Class<?>, String> entityIds = new ConcurrentHashMap<>();
    // Identifier of each block entity class.
    private final Map<Class<?>, String> blockEntityIds = new ConcurrentHashMap<>();

    // Identifier of a class without registration.
    private static final String UNREGISTERED = "";

    // Prevents instantiation
    private LightSourceSettings() {
    }

    // Returns the single instance.
    public static LightSourceSettings getInstance() {
        return INSTANCE;
    }

    // Returns whether the entity may emit light.
    public boolean isEntityEnabled(Entity entity) {
        if (this.disabledEntities.isEmpty()) {
            return true;
        }

        String id = this.entityId(entity);
        return id == null || !this.disabledEntities.contains(id);
    }

    // Returns whether the block entity may emit light.
    public boolean isBlockEntityEnabled(TileEntity tileEntity) {
        if (this.disabledBlockEntities.isEmpty()) {
            return true;
        }

        String id = this.blockEntityId(tileEntity);
        return id == null || !this.disabledBlockEntities.contains(id);
    }

    // Returns the identifier of the entity class or null when unregistered.
    private String entityId(Entity entity) {
        Class<?> clazz = entity.getClass();
        String cached = this.entityIds.get(clazz);

        if (cached == null) {
            ResourceLocation key = EntityList.getKey(entity);
            cached = key == null ? UNREGISTERED : key.toString();
            this.entityIds.put(clazz, cached);
        }

        return cached.isEmpty() ? null : cached;
    }

    // Returns the identifier of the block entity class or null when unregistered.
    private String blockEntityId(TileEntity tileEntity) {
        Class<?> clazz = tileEntity.getClass();
        String cached = this.blockEntityIds.get(clazz);

        if (cached == null) {
            ResourceLocation key = TileEntity.getKey(tileEntity.getClass());
            cached = key == null ? UNREGISTERED : key.toString();
            this.blockEntityIds.put(clazz, cached);
        }

        return cached.isEmpty() ? null : cached;
    }

    // Replaces the disabled entity types and ignores empty entries.
    public void loadDisabledEntities(String[] ids) {
        load(this.disabledEntities, ids);
    }

    // Replaces the disabled block entity types and ignores empty entries.
    public void loadDisabledBlockEntities(String[] ids) {
        load(this.disabledBlockEntities, ids);
    }

    // Returns the sorted identifiers of disabled entity types.
    public String[] getDisabledEntitiesArray() {
        return this.disabledEntities.stream().sorted().toArray(String[]::new);
    }

    // Returns the sorted identifiers of disabled block entity types.
    public String[] getDisabledBlockEntitiesArray() {
        return this.disabledBlockEntities.stream().sorted().toArray(String[]::new);
    }

    // Replaces the set with the identifiers and ignores null and empty entries.
    private static void load(Set<String> target, String[] ids) {
        target.clear();
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isEmpty()) {
                target.add(id);
            }
        }
    }
}
