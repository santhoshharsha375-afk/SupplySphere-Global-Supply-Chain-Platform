package com.supplysphere.algorithm.stringalgorithms;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Aho-Corasick multi-pattern matching — Module 2 / CO2 (intuition level).
 *
 * <p>Searching for {@code p} patterns with one KMP pass each costs
 * {@code O(p·n)}. Aho-Corasick fuses them into one automaton: a trie of all
 * patterns plus <em>failure links</em> that say "after this prefix, where do I
 * continue as if I had matched that shorter prefix?" — KMP's failure function
 * generalised from a chain to a tree of prefixes. One scan of the text is then
 * {@code O(n + z)} where {@code z} is the number of matches, no matter how many
 * patterns are loaded.</p>
 *
 * <p>Supply-chain reading: a single sweep over shipment notes fires every
 * alert keyword at once ({@code "DELAYED"}, {@code "CUSTOMS HOLD"},
 * {@code "DAMAGED"}…) instead of one sweep per keyword.</p>
 *
 * <p>Construction: BFS assigns each node's failure link, copies the failure
 * target's outputs into the node (so matches are reported in one place), then
 * completes the transition table so the scan never follows a link at run time.</p>
 */
public final class AhoCorasick {

    private static final int ALPHABET = 128;      // ASCII

    private final List<int[]> next = new ArrayList<>();
    private final List<Integer> failure = new ArrayList<>();
    private final List<List<Integer>> output = new ArrayList<>();
    private final List<String> patterns = new ArrayList<>();
    private boolean built;

    public AhoCorasick() {
        next.add(filledRow());       // root = node 0
        failure.add(0);
        output.add(new ArrayList<>());
    }

    /** Registers a pattern (lower-cased internally). Call before {@link #build()}. */
    public AhoCorasick add(String pattern) {
        Preconditions.requireNonBlank(pattern, "pattern");
        Preconditions.checkState(!built, "cannot add patterns after build()");
        String needle = pattern.toLowerCase(java.util.Locale.ROOT);
        int patternId = patterns.size();
        patterns.add(needle);

        int node = 0;
        for (int i = 0; i < needle.length(); i++) {
            int c = needle.charAt(i);
            if (c >= ALPHABET) {
                continue;                      // only ASCII paths are followed
            }
            if (next.get(node)[c] < 0) {
                next.get(node)[c] = next.size();
                next.add(filledRow());
                failure.add(0);
                output.add(new ArrayList<>());
            }
            node = next.get(node)[c];
        }
        output.get(node).add(patternId);
        return this;
    }

    /**
     * Builds failure links (BFS) and completes the goto table — must be called
     * once before searching.
     */
    public AhoCorasick build() {
        Preconditions.checkState(!built, "already built");
        Deque<Integer> queue = new ArrayDeque<>();

        int[] root = next.get(0);
        for (int c = 0; c < ALPHABET; c++) {
            int child = root[c];
            if (child > 0) {
                failure.set(child, 0);
                queue.add(child);
            } else {
                root[c] = 0;                   // missing root edges loop to root
            }
        }

        while (!queue.isEmpty()) {
            int node = queue.poll();
            int[] row = next.get(node);
            // Snapshot the genuine trie edges before the table is completed.
            for (int c = 0; c < ALPHABET; c++) {
                int child = row[c];
                if (child <= 0) {
                    continue;
                }
                int link = failure.get(node);
                int target = next.get(link)[c];
                failure.set(child, target);
                output.get(child).addAll(output.get(target));
                queue.add(child);
            }
            for (int c = 0; c < ALPHABET; c++) {
                if (row[c] < 0) {
                    row[c] = next.get(failure.get(node))[c];
                }
            }
        }
        built = true;
        return this;
    }

    /**
     * Every pattern occurrence in {@code text}, including overlaps, sorted by
     * start position. Case-insensitive; non-ASCII characters reset the walk
     * (patterns themselves are ASCII), so a match never spans a foreign byte.
     */
    public List<PatternMatch> findAll(String text) {
        Preconditions.requireNonNull(text, "text");
        Preconditions.checkState(built, "call build() first");
        List<PatternMatch> hits = new ArrayList<>();
        int node = 0;
        String lower = text.toLowerCase(java.util.Locale.ROOT);

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            node = c < ALPHABET ? next.get(node)[c] : 0;
            for (int patternId : output.get(node)) {
                int length = patterns.get(patternId).length();
                hits.add(new PatternMatch(patterns.get(patternId), patternId,
                        i - length + 1, i + 1));
            }
        }
        hits.sort(java.util.Comparator.comparingInt(PatternMatch::start)
                .thenComparingInt(PatternMatch::patternId));
        return hits;
    }

    /** Number of distinct patterns loaded. */
    public int patternCount() {
        return patterns.size();
    }

    private static int[] filledRow() {
        int[] row = new int[ALPHABET];
        java.util.Arrays.fill(row, -1);
        return row;
    }
}
