package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the boolean setting against an in memory Forge configuration
class BooleanSettingTest {
    private static final String CATEGORY = "box";

    // Group with one boolean field
    static final class Box {
        boolean flag = true;
    }

    // Returns a setting bound to the flag of a box with the given default
    private static BooleanSetting<Box> flag(boolean defaultValue) {
        return new BooleanSetting<>("flag", defaultValue, "A flag", b -> b.flag, (b, v) -> b.flag = v);
    }

    // Returns the stored text of the flag property
    private static String stored(Configuration config) {
        return config.getCategory(CATEGORY).get("flag").getString();
    }

    // Checks that every null part is rejected
    @Test
    void everyNullPartIsRejected() {
        assertThrows(NullPointerException.class, () -> new BooleanSetting<Box>(null, true, "c", b -> b.flag, (b, v) -> b.flag = v));
        assertThrows(NullPointerException.class, () -> new BooleanSetting<Box>("k", true, null, b -> b.flag, (b, v) -> b.flag = v));
        assertThrows(NullPointerException.class, () -> new BooleanSetting<Box>("k", true, "c", null, (b, v) -> b.flag = v));
        assertThrows(NullPointerException.class, () -> new BooleanSetting<Box>("k", true, "c", b -> b.flag, null));
    }

    // Checks that a missing key loads the default and is added with its comment
    @Test
    void aMissingKeyLoadsTheDefaultAndIsAddedWithItsComment() {
        Configuration config = new Configuration();
        Box box = new Box();
        flag(false).load(config, CATEGORY, box);
        assertFalse(box.flag);
        assertEquals("false", stored(config));
        assertEquals("A flag [default: false]", config.getCategory(CATEGORY).get("flag").getComment());
        assertTrue(config.hasChanged());
    }

    // Checks that stored text is read without regard to case
    @Test
    void storedTextIsReadWithoutRegardToCase() {
        Configuration config = new Configuration();
        Box box = new Box();
        for (String text : new String[] {"true", "TRUE", "True"}) {
            box.flag = false;
            config.get(CATEGORY, "flag", false).set(text);
            flag(false).load(config, CATEGORY, box);
            assertTrue(box.flag, text);
        }
        config.get(CATEGORY, "flag", true).set("FALSE");
        flag(true).load(config, CATEGORY, box);
        assertFalse(box.flag);
    }

    // Checks that unreadable text loads the default
    @Test
    void unreadableTextLoadsTheDefault() {
        Configuration config = new Configuration();
        Box box = new Box();
        for (String text : new String[] {"", "yes", "1", " true", "truee"}) {
            config.get(CATEGORY, "flag", true).set(text);
            box.flag = true;
            flag(false).load(config, CATEGORY, box);
            assertFalse(box.flag, text);
        }
    }

    // Checks that a list stored under the key loads the default
    @Test
    void aListStoredUnderTheKeyLoadsTheDefault() {
        Configuration config = new Configuration();
        config.get(CATEGORY, "flag", new String[] {"true"});
        Box box = new Box();
        flag(false).load(config, CATEGORY, box);
        assertFalse(box.flag);
    }

    // Checks that save writes the field and that a load reads it back
    @Test
    void saveWritesTheFieldAndRoundTrips() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.flag = false;
        flag(true).save(config, config.getCategory(CATEGORY), box);
        assertEquals("false", stored(config));
        flag(true).save(config, config.getCategory(CATEGORY), box);
        assertEquals("false", stored(config));
        Box reread = new Box();
        flag(true).load(config, CATEGORY, reread);
        assertFalse(reread.flag);
        box.flag = true;
        flag(true).save(config, config.getCategory(CATEGORY), box);
        assertEquals("true", stored(config));
    }

    // Checks that save adds a missing property with its default and comment
    @Test
    void saveAddsAMissingPropertyWithItsDefaultAndComment() {
        Configuration config = new Configuration();
        flag(false).save(config, config.getCategory(CATEGORY), new Box());
        Property property = config.getCategory(CATEGORY).get("flag");
        assertEquals("true", property.getString());
        assertEquals("false", property.getDefault());
        assertEquals("A flag [default: false]", property.getComment());
    }

    // Checks that save keeps the comment of a stored property
    @Test
    void saveKeepsTheCommentOfAStoredProperty() {
        Configuration config = new Configuration();
        Box box = new Box();
        flag(true).load(config, CATEGORY, box);
        box.flag = false;
        flag(true).save(config, config.getCategory(CATEGORY), box);
        Property property = config.getCategory(CATEGORY).get("flag");
        assertEquals("A flag [default: true]", property.getComment());
        assertEquals("false", property.getString());
        Property bare = config.get(CATEGORY, "bare", true);
        new BooleanSetting<Box>("bare", true, "Bare", b -> b.flag, (b, v) -> b.flag = v).save(config, config.getCategory(CATEGORY), box);
        assertSame(bare, config.getCategory(CATEGORY).get("bare"));
        assertNull(bare.getComment());
    }

    // Checks that create adds the property once and keeps a stored value
    @Test
    void createAddsThePropertyWithoutTouchingAnyGroup() {
        Configuration config = new Configuration();
        flag(false).create(config, CATEGORY);
        assertEquals("false", stored(config));
        flag(true).create(config, CATEGORY);
        assertEquals("false", stored(config));
        assertEquals("A flag [default: true]", config.getCategory(CATEGORY).get("flag").getComment());
    }

    // Checks that a null group or section fails a save before the section changes
    @Test
    void aNullGroupOnSaveFailsBeforeTheConfigIsTouched() {
        Configuration config = new Configuration();
        ConfigCategory section = config.getCategory(CATEGORY);
        assertThrows(NullPointerException.class, () -> flag(true).save(config, section, null));
        assertThrows(NullPointerException.class, () -> flag(true).save(config, null, new Box()));
        assertTrue(section.isEmpty());
        assertThrows(NullPointerException.class, () -> flag(true).load(config, CATEGORY, null));
    }

    // Checks that a null config is rejected
    @Test
    void aNullConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> flag(true).load(null, CATEGORY, new Box()));
        assertThrows(NullPointerException.class, () -> flag(true).save(null, new Configuration().getCategory(CATEGORY), new Box()));
    }

    // Checks that the same key in two categories stays apart
    @Test
    void theSameKeyInTwoCategoriesStaysApart() {
        Configuration config = new Configuration();
        Box first = new Box();
        Box second = new Box();
        second.flag = false;
        flag(true).save(config, config.getCategory("first"), first);
        flag(true).save(config, config.getCategory("second"), second);
        Box a = new Box();
        Box b = new Box();
        flag(false).load(config, "first", a);
        flag(true).load(config, "second", b);
        assertTrue(a.flag);
        assertFalse(b.flag);
    }
}
