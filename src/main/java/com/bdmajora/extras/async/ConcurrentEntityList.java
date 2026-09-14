package com.bdmajora.extras.async;

import net.minecraft.entity.Entity;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Drop-in for World.loadedEntityList when entities tick in parallel: vanilla walks it by index on the main thread while worker-side spawns append, and any thread may iterate it (getEntities, countEntities), so index reads are optimistic, writes take the lock, and iterators walk a snapshot
public final class ConcurrentEntityList extends AbstractList<Entity> {
    private final StampedLock lock = new StampedLock();
    private final ArrayList<Entity> backing;

    public ConcurrentEntityList(Collection<? extends Entity> initial) {
        this.backing = new ArrayList<>(initial);
    }

    // Optimistic first: a racing remove can make ArrayList throw or hand back a stale slot, and either way the stamp fails validation and the locked read settles it
    @Override
    public Entity get(int index) {
        long stamp = this.lock.tryOptimisticRead();
        if (stamp != 0L) {
            try {
                Entity value = this.backing.get(index);
                if (this.lock.validate(stamp)) {
                    return value;
                }
            } catch (IndexOutOfBoundsException ignored) {
                // Fall through to the locked read, which throws the real one if the index is bad
            }
        }
        stamp = this.lock.readLock();
        try {
            return this.backing.get(index);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public int size() {
        long stamp = this.lock.tryOptimisticRead();
        int size = this.backing.size();
        if (this.lock.validate(stamp)) {
            return size;
        }
        stamp = this.lock.readLock();
        try {
            return this.backing.size();
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean add(Entity entity) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.add(entity);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(int index, Entity entity) {
        long stamp = this.lock.writeLock();
        try {
            this.backing.add(index, entity);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public Entity set(int index, Entity entity) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.set(index, entity);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public Entity remove(int index) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.remove(index);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean remove(Object o) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.remove(o);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean addAll(Collection<? extends Entity> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.addAll(c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends Entity> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.addAll(index, c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.removeAll(c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.retainAll(c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeIf(Predicate<? super Entity> filter) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.removeIf(filter);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public void clear() {
        long stamp = this.lock.writeLock();
        try {
            this.backing.clear();
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean contains(Object o) {
        long stamp = this.lock.readLock();
        try {
            return this.backing.contains(o);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public int indexOf(Object o) {
        long stamp = this.lock.readLock();
        try {
            return this.backing.indexOf(o);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public Object[] toArray() {
        long stamp = this.lock.readLock();
        try {
            return this.backing.toArray();
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        long stamp = this.lock.readLock();
        try {
            return this.backing.toArray(a);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public void forEach(Consumer<? super Entity> action) {
        for (Entity entity : snapshot()) {
            action.accept(entity);
        }
    }

    // Iteration is over a copy; remove() still reaches the live list by identity so vanilla's iterator-driven removals keep working
    @Override
    public Iterator<Entity> iterator() {
        return new SnapshotIterator(snapshot());
    }

    // A copy taken under the read lock, shared by every iterating path
    public Entity[] snapshot() {
        long stamp = this.lock.readLock();
        try {
            return this.backing.toArray(new Entity[0]);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Copy of the current contents as a list, for callers that want to keep it
    public List<Entity> copy() {
        long stamp = this.lock.readLock();
        try {
            return new ArrayList<>(this.backing);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    private final class SnapshotIterator implements Iterator<Entity> {
        private final Entity[] items;
        private int cursor;
        private Entity last;

        SnapshotIterator(Entity[] items) {
            this.items = items;
        }

        @Override
        public boolean hasNext() {
            return this.cursor < this.items.length;
        }

        @Override
        public Entity next() {
            if (this.cursor >= this.items.length) {
                throw new NoSuchElementException();
            }
            this.last = this.items[this.cursor++];
            return this.last;
        }

        @Override
        public void remove() {
            Entity target = this.last;
            if (target == null) {
                throw new IllegalStateException();
            }
            this.last = null;
            long stamp = ConcurrentEntityList.this.lock.writeLock();
            try {
                ArrayList<Entity> list = ConcurrentEntityList.this.backing;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == target) {
                        list.remove(i);
                        return;
                    }
                }
            } finally {
                ConcurrentEntityList.this.lock.unlockWrite(stamp);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof List && Objects.equals(copy(), o));
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
