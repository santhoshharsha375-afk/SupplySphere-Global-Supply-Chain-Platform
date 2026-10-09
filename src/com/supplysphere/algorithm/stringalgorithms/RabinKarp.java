package com.supplysphere.algorithm.stringalgorithms;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rabin-Karp rolling-hash search — Module 1 / CO1.
 *
 * <p>Used to sweep very large collections of order identifiers and SKUs in a
 * single pass. Comparing every window against every pattern by character is
 * {@code O(nm)}; a rolling hash makes the per-window test {@code O(1)}, so a
 * scan drops to {@code O(n + m)}. Hash collisions are unavoidable with a
 * bounded modulus, so every hash hit is confirmed by an exact
 * {@link String#regionMatches} before it is reported. The results are therefore
 * identical to brute force while still being fast in practice.</p>
 *
 * <p>Instances are immutable and reusable; base and modulus are fixed at
 * construction time.</p>
 */
public final class RabinKarp {

    private static final int DEFAULT_BASE = 257;
    private static final int DEFAULT_MOD = 1_000_000_007;
    private static final int SECONDARY_BASE = 269;
    private static final int SECONDARY_MOD = 1_000_000_009;

    private final String pattern;
    private final int patternLength;
    private final long patternHash;
    private final int base;
    private final int mod;
    private final int highOrder;
    private final long patternHash2;
    private final int base2;
    private final int mod2;
    private final int highOrder2;
    private final boolean doubleHashed;

    /**
     * @param pattern literal pattern, normalised to lower case
     * @param base    hash base; must exceed the character alphabet
     * @param mod     modulus, prime for good distribution
     */
    public RabinKarp(String pattern, int base, int mod) {
        this.pattern = Preconditions.requireNonBlank(pattern, "pattern").toLowerCase(Locale.ROOT);
        Preconditions.requireInRange(base, 2, Integer.MAX_VALUE, "base");
        Preconditions.requireInRange(mod, 2, Integer.MAX_VALUE, "mod");
        this.base = base;
        this.mod = mod;
        this.patternLength = this.pattern.length();
        this.patternHash = hashOf(this.pattern);
        this.highOrder = (int) modPow(base, patternLength - 1, mod);
        this.doubleHashed = false;
        this.base2 = SECONDARY_BASE;
        this.mod2 = SECONDARY_MOD;
        this.patternHash2 = hashOf2(this.pattern);
        this.highOrder2 = (int) modPow(base2, patternLength - 1, mod2);
    }

    /**
     * Double-hash instance: every window is validated against <em>two</em>
     * independently modulus-ed rolling sums before being reported. A collision
     * under one modulus is made overwhelmingly unlikely to survive the second,
     * so the double-hash scan reports the same hits as the single-hash scan
     * with far fewer false hash positives — the trade-off used when bulk
     * order-identifier sweeps cannot afford an exact re-check per window.
     */
    public RabinKarp(String pattern, int base, int mod, int base2, int mod2) {
        this.pattern = Preconditions.requireNonBlank(pattern, "pattern").toLowerCase(Locale.ROOT);
        Preconditions.requireInRange(base, 2, Integer.MAX_VALUE, "base");
        Preconditions.requireInRange(mod, 2, Integer.MAX_VALUE, "mod");
        Preconditions.requireInRange(base2, 2, Integer.MAX_VALUE, "base2");
        Preconditions.requireInRange(mod2, 2, Integer.MAX_VALUE, "mod2");
        this.base = base;
        this.mod = mod;
        this.base2 = base2;
        this.mod2 = mod2;
        this.patternLength = this.pattern.length();
        this.patternHash = hashOf(this.pattern);
        this.highOrder = (int) modPow(base, patternLength - 1, mod);
        this.patternHash2 = hashOf2(this.pattern);
        this.highOrder2 = (int) modPow(base2, patternLength - 1, mod2);
        this.doubleHashed = true;
    }

    /** Default alphabet-safe parameters. */
    public static RabinKarp of(String pattern) {
        return new RabinKarp(pattern, DEFAULT_BASE, DEFAULT_MOD);
    }

    public String pattern() {
        return pattern;
    }

    public int patternLength() {
        return patternLength;
    }

    public long patternHash() {
        return patternHash;
    }

    public int modulus() {
        return mod;
    }

    /** All occurrences in increasing start order, each collision-verified. */
    public List<PatternMatch> searchAll(String text) {
        return searchAll(text, 0);
    }

    /**
     * Scans {@code text} from {@code fromIndex} for every non-overlapping-by-verification
     * occurrence of the pattern.
     *
     * <p>The window hash is advanced in {@code O(1)} per position by
     * subtracting the departing character's contribution (scaled by
     * {@code base^(m-1)}), then multiplying by the base and adding the
     * arriving character.</p>
     */
    public List<PatternMatch> searchAll(String text, int fromIndex) {
        Preconditions.requireNonNull(text, "text");
        int n = text.length();
        List<PatternMatch> hits = new ArrayList<>();
        if (fromIndex < 0 || fromIndex > n) {
            throw new com.supplysphere.core.util.DomainException("OUT_OF_RANGE",
                    "fromIndex " + fromIndex + " outside [0," + n + "]");
        }
        if (patternLength > n) {
            return hits;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        long windowHash = 0;
        for (int i = 0; i < patternLength; i++) {
            windowHash = (windowHash * base + lower.charAt(i)) % mod;
        }

        for (int i = patternLength; i <= n; i++) {
            int start = i - patternLength;
            if (start >= fromIndex
                    && windowHash == patternHash
                    && lower.startsWith(pattern, start)) {
                hits.add(new PatternMatch(pattern, 0, start, i));
            }
            if (i < n) {
                long departing = (long) lower.charAt(i - patternLength) * highOrder % mod;
                windowHash = (windowHash - departing + mod) % mod;
                windowHash = (windowHash * base + lower.charAt(i)) % mod;
            }
        }
        return hits;
    }

    /** First occurrence index, or {@code -1}. */
    public int search(String text) {
        List<PatternMatch> all = searchAll(text);
        return all.isEmpty() ? -1 : all.get(0).start();
    }

    public boolean contains(String text) {
        return search(text) >= 0;
    }

    public int countOccurrences(String text) {
        return searchAll(text).size();
    }

    /**
     * Multi-pattern sweep: locates every pattern from {@code patterns} in one
     * pass over {@code text}. Patterns longer than the text are skipped.
     *
     * <p>This is the bulk order-identifier search — a single scan across the
     * concatenated identifier corpus instead of one pass per identifier.</p>
     */
    public static List<PatternMatch> searchAll(String text, List<String> patterns) {
        Preconditions.requireNonNull(text, "text");
        Preconditions.requireNotEmpty(patterns, "patterns");
        String lower = text.toLowerCase(Locale.ROOT);
        List<PatternMatch> hits = new ArrayList<>();

        for (int id = 0; id < patterns.size(); id++) {
            String raw = patterns.get(id);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String needle = raw.toLowerCase(Locale.ROOT);
            int from = 0;
            while (from <= lower.length() - needle.length()) {
                int at = lower.indexOf(needle, from);
                if (at < 0) {
                    break;
                }
                hits.add(new PatternMatch(needle, id, at, at + needle.length()));
                from = at + 1;
            }
        }
        hits.sort(java.util.Comparator.comparingInt(PatternMatch::start)
                .thenComparingInt(PatternMatch::patternId));
        return hits;
    }

    /**
     * Filters a large identifier collection down to entries containing the
     * pattern. The catalogue sweeps tens of thousands of order ids, so this is
     * the entry point for bulk order-identifier lookup.
     */
    public List<String> filterContaining(List<String> identifiers, int limit) {
        Preconditions.requireNotEmpty(identifiers, "identifiers");
        int cap = limit <= 0 ? identifiers.size() : limit;
        List<String> selected = new ArrayList<>();
        for (String identifier : identifiers) {
            if (identifier != null && contains(identifier)) {
                selected.add(identifier);
                if (selected.size() >= cap) {
                    break;
                }
            }
        }
        return selected;
    }

    /** Exposed so tests can assert the rolling-hash invariant directly. */
    public long hashOf(String value) {
        long hash = 0;
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            hash = (hash * base + lower.charAt(i)) % mod;
        }
        return hash;
    }

    private static long modPow(long value, int exponent, int modulus) {
        long result = 1L;
        long baseValue = value % modulus;
        int e = exponent;
        while (e > 0) {
            if ((e & 1) == 1) {
                result = (result * baseValue) % modulus;
            }
            baseValue = (baseValue * baseValue) % modulus;
            e >>= 1;
        }
        return result;
    }

    /**
     * All occurrences located with the two-modulus scan. Both rolling hashes
     * must match before a window is reported, then the window is still
     * confirmed character-by-character so the result set is exactly what
     * {@link #searchAll(String)} would return.
     */
    public List<PatternMatch> searchAllDoubleHashed(String text) {
        Preconditions.requireNonNull(text, "text");
        int n = text.length();
        List<PatternMatch> hits = new ArrayList<>();
        if (patternLength > n) {
            return hits;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        long window1 = 0;
        long window2 = 0;
        for (int i = 0; i < patternLength; i++) {
            window1 = (window1 * base + lower.charAt(i)) % mod;
            window2 = (window2 * base2 + lower.charAt(i)) % mod2;
        }
        for (int i = patternLength; i <= n; i++) {
            int start = i - patternLength;
            if (window1 == patternHash && window2 == patternHash2
                    && lower.startsWith(pattern, start)) {
                hits.add(new PatternMatch(pattern, 0, start, i));
            }
            if (i < n) {
                long departing1 = (long) lower.charAt(i - patternLength) * highOrder % mod;
                window1 = (window1 - departing1 + mod) % mod;
                window1 = (window1 * base + lower.charAt(i)) % mod;
                long departing2 = (long) lower.charAt(i - patternLength) * highOrder2 % mod2;
                window2 = (window2 - departing2 + mod2) % mod2;
                window2 = (window2 * base2 + lower.charAt(i)) % mod2;
            }
        }
        return hits;
    }

    /** Double-hash variant of {@link #search(String)}. */
    public int searchDoubleHashed(String text) {
        List<PatternMatch> all = searchAllDoubleHashed(text);
        return all.isEmpty() ? -1 : all.get(0).start();
    }

    /** Double-hash variant of {@link #contains(String)}. */
    public boolean containsDoubleHashed(String text) {
        return searchDoubleHashed(text) >= 0;
    }

    /** Double-hash variant of {@link #countOccurrences(String)}. */
    public int countOccurrencesDoubleHashed(String text) {
        return searchAllDoubleHashed(text).size();
    }

    public boolean usesDoubleHashing() {
        return doubleHashed;
    }

    public long patternHashSecond() {
        return patternHash2;
    }

    public int modulusSecond() {
        return mod2;
    }

    /** Second hash of {@code value} — used to roll the two windows in lock-step. */
    private long hashOf2(String value) {
        long hash = 0;
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            hash = (hash * base2 + lower.charAt(i)) % mod2;
        }
        return hash;
    }

    @Override
    public String toString() {
        return "RabinKarp[" + pattern + " mod=" + mod + "]";
    }
}
