package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the enum setting against an in memory Forge configuration
class ChoiceSettingTest {
    private static final String CATEGORY = "box";

    // Choice with three constants
    enum Mode { FIRST, SECOND, THIRD }

    // Choice with one constant
    enum Solo { ONLY }

    // Choice whose constants carry their own bodies
    enum Body {
        LEFT {
            // Returns minus one
            @Override
            int sign() {
                return -1;
            }
        },
        RIGHT {
            // Returns one
            @Override
            int sign() {
                return 1;
            }
        };

        // Returns the sign of the side
        abstract int sign();
    }

    // Group with one field of each choice
    static final class Box {
        Mode mode = Mode.FIRST;
        Solo solo = Solo.ONLY;
        Body body = Body.LEFT;
    }

    // Returns a setting bound to the mode of a box
    private static ChoiceSetting<Box, Mode> mode(Mode defaultValue) {
        return new ChoiceSetting<>("mode", defaultValue, "A mode", b -> b.mode, (b, v) -> b.mode = v);
    }

    // Returns the mode a box holds after it loads the stored text
    private static Mode loaded(ChoiceSetting<Box, Mode> setting, String text) {
        Configuration config = new Configuration();
        config.get(CATEGORY, "mode", 0).set(text);
        Box box = new Box();
        box.mode = null;
        setting.load(config, CATEGORY, box);
        return box.mode;
    }

    // Checks that every null part is rejected
    @Test
    void everyNullPartIsRejected() {
        Mode[] values = Mode.values();
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>(null, Mode.FIRST, "c", b -> b.mode, (b, v) -> b.mode = v, values));
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>("k", null, "c", b -> b.mode, (b, v) -> b.mode = v, values));
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, null, b -> b.mode, (b, v) -> b.mode = v, values));
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, "c", null, (b, v) -> b.mode = v, values));
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, "c", b -> b.mode, null, values));
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, "c", b -> b.mode, (b, v) -> b.mode = v, null));
        assertThrows(NullPointerException.class, () -> new ChoiceSetting<Box, Mode>("k", null, "c", b -> b.mode, (b, v) -> b.mode = v));
    }

    // Checks that a value table without the default at its ordinal is rejected
    @Test
    void aValueTableWithoutTheDefaultAtItsOrdinalIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, "c",
                b -> b.mode, (b, v) -> b.mode = v, new Mode[0]));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.THIRD, "c",
                b -> b.mode, (b, v) -> b.mode = v, new Mode[] {Mode.FIRST, Mode.SECOND}));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, "c",
                b -> b.mode, (b, v) -> b.mode = v, new Mode[] {Mode.SECOND, Mode.FIRST, Mode.THIRD}));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceSetting<Box, Mode>("k", Mode.FIRST, "c",
                b -> b.mode, (b, v) -> b.mode = v, new Mode[] {null, Mode.SECOND}));
    }

    // Checks that the short constructor takes every constant of the enum
    @Test
    void theShortConstructorTakesEveryConstantOfTheEnum() {
        assertArrayEquals(Mode.values(), mode(Mode.SECOND).values());
        assertArrayEquals(Body.values(), new ChoiceSetting<Box, Body>("body", Body.RIGHT, "c",
                b -> b.body, (b, v) -> b.body = v).values());
    }

    // Checks that a missing key loads the default and is added with the ordinal range
    @Test
    void aMissingKeyLoadsTheDefaultAndIsAddedWithTheOrdinalRange() {
        Configuration config = new Configuration();
        Box box = new Box();
        mode(Mode.SECOND).load(config, CATEGORY, box);
        assertEquals(Mode.SECOND, box.mode);
        assertEquals("1", config.getCategory(CATEGORY).get("mode").getString());
        assertEquals("A mode [range: 0 ~ 2, default: 1]", config.getCategory(CATEGORY).get("mode").getComment());
    }

    // Checks that every ordinal loads its constant
    @Test
    void everyOrdinalLoadsItsConstant() {
        for (Mode value : Mode.values()) {
            assertEquals(value, loaded(mode(Mode.FIRST), Integer.toString(value.ordinal())));
        }
    }

    // Checks that ordinals outside the constants are clamped to the nearest end
    @Test
    void ordinalsOutsideTheConstantsAreClampedToTheNearestEnd() {
        assertEquals(Mode.FIRST, loaded(mode(Mode.SECOND), "-1"));
        assertEquals(Mode.THIRD, loaded(mode(Mode.SECOND), "3"));
        assertEquals(Mode.FIRST, loaded(mode(Mode.SECOND), Integer.toString(Integer.MIN_VALUE)));
        assertEquals(Mode.THIRD, loaded(mode(Mode.SECOND), Integer.toString(Integer.MAX_VALUE)));
    }

    // Checks that unreadable text or text beyond the int limits loads the default
    @Test
    void unreadableOrOverflowingTextLoadsTheDefault() {
        for (String text : new String[] {"", "SECOND", "1.0", "2147483648", "-2147483649"}) {
            assertEquals(Mode.THIRD, loaded(mode(Mode.THIRD), text), text);
        }
    }

    // Checks that an enum with one constant always loads that constant
    @Test
    void aSingleConstantEnumAlwaysLoadsThatConstant() {
        ChoiceSetting<Box, Solo> setting = new ChoiceSetting<>("solo", Solo.ONLY, "c", b -> b.solo, (b, v) -> b.solo = v);
        for (String text : new String[] {"0", "1", "-1", "junk"}) {
            Configuration config = new Configuration();
            config.get(CATEGORY, "solo", 0).set(text);
            Box box = new Box();
            box.solo = null;
            setting.load(config, CATEGORY, box);
            assertEquals(Solo.ONLY, box.solo, text);
        }
    }

    // Checks that constants with their own bodies load and save by ordinal
    @Test
    void constantsWithBodiesLoadAndSaveByOrdinal() {
        ChoiceSetting<Box, Body> setting = new ChoiceSetting<>("body", Body.LEFT, "c", b -> b.body, (b, v) -> b.body = v);
        Configuration config = new Configuration();
        Box box = new Box();
        box.body = Body.RIGHT;
        setting.save(config, config.getCategory(CATEGORY), box);
        assertEquals("1", config.getCategory(CATEGORY).get("body").getString());
        Box reread = new Box();
        setting.load(config, CATEGORY, reread);
        assertSame(Body.RIGHT, reread.body);
        assertEquals(1, reread.body.sign());
    }

    // Checks that save writes the ordinal and that a load reads it back
    @Test
    void saveWritesTheOrdinalAndRoundTrips() {
        Configuration config = new Configuration();
        ChoiceSetting<Box, Mode> setting = mode(Mode.FIRST);
        for (Mode value : Mode.values()) {
            Box box = new Box();
            box.mode = value;
            setting.save(config, config.getCategory(CATEGORY), box);
            assertEquals(Integer.toString(value.ordinal()), config.getCategory(CATEGORY).get("mode").getString());
            Box reread = new Box();
            setting.load(config, CATEGORY, reread);
            assertEquals(value, reread.mode);
        }
    }

    // Checks that save adds a missing property and keeps the comment of a stored one
    @Test
    void saveAddsAMissingPropertyAndKeepsTheCommentOfAStoredOne() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.mode = Mode.THIRD;
        mode(Mode.SECOND).save(config, config.getCategory(CATEGORY), box);
        assertEquals("A mode [range: 0 ~ 2, default: 1]", config.getCategory(CATEGORY).get("mode").getComment());
        assertEquals("2", config.getCategory(CATEGORY).get("mode").getString());
        box.mode = Mode.FIRST;
        mode(Mode.SECOND).save(config, config.getCategory(CATEGORY), box);
        assertEquals("A mode [range: 0 ~ 2, default: 1]", config.getCategory(CATEGORY).get("mode").getComment());
        assertEquals("0", config.getCategory(CATEGORY).get("mode").getString());
    }

    // Checks that a null field or group or section fails a save before the section changes
    @Test
    void aNullFieldOrGroupOnSaveFailsBeforeTheConfigIsTouched() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.mode = null;
        ConfigCategory section = config.getCategory(CATEGORY);
        assertThrows(NullPointerException.class, () -> mode(Mode.FIRST).save(config, section, box));
        assertThrows(NullPointerException.class, () -> mode(Mode.FIRST).save(config, section, null));
        assertThrows(NullPointerException.class, () -> mode(Mode.FIRST).save(config, null, new Box()));
        assertTrue(section.isEmpty());
        assertThrows(NullPointerException.class, () -> mode(Mode.FIRST).load(config, CATEGORY, null));
    }

    // Checks that a null config is rejected
    @Test
    void aNullConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> mode(Mode.FIRST).load(null, CATEGORY, new Box()));
        assertThrows(NullPointerException.class, () -> mode(Mode.FIRST).save(null, new Configuration().getCategory(CATEGORY), new Box()));
    }
}
