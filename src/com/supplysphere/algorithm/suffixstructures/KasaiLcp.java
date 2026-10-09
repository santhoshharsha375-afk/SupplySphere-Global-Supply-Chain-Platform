package com.supplysphere.algorithm.suffixstructures;

import com.supplysphere.core.util.Preconditions;

/**
 * Kasai's LCP construction — Module 2 / CO2.
 *
 * <p>The LCP array holds {@code lcp[i] = longest common prefix of suffixes
 * sa[i] and sa[i+1]}. Together with the suffix array it answers "how often does
 * this phrase repeat, and where?" in linear time — the workhorse behind
 * document similarity and near-duplicate detection.</p>
 *
 * <p>Kasai's trick: the key observation is that LCP values change by at most
 * one between consecutive suffixes in text order. Walking the suffixes in
 * text order and reusing the previous match length keeps every comparison
 * amortised constant, so the whole array costs {@code O(n)} — cheaper than the
 * {@code O(n log n)} suffix array it depends on.</p>
 */
public final class KasaiLcp {

    private KasaiLcp() {
    }

    /**
     * @param text         the same text the suffix array was built from
     * @param suffixArray  output of {@link SuffixArray#build}
     * @return {@code lcp[i]} shared by suffixes {@code sa[i]} and {@code sa[i+1]};
     *         length {@code n - 1} (empty for texts shorter than two characters)
     */
    public static int[] build(String text, int[] suffixArray) {
        Preconditions.requireNonNull(text, "text");
        Preconditions.requireNonNull(suffixArray, "suffixArray");
        int n = text.length();
        Preconditions.checkState(suffixArray.length == n, "suffix array length must match text");

        int[] lcp = new int[Math.max(0, n - 1)];
        if (n < 2) {
            return lcp;
        }

        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            rank[suffixArray[i]] = i;
        }

        int matched = 0;
        for (int i = 0; i < n; i++) {
            int position = rank[i];
            if (position == n - 1) {
                matched = 0;                    // no successor in the array
                continue;
            }
            int other = suffixArray[position + 1];
            while (i + matched < n && other + matched < n
                    && text.charAt(i + matched) == text.charAt(other + matched)) {
                matched++;
            }
            lcp[position] = matched;
            if (matched > 0) {
                matched--;                     // drop one for the next pair
            }
        }
        return lcp;
    }
}
