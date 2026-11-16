package com.bdmajora.coarctatio.collections;

import com.google.common.collect.Iterators;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

// Read only list backed by one array.
public class FixedArrayList<T> implements List<T> {
    // Elements of the list.
    private final T[] array;

    // Creates a list holding a copy of the elements of the list.
    @SuppressWarnings("unchecked")
    public FixedArrayList(List<T> list) {
        this(list.toArray((T[]) new Object[0]));
    }

    // Creates a list that uses the array without copying it.
    public FixedArrayList(T[] array) {
        this.array = array;
    }

    // Returns the number of elements.
    @Override
    public int size() {
        return this.array.length;
    }

    // Returns whether the list holds no elements.
    @Override
    public boolean isEmpty() {
        return this.array.length == 0;
    }

    // Returns whether the element is present.
    @Override
    public boolean contains(Object o) {
        return ArrayUtils.contains(this.array, o);
    }

    // Returns a read only iterator.
    @Override
    public Iterator<T> iterator() {
        return Iterators.forArray(this.array);
    }

    // Returns a copy of the elements.
    @Override
    public Object[] toArray() {
        return this.array.clone();
    }

    // Copies the elements into the array or into a new array of its type when too small.
    @SuppressWarnings("unchecked")
    @Override
    public <T1> T1[] toArray(T1[] dst) {
        T[] src = this.array;

        if (dst.length < src.length) {
            return (T1[]) Arrays.copyOf(src, src.length, dst.getClass());
        }

        System.arraycopy(src, 0, dst, 0, src.length);

        if (dst.length > src.length) {
            dst[src.length] = null;
        }

        return dst;
    }

    // Rejects modification.
    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    // Returns whether every element of the collection is present.
    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!ArrayUtils.contains(this.array, o)) {
                return false;
            }
        }

        return true;
    }

    // Rejects modification.
    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    // Returns the element at the index.
    @Override
    public T get(int index) {
        return this.array[index];
    }

    // Rejects modification.
    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    // Returns the first position of the element or minus one.
    @Override
    public int indexOf(Object o) {
        return ArrayUtils.indexOf(this.array, o);
    }

    // Returns the last position of the element or minus one.
    @Override
    public int lastIndexOf(Object o) {
        return ArrayUtils.lastIndexOf(this.array, o);
    }

    // Returns a read only list iterator.
    @Override
    public ListIterator<T> listIterator() {
        return java.util.Collections.unmodifiableList(Arrays.asList(this.array)).listIterator();
    }

    // Returns a read only list iterator starting at the index.
    @Override
    public ListIterator<T> listIterator(int index) {
        return java.util.Collections.unmodifiableList(Arrays.asList(this.array)).listIterator(index);
    }

    // Returns a read only view of a range.
    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return java.util.Collections.unmodifiableList(Arrays.asList(this.array).subList(fromIndex, toIndex));
    }

    // Returns whether the object is a list with equal elements in the same order.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof List)) {
            return false;
        }

        List<?> other = (List<?>) o;
        if (other.size() != this.array.length) {
            return false;
        }

        Iterator<?> it = other.iterator();
        for (T element : this.array) {
            if (!java.util.Objects.equals(element, it.next())) {
                return false;
            }
        }

        return true;
    }

    // Returns the hash of the elements in list order.
    @Override
    public int hashCode() {
        int hash = 1;
        for (T element : this.array) {
            hash = 31 * hash + (element == null ? 0 : element.hashCode());
        }
        return hash;
    }

    // Returns the elements as text.
    @Override
    public String toString() {
        return Arrays.toString(this.array);
    }
}
