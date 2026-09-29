package com.bdmajora.impetus.impl.config;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ConfigPropertyTest {
    private enum Shape { ROUND, SQUARE }

    @Test
    void eachEntryIsAValueOfItsBinding() {
        Consumer<Boolean> setFlag = value -> {};
        Supplier<Boolean> getFlag = () -> true;
        ConfigProperty.BooleanProperty flag = (ConfigProperty.BooleanProperty) ConfigProperty.bool("render", "flag", true, "A flag", setFlag, getFlag);
        assertEquals("render", flag.category());
        assertEquals("flag", flag.key());
        assertTrue(flag.defaultValue());
        assertEquals("A flag", flag.comment());
        assertSame(setFlag, flag.setter());
        assertSame(getFlag, flag.getter());
        assertEquals(new ConfigProperty.BooleanProperty("render", "flag", true, "A flag", setFlag, getFlag), flag);
        assertEquals(new ConfigProperty.BooleanProperty("render", "flag", true, "A flag", setFlag, getFlag).hashCode(), flag.hashCode());
        assertNotEquals(new ConfigProperty.BooleanProperty("render", "other", true, "A flag", setFlag, getFlag), flag);
        assertTrue(flag.toString().contains("flag"));

        Consumer<Integer> setCount = value -> {};
        Supplier<Integer> getCount = () -> 3;
        ConfigProperty.IntProperty count = (ConfigProperty.IntProperty) ConfigProperty.integer("limits", "count", 3, 1, 9, "A count", setCount, getCount);
        assertEquals("limits", count.category());
        assertEquals("count", count.key());
        assertEquals(3, count.defaultValue());
        assertEquals(1, count.min());
        assertEquals(9, count.max());
        assertEquals("A count", count.comment());
        assertSame(setCount, count.setter());
        assertSame(getCount, count.getter());
        assertEquals(new ConfigProperty.IntProperty("limits", "count", 3, 1, 9, "A count", setCount, getCount), count);
        assertEquals(new ConfigProperty.IntProperty("limits", "count", 3, 1, 9, "A count", setCount, getCount).hashCode(), count.hashCode());
        assertTrue(count.toString().contains("count"));

        Shape[] shapes = Shape.values();
        Consumer<Shape> setShape = value -> {};
        Supplier<Shape> getShape = () -> Shape.ROUND;
        ConfigProperty.EnumProperty<?> shape = (ConfigProperty.EnumProperty<?>) ConfigProperty.enumeration("render", "shape", shapes, Shape.SQUARE, "A shape", setShape, getShape);
        assertEquals("render", shape.category());
        assertEquals("shape", shape.key());
        assertSame(shapes, shape.values());
        assertEquals(Shape.SQUARE, shape.defaultValue());
        assertEquals("A shape", shape.comment());
        assertSame(setShape, shape.setter());
        assertSame(getShape, shape.getter());
        assertEquals(new ConfigProperty.EnumProperty<>("render", "shape", shapes, Shape.SQUARE, "A shape", setShape, getShape), shape);
        assertEquals(new ConfigProperty.EnumProperty<>("render", "shape", shapes, Shape.SQUARE, "A shape", setShape, getShape).hashCode(), shape.hashCode());
        assertTrue(shape.toString().contains("shape"));
    }
}
