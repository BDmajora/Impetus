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

// Posts the model bake event and reports slow listeners.
public final class BakeEventDispatcher {
    // Amount of models that a mod may see.
    private enum Visibility { NONE, SELF_AND_DEPENDENCIES, EVERYTHING }

    // Visibility of mods that need a limit.
    private static final Map<String, Visibility> VISIBILITY = ImmutableMap.<String, Visibility>builder()
            .put("opencomputers", Visibility.SELF_AND_DEPENDENCIES)
            .put("refinedstorage", Visibility.SELF_AND_DEPENDENCIES)
            .put("cabletiers", Visibility.SELF_AND_DEPENDENCIES)
            .put("thebetweenlands", Visibility.NONE)
            .put("ebwizardry", Visibility.SELF_AND_DEPENDENCIES)
            .build();

    // Prevents instantiation
    private BakeEventDispatcher() {
    }

    // Posts the event to each listener and logs the slowest mods.
    public static void post(ModelManager manager, IRegistry<ModelResourceLocation, IBakedModel> registry, ModelLoader loader) {
        // Creates a wrapper around the registry.
        WrappingRegistry wrapped = new WrappingRegistry(registry);
        ModelBakeEvent event = new ModelBakeEvent(manager, wrapped, loader);
        int busId = ((EventBusAccessor) MinecraftForge.EVENT_BUS).coarctatio$busId();
        IEventListener[] listeners = event.getListenerList().getListeners(busId);
        ProgressManager.ProgressBar bar = ProgressManager.push("Posting bake events", listeners.length);
        Object2LongOpenHashMap<ModContainer> perMod = new Object2LongOpenHashMap<>();
        long start = System.nanoTime();
        for (IEventListener listener : listeners) {
            bar.step(listener.toString());
            ((IContextSetter) event).setModContainer(null);
            long listenerStart = System.nanoTime();
            try {
                listener.invoke(event);
            } catch (Throwable e) {
                Coarctatio.LOGGER.error("{} listener '{}' threw an exception, its models may be broken", event, listener, e);
            }
            // Returns the mod of the last listener.
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

    // Event that remembers the mod of its last listener.
    public interface ContextAwareBakeEvent {
        ModContainer coarctatio$lastMod();
    }

    // Registry that limits the model locations a mod can list.
    private static final class WrappingRegistry extends RegistrySimple<ModelResourceLocation, IBakedModel> {
        // Registry that stores the models.
        private final IRegistry<ModelResourceLocation, IBakedModel> delegate;
        // Graph of mod dependencies.
        private final MutableGraph<String> dependencies;

        WrappingRegistry(IRegistry<ModelResourceLocation, IBakedModel> delegate) {
            this.delegate = delegate;
            this.dependencies = dependencyGraph();
        }

        // Returns the model of the location.
        @Nullable
        @Override
        public IBakedModel getObject(ModelResourceLocation name) {
            return this.delegate.getObject(name);
        }

        // Stores the model.
        @Override
        public void putObject(ModelResourceLocation key, IBakedModel value) {
            this.delegate.putObject(key, value);
        }

        // Returns the locations that the active mod may see.
        @Override
        public Set<ModelResourceLocation> getKeys() {
            Set<ModelResourceLocation> visible = this.visibleTo(Loader.instance().activeModContainer());
            Set<ModelResourceLocation> pinned = this.delegate.getKeys();
            return visible == null ? pinned : Sets.union(visible, pinned);
        }

        // Returns whether the location is known.
        @Override
        public boolean containsKey(ModelResourceLocation key) {
            return ModelLocations.ALL_KNOWN.contains(key);
        }

        // Returns an iterator over the stored models.
        @Override
        public Iterator<IBakedModel> iterator() {
            return this.delegate.iterator();
        }

        // Returns the graph of mod dependencies that own models.
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

        // Returns the locations that the mod may see or null for a mod that sees none.
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
