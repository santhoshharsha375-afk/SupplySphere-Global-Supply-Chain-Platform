package com.supplysphere.engine;

import java.lang.reflect.Array;

/**
 * Hand-built growable array list used by the query engine's algorithm code —
 * Module 1 / CO1.
 *
 * <p>The engine's ranking code is written against hand-built containers rather
 * than the JDK collections, so the retrieval algorithms are genuinely
 * implemented rather than imported. This list grows a plain {@code Object[]}
 * and exposes only what the engine needs: append, read, and a copy into a
 * caller-typed array at the point where results cross into the public API.</p>
 */
public final class EngineList<T> {

    private Object[] items;
    private int size;

    public EngineList() {
        this(8);
    }

    public EngineList(int initialCapacity) {
        items = new Object[Math.max(1, initialCapacity)];
    }

    public void add(T item) {
        if (size == items.length) {
            Object[] grown = new Object[items.length * 2];
            System.arraycopy(items, 0, grown, 0, size);
            items = grown;
        }
        items[size++] = item;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + " of " + size);
        }
        return (T) items[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Copies the stored elements into {@code template}, growing it via
     * reflection when too small, and returns the array actually used.
     */
    @SuppressWarnings("unchecked")
    public T[] toArray(T[] template) {
        if (template.length < size) {
            template = (T[]) Array.newInstance(template.getClass().getComponentType(), size);
        }
        System.arraycopy(items, 0, template, 0, size);
        return template;
    }
}