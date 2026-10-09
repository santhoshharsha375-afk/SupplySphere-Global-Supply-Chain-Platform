package com.supplysphere.core.util;

import java.util.Collection;
import java.util.Map;

/**
 * Central argument-validation helpers. Every public algorithm and service entry
 * point funnels its arguments through here so failures surface immediately at
 * the boundary instead of as an obscure {@code ArrayIndexOutOfBoundsException}
 * deep inside a loop.
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static <T> T requireNonNull(T reference, String name) {
        if (reference == null) {
            throw new DomainException("NULL_ARGUMENT", name + " must not be null");
        }
        return reference;
    }

    public static String requireNonBlank(String value, String name) {
        requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new DomainException("BLANK_ARGUMENT", name + " must not be blank");
        }
        return value;
    }

    public static int requireInRange(int value, int minInclusive, int maxInclusive, String name) {
        if (value < minInclusive || value > maxInclusive) {
            throw new DomainException("OUT_OF_RANGE",
                    name + " must be within [" + minInclusive + ", " + maxInclusive + "] but was " + value);
        }
        return value;
    }

    public static long requireInRange(long value, long minInclusive, long maxInclusive, String name) {
        if (value < minInclusive || value > maxInclusive) {
            throw new DomainException("OUT_OF_RANGE",
                    name + " must be within [" + minInclusive + ", " + maxInclusive + "] but was " + value);
        }
        return value;
    }

    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new DomainException("NOT_POSITIVE", name + " must be > 0 but was " + value);
        }
        return value;
    }

    public static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new DomainException("NOT_POSITIVE", name + " must be > 0 but was " + value);
        }
        return value;
    }

    public static double requirePositive(double value, String name) {
        if (!(value > 0.0) || Double.isNaN(value)) {
            throw new DomainException("NOT_POSITIVE", name + " must be > 0 but was " + value);
        }
        return value;
    }

    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new DomainException("NEGATIVE", name + " must be >= 0 but was " + value);
        }
        return value;
    }

    public static double requireNonNegative(double value, String name) {
        if (Double.isNaN(value) || value < 0.0) {
            throw new DomainException("NEGATIVE", name + " must be >= 0 but was " + value);
        }
        return value;
    }

    public static <T extends Collection<?>> T requireNotEmpty(T collection, String name) {
        requireNonNull(collection, name);
        if (collection.isEmpty()) {
            throw new DomainException("EMPTY_COLLECTION", name + " must not be empty");
        }
        return collection;
    }

    public static <K, V> Map<K, V> requireNotEmpty(Map<K, V> map, String name) {
        requireNonNull(map, name);
        if (map.isEmpty()) {
            throw new DomainException("EMPTY_COLLECTION", name + " must not be empty");
        }
        return map;
    }

    public static int[] requireNotEmpty(int[] array, String name) {
        requireNonNull(array, name);
        if (array.length == 0) {
            throw new DomainException("EMPTY_ARRAY", name + " must not be empty");
        }
        return array;
    }

    public static char[] requireNotEmpty(char[] array, String name) {
        requireNonNull(array, name);
        if (array.length == 0) {
            throw new DomainException("EMPTY_ARRAY", name + " must not be empty");
        }
        return array;
    }

    public static <T> T[] requireNotEmpty(T[] array, String name) {
        requireNonNull(array, name);
        if (array.length == 0) {
            throw new DomainException("EMPTY_ARRAY", name + " must not be empty");
        }
        return array;
    }

    public static int[] requireSameLength(int[] a, int[] b, String nameA, String nameB) {
        requireNotEmpty(a, nameA);
        requireNotEmpty(b, nameB);
        if (a.length != b.length) {
            throw new DomainException("LENGTH_MISMATCH",
                    nameA + " length " + a.length + " != " + nameB + " length " + b.length);
        }
        return a;
    }

    public static void checkState(boolean condition, String message) {
        if (!condition) {
            throw DomainException.illegalState(message);
        }
    }

    public static int requireSupported(int actual, int minInclusive, int maxInclusive, String name) {
        if (actual < minInclusive || actual > maxInclusive) {
            throw new DomainException("UNSUPPORTED",
                    name + " only supports sizes in [" + minInclusive + ", " + maxInclusive + "] but got " + actual);
        }
        return actual;
    }
}
