package com.bdmajora.extras.config;

import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.particle.ParticleFlame;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Covers the particle class lists stored beside the setting groups
class ParticleClassListsTest {
    // Shared registry the lists read into and write from
    private final ParticleClassRegistry registry = ParticleClassRegistry.getInstance();

    // Empties the registry before each case
    @BeforeEach
    void emptyRegistry() {
        RegistryState.clear();
    }

    // Empties the registry after each case
    @AfterEach
    void forgetRegistry() {
        RegistryState.clear();
    }

    // Returns the stored list under the key
    private static String[] stored(Configuration config, String key) {
        return config.getCategory(ParticleClassLists.CATEGORY).get(key).getStringList();
    }

    // Checks that missing keys clear the disabled set and are added with their comments
    @Test
    void missingKeysLoadEmptyListsAndAreAdded() {
        registry.loadDisabledClasses(new String[] {"a.B"});
        Configuration config = new Configuration();
        ParticleClassLists.load(config);
        assertEquals(0, registry.getDisabledClassesArray().length);
        assertEquals(0, registry.getDiscoveredClassesArray().length);
        assertEquals("Particle classes the user has switched off [default: ]",
                config.getCategory(ParticleClassLists.CATEGORY).get("disabledClasses").getComment());
        assertEquals("Cache of discovered particle classes; rebuilt automatically [default: ]",
                config.getCategory(ParticleClassLists.CATEGORY).get("discoveredClasses").getComment());
        assertTrue(config.hasChanged());
    }

    // Checks that both stored lists reach the registry with empty entries skipped
    @Test
    void storedListsAreReadIntoTheRegistry() {
        Configuration config = new Configuration();
        config.get(ParticleClassLists.CATEGORY, "disabledClasses", new String[0]).set(new String[] {"c.D", "", "a.B"});
        config.get(ParticleClassLists.CATEGORY, "discoveredClasses", new String[0]).set(new String[] {"x.Y|Why", "z.W", "", "|bad"});
        ParticleClassLists.load(config);
        assertArrayEquals(new String[] {"a.B", "c.D"}, registry.getDisabledClassesArray());
        Map<String, String> discovered = registry.getDiscoveredClasses();
        assertEquals("Why", discovered.get("x.Y"));
        assertEquals("W", discovered.get("z.W"));
        assertEquals(Set.of("x.Y", "z.W", "|bad"), discovered.keySet());
    }

    // Checks that save writes both sorted lists and that a reload restores them
    @Test
    void saveWritesSortedListsThatReloadUnchanged() {
        registry.loadDisabledClasses(new String[] {"z.Z", "a.A"});
        registry.loadDiscoveredClasses(new String[] {"q.Q|Que", "b.B|Bee"});
        Configuration config = new Configuration();
        ParticleClassLists.save(config);
        assertArrayEquals(new String[] {"a.A", "z.Z"}, stored(config, "disabledClasses"));
        assertArrayEquals(new String[] {"b.B|Bee", "q.Q|Que"}, stored(config, "discoveredClasses"));
        ParticleClassLists.save(config);
        assertArrayEquals(new String[] {"a.A", "z.Z"}, stored(config, "disabledClasses"));
        RegistryState.clear();
        ParticleClassLists.load(config);
        assertArrayEquals(new String[] {"a.A", "z.Z"}, registry.getDisabledClassesArray());
        assertArrayEquals(new String[] {"b.B|Bee", "q.Q|Que"}, registry.getDiscoveredClassesArray());
    }

    // Checks that save clears the dirty flag of the registry
    @Test
    void saveClearsTheDirtyFlag() {
        registry.recordClass(ParticleFlame.class);
        assertTrue(registry.isDirty());
        ParticleClassLists.save(new Configuration());
        assertFalse(registry.isDirty());
    }

    // Checks that a class found after the discovered list was taken leaves the registry dirty
    @Test
    void aClassFoundDuringTheWriteStaysDirty() {
        Configuration config = new Configuration() {
            // Records a class while the missing discovered list property is added
            @Override
            public String[] getStringList(String name, String category, String[] defaultValue, String comment) {
                if (name.equals("discoveredClasses")) {
                    registry.recordClass(ParticleFlame.class);
                }
                return super.getStringList(name, category, defaultValue, comment);
            }
        };
        ParticleClassLists.save(config);
        assertTrue(registry.isDirty());
        assertEquals(0, stored(config, "discoveredClasses").length);
    }

    // Checks that save keeps the comments that load gave both properties
    @Test
    void saveKeepsTheComments() {
        Configuration config = new Configuration();
        ParticleClassLists.load(config);
        registry.loadDisabledClasses(new String[] {"a.B"});
        ParticleClassLists.save(config);
        Property disabled = config.getCategory(ParticleClassLists.CATEGORY).get("disabledClasses");
        assertEquals("Particle classes the user has switched off [default: ]", disabled.getComment());
        assertArrayEquals(new String[] {"a.B"}, disabled.getStringList());
    }

    // Checks that a null config is rejected
    @Test
    void aNullConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> ParticleClassLists.load(null));
        assertThrows(NullPointerException.class, () -> ParticleClassLists.save(null));
    }

    // Checks that the private constructor runs
    @Test
    void theConstructorRuns() {
        assertNotNull(Mixins.construct(ParticleClassLists.class));
    }
}
