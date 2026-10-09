package com.supplysphere.algorithm.suffixstructures;

import com.supplysphere.core.util.Preconditions;

/**
 * Suffix array by prefix doubling — Module 2 / CO2.
 *
 * <p>The suffix array of a text is the array of all its suffixes in
 * lexicographic order. Once built, binary search answers substring existence,
 * and the LCP array turns it into a full index for repeated-substring and
 * similarity queries — the same power as a suffix tree, with a flat array.</p>
 *
 * <p><b>Construction.</b> Sort suffixes by their first character (counting
 * sort), then repeatedly double the compared prefix length: suffixes ranked at
 * step {@code k} are re-sorted by the pair
 * {@code (rank[i], rank[i+k])}, which is a radix of two integer keys, and the
 * pair's lexicographic order is exactly the order of the first
 * {@code 2k} characters. Each round costs {@code O(n)} with counting sort and
 * the prefix length doubles, so the total is {@code O(n log n)} — the
 * practical implementation of the {@code O(n)} SA-IS algorithm that the
 * syllabus covers at conceptual level (SA-IS finds the critical/intuition
 * sampling recursively; the array interface below is identical).</p>
 */
public final class SuffixArray {

    private SuffixArray() {
    }

    /**
     * Builds the suffix array of {@code text} in {@code O(n log n)} time and
     * {@code O(n)} space.
     *
     * @return {@code sa[rank]} = starting index of the rank-th smallest suffix
     */
    public static int[] build(String text) {
        Preconditions.requireNonNull(text, "text");
        int n = text.length();
        if (n == 0) {
            return new int[0];
        }

        int[] sa = new int[n];
        int[] rank = compressCharacters(text);
        int[] nextRank = new int[n];
        int[] buffer = new int[n];
        int[] count = new int[n + 1];

        for (int i = 0; i < n; i++) {
            sa[i] = i;
        }

        for (int k = 1; k < n; k *= 2) {
            radixSort(sa, buffer, count, rank, k, n);

            nextRank[sa[0]] = 0;
            for (int i = 1; i < n; i++) {
                int previous = sa[i - 1];
                int current = sa[i];
                int previousTail = previous + k < n ? rank[previous + k] : -1;
                int currentTail = current + k < n ? rank[current + k] : -1;
                nextRank[current] = nextRank[previous]
                        + (rank[previous] == rank[current] && previousTail == currentTail ? 0 : 1);
            }
            int[] swap = rank;
            rank = nextRank;
            nextRank = swap;

            if (rank[sa[n - 1]] == n - 1) {
                break;                          // every suffix ranked distinctly
            }
        }
        return sa;
    }

    /**
     * Two stable counting passes (radix): first on the tail rank shifted by
     * one so the {@code -1} "past the end" key sorts first, then on the head
     * rank. Both key ranges fit in {@code [0, n]}.
     */
    private static void radixSort(int[] sa, int[] buffer, int[] count, int[] rank, int k, int n) {
        java.util.Arrays.fill(count, 0);
        for (int index : sa) {
            int tail = index + k < n ? rank[index + k] : -1;
            count[tail + 1]++;
        }
        for (int i = 1; i <= n; i++) {
            count[i] += count[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            int index = sa[i];
            int tail = index + k < n ? rank[index + k] : -1;
            buffer[--count[tail + 1]] = index;
        }

        java.util.Arrays.fill(count, 0);
        for (int index : buffer) {
            count[rank[index]]++;
        }
        for (int i = 1; i < n; i++) {
            count[i] += count[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            int index = buffer[i];
            sa[--count[rank[index]]] = index;
        }
    }

    /**
     * Maps characters to dense ranks in {@code [0, distinct)}, preserving the
     * characters' own ordering — so counting sort on ranks matches string
     * order. Two passes: mark which characters appear, then number them in
     * ascending character order.
     */
    private static int[] compressCharacters(String text) {
        int n = text.length();
        int maxChar = 0;
        for (int i = 0; i < n; i++) {
            maxChar = Math.max(maxChar, text.charAt(i));
        }
        boolean[] present = new boolean[maxChar + 1];
        for (int i = 0; i < n; i++) {
            present[text.charAt(i)] = true;
        }
        int[] denseRank = new int[maxChar + 1];
        int distinct = 0;
        for (int c = 0; c <= maxChar; c++) {
            if (present[c]) {
                denseRank[c] = distinct++;
            }
        }
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            rank[i] = denseRank[text.charAt(i)];
        }
        return rank;
    }

    /**
     * Reference implementation: suffixes compared directly, {@code O(n^2 log n)}.
     * Used by tests as the correctness oracle for {@link #build}.
     */
    public static int[] naive(String text) {
        int n = text.length();
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        java.util.Arrays.sort(indices, (a, b) -> text.substring(a).compareTo(text.substring(b)));
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = indices[i];
        }
        return result;
    }
}
