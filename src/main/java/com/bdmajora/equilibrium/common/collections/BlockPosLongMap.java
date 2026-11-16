package com.bdmajora.equilibrium.common.collections;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.math.BlockPos;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// Map from block positions to values that stores each position as one long.
public final class BlockPosLongMap<V> implements Map<BlockPos, V> {
    // Values by packed position.
    private final Long2ObjectMap<V> map = new Long2ObjectLinkedOpenHashMap<>();
    // View of the entries created on first use.
    private EntrySetView entrySet;
    // View of the keys created on first use.
    private KeySetView keySet;

    // Returns the number of entries.
    @Override
    public int size() {
        return this.map.size();
    }

    // Returns whether the map holds no entries.
    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    // Returns whether the position is present.
    @Override
    public boolean containsKey(Object key) {
        return key instanceof BlockPos && this.map.containsKey(((BlockPos) key).toLong());
    }

    // Returns whether the value is present.
    @Override
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    // Returns the value of the position or null.
    @Override
    public V get(Object key) {
        return key instanceof BlockPos ? this.map.get(((BlockPos) key).toLong()) : null;
    }

    // Stores the value of the position.
    @Override
    public V put(BlockPos key, V value) {
        return this.map.put(key.toLong(), value);
    }

    // Removes the position and returns its value.
    @Override
    public V remove(Object key) {
        return key instanceof BlockPos ? this.map.remove(((BlockPos) key).toLong()) : null;
    }

    // Stores every entry of the other map.
    @Override
    public void putAll(Map<? extends BlockPos, ? extends V> other) {
        for (Map.Entry<? extends BlockPos, ? extends V> entry : other.entrySet()) {
            this.map.put(entry.getKey().toLong(), entry.getValue());
        }
    }

    // Removes every entry.
    @Override
    public void clear() {
        this.map.clear();
    }

    // Returns the live key view.
    @Override
    public Set<BlockPos> keySet() {
        return this.keySet == null ? this.keySet = new KeySetView() : this.keySet;
    }

    // Returns the live value view.
    @Override
    public Collection<V> values() {
        return this.map.values();
    }

    // Returns the live entry view.
    @Override
    public Set<Map.Entry<BlockPos, V>> entrySet() {
        return this.entrySet == null ? this.entrySet = new EntrySetView() : this.entrySet;
    }

    // Live view of the entries.
    private final class EntrySetView extends AbstractSet<Map.Entry<BlockPos, V>> {
        // Returns the number of entries.
        @Override
        public int size() {
            return map.size();
        }

        // Removes every entry.
        @Override
        public void clear() {
            map.clear();
        }

        // Returns an iterator over the entries.
        @Override
        public Iterator<Map.Entry<BlockPos, V>> iterator() {
            Iterator<Long2ObjectMap.Entry<V>> backing = map.long2ObjectEntrySet().iterator();
            return new Iterator<Map.Entry<BlockPos, V>>() {
                @Override
                public boolean hasNext() {
                    return backing.hasNext();
                }

                @Override
                public Map.Entry<BlockPos, V> next() {
                    // Creates a view of the entry.
                    return new EntryView(backing.next());
                }

                @Override
                public void remove() {
                    backing.remove();
                }
            };
        }
    }

    // Live view of the positions.
    private final class KeySetView extends AbstractSet<BlockPos> {
        // Returns the number of positions.
        @Override
        public int size() {
            return map.size();
        }

        // Returns whether the position is present.
        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        // Removes the position and returns whether it was present.
        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) {
                return false;
            }

            BlockPosLongMap.this.remove(o);
            return true;
        }

        // Returns an iterator over the positions.
        @Override
        public Iterator<BlockPos> iterator() {
            Iterator<Map.Entry<BlockPos, V>> backing = entrySet().iterator();
            return new Iterator<BlockPos>() {
                @Override
                public boolean hasNext() {
                    return backing.hasNext();
                }

                @Override
                public BlockPos next() {
                    return backing.next().getKey();
                }

                @Override
                public void remove() {
                    backing.remove();
                }
            };
        }
    }

    // Entry that reads and writes the packed entry.
    private final class EntryView implements Map.Entry<BlockPos, V> {
        // Packed entry behind the view.
        private final Long2ObjectMap.Entry<V> entry;

        EntryView(Long2ObjectMap.Entry<V> entry) {
            this.entry = entry;
        }

        // Returns the position of the entry.
        @Override
        public BlockPos getKey() {
            return BlockPos.fromLong(this.entry.getLongKey());
        }

        // Returns the value of the entry.
        @Override
        public V getValue() {
            return this.entry.getValue();
        }

        // Stores the value in the entry and returns the old value.
        @Override
        public V setValue(V value) {
            return this.entry.setValue(value);
        }

        // Returns whether the other entry has the same position and value.
        @Override
        public boolean equals(Object other) {
            return other instanceof Map.Entry
                    && java.util.Objects.equals(this.getKey(), ((Map.Entry<?, ?>) other).getKey())
                    && java.util.Objects.equals(this.getValue(), ((Map.Entry<?, ?>) other).getValue());
        }

        // Returns the hash of the position combined with the value.
        @Override
        public int hashCode() {
            return this.getKey().hashCode() ^ java.util.Objects.hashCode(this.getValue());
        }

        // Returns the entry as text.
        @Override
        public String toString() {
            return this.getKey() + "=" + this.getValue();
        }
    }
}
