package com.bdmajora.impetus.engine.impl.util.collections;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// Weakly held set compared by identity, so an element's own equals and hashCode are never called and an element held nowhere else can be collected
public final class WeakIdentitySet<E> {
    private final ReferenceQueue<E> cleared = new ReferenceQueue<>();
    private final Set<Entry<E>> entries = new HashSet<>();

    // Adds the element and returns whether it was not held before
    public synchronized boolean add(E element) {
        Objects.requireNonNull(element, "Element must not be null");
        this.expunge();
        return this.entries.add(new Entry<>(element, this.cleared));
    }

    // Returns whether the element is held
    public synchronized boolean contains(E element) {
        Objects.requireNonNull(element, "Element must not be null");
        this.expunge();
        return this.entries.contains(new Entry<>(element, null));
    }

    // Drops the entries whose elements were collected
    private void expunge() {
        for (Reference<? extends E> entry; (entry = this.cleared.poll()) != null; ) {
            this.entries.remove(entry);
        }
    }

    // Weak reference hashed by the identity of its element, equal only to itself once cleared
    private static final class Entry<E> extends WeakReference<E> {
        private final int hash;

        Entry(E element, ReferenceQueue<? super E> queue) {
            super(element, queue);
            this.hash = System.identityHashCode(element);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        // The set compares hashes before asking, so only the elements themselves are compared here
        @Override
        public boolean equals(Object other) {
            E element = this.get();
            return other == this || other instanceof Entry<?> entry && element != null && element == entry.get();
        }
    }
}
