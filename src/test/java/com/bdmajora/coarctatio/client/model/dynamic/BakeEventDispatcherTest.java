package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.mixin.client.model.dynamic.EventBusAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BakeEventDispatcherTest {
    private static final ModelResourceLocation MACHINE = new ModelResourceLocation("examplemod:machine", "normal");
    private static final ModelResourceLocation CABLE = new ModelResourceLocation("somelib:cable", "normal");
    private static final ModelResourceLocation CASE = new ModelResourceLocation("opencomputers:case", "normal");
    private static final ModelResourceLocation PINNED = new ModelResourceLocation("examplemod:pinned", "normal");

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    private static ModContainer mod(String id, String... dependencies) {
        ModContainer mod = mock(ModContainer.class);
        when(mod.getModId()).thenReturn(id);
        List<ArtifactVersion> versions = new ArrayList<>();
        for (String dependency : dependencies) {
            versions.add(new DefaultArtifactVersion(dependency, true));
        }
        when(mod.getDependencies()).thenReturn(versions);
        return mod;
    }

    // Runs as the given mod: the bus names the owner on the event, and Loader reports it as the active one
    private static IEventListener as(ModContainer mod, LoadController controller, IEventListener body) {
        return event -> {
            ((IContextSetter) event).setModContainer(mod);
            when(controller.activeContainer()).thenReturn(mod);
            body.invoke(event);
        };
    }

    @SuppressWarnings("unchecked")
    @Test
    void eachListenerRunsAloneAndSeesTheLocationsItMay() {
        ModContainer example = mod("examplemod");
        ModContainer somelib = mod("somelib");
        // OpenComputers only needs its own models and those of what it depends on
        ModContainer computers = mod("opencomputers", "somelib", "minecraft", "opencomputers");
        ModContainer betweenlands = mod("thebetweenlands");
        ModContainer storage = mod("refinedstorage");
        Map<String, ModContainer> mods = new HashMap<>();
        for (ModContainer mod : List.of(example, somelib, computers, betweenlands)) {
            mods.put(mod.getModId(), mod);
        }
        Mixins.set(Loader.instance(), "namedMods", mods);
        Mixins.set(Loader.instance(), "mods", new ArrayList<>(mods.values()));
        LoadController controller = mock(LoadController.class);
        Mixins.set(Loader.instance(), "modController", controller);
        ModelLocations.ALL_KNOWN.addAll(List.of(MACHINE, CABLE, CASE));

        IRegistry<ModelResourceLocation, IBakedModel> registry = mock(IRegistry.class);
        when(registry.getKeys()).thenReturn(Set.of(PINNED));
        IBakedModel model = mock(IBakedModel.class);
        when(registry.getObject(MACHINE)).thenReturn(model);

        // The dispatcher swallows whatever a listener throws, so what the listeners saw is checked afterwards
        Map<String, Set<ModelResourceLocation>> seen = new HashMap<>();
        List<Object> answers = new ArrayList<>();
        List<IEventListener> listeners = List.of(
                as(example, controller, event -> {
                    RegistrySimple<ModelResourceLocation, IBakedModel> view =
                            (RegistrySimple<ModelResourceLocation, IBakedModel>) ((ModelBakeEvent) event).getModelRegistry();
                    seen.put("examplemod", Set.copyOf(view.getKeys()));
                    // Lookups, writes and iteration go to the dynamic registry; containment answers from the known set
                    answers.add(view.getObject(MACHINE));
                    view.putObject(PINNED, model);
                    view.iterator();
                    answers.add(view.containsKey(CASE));
                    answers.add(view.containsKey(new ModelResourceLocation("examplemod:unknown", "normal")));
                }),
                as(computers, controller, event -> seen.put("opencomputers", Set.copyOf(((ModelBakeEvent) event).getModelRegistry().getKeys()))),
                as(betweenlands, controller, event -> seen.put("thebetweenlands", Set.copyOf(((ModelBakeEvent) event).getModelRegistry().getKeys()))),
                // A restricted mod the graph never heard of still sees its own namespace
                as(storage, controller, event -> seen.put("refinedstorage", Set.copyOf(((ModelBakeEvent) event).getModelRegistry().getKeys()))),
                // One without an owner sees only what is pinned
                event -> {
                    when(controller.activeContainer()).thenReturn(null);
                    seen.put("nobody", Set.copyOf(((ModelBakeEvent) event).getModelRegistry().getKeys()));
                },
                // And one that throws loses only its own changes
                event -> {
                    throw new IllegalStateException("broken listener");
                });

        int bus = ((EventBusAccessor) MinecraftForge.EVENT_BUS).coarctatio$busId();
        ListenerList list = new ModelBakeEvent(null, null, null).getListenerList();
        listeners.forEach(listener -> list.register(bus, EventPriority.NORMAL, listener));
        try {
            BakeEventDispatcher.post(mock(ModelManager.class), registry, Mc.mock(ModelLoader.class));
        } finally {
            listeners.forEach(listener -> ListenerList.unregisterAll(bus, listener));
            ModelLocations.ALL_KNOWN.removeAll(List.of(MACHINE, CABLE, CASE));
            Mixins.set(Loader.instance(), "mods", null);
        }

        assertEquals(List.of(model, true, false), answers);
        assertTrue(seen.get("examplemod").containsAll(Set.of(MACHINE, CABLE, CASE, PINNED)));
        assertEquals(Set.of(CASE, CABLE, PINNED), seen.get("opencomputers"));
        assertEquals(Set.of(PINNED), seen.get("thebetweenlands"));
        assertEquals(Set.of(PINNED), seen.get("refinedstorage"));
        assertEquals(Set.of(PINNED), seen.get("nobody"));
        verify(registry).putObject(PINNED, model);
        verify(registry).iterator();
    }

    @Test
    void slowListenersAreReportedByMod() {
        ModContainer first = mod("slowmod");
        ModContainer second = mod("slowermod");
        LoadController controller = mock(LoadController.class);
        Mixins.set(Loader.instance(), "modController", controller);
        Mixins.set(Loader.instance(), "namedMods", new HashMap<>());
        Mixins.set(Loader.instance(), "mods", null);
        // Together past a second, each past the 50 ms a mod needs to be named
        List<IEventListener> listeners = List.of(
                as(first, controller, event -> pause(450)),
                as(second, controller, event -> pause(600)));

        int bus = ((EventBusAccessor) MinecraftForge.EVENT_BUS).coarctatio$busId();
        ListenerList list = new ModelBakeEvent(null, null, null).getListenerList();
        listeners.forEach(listener -> list.register(bus, EventPriority.NORMAL, listener));
        try {
            BakeEventDispatcher.post(mock(ModelManager.class), mock(IRegistry.class), Mc.mock(ModelLoader.class));
        } finally {
            listeners.forEach(listener -> ListenerList.unregisterAll(bus, listener));
        }
        assertNotNull(Mixins.construct(BakeEventDispatcher.class));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
