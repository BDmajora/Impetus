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

// Packs every listed property of a block into one int (FoamFix's technique), replacing vanilla's per-state ImmutableTable (250-400 MB on a 300-mod pack); values resolve via the block's own property, allocation is bounded by MAX_STATE_ARRAY, and the table is still producible on demand
public final class PropertyValueMapper {
    // Hard ceiling on the shared state array; a block needing more than a million slots is pathological and falling back to vanilla costs a table that was never going to fit
    private static final int MAX_STATE_ARRAY = 1 << 20;

    // Per-property immutable entries shared by every block using a static property (BlockHorizontal.FACING etc); reference-keyed since two equal-but-distinct properties may order values differently
    private static final Map<IProperty<?>, Entry> ENTRY_CACHE = new Reference2ObjectOpenHashMap<>();

    // Counters for the memory report; atomic because block registration is not thread-confined once mods register from their own init
    private static final AtomicInteger BLOCKS_MAPPED = new AtomicInteger();
    private static final AtomicInteger BLOCKS_SKIPPED = new AtomicInteger();
    static final AtomicInteger TABLES_MATERIALISED = new AtomicInteger();

    // Orders properties least-wasteful first (waste = padded width minus real value count) so the worst fit's unused tail runs off the end of the array; ties break on name so the layout is deterministic across launches
    private static final Comparator<Entry> BY_BIT_FITNESS = (a, b) -> {
        int wasteA = a.bitSize - a.count;
        int wasteB = b.bitSize - b.count;

        return wasteA == wasteB
                ? a.property.getName().compareTo(b.property.getName())
                : Integer.compare(wasteA, wasteB);
    };

    // Sorted by BY_BIT_FITNESS, so index order here is bit order, not the container's property order
    private final Entry[] entries;
    // offsets[i] is the low bit of entries[i]'s slice within the packed int
    private final int[] offsets;
    // Property name -> index into entries; -1 for anything this block does not have
    private final Object2IntOpenHashMap<String> indexByName;
    // Every state of the block indexed by packed value, shared by all of them; this array replaces the per-state tables
    private final IBlockState[] states;

    // Donated by the first state of this block and then reused by all of them; see sharedKeys below
    private Object[] sharedKeys;

    private PropertyValueMapper(Entry[] entries, int[] offsets, Object2IntOpenHashMap<String> indexByName,
                                IBlockState[] states) {
        this.entries = entries;
        this.offsets = offsets;
        this.indexByName = indexByName;
        this.states = states;
    }

    // Builds a mapper for a container, or null when the block should keep vanilla states (blacklisted, unindexable, too large all just mean "leave it alone"); safe inside the constructor since the property map is assigned before the state loop
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

        // 30 rather than 31: the packed value is a signed int and the array index derived from it must stay positive
        if (bitPos > 30) {
            BLOCKS_SKIPPED.incrementAndGet();
            return null;
        }

        long size;
        if (entries.length == 0) {
            size = 1;
        } else {
            // The highest property needs no power-of-two padding; nothing is packed above it so the unused tail is never addressed
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

    // Prefix match against the config blacklist, for blocks whose state handling breaks under packing
    private static boolean isBlacklisted(Block block) {
        String name = block.getClass().getName();

        for (String prefix : CoarctatioConfig.get().blockStateBlacklist) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    // Computes the packed index, files the state in the shared array and returns the index; called from buildPropertyValueTable on every state, so the array is full before byValue can be called
    int register(IBlockState state) {
        int value = 0;

        for (int i = 0; i < this.entries.length; i++) {
            Entry entry = this.entries[i];
            int index = entry.indexOf(state.getValue(entry.property));

            if (index < 0) {
                // The state came from the container's own cartesian product, so reaching here means an IProperty whose getAllowedValues() disagrees with itself between calls
                throw new IllegalStateException("Property " + entry.property.getName()
                        + " rejected its own allowed value on " + state);
            }

            value |= index << this.offsets[i];
        }

        if (this.states[value] != null) {
            // Two distinct states packing to the same index means the bit layout does not separate them, a corrupted world waiting to happen; fail here where the cause is visible
            throw new IllegalStateException("Packed index " + value + " is claimed by both "
                    + this.states[value] + " and " + state
                    + ". Please report this to Impetus with the mod list.");
        }

        this.states[value] = state;
        MemoryReport.recordPackedStates(1);
        return value;
    }

    // The state at a packed index, or null when that bit combination lands in a padding hole no real state occupies
    public IBlockState byValue(int value) {
        return this.states[value];
    }

    // Shared property key array for CoarctatioPropertyMap, captured from the FIRST state's own map (entries is reordered for bit fitness and would mismatch keys with values); null when the caller's map differs in shape, synchronized since states can be created off-thread
    public synchronized Object[] sharedKeys(Map<IProperty<?>, Comparable<?>> properties) {
        if (this.sharedKeys == null) {
            this.sharedKeys = properties.keySet().toArray();
        }

        return this.sharedKeys.length == properties.size() ? this.sharedKeys : null;
    }

    // Returns packed with one property changed, or -1 if the block lacks the property or the value is disallowed; clears the bit slice and ORs the new index, resolving by property NAME so an equal-but-distinct IProperty still works
    public int withValue(int packed, IProperty<?> property, Object newValue) {
        int index = this.indexByName.getInt(property.getName());

        if (index < 0) {
            return -1;
        }

        Entry entry = this.entries[index];
        int valueIndex = entry.indexOf(newValue);

        if (valueIndex < 0) {
            return -1;
        }

        // bitSize is a power of two, so bitSize - 1 is the slice's low-bit mask, shifted into position
        int offset = this.offsets[index];
        int mask = (entry.bitSize - 1) << offset;

        return (packed & ~mask) | (valueIndex << offset);
    }

    // Counts for /coarctatio
    public static String statistics() {
        return String.format("%d blocks packed, %d left on vanilla states, %d tables rebuilt on demand",
                BLOCKS_MAPPED.get(), BLOCKS_SKIPPED.get(), TABLES_MATERIALISED.get());
    }

    // Cache lookup for buildEntry; null is NOT cached, so an unindexable property is rebuilt and re-rejected once per block declaring it
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

    // Chooses the tightest entry type for a property's values; null means it cannot be packed
    private static Entry buildEntry(IProperty<?> property) {
        Collection<?> allowed = property.getAllowedValues();
        int count = allowed.size();

        if (count == 0) {
            return null;
        }

        // Only exact vanilla property classes get a closed-form index; a subclass may override getAllowedValues() or parseValue(), so anything else uses an explicit value map
        Class<?> type = property.getClass();

        if (type == PropertyBool.class && count == 2) {
            return new BooleanEntry(property, count);
        }

        if (type == PropertyEnum.class || type == PropertyDirection.class) {
            Object[] constants = property.getValueClass().getEnumConstants();

            // Ordinals are only dense when every constant is allowed; a filtered PropertyEnum (BlockStone's variants) would leave holes in the bit range
            if (constants != null && constants.length == count) {
                return new OrdinalEntry(property, count);
            }
        }

        if (type == PropertyInteger.class) {
            Entry contiguous = ContiguousIntegerEntry.tryCreate(property, allowed);

            if (contiguous != null) {
                return contiguous;
            }
        }

        return new MappedEntry(property, allowed);
    }

    // Rounds up to the next power of two by smearing the highest set bit down; pads a value count to a whole bit width so the layout is addressable with shifts and masks
    private static int ceilPowerOfTwo(int value) {
        int v = value - 1;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }

    // One property's slice of the packed int: width and value mapping; subclassed so the hot indexOf is one virtual dispatch and each type uses its cheapest indexing
    abstract static class Entry {
        final IProperty<?> property;
        // Real number of allowed values
        final int count;
        // count rounded up to a power of two, i.e. how many slots the slice actually occupies
        final int bitSize;
        // Width of the slice in bits, log2 of bitSize
        final int bits;

        Entry(IProperty<?> property, int count) {
            this.property = property;
            this.count = count;
            this.bitSize = ceilPowerOfTwo(count);
            this.bits = Integer.numberOfTrailingZeros(this.bitSize);
        }

        // Index of value among the allowed values, or -1 if it does not belong; never defaults, since a wrong answer produces a valid-looking but incorrect state
        abstract int indexOf(Object value);
    }

    private static final class BooleanEntry extends Entry {
        BooleanEntry(IProperty<?> property, int count) {
            super(property, count);
        }

        @Override
        int indexOf(Object value) {
            if (Boolean.TRUE.equals(value)) {
                return 1;
            }

            // Not a fall-through to 0: a non-Boolean means the wrong property was passed, and answering "false" would silently return a wrong state
            return Boolean.FALSE.equals(value) ? 0 : -1;
        }
    }

    private static final class OrdinalEntry extends Entry {
        private final Class<?> valueClass;

        OrdinalEntry(IProperty<?> property, int count) {
            super(property, count);
            this.valueClass = property.getValueClass();
        }

        @Override
        int indexOf(Object value) {
            return this.valueClass.isInstance(value) ? ((Enum<?>) value).ordinal() : -1;
        }
    }

    private static final class ContiguousIntegerEntry extends Entry {
        private final int minimum;

        private ContiguousIntegerEntry(IProperty<?> property, int count, int minimum) {
            super(property, count);
            this.minimum = minimum;
        }

        // Null unless every value is an Integer and they form one unbroken run, which lets the value be the index
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

            // Contiguous iff the span matches the count; that also rules out duplicates.
            if (maximum - minimum + 1 != allowed.size()) {
                return null;
            }

            return new ContiguousIntegerEntry(property, allowed.size(), minimum);
        }

        @Override
        int indexOf(Object value) {
            if (!(value instanceof Integer)) {
                return -1;
            }

            int index = (Integer) value - this.minimum;
            return index >= 0 && index < this.count ? index : -1;
        }
    }

    // The general case, an explicit value-to-index map keyed by equals since Integer autoboxing only caches -128..127
    private static final class MappedEntry extends Entry {
        private final Object2IntOpenHashMap<Object> indices;

        MappedEntry(IProperty<?> property, Collection<?> allowed) {
            super(property, allowed.size());

            this.indices = new Object2IntOpenHashMap<>(allowed.size());
            this.indices.defaultReturnValue(-1);

            int index = 0;
            for (Object value : allowed) {
                this.indices.put(value, index++);
            }
        }

        @Override
        int indexOf(Object value) {
            return value == null ? -1 : this.indices.getInt(value);
        }
    }
}
