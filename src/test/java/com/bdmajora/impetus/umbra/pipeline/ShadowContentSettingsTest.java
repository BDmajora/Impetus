package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the shadow content switches and the culling mode
class ShadowContentSettingsTest {
    // Returns settings from shaders.properties lines
    private static ShadowContentSettings from(String... lines) {
        return ShadowContentSettings.from(ShaderProperties.parse(String.join("\n", lines)));
    }

    // Checks that missing directives give the defaults
    @Test
    void missingDirectivesGiveTheDefaults() {
        ShadowContentSettings parsed = from();
        ShadowContentSettings defaults = ShadowContentSettings.defaults();
        assertEquals(defaults.toString(), parsed.toString());
        assertTrue(parsed.shouldRenderTerrain());
        assertTrue(parsed.shouldRenderTranslucent());
        assertTrue(parsed.shouldRenderEntities());
        assertTrue(parsed.shouldRenderPlayer());
        assertTrue(parsed.shouldRenderBlockEntities());
        assertFalse(parsed.shouldRenderLightBlockEntitiesOnly());
        assertEquals(ShadowContentSettings.Culling.ON, parsed.getCulling());
    }

    // Checks every combination of the two block entity switches
    @Test
    void theBlockEntitySwitchesCombine() {
        boolean[][] cases = {{false, false}, {false, true}, {true, false}, {true, true}};
        for (boolean[] pair : cases) {
            ShadowContentSettings settings = from("shadowBlockEntities = " + pair[0], "shadowLightBlockEntities = " + pair[1]);
            assertEquals(pair[0], settings.shouldRenderBlockEntities());
            assertEquals(pair[0] || pair[1], settings.shouldRenderAnyBlockEntities());
            assertEquals(!pair[0] && pair[1], settings.shouldRenderLightBlockEntitiesOnly());
        }
    }

    // Checks that each switch reads its own directive
    @Test
    void eachSwitchReadsItsOwnDirective() {
        assertFalse(from("shadowTerrain = false").shouldRenderTerrain());
        assertFalse(from("shadowTranslucent = false").shouldRenderTranslucent());
        assertFalse(from("shadowEntities = false").shouldRenderEntities());
        assertFalse(from("shadowPlayer = false").shouldRenderPlayer());
        assertTrue(from("shadowEntities = false").shouldRenderPlayer());
        assertTrue(from("shadowPlayer = false").shouldRenderEntities());
    }

    // Checks the culling values in any letter case that map to each mode and that anything else means on
    @Test
    void cullingValuesMapToTheirModes() {
        assertEquals(ShadowContentSettings.Culling.OFF, from("shadow.culling = false").getCulling());
        assertEquals(ShadowContentSettings.Culling.OFF, from("shadow.culling = off").getCulling());
        assertEquals(ShadowContentSettings.Culling.REVERSED, from("shadow.culling = reversed").getCulling());
        assertEquals(ShadowContentSettings.Culling.ON, from("shadow.culling = true").getCulling());
        assertEquals(ShadowContentSettings.Culling.OFF, from("shadow.culling = OFF").getCulling());
        assertEquals(ShadowContentSettings.Culling.REVERSED, from("shadow.culling = Reversed").getCulling());
        assertEquals(ShadowContentSettings.Culling.ON, from("shadow.culling = sideways").getCulling());
        assertEquals(ShadowContentSettings.Culling.ON, from("shadow.culling =").getCulling());
    }

    // Checks the exact text of the log line
    @Test
    void theTextListsEverySwitch() {
        assertEquals("terrain=true, translucent=true, entities=true, blockEntities=true, lightBlockEntities=false, "
                + "player=true, culling=ON", ShadowContentSettings.defaults().toString());
        assertEquals("terrain=false, translucent=false, entities=false, blockEntities=false, lightBlockEntities=true, "
                + "player=false, culling=REVERSED", from("shadowTerrain = false", "shadowTranslucent = false",
                "shadowEntities = false", "shadowBlockEntities = false", "shadowLightBlockEntities = true",
                "shadowPlayer = false", "shadow.culling = reversed").toString());
    }

    // Checks that null properties are rejected
    @Test
    void nullPropertiesAreRejected() {
        assertThrows(NullPointerException.class, () -> ShadowContentSettings.from(null));
    }
}
