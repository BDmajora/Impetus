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

// Registry of per-type light handlers; lookup walks up the class hierarchy (a subclassed EntityItem would otherwise lose its glow) and memoises per concrete class
public final class DynamicLightHandlers {
    private static final Map<Class<?>, DynamicLightHandler<?>> ENTITY_HANDLERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, DynamicLightHandler<?>> TILE_ENTITY_HANDLERS = new ConcurrentHashMap<>();

    // Concrete class to the handler found by walking its supertypes; includes negative results
    private static final Map<Class<?>, Object> ENTITY_LOOKUP = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> TILE_ENTITY_LOOKUP = new ConcurrentHashMap<>();

    // Stands in for "no handler", since ConcurrentHashMap can't store null as a value
    private static final Object NONE = new Object();

    private DynamicLightHandlers() {
    }

    // Vanilla light sources that aren't simply "an entity holding a bright item". The enderman's carried block has no world position, so it asks the state's own light value
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

    // Registration

    // Registers a handler for entityClass and its subclasses; registering over an existing entry combines them by taking the brighter result
    public static <T extends Entity> void registerEntityHandler(Class<T> entityClass,
                                                                DynamicLightHandler<T> handler) {
        register(ENTITY_HANDLERS, ENTITY_LOOKUP, entityClass, handler);
    }

    // As registerEntityHandler, for block entities
    public static <T extends TileEntity> void registerTileEntityHandler(Class<T> tileEntityClass,
                                                                        DynamicLightHandler<T> handler) {
        register(TILE_ENTITY_HANDLERS, TILE_ENTITY_LOOKUP, tileEntityClass, handler);
    }

    // True when a mod has registered at least one block entity handler; nothing does by default
    public static boolean hasTileEntityHandlers() {
        return !TILE_ENTITY_HANDLERS.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> void register(Map<Class<?>, DynamicLightHandler<?>> handlers,
                                     Map<Class<?>, Object> lookup,
                                     Class<T> clazz, DynamicLightHandler<T> handler) {
        DynamicLightHandler<T> existing = (DynamicLightHandler<T>) handlers.get(clazz);
        DynamicLightHandler<T> combined = existing == null
                ? handler
                : source -> Math.max(existing.getLuminance(source), handler.getLuminance(source));

        handlers.put(clazz, combined);

        // A new registration can change what any concrete class resolves to, so the memo is void.
        lookup.clear();
    }

    // Lookup

    @SuppressWarnings("unchecked")
    public static <T extends Entity> DynamicLightHandler<T> getDynamicLightHandler(T entity) {
        return (DynamicLightHandler<T>) resolve(ENTITY_HANDLERS, ENTITY_LOOKUP, entity.getClass());
    }

    // Handler for a block entity, walking up its class hierarchy and memoising the result
    @SuppressWarnings("unchecked")
    public static <T extends TileEntity> DynamicLightHandler<T> getDynamicLightHandler(T tileEntity) {
        return (DynamicLightHandler<T>) resolve(TILE_ENTITY_HANDLERS, TILE_ENTITY_LOOKUP, tileEntity.getClass());
    }

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

    // Gating

    // Whether this entity is allowed to light up at all; player is gated on the first-person switch, everything else on its per-type toggle
    public static boolean canEntityLightUp(Entity entity) {
        if (entity == Minecraft.getMinecraft().player && !DynamicLights.options().selfLightSource) {
            return false;
        }

        return LightSourceSettings.getInstance().isEntityEnabled(entity);
    }

    // Whether the user has left this block entity type enabled
    public static boolean canTileEntityLightUp(TileEntity tileEntity) {
        return LightSourceSettings.getInstance().isBlockEntityEnabled(tileEntity);
    }

    // The luminance this entity's registered handler reports, or zero if it has none or is gated off
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

    // As getLuminanceFrom(Entity), for block entities
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

    // Caps a handler's answer at 14; a source at 15 reads as a full-bright block with no visible falloff, so it looks like a rectangle of light instead of a glow
    private static int clamp(int luminance) {
        return Math.min(luminance, 14);
    }
}
