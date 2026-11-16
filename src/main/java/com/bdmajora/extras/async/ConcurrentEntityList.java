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

// List of entities that many threads can read and change.
public final class ConcurrentEntityList extends AbstractList<Entity> {
    // Lock that guards the list.
    private final StampedLock lock = new StampedLock();
    // Entities in order.
    private final ArrayList<Entity> backing;

    // Creates a list holding a copy of the entities.
    public ConcurrentEntityList(Collection<? extends Entity> initial) {
        this.backing = new ArrayList<>(initial);
    }

    // Returns the entity at the index.
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
            }
        }
        stamp = this.lock.readLock();
        try {
            return this.backing.get(index);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Returns the number of entities.
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

    // Appends the entity.
    @Override
    public boolean add(Entity entity) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.add(entity);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Inserts the entity at the index.
    @Override
    public void add(int index, Entity entity) {
        long stamp = this.lock.writeLock();
        try {
            this.backing.add(index, entity);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Replaces the entity at the index.
    @Override
    public Entity set(int index, Entity entity) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.set(index, entity);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Removes the entity at the index.
    @Override
    public Entity remove(int index) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.remove(index);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Removes the first occurrence of the entity.
    @Override
    public boolean remove(Object o) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.remove(o);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Appends every entity.
    @Override
    public boolean addAll(Collection<? extends Entity> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.addAll(c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Inserts every entity at the index.
    @Override
    public boolean addAll(int index, Collection<? extends Entity> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.addAll(index, c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Removes every entity that the collection holds.
    @Override
    public boolean removeAll(Collection<?> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.removeAll(c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Removes every entity that the collection lacks.
    @Override
    public boolean retainAll(Collection<?> c) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.retainAll(c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Removes every entity that matches the filter.
    @Override
    public boolean removeIf(Predicate<? super Entity> filter) {
        long stamp = this.lock.writeLock();
        try {
            return this.backing.removeIf(filter);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Removes every entity.
    @Override
    public void clear() {
        long stamp = this.lock.writeLock();
        try {
            this.backing.clear();
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // Returns whether the entity is present.
    @Override
    public boolean contains(Object o) {
        long stamp = this.lock.readLock();
        try {
            return this.backing.contains(o);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Returns the first position of the entity or minus one.
    @Override
    public int indexOf(Object o) {
        long stamp = this.lock.readLock();
        try {
            return this.backing.indexOf(o);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Returns the entities as an array.
    @Override
    public Object[] toArray() {
        long stamp = this.lock.readLock();
        try {
            return this.backing.toArray();
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Copies the entities into the array.
    @Override
    public <T> T[] toArray(T[] a) {
        long stamp = this.lock.readLock();
        try {
            return this.backing.toArray(a);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Runs the action on a snapshot of the entities.
    @Override
    public void forEach(Consumer<? super Entity> action) {
        for (Entity entity : snapshot()) {
            action.accept(entity);
        }
    }

    // Returns an iterator over a snapshot.
    @Override
    public Iterator<Entity> iterator() {
        // Creates an iterator over the snapshot.
        return new SnapshotIterator(snapshot());
    }

    // Returns a copy of the entities as an array.
    public Entity[] snapshot() {
        long stamp = this.lock.readLock();
        try {
            return this.backing.toArray(new Entity[0]);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Returns a copy of the entities as a list.
    public List<Entity> copy() {
        long stamp = this.lock.readLock();
        try {
            return new ArrayList<>(this.backing);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    // Iterator over a snapshot that removes from the live list.
    private final class SnapshotIterator implements Iterator<Entity> {
        // Entities of the snapshot.
        private final Entity[] items;
        // Position of the next entity.
        private int cursor;
        // Entity returned last.
        private Entity last;

        SnapshotIterator(Entity[] items) {
            this.items = items;
        }

        // Returns whether another entity remains.
        @Override
        public boolean hasNext() {
            return this.cursor < this.items.length;
        }

        // Returns the next entity.
        @Override
        public Entity next() {
            if (this.cursor >= this.items.length) {
                throw new NoSuchElementException();
            }
            this.last = this.items[this.cursor++];
            return this.last;
        }

        // Removes the entity returned last from the live list.
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

    // Returns whether the object is a list with equal entities.
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof List && Objects.equals(copy(), o));
    }

    // Returns the identity hash because the contents change.
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
