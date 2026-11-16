package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.tileentity.TileEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Registry of the light handlers of entity and block entity classes.
public final class DynamicLightHandlers {
    // Handler of each entity class.
    private static final Map<Class<?>, DynamicLightHandler<?>> ENTITY_HANDLERS = new ConcurrentHashMap<>();
    // Handler of each block entity class.
    private static final Map<Class<?>, DynamicLightHandler<?>> TILE_ENTITY_HANDLERS = new ConcurrentHashMap<>();

    // Resolved handler of each entity class.
    private static final Map<Class<?>, Object> ENTITY_LOOKUP = new ConcurrentHashMap<>();
    // Resolved handler of each block entity class.
    private static final Map<Class<?>, Object> TILE_ENTITY_LOOKUP = new ConcurrentHashMap<>();

    // Marker for a class without handler.
    private static final Object NONE = new Object();

    // Prevents instantiation
    private DynamicLightHandlers() {
    }

    // Registers the handlers of the vanilla entities.
    @SuppressWarnings("deprecation")
    public static void registerDefaultHandlers() {
        registerEntityHandler(EntityBlaze.class, DynamicLightHandler.makeHandler(blaze -> 10, blaze -> true));
        registerEntityHandler(EntityCreeper.class, DynamicLightHandler.makeCreeperEntityHandler(null));
        registerEntityHandler(EntityEnderman.class, entity ->
                entity.getHeldBlockState() == null ? 0 : entity.getHeldBlockState().getLightValue());
        registerEntityHandler(EntityItem.class, entity ->
                DynamicLightsEngine.getLuminanceFromItemStack(entity.getItem(), entity.isOverWater()));
        registerEntityHandler(EntityItemFrame.class, entity ->
                DynamicLightsEngine.getLuminanceFromItemStack(entity.getDisplayedItem(),
                        FluidHandler.isFluid(entity)));
        registerEntityHandler(EntityMagmaCube.class, entity -> entity.squishFactor > 0.6F ? 11 : 8);
        registerEntityHandler(EntitySpectralArrow.class, entity -> 8);
    }

    // Registers a handler for an entity class and combines it with an existing one.
    public static <T extends Entity> void registerEntityHandler(Class<T> entityClass,
                                                                DynamicLightHandler<T> handler) {
        register(ENTITY_HANDLERS, ENTITY_LOOKUP, entityClass, handler);
    }

    // Registers a handler for a block entity class and combines it with an existing one.
    public static <T extends TileEntity> void registerTileEntityHandler(Class<T> tileEntityClass,
                                                                        DynamicLightHandler<T> handler) {
        register(TILE_ENTITY_HANDLERS, TILE_ENTITY_LOOKUP, tileEntityClass, handler);
    }

    // Returns whether any block entity handler exists.
    public static boolean hasTileEntityHandlers() {
        return !TILE_ENTITY_HANDLERS.isEmpty();
    }

    // Stores the handler and clears the resolved handlers.
    @SuppressWarnings("unchecked")
    private static <T> void register(Map<Class<?>, DynamicLightHandler<?>> handlers,
                                     Map<Class<?>, Object> lookup,
                                     Class<T> clazz, DynamicLightHandler<T> handler) {
        DynamicLightHandler<T> existing = (DynamicLightHandler<T>) handlers.get(clazz);
        DynamicLightHandler<T> combined = existing == null
                ? handler
                : new DynamicLightHandler<T>() {
                    @Override
                    public int getLuminance(T source) {
                        return Math.max(existing.getLuminance(source), handler.getLuminance(source));
                    }

                    @Override
                    public boolean isWaterSensitive(T source) {
                        return existing.isWaterSensitive(source) || handler.isWaterSensitive(source);
                    }
                };

        handlers.put(clazz, combined);

        lookup.clear();
    }

    // Returns the handler of the entity class or null.
    @SuppressWarnings("unchecked")
    public static <T extends Entity> DynamicLightHandler<T> getDynamicLightHandler(T entity) {
        return (DynamicLightHandler<T>) resolve(ENTITY_HANDLERS, ENTITY_LOOKUP, entity.getClass());
    }

    // Returns the handler of the block entity class or null.
    @SuppressWarnings("unchecked")
    public static <T extends TileEntity> DynamicLightHandler<T> getDynamicLightHandler(T tileEntity) {
        return (DynamicLightHandler<T>) resolve(TILE_ENTITY_HANDLERS, TILE_ENTITY_LOOKUP, tileEntity.getClass());
    }

    // Returns the handler of the class or of its nearest superclass.
    private static DynamicLightHandler<?> resolve(Map<Class<?>, DynamicLightHandler<?>> handlers,
                                                  Map<Class<?>, Object> lookup, Class<?> concrete) {
        Object memo = lookup.get(concrete);
        if (memo != null) {
            return memo == NONE ? null : (DynamicLightHandler<?>) memo;
        }

        DynamicLightHandler<?> found = null;
        for (Class<?> clazz = concrete; clazz != null; clazz = clazz.getSuperclass()) {
            found = handlers.get(clazz);
            if (found != null) {
                break;
            }
        }

        lookup.put(concrete, found == null ? NONE : found);
        return found;
    }

    // Returns whether the entity may emit light.
    public static boolean canEntityLightUp(Entity entity) {
        if (entity == Minecraft.getMinecraft().player && !DynamicLights.options().selfLightSource) {
            return false;
        }

        return LightSourceSettings.getInstance().isEntityEnabled(entity);
    }

    // Returns whether the block entity may emit light.
    public static boolean canTileEntityLightUp(TileEntity tileEntity) {
        return LightSourceSettings.getInstance().isBlockEntityEnabled(tileEntity);
    }

    // Returns the light level of the entity.
    public static <T extends Entity> int getLuminanceFrom(T entity) {
        if (!DynamicLights.options().entitiesLightSource) {
            return 0;
        }
        if (entity == Minecraft.getMinecraft().player && !DynamicLights.options().selfLightSource) {
            return 0;
        }

        DynamicLightHandler<T> handler = getDynamicLightHandler(entity);
        if (handler == null || !canEntityLightUp(entity)) {
            return 0;
        }

        if (handler.isWaterSensitive(entity) && DynamicLights.options().waterSensitiveCheck
                && (entity.isInWater() || entity.isInLava())) {
            return 0;
        }

        return clamp(handler.getLuminance(entity));
    }

    // Returns the light level of the block entity.
    public static <T extends TileEntity> int getLuminanceFrom(T tileEntity) {
        if (!DynamicLights.options().blockEntitiesLightSource) {
            return 0;
        }

        DynamicLightHandler<T> handler = getDynamicLightHandler(tileEntity);
        if (handler == null || !canTileEntityLightUp(tileEntity)) {
            return 0;
        }

        if (handler.isWaterSensitive(tileEntity) && DynamicLights.options().waterSensitiveCheck
                && tileEntity.getWorld() != null
                && FluidHandler.isFluid(tileEntity.getWorld(), tileEntity.getPos())) {
            return 0;
        }

        return clamp(handler.getLuminance(tileEntity));
    }

    // Returns the light level limited to the maximum.
    private static int clamp(int luminance) {
        return Math.min(luminance, 14);
    }
}
