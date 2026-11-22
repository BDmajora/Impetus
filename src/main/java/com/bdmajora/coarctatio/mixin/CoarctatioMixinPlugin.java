package com.bdmajora.coarctatio.mixin;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.impetus.core.SimpleMixinPlugin;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

// Class coarctatio mixin plugin.
public class CoarctatioMixinPlugin extends SimpleMixinPlugin {
    // Record guarded.
    private record Guarded(Predicate<CoarctatioConfig> enabled, String feature, List<String> mixins, List<String> targets) {
    }

    // Mixin groups that are only listed when their switch is on and none of their targets loaded before mixins could apply.
    private static final List<Guarded> GUARDED = List.of(
            new Guarded(config -> config.recycleEvents, "event recycling",
                    List.of("events.TickEventMixin", "events.PlayerTickEventMixin", "events.WorldTickEventMixin",
                            "events.RenderTickEventMixin", "events.AttachCapabilitiesEventMixin", "events.BlockEventMixin",
                            "events.NeighborNotifyEventMixin", "events.FMLCommonHandlerMixin", "events.ForgeEventFactoryMixin"),
                    List.of("net.minecraftforge.fml.common.gameevent.TickEvent", "net.minecraftforge.event.AttachCapabilitiesEvent",
                            "net.minecraftforge.event.world.BlockEvent", "net.minecraftforge.fml.common.FMLCommonHandler",
                            "net.minecraftforge.event.ForgeEventFactory")),
            new Guarded(config -> config.internLoaderStrings, "annotation string interning",
                    List.of("forge.ASMDataMixin"), List.of("net.minecraftforge.fml.common.discovery.ASMDataTable$ASMData")),
            new Guarded(config -> config.internLoaderStrings, "package name interning",
                    List.of("forge.ModCandidateMixin"), List.of("net.minecraftforge.fml.common.discovery.ModCandidate")));

    // Package of the Coarctatio mixins.
    private static final String PACKAGE = "com.bdmajora.coarctatio.mixin.";

    // Loaded config.
    private CoarctatioConfig config;

    // Reads the config once.
    @Override
    public void onLoad(String mixinPackage) {
        this.config = CoarctatioConfig.get();
    }

    // Maps the mixin's simple name to its switch and refuses anything unlisted.
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.startsWith(PACKAGE)
                ? mixinClassName.substring(PACKAGE.length())
                : mixinClassName;

        switch (name) {
            case "util.ResourceLocationMixin":
                return this.config.deduplicateResourceLocations;
            case "nbt.NBTTagCompoundMixin":
                return this.config.compactNbtBackingMap;
            case "world.AnvilChunkLoaderMixin":
                return this.config.stripChunkNbt;
            case "world.ChunkMixin":
                return this.config.dropEmptyChunkSections;
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
            case "client.ModelManagerMixin":
            case "client.model.part.BlockPartMixin":
            case "client.model.part.BlockFaceUVMixin":
            case "client.model.part.ModelBlockMixin":
                return this.config.canonicalizeModelParts;
            case "forge.ASMDataMixin":
            case "forge.ModCandidateMixin":
                return this.config.internLoaderStrings;
            default:
                Coarctatio.LOGGER.warn("No config switch is wired up for {}, applying it", mixinClassName);
                return true;
        }
    }

    // Returns the mixins.
    @Override
    public List<String> getMixins() {
        IClassTracker tracker = MixinService.getService().getClassTracker();
        List<String> mixins = new ArrayList<>();
        for (Guarded group : GUARDED) {
            if (!group.enabled().test(this.config)) {
                continue;
            }
            String early = tracker == null ? null : group.targets().stream().filter(tracker::isClassLoaded).findFirst().orElse(null);
            if (early != null) {
                Coarctatio.LOGGER.warn("{} was loaded before mixins could apply; {} is off for this launch", early, group.feature());
                continue;
            }
            mixins.addAll(group.mixins());
        }
        return mixins.isEmpty() ? null : mixins;
    }
}
