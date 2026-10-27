package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the integer setting against an in memory Forge configuration
class IntSettingTest {
    private static final String CATEGORY = "box";

    // Group with one integer field
    static final class Box {
        int number = 7;
    }

    // Returns a setting bound to the number of a box
    private static IntSetting<Box> number(int defaultValue, int min, int max) {
        return new IntSetting<>("number", defaultValue, min, max, "A number", b -> b.number, (b, v) -> b.number = v);
    }

    // Returns the number a box holds after it loads the stored text
    private static int loaded(IntSetting<Box> setting, String text) {
        Configuration config = new Configuration();
        config.get(CATEGORY, "number", setting.defaultValue()).set(text);
        Box box = new Box();
        setting.load(config, CATEGORY, box);
        return box.number;
    }

    // Checks that every null part is rejected
    @Test
    void everyNullPartIsRejected() {
        assertThrows(NullPointerException.class, () -> new IntSetting<Box>(null, 0, 0, 1, "c", b -> b.number, (b, v) -> b.number = v));
        assertThrows(NullPointerException.class, () -> new IntSetting<Box>("k", 0, 0, 1, null, b -> b.number, (b, v) -> b.number = v));
        assertThrows(NullPointerException.class, () -> new IntSetting<Box>("k", 0, 0, 1, "c", null, (b, v) -> b.number = v));
        assertThrows(NullPointerException.class, () -> new IntSetting<Box>("k", 0, 0, 1, "c", b -> b.number, null));
    }

    // Checks that a default outside the range or an inverted range is rejected
    @Test
    void aDefaultOutsideTheRangeOrAnInvertedRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> number(-1, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> number(11, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> number(5, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> number(Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> number(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE - 1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> number(-1, 0, 10));
        assertTrue(e.getMessage().contains("number"));
    }

    // Checks that both edges of a range and the int limits are accepted as defaults
    @Test
    void theRangeEdgesAreAcceptedAsDefaults() {
        assertEquals(0, number(0, 0, 10).defaultValue());
        assertEquals(10, number(10, 0, 10).defaultValue());
        assertEquals(4, number(4, 4, 4).defaultValue());
        assertEquals(Integer.MIN_VALUE, number(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE).defaultValue());
        assertEquals(Integer.MAX_VALUE, number(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE).defaultValue());
    }

    // Checks that a missing key loads the default and is added with its range
    @Test
    void aMissingKeyLoadsTheDefaultAndIsAddedWithItsRange() {
        Configuration config = new Configuration();
        Box box = new Box();
        number(3, 1, 9).load(config, CATEGORY, box);
        assertEquals(3, box.number);
        assertEquals("3", config.getCategory(CATEGORY).get("number").getString());
        assertEquals("A number [range: 1 ~ 9, default: 3]", config.getCategory(CATEGORY).get("number").getComment());
    }

    // Checks that every value inside the range loads exactly
    @Test
    void valuesInsideTheRangeLoadExactly() {
        IntSetting<Box> setting = number(5, 0, 10);
        for (int value = 0; value <= 10; value++) {
            assertEquals(value, loaded(setting, Integer.toString(value)));
        }
    }

    // Checks that values outside the range are clamped to the nearest edge
    @Test
    void valuesOutsideTheRangeAreClampedToTheNearestEdge() {
        IntSetting<Box> setting = number(5, 0, 10);
        assertEquals(10, loaded(setting, "11"));
        assertEquals(0, loaded(setting, "-1"));
        assertEquals(10, loaded(setting, Integer.toString(Integer.MAX_VALUE)));
        assertEquals(0, loaded(setting, Integer.toString(Integer.MIN_VALUE)));
    }

    // Checks that the int limits load exactly over the full int range
    @Test
    void theIntLimitsLoadExactlyOverTheFullRange() {
        IntSetting<Box> setting = number(0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, loaded(setting, "2147483647"));
        assertEquals(Integer.MIN_VALUE, loaded(setting, "-2147483648"));
        assertEquals(-1, loaded(setting, "-1"));
    }

    // Checks that text beyond the int limits loads the default
    @Test
    void textThatOverflowsAnIntLoadsTheDefault() {
        IntSetting<Box> setting = number(5, 0, 10);
        assertEquals(5, loaded(setting, "2147483648"));
        assertEquals(5, loaded(setting, "-2147483649"));
        assertEquals(5, loaded(setting, "99999999999999999999"));
        assertEquals(5, loaded(setting, Long.toString(Long.MIN_VALUE)));
    }

    // Checks that text other than a decimal int loads the default
    @Test
    void textThatIsNotADecimalIntLoadsTheDefault() {
        IntSetting<Box> setting = number(5, 0, 10);
        for (String text : new String[] {"", " 3", "3 ", "3.0", "0x3", "three", "+", "-"}) {
            assertEquals(5, loaded(setting, text), text);
        }
        assertEquals(3, loaded(setting, "+3"));
        assertEquals(3, loaded(setting, "003"));
    }

    // Checks that a range of one value always loads that value
    @Test
    void aSingleValueRangeAlwaysLoadsThatValue() {
        IntSetting<Box> setting = number(4, 4, 4);
        assertEquals(4, loaded(setting, "0"));
        assertEquals(4, loaded(setting, "100"));
        assertEquals(4, loaded(setting, "junk"));
    }

    // Checks that save writes a value outside the range and the next load clamps it
    @Test
    void saveWritesTheFieldEvenOutsideTheRangeAndTheNextLoadClampsIt() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.number = 99;
        IntSetting<Box> setting = number(5, 0, 10);
        setting.save(config, config.getCategory(CATEGORY), box);
        assertEquals("99", config.getCategory(CATEGORY).get("number").getString());
        setting.load(config, CATEGORY, box);
        assertEquals(10, box.number);
        box.number = Integer.MIN_VALUE;
        setting.save(config, config.getCategory(CATEGORY), box);
        assertEquals("-2147483648", config.getCategory(CATEGORY).get("number").getString());
    }

    // Checks that save adds a missing property and keeps the comment of a stored one
    @Test
    void saveAddsAMissingPropertyAndKeepsTheCommentOfAStoredOne() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.number = 6;
        IntSetting<Box> setting = number(5, 0, 10);
        setting.save(config, config.getCategory(CATEGORY), box);
        assertEquals("A number [range: 0 ~ 10, default: 5]", config.getCategory(CATEGORY).get("number").getComment());
        assertEquals("6", config.getCategory(CATEGORY).get("number").getString());
        setting.load(config, CATEGORY, box);
        box.number = 2;
        setting.save(config, config.getCategory(CATEGORY), box);
        assertEquals("A number [range: 0 ~ 10, default: 5]", config.getCategory(CATEGORY).get("number").getComment());
        assertEquals("2", config.getCategory(CATEGORY).get("number").getString());
    }

    // Checks that repeated saves and loads round trip
    @Test
    void repeatedSavesAndLoadsRoundTrip() {
        Configuration config = new Configuration();
        IntSetting<Box> setting = number(5, 0, 10);
        Box box = new Box();
        box.number = 8;
        setting.save(config, config.getCategory(CATEGORY), box);
        setting.save(config, config.getCategory(CATEGORY), box);
        Box reread = new Box();
        setting.load(config, CATEGORY, reread);
        setting.load(config, CATEGORY, reread);
        assertEquals(8, reread.number);
    }

    // Checks that a null group or section fails a save before the section changes
    @Test
    void aNullGroupOnSaveFailsBeforeTheConfigIsTouched() {
        Configuration config = new Configuration();
        ConfigCategory section = config.getCategory(CATEGORY);
        assertThrows(NullPointerException.class, () -> number(5, 0, 10).save(config, section, null));
        assertThrows(NullPointerException.class, () -> number(5, 0, 10).save(config, null, new Box()));
        assertTrue(section.isEmpty());
        assertThrows(NullPointerException.class, () -> number(5, 0, 10).load(config, CATEGORY, null));
    }

    // Checks that a null config is rejected
    @Test
    void aNullConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> number(5, 0, 10).load(null, CATEGORY, new Box()));
        assertThrows(NullPointerException.class, () -> number(5, 0, 10).save(null, new Configuration().getCategory(CATEGORY), new Box()));
    }
}
