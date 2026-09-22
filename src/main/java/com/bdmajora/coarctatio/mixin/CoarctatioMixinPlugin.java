package com.bdmajora.coarctatio.mixin;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.impetus.booter.mixin.SimpleMixinPlugin;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.MixinService;

import java.util.Arrays;
import java.util.List;

// Gates each Coarctatio mixin on its config switch; off means never loaded, so a suspect feature can be disabled without a rebuild
public class CoarctatioMixinPlugin extends SimpleMixinPlugin {
    // The event-recycling mixins, added dynamically rather than from the json for the reason given in getMixins
    private static final List<String> RECYCLED_EVENT_MIXINS = Arrays.asList(
            "events.TickEventMixin",
            "events.PlayerTickEventMixin",
            "events.WorldTickEventMixin",
            "events.RenderTickEventMixin",
            "events.AttachCapabilitiesEventMixin",
            "events.BlockEventMixin",
            "events.NeighborNotifyEventMixin",
            "events.FMLCommonHandlerMixin",
            "events.ForgeEventFactoryMixin");

    private static final List<String> RECYCLED_EVENT_TARGETS = Arrays.asList(
            "net.minecraftforge.fml.common.gameevent.TickEvent",
            "net.minecraftforge.event.AttachCapabilitiesEvent",
            "net.minecraftforge.event.world.BlockEvent",
            "net.minecraftforge.fml.common.FMLCommonHandler",
            "net.minecraftforge.event.ForgeEventFactory");

    private static final String PACKAGE = "com.bdmajora.coarctatio.mixin.";

    private CoarctatioConfig config;

    // Reads the config once; it is a Properties file precisely so this is safe during coremod setup
    @Override
    public void onLoad(String mixinPackage) {
        this.config = CoarctatioConfig.get();
    }

    // Maps the mixin's simple name to its switch; anything unlisted is refused
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.startsWith(PACKAGE)
                ? mixinClassName.substring(PACKAGE.length())
                : mixinClassName;

        switch (name) {
            case "util.ResourceLocationMixin":
                return this.config.deduplicateResourceLocations;
            case "util.ModelResourceLocationMixin":
                return this.config.deduplicateModelVariants;
            case "nbt.NBTTagCompoundMixin":
                return this.config.compactNbtBackingMap;
            case "client.model.CoarctatioBakedQuadMixin":
                return this.config.poolQuadVertexData;
            case "client.model.SimpleBakedModelMixin":
            case "client.model.WeightedBakedModelMixin":
            case "client.model.MultipartBakedModelMixin":
                return this.config.compactBakedModels;
            case "client.model.multipart.ConditionAndMixin":
            case "client.model.multipart.ConditionOrMixin":
            case "client.model.multipart.ConditionPropertyValueMixin":
                return this.config.canonicalizeMultipartConditions;
            case "state.BlockStateContainerMixin":
            case "state.ExtendedBlockStateMixin":
                return this.config.optimizeBlockStates;
            case "client.model.ModelBakeryMixin":
            case "client.model.ModelLoaderMixin":
                return this.config.compactModelGraph;
            case "world.AnvilChunkLoaderMixin":
                return this.config.stripChunkNbt;
            case "world.ChunkMixin":
                return this.config.dropEmptyChunkSections;
            case "client.texture.TextureMapMixin":
                return this.config.releaseSpriteData;
            case "client.model.ModelLoaderCleanupMixin":
                return this.config.releaseBakeState;
            case "client.SearchTreeMixin":
                return this.config.lazySearchTrees;
            case "core.LockCodeMixin":
            case "client.SoundRegistryMixin":
            case "core.ObjectHolderRegistryMixin":
            case "core.RegistrySimpleMixin":
            case "core.EntityDataManagerMixin":
            case "core.ClassInheritanceMultiMapMixin":
                return this.config.compactRuntimeCollections;
            case "item.ItemStackCapabilityMixin":
                return this.config.lazyItemStackCapabilities;
            case "nbt.NBTTagCompoundPoolMixin":
            case "nbt.NBTTagListPoolMixin":
                return this.config.poolNbtPrimitives;
            case "state.PropertyEnumHashMixin":
            case "state.PropertyIntegerHashMixin":
                return this.config.cachePropertyHashes;
            case "state.StateImplementationHashMixin":
                return this.config.cacheStateHashes;
            case "client.resources.FileResourcePackMixin":
            case "client.resources.FolderResourcePackMixin":
            case "client.resources.DefaultResourcePackMixin":
                return this.config.resourceExistenceCache;
            case "client.resources.FallbackResourceManagerMixin":
                return this.config.stacklessResourceExceptions;
            case "client.resources.SimpleReloadableResourceManagerMixin":
                // Carries the reload generation the existence caches key on as well as its own throw
                return this.config.stacklessResourceExceptions || this.config.resourceExistenceCache;
            case "client.texture.StitcherMixin":
                return this.config.fastAtlasStitching;
            case "client.texture.TextureMapPrefetchMixin":
            case "client.texture.TextureAtlasSpritePrefetchMixin":
                return this.config.parallelTextureLoad;
            case "forge.OreDictionaryMixin":
                return this.config.primitiveOreDictionary;
            case "forge.GameDataMixin":
                return this.config.quietPrefixWarnings;
            case "client.SoundHandlerMixin":
                return this.config.skipSoundDebugChecks;
            case "client.model.ModelLoaderRegistryMissingMixin":
                return this.config.plainMissingModels;
            case "events.TickEventMixin":
            case "events.PlayerTickEventMixin":
            case "events.WorldTickEventMixin":
            case "events.RenderTickEventMixin":
            case "events.AttachCapabilitiesEventMixin":
            case "events.BlockEventMixin":
            case "events.NeighborNotifyEventMixin":
            case "events.FMLCommonHandlerMixin":
            case "events.ForgeEventFactoryMixin":
                return this.config.recycleEvents;
            case "client.model.part.BlockPartMixin":
            case "client.model.part.BlockFaceUVMixin":
            case "client.model.part.ModelBlockMixin":
                return this.config.canonicalizeModelParts;
            case "world.TemplateManagerMixin":
                return this.config.softStructureTemplates;
            case "forge.ASMDataMixin":
            case "forge.ModCandidateMixin":
                return this.config.internLoaderStrings;
            case "forge.ASMModParserAccessor":
            case "forge.ModAnnotationAccessor":
            case "forge.JarDiscovererMixin":
            case "forge.ModDiscovererMixin":
                return this.config.modScanCache;
            case "client.ModelManagerMixin":
                // Drives the pool lifecycle and the statistics dump; pointless with nothing pooling, and the dynamic reload opens the pools itself
                return (this.config.poolQuadVertexData || this.config.canonicalizeMultipartConditions) && !this.config.dynamicModels;
            case "client.model.bake.ItemLayerModelQuadMixin":
            case "client.model.bake.ItemLayerFaceDataMixin":
            case "client.model.bake.LightUtilUnpackMixin":
            case "client.model.bake.TRSRTransformationIdentityMixin":
                return this.config.fastItemLayerBaking;
            case "client.model.dynamic.RenderItemPrebakeMixin":
            case "client.model.dynamic.ItemModelMesherForgeAccessor":
                return this.config.dynamicModels && this.config.dynamicModelsPrebakeItems;
            case "client.model.dynamic.FileResourcePackAccessor":
            case "client.model.dynamic.AbstractResourcePackAccessor":
                // Only needed to list packs the existence index has not already indexed
                return this.config.dynamicModels && !this.config.resourceExistenceCache;
            default:
                // The dynamic-model mixins and their compat pseudo-mixins all hang off the one switch
                if (name.startsWith("client.model.dynamic.")) {
                    return this.config.dynamicModels;
                }
                Coarctatio.LOGGER.warn("No config switch is wired up for {}, applying it", mixinClassName);
                return true;
        }
    }

    // Null means use the mixin list from the json
    @Override
    public List<String> getMixins() {
        // Only listed when the option is on: Mixin validates every listed target at prepare time, before the plugin is asked, and refuses a config whose target is already loaded
        if (!this.config.recycleEvents) {
            return null;
        }
        // Another coremod may already have pulled one of the targets in, which would fail the whole config rather than just this feature
        IClassTracker tracker = MixinService.getService().getClassTracker();
        for (String target : RECYCLED_EVENT_TARGETS) {
            if (tracker != null && tracker.isClassLoaded(target)) {
                Coarctatio.LOGGER.warn("{} was loaded before mixins could apply; event recycling is off for this launch", target);
                return null;
            }
        }
        return RECYCLED_EVENT_MIXINS;
    }
}
