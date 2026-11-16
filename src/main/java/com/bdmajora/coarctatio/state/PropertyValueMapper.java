package com.bdmajora.coarctatio.state;

import com.bdmajora.coarctatio.MemoryReport;
import com.bdmajora.coarctatio.CoarctatioConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Packs the property values of a block into one int and finds states by it.
public final class PropertyValueMapper {
    // Largest number of states a mapper may hold.
    private static final int MAX_STATE_ARRAY = 1 << 20;

    // Entries by property.
    private static final Map<IProperty<?>, Entry> ENTRY_CACHE = new Reference2ObjectOpenHashMap<>();

    // Count of blocks that got a mapper.
    private static final AtomicInteger BLOCKS_MAPPED = new AtomicInteger();
    // Count of blocks left on vanilla states.
    private static final AtomicInteger BLOCKS_SKIPPED = new AtomicInteger();
    // Count of neighbor tables built on demand.
    static final AtomicInteger TABLES_MATERIALISED = new AtomicInteger();

    // Order that puts the least wasteful properties first.
    private static final Comparator<Entry> BY_BIT_FITNESS = (a, b) -> {
        int wasteA = a.bitSize - a.count;
        int wasteB = b.bitSize - b.count;

        return wasteA == wasteB
                ? a.property.getName().compareTo(b.property.getName())
                : Integer.compare(wasteA, wasteB);
    };

    // Packing rule of each property.
    private final Entry[] entries;
    // Bit offset of each property.
    private final int[] offsets;
    // Position of each property by name.
    private final Object2IntOpenHashMap<String> indexByName;
    // States by packed value.
    private final IBlockState[] states;

    // Property keys shared by every state.
    private Object[] sharedKeys;

    // Creates a mapper from its parts.
    private PropertyValueMapper(Entry[] entries, int[] offsets, Object2IntOpenHashMap<String> indexByName,
                                IBlockState[] states) {
        this.entries = entries;
        this.offsets = offsets;
        this.indexByName = indexByName;
        this.states = states;
    }

    // Returns a mapper for the block or null when it cannot be packed.
    public static PropertyValueMapper create(BlockStateContainer container, Block block) {
        if (block == null || isBlacklisted(block)) {
            BLOCKS_SKIPPED.incrementAndGet();
            return null;
        }

        Collection<IProperty<?>> properties = container.getProperties();

        List<Entry> sorted = new ArrayList<>(properties.size());
        for (IProperty<?> property : properties) {
            Entry entry = entryFor(property);

            if (entry == null) {
                BLOCKS_SKIPPED.incrementAndGet();
                return null;
            }

            sorted.add(entry);
        }
        sorted.sort(BY_BIT_FITNESS);

        Entry[] entries = sorted.toArray(new Entry[0]);
        int[] offsets = new int[entries.length];
        Object2IntOpenHashMap<String> indexByName = new Object2IntOpenHashMap<>(entries.length);
        indexByName.defaultReturnValue(-1);

        int bitPos = 0;
        for (int i = 0; i < entries.length; i++) {
            offsets[i] = bitPos;
            indexByName.put(entries[i].property.getName(), i);
            bitPos += entries[i].bits;
        }

        if (bitPos > 30) {
            BLOCKS_SKIPPED.incrementAndGet();
            return null;
        }

        long size;
        if (entries.length == 0) {
            size = 1;
        } else {
            Entry last = entries[entries.length - 1];
            size = (1L << (bitPos - last.bits)) * last.count;
        }

        if (size > MAX_STATE_ARRAY) {
            BLOCKS_SKIPPED.incrementAndGet();
            return null;
        }

        BLOCKS_MAPPED.incrementAndGet();
        return new PropertyValueMapper(entries, offsets, indexByName, new IBlockState[(int) size]);
    }

    // Returns whether the class of the block is excluded by configuration.
    private static boolean isBlacklisted(Block block) {
        String name = block.getClass().getName();

        for (String prefix : CoarctatioConfig.get().blockStateBlacklist) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    // Stores the state under its packed value and returns it.
    int register(IBlockState state) {
        int value = 0;

        for (int i = 0; i < this.entries.length; i++) {
            Entry entry = this.entries[i];
            int index = entry.indexOf(state.getValue(entry.property));

            if (index < 0) {
                throw new IllegalStateException("Property " + entry.property.getName()
                        + " rejected its own allowed value on " + state);
            }

            value |= index << this.offsets[i];
        }

        if (this.states[value] != null) {
            throw new IllegalStateException("Packed index " + value + " is claimed by both "
                    + this.states[value] + " and " + state
                    + ". Please report this to Impetus with the mod list.");
        }

        this.states[value] = state;
        MemoryReport.recordPackedStates(1);
        return value;
    }

    // Returns the state of the packed value.
    public IBlockState byValue(int value) {
        return this.states[value];
    }

    // Returns the shared keys or null when the property count differs.
    public synchronized Object[] sharedKeys(Map<IProperty<?>, Comparable<?>> properties) {
        if (this.sharedKeys == null) {
            this.sharedKeys = properties.keySet().toArray();
        }

        return this.sharedKeys.length == properties.size() ? this.sharedKeys : null;
    }

    // Returns the packed value with the property changed or minus one when it cannot be changed.
    public int withValue(int packed, IProperty<?> property, Object newValue) {
        if (property == null) {
            return -1;
        }

        int index = this.indexByName.getInt(property.getName());

        if (index < 0) {
            return -1;
        }

        Entry entry = this.entries[index];
        int valueIndex = entry.indexOf(newValue);

        if (valueIndex < 0) {
            return -1;
        }

        int offset = this.offsets[index];
        int mask = (entry.bitSize - 1) << offset;

        return (packed & ~mask) | (valueIndex << offset);
    }

    // Returns a one line report of the results.
    public static String statistics() {
        return String.format("%d blocks packed, %d left on vanilla states, %d tables rebuilt on demand",
                BLOCKS_MAPPED.get(), BLOCKS_SKIPPED.get(), TABLES_MATERIALISED.get());
    }

    // Returns the cached entry of the property.
    private static Entry entryFor(IProperty<?> property) {
        synchronized (ENTRY_CACHE) {
            Entry cached = ENTRY_CACHE.get(property);

            if (cached == null) {
                cached = buildEntry(property);
                ENTRY_CACHE.put(property, cached);
            }

            return cached;
        }
    }

    // Returns the packing rule of the property or null when it has no values.
    private static Entry buildEntry(IProperty<?> property) {
        Collection<?> allowed = property.getAllowedValues();
        int count = allowed.size();

        if (count == 0) {
            return null;
        }

        Class<?> type = property.getClass();

        if (type == PropertyBool.class && count == 2) {
            // Packs a boolean by its truth value
            return new BooleanEntry(property, count);
        }

        if (type == PropertyEnum.class || type == PropertyDirection.class) {
            Object[] constants = property.getValueClass().getEnumConstants();

            if (constants != null && constants.length == count) {
                // Packs an enum that allows every constant by its ordinal
                return new OrdinalEntry(property, count);
            }
        }

        if (type == PropertyInteger.class) {
            Entry contiguous = ContiguousIntegerEntry.tryCreate(property, allowed);

            if (contiguous != null) {
                return contiguous;
            }
        }

        // Packs any other property by a lookup table
        return new MappedEntry(property, allowed);
    }

    // Returns the smallest power of two not below the value.
    private static int ceilPowerOfTwo(int value) {
        int v = value - 1;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }

    // Packing rule of one property.
    abstract static class Entry {
        // Property that the rule packs.
        final IProperty<?> property;
        // Number of allowed values.
        final int count;
        // Count rounded up to a power of two.
        final int bitSize;
        // Number of bits the rule uses.
        final int bits;

        // Creates a rule for the property.
        Entry(IProperty<?> property, int count) {
            this.property = property;
            this.count = count;
            this.bitSize = ceilPowerOfTwo(count);
            this.bits = Integer.numberOfTrailingZeros(this.bitSize);
        }

        // Returns the index of the value or minus one when it is not allowed.
        abstract int indexOf(Object value);
    }

    // Rule for a boolean property.
    private static final class BooleanEntry extends Entry {
        // Creates a rule for a boolean property.
        BooleanEntry(IProperty<?> property, int count) {
            super(property, count);
        }

        // Returns one for true and zero for false.
        @Override
        int indexOf(Object value) {
            if (Boolean.TRUE.equals(value)) {
                return 1;
            }

            return Boolean.FALSE.equals(value) ? 0 : -1;
        }
    }

    // Rule for an enum property that allows every constant.
    private static final class OrdinalEntry extends Entry {
        // Enum class of the property.
        private final Class<?> valueClass;

        // Creates a rule for an enum property.
        OrdinalEntry(IProperty<?> property, int count) {
            super(property, count);
            this.valueClass = property.getValueClass();
        }

        // Returns the ordinal of the value.
        @Override
        int indexOf(Object value) {
            return this.valueClass.isInstance(value) ? ((Enum<?>) value).ordinal() : -1;
        }
    }

    // Rule for an integer property with consecutive values.
    private static final class ContiguousIntegerEntry extends Entry {
        // Smallest allowed value.
        private final int minimum;

        // Creates a rule for the property.
        private ContiguousIntegerEntry(IProperty<?> property, int count, int minimum) {
            super(property, count);
            this.minimum = minimum;
        }

        // Returns a rule when the values are consecutive integers or null otherwise.
        static ContiguousIntegerEntry tryCreate(IProperty<?> property, Collection<?> allowed) {
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;

            for (Object value : allowed) {
                if (!(value instanceof Integer)) {
                    return null;
                }

                int i = (Integer) value;
                minimum = Math.min(minimum, i);
                maximum = Math.max(maximum, i);
            }

            if ((long) maximum - minimum + 1 != allowed.size()) {
                return null;
            }

            return new ContiguousIntegerEntry(property, allowed.size(), minimum);
        }

        // Returns the distance of the value from the smallest value.
        @Override
        int indexOf(Object value) {
            if (!(value instanceof Integer)) {
                return -1;
            }

            int index = (Integer) value - this.minimum;
            return index >= 0 && index < this.count ? index : -1;
        }
    }

    // Rule for any other property.
    private static final class MappedEntry extends Entry {
        // Index of each allowed value.
        private final Object2IntOpenHashMap<Object> indices;

        // Creates a rule with the index of each allowed value.
        MappedEntry(IProperty<?> property, Collection<?> allowed) {
            super(property, allowed.size());

            this.indices = new Object2IntOpenHashMap<>(allowed.size());
            this.indices.defaultReturnValue(-1);

            int index = 0;
            for (Object value : allowed) {
                this.indices.put(value, index++);
            }
        }

        // Returns the stored index of the value.
        @Override
        int indexOf(Object value) {
            return value == null ? -1 : this.indices.getInt(value);
        }
    }
}
