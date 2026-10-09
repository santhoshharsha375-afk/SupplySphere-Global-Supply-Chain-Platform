package com.supplysphere.algorithm.stringalgorithms;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Knuth-Morris-Pratt substring search — Module 1 / CO1.
 *
 * <p>Powers exact product-name, supplier-id and shipment-code lookup across the
 * catalogue. The prefix function (failure function) {@code lps[i]} stores the
 * length of the longest proper prefix of {@code pattern[0..i]} that is also a
 * suffix, so the scanner never rewinds: the whole search is
 * {@code O(n + m)} with {@code O(1)} auxiliary search space, versus
 * {@code O(nm)} for naive search.</p>
 *
 * <p>Instances are immutable and reusable across many texts — the prefix
 * function is computed once in the constructor.</p>
 */
public final class Kmp {

    private final String pattern;
    private final int[] lps;

    /**
     * @param pattern literal pattern; normalised to lower case so callers do not
     *                have to remember to normalise both sides
     */
    public Kmp(String pattern) {
        this.pattern = Preconditions.requireNonBlank(pattern, "pattern").toLowerCase(java.util.Locale.ROOT);
        this.lps = buildPrefixFunction(this.pattern);
    }

    public static Kmp of(String pattern) {
        return new Kmp(pattern);
    }

    public String pattern() {
        return pattern;
    }

    public int patternLength() {
        return pattern.length();
    }

    public int[] prefixFunction() {
        return lps.clone();
    }

    /**
     * Builds the failure function: {@code lps[i]} is the length of the longest
     * proper prefix of {@code pattern[0..i]} which is also a suffix of it.
     */
    public static int[] buildPrefixFunction(String pattern) {
        Preconditions.requireNonBlank(pattern, "pattern");
        int m = pattern.length();
        int[] lps = new int[m];
        int matched = 0;

        for (int i = 1; i < m; i++) {
            while (matched > 0 && pattern.charAt(i) != pattern.charAt(matched)) {
                matched = lps[matched - 1];
            }
            if (pattern.charAt(i) == pattern.charAt(matched)) {
                matched++;
            }
            lps[i] = matched;
        }
        return lps;
    }

    /** First occurrence index, or {@code -1}. */
    public int search(String text) {
        return searchIn(text, 0);
    }

    /**
     * First occurrence at or after {@code fromIndex}.
     *
     * @return index of the match, or {@code -1} when absent
     */
    public int searchIn(String text, int fromIndex) {
        Preconditions.requireNonNull(text, "text");
        int n = text.length();
        int m = pattern.length();
        if (m == 0) {
            return 0;
        }
        if (fromIndex < 0 || fromIndex > n) {
            throw new com.supplysphere.core.util.DomainException("OUT_OF_RANGE",
                    "fromIndex " + fromIndex + " outside [0," + n + "]");
        }

        int matched = 0;
        for (int i = fromIndex; i < n; i++) {
            char c = Character.toLowerCase(text.charAt(i));
            while (matched > 0 && c != pattern.charAt(matched)) {
                matched = lps[matched - 1];
            }
            if (c == pattern.charAt(matched)) {
                matched++;
                if (matched == m) {
                    return i - m + 1;
                }
            }
        }
        return -1;
    }

    public boolean contains(String text) {
        return search(text) >= 0;
    }

    /** All occurrences in increasing start order, including overlaps. */
    public List<PatternMatch> searchAll(String text) {
        return searchAll(text, 0);
    }

    public List<PatternMatch> searchAll(String text, int fromIndex) {
        Preconditions.requireNonNull(text, "text");
        int n = text.length();
        int m = pattern.length();
        List<PatternMatch> hits = new ArrayList<>();
        if (m == 0 || n == 0 || fromIndex < 0 || fromIndex > n) {
            return hits;
        }

        int matched = 0;
        for (int i = fromIndex; i < n; i++) {
            char c = Character.toLowerCase(text.charAt(i));
            while (matched > 0 && c != pattern.charAt(matched)) {
                matched = lps[matched - 1];
            }
            if (c == pattern.charAt(matched)) {
                matched++;
                if (matched == m) {
                    hits.add(new PatternMatch(pattern, 0, i - m + 1, i + 1));
                    matched = lps[matched - 1];
                }
            }
        }
        return hits;
    }

    public int countOccurrences(String text) {
        return searchAll(text).size();
    }

    /** True when the text starts with the pattern. */
    public boolean startsWith(String text) {
        Preconditions.requireNonNull(text, "text");
        int m = pattern.length();
        if (text.length() < m) {
            return false;
        }
        for (int i = 0; i < m; i++) {
            if (Character.toLowerCase(text.charAt(i)) != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Searches {@code haystacks} for the pattern and returns only the ones that
     * contain it, preserving input order. This is the catalogue-facing
     * operation behind product and supplier lookup.
     */
    public List<String> filterContaining(List<String> haystacks, int limit) {
        Preconditions.requireNotEmpty(haystacks, "haystacks");
        int cap = limit <= 0 ? haystacks.size() : limit;
        List<String> selected = new ArrayList<>(Math.min(cap, haystacks.size()));
        for (String candidate : haystacks) {
            if (contains(candidate)) {
                selected.add(candidate);
                if (selected.size() >= cap) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(selected);
    }

    @Override
    public String toString() {
        return "Kmp[" + pattern + "]";
    }
}
