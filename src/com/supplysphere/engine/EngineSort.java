package com.supplysphere.engine;

/**
 * Hand-built relevance sort for engine results — Module 1 / CO1.
 *
 * <p>The engine ranks hits by {@link SearchHit#score()} descending, ties broken
 * by article title ascending — previously done on the JDK stream pipeline,
 * here re-implemented as a straightforward top-down merge sort ({@code O(n log n)})
 * over a plain array, so the ranking strategy is part of the hand-built engine
 * rather than a library call.</p>
 */
public final class EngineSort {

    private EngineSort() {
    }

    /** Sorts {@code hits[0..length)} in-place by score desc, then title asc. */
    public static void sortByRelevance(SearchHit[] hits, int length) {
        SearchHit[] work = new SearchHit[length];
        mergeSort(hits, work, 0, length);
    }

    private static void mergeSort(SearchHit[] hits, SearchHit[] work, int from, int to) {
        if (to - from < 2) {
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(hits, work, from, mid);
        mergeSort(hits, work, mid, to);
        System.arraycopy(hits, from, work, from, to - from);
        int left = from;
        int right = mid;
        int out = from;
        while (left < mid && right < to) {
            hits[out++] = before(work[left], work[right]) ? work[left++] : work[right++];
        }
        while (left < mid) {
            hits[out++] = work[left++];
        }
    }

    /** True when {@code x} should sort ahead of {@code y}. */
    private static boolean before(SearchHit x, SearchHit y) {
        if (x.score() > y.score()) {
            return true;
        }
        if (x.score() < y.score()) {
            return false;
        }
        return x.article().title().compareTo(y.article().title()) <= 0;
    }
}