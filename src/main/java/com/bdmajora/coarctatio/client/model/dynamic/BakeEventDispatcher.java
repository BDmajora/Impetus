package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.EventBusAccessor;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// Posts ModelBakeEvent one listener at a time, so a throwing mod loses only its own changes, with a registry view whose keys are every known location and whose lookups bake on demand
public final class BakeEventDispatcher {
    // How much of the location universe a mod sees when it iterates the registry's keys: the default is everything, since that is what vanilla gave; the listed mods walk every key and load every model, and only need their own
    private enum Visibility { NONE, SELF_AND_DEPENDENCIES, EVERYTHING }

    private static final Map<String, Visibility> VISIBILITY = ImmutableMap.<String, Visibility>builder()
            .put("opencomputers", Visibility.SELF_AND_DEPENDENCIES)
            .put("refinedstorage", Visibility.SELF_AND_DEPENDENCIES)
            .put("cabletiers", Visibility.SELF_AND_DEPENDENCIES)
            .put("thebetweenlands", Visibility.NONE)
            .put("ebwizardry", Visibility.SELF_AND_DEPENDENCIES)
            .build();

    private BakeEventDispatcher() {
    }

    public static void post(ModelManager manager, IRegistry<ModelResourceLocation, IBakedModel> registry, ModelLoader loader) {
        WrappingRegistry wrapped = new WrappingRegistry(registry);
        ModelBakeEvent event = new ModelBakeEvent(manager, wrapped, loader);
        int busId = ((EventBusAccessor) MinecraftForge.EVENT_BUS).coarctatio$busId();
        IEventListener[] listeners = event.getListenerList().getListeners(busId);
        ProgressManager.ProgressBar bar = ProgressManager.push("Posting bake events", listeners.length);
        Object2LongOpenHashMap<ModContainer> perMod = new Object2LongOpenHashMap<>();
        long start = System.nanoTime();
        for (IEventListener listener : listeners) {
            bar.step(listener.toString());
            // The bus wrapper for a context-setting event names the owning mod before invoking; cleared so a listener without one is not charged to the previous
            ((IContextSetter) event).setModContainer(null);
            long listenerStart = System.nanoTime();
            try {
                listener.invoke(event);
            } catch (Throwable e) {
                Coarctatio.LOGGER.error("{} listener '{}' threw an exception, its models may be broken", event, listener, e);
            }
            ModContainer mod = ((ContextAwareBakeEvent) event).coarctatio$lastMod();
            if (mod != null) {
                perMod.addTo(mod, System.nanoTime() - listenerStart);
            }
        }
        long total = System.nanoTime() - start;
        if (total >= TimeUnit.SECONDS.toNanos(1)) {
            Coarctatio.LOGGER.warn("Posting ModelBakeEvent to mods took {} ms, the slow ones:", TimeUnit.NANOSECONDS.toMillis(total));
            perMod.object2LongEntrySet().stream()
                    .filter(entry -> TimeUnit.NANOSECONDS.toMillis(entry.getLongValue()) > 50)
                    .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                    .forEach(entry -> Coarctatio.LOGGER.warn("    {}: {} ms", entry.getKey().getModId(), TimeUnit.NANOSECONDS.toMillis(entry.getLongValue())));
        }
        ProgressManager.pop(bar);
    }

    // Implemented on ModelBakeEvent by mixin, alongside IContextSetter, so the mod each listener belongs to is known here
    public interface ContextAwareBakeEvent {
        ModContainer coarctatio$lastMod();
    }

    // getKeys answers the known-location universe the calling mod may see, containsKey answers from it too, and everything else goes to the dynamic registry
    private static final class WrappingRegistry extends RegistrySimple<ModelResourceLocation, IBakedModel> {
        private final IRegistry<ModelResourceLocation, IBakedModel> delegate;
        private final MutableGraph<String> dependencies;

        WrappingRegistry(IRegistry<ModelResourceLocation, IBakedModel> delegate) {
            this.delegate = delegate;
            this.dependencies = dependencyGraph();
        }

        @Nullable
        @Override
        public IBakedModel getObject(ModelResourceLocation name) {
            return this.delegate.getObject(name);
        }

        @Override
        public void putObject(ModelResourceLocation key, IBakedModel value) {
            this.delegate.putObject(key, value);
        }

        @Override
        public Set<ModelResourceLocation> getKeys() {
            Set<ModelResourceLocation> visible = this.visibleTo(Loader.instance().activeModContainer());
            Set<ModelResourceLocation> pinned = this.delegate.getKeys();
            return visible == null ? pinned : Sets.union(visible, pinned);
        }

        @Override
        public boolean containsKey(ModelResourceLocation key) {
            return ModelLocations.ALL_KNOWN.contains(key);
        }

        @Override
        public Iterator<IBakedModel> iterator() {
            return this.delegate.iterator();
        }

        // Mods linked to the ones whose namespaces have models, so a restricted mod still sees the models of what it depends on
        private static MutableGraph<String> dependencyGraph() {
            MutableGraph<String> graph = GraphBuilder.undirected().build();
            for (ModContainer mod : Loader.instance().getModList()) {
                graph.addNode(mod.getModId());
                for (ArtifactVersion dependency : mod.getDependencies()) {
                    graph.addNode(dependency.getLabel());
                }
            }
            Set<String> namespaces = new ObjectOpenHashSet<>();
            for (ModelResourceLocation location : ModelLocations.ALL_KNOWN) {
                namespaces.add(location.getNamespace());
            }
            for (String id : graph.nodes()) {
                ModContainer mod = Loader.instance().getIndexedModList().get(id);
                if (mod == null) {
                    continue;
                }
                for (ArtifactVersion dependency : mod.getDependencies()) {
                    String label = dependency.getLabel();
                    if (!Objects.equals(id, label) && !label.equals("minecraft") && namespaces.contains(label)) {
                        graph.putEdge(id, label);
                    }
                }
            }
            return graph;
        }

        private Set<ModelResourceLocation> visibleTo(ModContainer mod) {
            if (mod == null) {
                return null;
            }
            String id = mod.getModId();
            Visibility visibility = VISIBILITY.getOrDefault(id, Visibility.EVERYTHING);
            if (visibility == Visibility.NONE) {
                return null;
            }
            if (visibility == Visibility.EVERYTHING) {
                return Collections.unmodifiableSet(ModelLocations.ALL_KNOWN);
            }
            Set<String> ids;
            try {
                ids = this.dependencies.adjacentNodes(id);
            } catch (IllegalArgumentException e) {
                ids = ImmutableSet.of();
            }
            Set<String> visibleIds = new ObjectOpenHashSet<>(ids);
            visibleIds.add(id);
            Coarctatio.LOGGER.debug("Mod {} sees models from {}", id, visibleIds);
            return Sets.filter(ModelLocations.ALL_KNOWN, location -> visibleIds.contains(location.getNamespace()));
        }
    }
}
