package com.supplysphere.engine;

/**
 * Hand-built open-addressing set of strings — Module 1 / CO1.
 *
 * <p>Replaces {@code java.util.HashSet} inside the query engine so the
 * de-duplication of query terms and the "which terms did we find" bookkeeping
 * are implemented, not imported. Open addressing with linear probing keeps the
 * whole structure to one {@code String[]} table; FNV-1a hashing is computed
 * inline. The table doubles (re-hashing all entries) once half full, which is
 * exactly the load where probing stays cheap.</p>
 */
public final class EngineSet {

    private static final int INITIAL_CAPACITY = 16;
    private static final int FNV_OFFSET = (int) 2166136261L;
    private static final int FNV_PRIME = 16777619;

    private String[] table;
    private int size;

    public EngineSet() {
        table = new String[INITIAL_CAPACITY];
    }

    /** Adds {@code value}; returns {@code false} when it was already present. */
    public boolean add(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (contains(value)) {
            return false;
        }
        ensureRoom();
        int index = hash(value) & (table.length - 1);
        while (table[index] != null) {
            index = (index + 1) & (table.length - 1);
        }
        table[index] = value;
        size++;
        return true;
    }

    public boolean contains(String value) {
        int index = hash(value) & (table.length - 1);
        while (table[index] != null) {
            if (table[index].equals(value)) {
                return true;
            }
            index = (index + 1) & (table.length - 1);
        }
        return false;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Table-order snapshot of the members, for boundary conversion. */
    public String[] toStringArray() {
        String[] out = new String[size];
        int count = 0;
        for (String entry : table) {
            if (entry != null) {
                out[count++] = entry;
            }
        }
        return out;
    }

    private void ensureRoom() {
        if ((size + 1) * 2 <= table.length) {
            return;
        }
        String[] old = table;
        table = new String[old.length * 2];
        size = 0;
        for (String entry : old) {
            if (entry != null) {
                add(entry);
            }
        }
    }

    private static int hash(String value) {
        int h = FNV_OFFSET;
        for (int i = 0; i < value.length(); i++) {
            h ^= value.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }
}