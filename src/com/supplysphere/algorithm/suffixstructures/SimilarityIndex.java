package com.supplysphere.algorithm.suffixstructures;

import com.supplysphere.core.util.Preconditions;
import com.supplysphere.core.util.Text;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Document similarity on top of suffix structures — Module 2 / CO2.
 *
 * <p>Two complementary measures, both standard in deduplication pipelines:</p>
 *
 * <ul>
 *   <li><b>Longest common substring</b> — built from the suffix array of the
 *       concatenation {@code a + SEP0 + b + SEP1}: adjacent suffixes in the
 *       order that belong to <em>different</em> sides can only be neighbours
 *       because they share a prefix, and the largest LCP among them is exactly
 *       the longest shared run. One suffix array plus one Kasai pass gives an
 *       exact answer in {@code O(n log n)}.</li>
 *   <li><b>Shingle Jaccard</b> — documents are reduced to the set of their
 *       {@code k}-word shingles; similarity is {@code |A ∩ B| / |A ∪ B|}.
 *       This scores overall topical overlap, where the substring measure
 *       scores verbatim copying — the two disagree precisely when it is
 *       interesting (paraphrase versus plagiarism).</li>
 * </ul>
 */
public final class SimilarityIndex {

    private static final char SEPARATOR_A = '\u0000';
    private static final char SEPARATOR_B = '\u0001';

    private final List<String> documents;

    /**
     * @param documents raw document texts; stored as given, normalised on use
     */
    public SimilarityIndex(List<String> documents) {
        Preconditions.requireNotEmpty(documents, "documents");
        this.documents = List.copyOf(documents);
    }

    public int documentCount() {
        return documents.size();
    }

    public String document(int index) {
        return documents.get(index);
    }

    /** Exact longest substring shared by {@code a} and {@code b}, via SA + LCP. */
    public static String longestCommonSubstring(String a, String b) {
        Preconditions.requireNonNull(a, "a");
        Preconditions.requireNonNull(b, "b");
        if (a.isEmpty() || b.isEmpty()) {
            return "";
        }
        String left = Text.normalize(a);
        String right = Text.normalize(b);
        if (left.isEmpty() || right.isEmpty()) {
            return "";
        }

        String combined = left + SEPARATOR_A + right + SEPARATOR_B;
        int[] suffixArray = SuffixArray.build(combined);
        int[] lcp = KasaiLcp.build(combined, suffixArray);
        int leftEnd = left.length();

        int bestLength = 0;
        int bestStart = -1;
        for (int i = 0; i < lcp.length; i++) {
            int first = suffixArray[i];
            int second = suffixArray[i + 1];
            boolean crossesSides = (first <= leftEnd && second > leftEnd)
                    || (second <= leftEnd && first > leftEnd);
            if (crossesSides && lcp[i] > bestLength) {
                bestLength = lcp[i];
                bestStart = Math.min(first, second);
            }
        }
        return bestLength == 0 ? "" : combined.substring(bestStart, bestStart + bestLength);
    }

    /**
     * Longest common substring between two indexed documents,
     * or {@code ""} when they share no run.
     */
    public String longestCommonSubstring(int leftIndex, int rightIndex) {
        return longestCommonSubstring(documents.get(leftIndex), documents.get(rightIndex));
    }

    /**
     * Jaccard similarity of the documents' {@code shingleSize}-word shingle
     * sets, in {@code [0, 1]}.
     */
    public double jaccard(int leftIndex, int rightIndex, int shingleSize) {
        Preconditions.requirePositive(shingleSize, "shingleSize");
        return jaccard(documents.get(leftIndex), documents.get(rightIndex), shingleSize);
    }

    /** Jaccard of two raw texts; {@code 1.0} when both normalise to nothing. */
    public static double jaccard(String a, String b, int shingleSize) {
        Set<String> left = shingles(a, shingleSize);
        Set<String> right = shingles(b, shingleSize);
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /** Word {@code k}-shingles of the text; a text shorter than k yields nothing. */
    public static Set<String> shingles(String text, int k) {
        List<String> words = Text.words(text);
        Set<String> shingles = new HashSet<>();
        if (words.size() < k) {
            return shingles;
        }
        StringBuilder shingle = new StringBuilder();
        for (int i = 0; i + k <= words.size(); i++) {
            shingle.setLength(0);
            for (int j = 0; j < k; j++) {
                if (j > 0) {
                    shingle.append(' ');
                }
                shingle.append(words.get(i + j));
            }
            shingles.add(shingle.toString());
        }
        return shingles;
    }
}
