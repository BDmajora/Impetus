package com.bdmajora.impetus.engine.impl.util.collections;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// Set that holds its elements weakly and compares them by identity.
public final class WeakIdentitySet<E> {
    // Queue of the cleared references.
    private final ReferenceQueue<E> cleared = new ReferenceQueue<>();
    // Held references.
    private final Set<Entry<E>> entries = new HashSet<>();

    // Adds an element and returns whether it was new and rejects null.
    public synchronized boolean add(E element) {
        Objects.requireNonNull(element, "Element must not be null");
        this.expunge();
        return this.entries.add(new Entry<>(element, this.cleared));
    }

    // Returns whether an element is held and rejects null.
    public synchronized boolean contains(E element) {
        Objects.requireNonNull(element, "Element must not be null");
        this.expunge();
        return this.entries.contains(new Entry<>(element, null));
    }

    // Removes the references that were cleared.
    private void expunge() {
        for (Reference<? extends E> entry; (entry = this.cleared.poll()) != null; ) {
            this.entries.remove(entry);
        }
    }

    // Weak reference that hashes by the identity of its element.
    private static final class Entry<E> extends WeakReference<E> {
        // Identity hash of the element.
        private final int hash;

        // Creates a reference.
        Entry(E element, ReferenceQueue<? super E> queue) {
            super(element, queue);
            this.hash = System.identityHashCode(element);
        }

        // Returns the identity hash of the element.
        @Override
        public int hashCode() {
            return this.hash;
        }

        // Returns whether both references hold the same element.
        @Override
        public boolean equals(Object other) {
            E element = this.get();
            return other == this || other instanceof Entry<?> entry && element != null && element == entry.get();
        }
    }
}
