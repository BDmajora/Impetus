package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.gui.DynamicLightsOptionPages;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void thePageMirrorsTheConfig() {
        OptionPage page = DynamicLightsOptionPages.page();
        assertTrue(OptionPages.exercise(List.of(page)) > 0);
        assertEquals("impetus:dynamic_lights", page.getId().toString());
        List<OptionGroup> groups = page.getGroups();
        assertEquals(2, groups.size());
        assertEquals(5, groups.get(0).getOptions().size());
        assertEquals(2, groups.get(1).getOptions().size());

        // The mode gates every other control on the page
        @SuppressWarnings("unchecked")
        Option<DynamicLightsMode> mode = (Option<DynamicLightsMode>) groups.get(0).getOptions().get(0);
        assertEquals(DynamicLightsMode.REALTIME, mode.getValue());
        Option<?> self = groups.get(0).getOptions().get(1);
        assertTrue(self.isAvailable());
        mode.setValue(DynamicLightsMode.OFF);
        assertFalse(self.isAvailable());
        assertFalse(groups.get(1).getOptions().get(0).isAvailable());
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
        assertNotNull(Mixins.construct(DynamicLightsOptionPages.class));
    }
}
