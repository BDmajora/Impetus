package com.bdmajora.equilibrium.common.collections;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.math.BlockPos;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// A Map<BlockPos, V> stored as a long-keyed open hash map (StellarCore's chunkTEMap): a chunk's tile entity map is probed on every getTileEntity, and keying by the packed long drops the BlockPos hash/equals dispatch and the per-entry node. Insertion order is kept since Chunk.onUnload and mods iterate it and some rely on load order; keys are materialised on iteration only
public final class BlockPosLongMap<V> implements Map<BlockPos, V> {
    private final Long2ObjectMap<V> map = new Long2ObjectLinkedOpenHashMap<>();
    private EntrySetView entrySet;
    private KeySetView keySet;

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof BlockPos && this.map.containsKey(((BlockPos) key).toLong());
    }

    @Override
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return key instanceof BlockPos ? this.map.get(((BlockPos) key).toLong()) : null;
    }

    @Override
    public V put(BlockPos key, V value) {
        return this.map.put(key.toLong(), value);
    }

    @Override
    public V remove(Object key) {
        return key instanceof BlockPos ? this.map.remove(((BlockPos) key).toLong()) : null;
    }

    @Override
    public void putAll(Map<? extends BlockPos, ? extends V> other) {
        for (Map.Entry<? extends BlockPos, ? extends V> entry : other.entrySet()) {
            this.map.put(entry.getKey().toLong(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        this.map.clear();
    }

    @Override
    public Set<BlockPos> keySet() {
        return this.keySet == null ? this.keySet = new KeySetView() : this.keySet;
    }

    @Override
    public Collection<V> values() {
        return this.map.values();
    }

    @Override
    public Set<Map.Entry<BlockPos, V>> entrySet() {
        return this.entrySet == null ? this.entrySet = new EntrySetView() : this.entrySet;
    }

    // Removal through the iterator must reach the backing map, since Chunk.onUnload and World's TE cleanup iterate and remove
    private final class EntrySetView extends AbstractSet<Map.Entry<BlockPos, V>> {
        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void clear() {
            map.clear();
        }

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
                    return new EntryView(backing.next());
                }

                @Override
                public void remove() {
                    backing.remove();
                }
            };
        }
    }

    private final class KeySetView extends AbstractSet<BlockPos> {
        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return BlockPosLongMap.this.remove(o) != null;
        }

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

    private final class EntryView implements Map.Entry<BlockPos, V> {
        private final Long2ObjectMap.Entry<V> entry;

        EntryView(Long2ObjectMap.Entry<V> entry) {
            this.entry = entry;
        }

        @Override
        public BlockPos getKey() {
            return BlockPos.fromLong(this.entry.getLongKey());
        }

        @Override
        public V getValue() {
            return this.entry.getValue();
        }

        @Override
        public V setValue(V value) {
            return this.entry.setValue(value);
        }
    }
}
