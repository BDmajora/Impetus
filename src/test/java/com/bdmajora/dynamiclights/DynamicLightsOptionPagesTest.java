package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.LightSourceSettings;
import com.bdmajora.dynamiclights.gui.DynamicLightsOptionPages;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class DynamicLightsOptionPagesTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
        Mc.client();
    }

    @BeforeEach
    void freshConfig() {
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
    }

    // Reads an option and writes the same value back, so both halves of its binding run
    @SuppressWarnings("unchecked")
    private static <T> void touch(Option<T> option) {
        T value = option.getValue();
        option.setValue(value);
        option.applyChanges();
    }

    @Test
    void theSettingsPageMirrorsTheConfig() {
        // One registered entity type, so the per-type page has rows to build
        // Forge's real registry cannot be written to outside mod loading, so the page reads a stand-in
        net.minecraftforge.fml.common.registry.EntityEntry entry =
                new net.minecraftforge.fml.common.registry.EntityEntry(net.minecraft.entity.monster.EntityZombie.class, "Zombie");
        entry.setRegistryName(new net.minecraft.util.ResourceLocation("minecraft", "zombie"));
        net.minecraftforge.fml.common.registry.EntityEntry unnamed =
                new net.minecraftforge.fml.common.registry.EntityEntry(net.minecraft.entity.monster.EntityCreeper.class, "");
        @SuppressWarnings("unchecked")
        net.minecraftforge.registries.IForgeRegistry<net.minecraftforge.fml.common.registry.EntityEntry> entities =
                org.mockito.Mockito.mock(net.minecraftforge.registries.IForgeRegistry.class);
        org.mockito.Mockito.when(entities.getValuesCollection()).thenReturn(List.of(entry, unnamed));
        com.bdmajora.testing.Statics.set(net.minecraftforge.fml.common.registry.ForgeRegistries.class, "ENTITIES", entities);

        List<OptionPage> pages = DynamicLightsOptionPages.pages();
        assertEquals(2, pages.size());
        assertEquals("dynamiclights", pages.get(0).getId().getPath());
        assertEquals("dynamiclights_sources", pages.get(1).getId().getPath());
        List<OptionGroup> groups = pages.get(0).getGroups();
        assertEquals(2, groups.size());
        assertEquals(6, groups.get(0).getOptions().size());
        assertEquals(2, groups.get(1).getOptions().size());

        // The master switch gates every other control on the page
        @SuppressWarnings("unchecked")
        Option<DynamicLightsMode> mode = (Option<DynamicLightsMode>) groups.get(0).getOptions().get(0);
        assertEquals(DynamicLightsMode.REALTIME, mode.getValue());
        Option<?> self = groups.get(0).getOptions().get(1);
        assertTrue(self.isAvailable());
        mode.setValue(DynamicLightsMode.OFF);
        assertFalse(self.isAvailable());
        assertFalse(groups.get(1).getOptions().get(0).isAvailable());
        // The debug line is readable even with the feature switched off
        assertTrue(groups.get(0).getOptions().get(5).isAvailable());
        mode.setValue(DynamicLightsMode.FAST);
        mode.applyChanges();
        assertEquals(DynamicLightsMode.FAST, DynamicLights.options().mode);

        @SuppressWarnings("unchecked")
        Option<Boolean> water = (Option<Boolean>) groups.get(0).getOptions().get(4);
        water.setValue(false);
        water.applyChanges();
        assertFalse(DynamicLights.options().waterSensitiveCheck);
        @SuppressWarnings("unchecked")
        Option<ExplosiveLightingMode> tnt = (Option<ExplosiveLightingMode>) groups.get(1).getOptions().get(0);
        tnt.setValue(ExplosiveLightingMode.SIMPLE);
        tnt.applyChanges();
        assertEquals(ExplosiveLightingMode.SIMPLE, DynamicLights.options().tntLighting);

        // Every binding on both pages reads and writes its own setting
        for (OptionPage page : pages) {
            for (OptionGroup group : page.getGroups()) {
                for (Option<?> option : group.getOptions()) {
                    touch(option);
                }
            }
        }
        assertFalse(pages.get(1).getGroups().isEmpty());
    }

    @Test
    void perTypeTogglesAreGroupedByTheModThatOwnsThem() {
        // The registries are empty here, so the rows are built from a supplied table instead
        Map<String, String> types = new LinkedHashMap<>();
        types.put("minecraft:creeper", "Creeper");
        types.put("somemod:lamp", "Lamp");
        types.put("nocolon", "Odd One");
        List<OptionGroup> groups = new ArrayList<>();
        BooleanSupplier enabled = () -> true;
        Mixins.call(DynamicLightsOptionPages.class, "addTypeGroups", groups, "entity", types, enabled,
                (java.util.function.BiPredicate<LightSourceSettings, String>)
                        (settings, id) -> !settings.isEntityTypeDisabled(id),
                typeSetter());
        assertEquals(3, groups.size());
        // Vanilla rows drop the namespace suffix, modded ones keep it
        assertEquals("Creeper", label(groups, "minecraft", 0));
        assertEquals("Lamp (somemod)", label(groups, "somemod", 0));
        assertEquals("Odd One (unknown)", label(groups, "unknown", 0));

        @SuppressWarnings("unchecked")
        Option<Boolean> creeper = (Option<Boolean>) group(groups, "minecraft").getOptions().get(0);
        assertTrue(creeper.getValue());
        creeper.setValue(false);
        creeper.applyChanges();
        assertTrue(LightSourceSettings.getInstance().isEntityTypeDisabled("minecraft:creeper"));
        creeper.setValue(true);
        creeper.applyChanges();
        assertFalse(LightSourceSettings.getInstance().isEntityTypeDisabled("minecraft:creeper"));

        // An empty table contributes nothing
        groups.clear();
        Mixins.call(DynamicLightsOptionPages.class, "addTypeGroups", groups, "entity", Map.of(), enabled,
                (java.util.function.BiPredicate<LightSourceSettings, String>)
                        (settings, id) -> true, typeSetter());
        assertTrue(groups.isEmpty());
    }

    // The private functional interface the page uses for "switch this type on or off"
    private static Object typeSetter() {
        Class<?> setter = java.util.Arrays.stream(DynamicLightsOptionPages.class.getDeclaredClasses())
                .filter(Class::isInterface)
                .findFirst()
                .orElseThrow();
        return java.lang.reflect.Proxy.newProxyInstance(setter.getClassLoader(), new Class<?>[] {setter},
                (proxy, method, args) -> {
                    LightSourceSettings.getInstance().setEntityTypeEnabled((String) args[1], (Boolean) args[2]);
                    return null;
                });
    }

    private static OptionGroup group(List<OptionGroup> groups, String namespace) {
        return groups.stream()
                .filter(group -> group.getId().getPath().endsWith(namespace))
                .findFirst()
                .orElseThrow();
    }

    private static String label(List<OptionGroup> groups, String namespace, int index) {
        return group(groups, namespace).getOptions().get(index).getName().toString();
    }
}
