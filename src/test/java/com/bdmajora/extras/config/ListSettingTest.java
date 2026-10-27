package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the string list setting against an in memory Forge configuration
class ListSettingTest {
    private static final String CATEGORY = "box";

    // Group with one string list field
    static final class Box {
        String[] names = {"a"};
    }

    // Returns a setting bound to the names of a box with the given default
    private static ListSetting<Box> names(String... defaultValue) {
        return new ListSetting<>("names", defaultValue, "Names", b -> b.names, (b, v) -> b.names = v);
    }

    // Returns the stored list of the names property
    private static String[] stored(Configuration config) {
        return config.getCategory(CATEGORY).get("names").getStringList();
    }

    // Checks that every null part and a null default entry is rejected
    @Test
    void everyNullPartAndANullDefaultEntryIsRejected() {
        String[] none = new String[0];
        assertThrows(NullPointerException.class, () -> new ListSetting<Box>(null, none, "c", b -> b.names, (b, v) -> b.names = v));
        assertThrows(NullPointerException.class, () -> new ListSetting<Box>("k", null, "c", b -> b.names, (b, v) -> b.names = v));
        assertThrows(NullPointerException.class, () -> new ListSetting<Box>("k", none, null, b -> b.names, (b, v) -> b.names = v));
        assertThrows(NullPointerException.class, () -> new ListSetting<Box>("k", none, "c", null, (b, v) -> b.names = v));
        assertThrows(NullPointerException.class, () -> new ListSetting<Box>("k", none, "c", b -> b.names, null));
        assertThrows(NullPointerException.class, () -> names("a", null));
    }

    // Checks that the default is copied on construction
    @Test
    void theDefaultIsCopiedOnConstruction() {
        String[] source = {"x", "y"};
        ListSetting<Box> setting = names(source);
        source[0] = "changed";
        assertArrayEquals(new String[] {"x", "y"}, setting.defaultValue());
        assertNotSame(source, setting.defaultValue());
    }

    // Checks that a missing key loads a copy of the default and is added with its comment
    @Test
    void aMissingKeyLoadsACopyOfTheDefaultAndIsAddedWithItsComment() {
        Configuration config = new Configuration();
        ListSetting<Box> setting = names("x", "y");
        Box box = new Box();
        setting.load(config, CATEGORY, box);
        assertArrayEquals(new String[] {"x", "y"}, box.names);
        assertNotSame(setting.defaultValue(), box.names);
        assertEquals("Names [default: [x], [y]]",config.getCategory(CATEGORY).get("names").getComment());
        box.names[0] = "changed";
        assertArrayEquals(new String[] {"x", "y"}, setting.defaultValue());
    }

    // Checks that empty and single entry lists load as stored
    @Test
    void emptyAndSingleEntryListsLoadAsStored() {
        Configuration config = new Configuration();
        Box box = new Box();
        config.get(CATEGORY, "names", new String[0]).set(new String[0]);
        names("x").load(config, CATEGORY, box);
        assertEquals(0, box.names.length);
        config.get(CATEGORY, "names", new String[0]).set(new String[] {" padded entry "});
        names("x").load(config, CATEGORY, box);
        assertArrayEquals(new String[] {" padded entry "}, box.names);
    }

    // Checks that a scalar stored under the key loads as an empty list
    @Test
    void aScalarStoredUnderTheKeyLoadsAsAnEmptyList() {
        Configuration config = new Configuration();
        config.get(CATEGORY, "names", "scalar");
        Box box = new Box();
        names("x").load(config, CATEGORY, box);
        assertNotNull(box.names);
        assertEquals(0, box.names.length);
    }

    // Checks that save writes a copy of the field and that a load reads it back
    @Test
    void saveWritesACopyOfTheFieldAndRoundTrips() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.names = new String[] {"one", "two", ""};
        names().save(config, config.getCategory(CATEGORY), box);
        assertArrayEquals(new String[] {"one", "two", ""}, stored(config));
        box.names[0] = "changed";
        assertEquals("one", stored(config)[0]);
        Box reread = new Box();
        names().load(config, CATEGORY, reread);
        assertArrayEquals(new String[] {"one", "two", ""}, reread.names);
        box.names = new String[0];
        names().save(config, config.getCategory(CATEGORY), box);
        assertEquals(0, stored(config).length);
    }

    // Checks that save adds a missing property and keeps the comment of a stored one
    @Test
    void saveAddsAMissingPropertyAndKeepsTheCommentOfAStoredOne() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.names = new String[] {"p"};
        names("d").save(config, config.getCategory(CATEGORY), box);
        assertEquals("Names [default: [d]]", config.getCategory(CATEGORY).get("names").getComment());
        assertArrayEquals(new String[] {"p"}, stored(config));
        box.names = new String[] {"q", "r"};
        names("d").save(config, config.getCategory(CATEGORY), box);
        assertEquals("Names [default: [d]]", config.getCategory(CATEGORY).get("names").getComment());
        assertArrayEquals(new String[] {"q", "r"}, stored(config));
    }

    // Checks that a null list or entry or group or section fails a save before the section changes
    @Test
    void aNullListOrNullEntryOnSaveFailsBeforeTheConfigIsTouched() {
        Configuration config = new Configuration();
        Box box = new Box();
        box.names = null;
        ConfigCategory section = config.getCategory(CATEGORY);
        NullPointerException list = assertThrows(NullPointerException.class, () -> names().save(config, section, box));
        assertEquals("names", list.getMessage());
        box.names = new String[] {"ok", null};
        NullPointerException entry = assertThrows(NullPointerException.class, () -> names().save(config, section, box));
        assertTrue(entry.getMessage().contains("names"));
        assertThrows(NullPointerException.class, () -> names().save(config, section, null));
        assertThrows(NullPointerException.class, () -> names().save(config, null, new Box()));
        assertTrue(section.isEmpty());
        assertThrows(NullPointerException.class, () -> names().load(config, CATEGORY, null));
    }

    // Checks that a null config is rejected
    @Test
    void aNullConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> names().load(null, CATEGORY, new Box()));
        assertThrows(NullPointerException.class, () -> names().save(null, new Configuration().getCategory(CATEGORY), new Box()));
    }
}
