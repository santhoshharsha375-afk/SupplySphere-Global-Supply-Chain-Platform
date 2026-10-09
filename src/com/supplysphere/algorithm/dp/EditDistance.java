package com.supplysphere.algorithm.dp;

import com.supplysphere.core.util.Preconditions;

/**
 * Edit distance and its variants — Module 3 / CO3.
 *
 * <p>Wagner-Fischer dynamic programming answers "how far apart are these two
 * strings?", which is the engine behind fuzzy supplier lookup: a query typed as
 * {@code "acme logstics"} must still reach {@code "ACME Logistics"}.</p>
 *
 * <p>State: {@code dp[i][j]} = cheapest way to turn {@code a[0..i)} into
 * {@code b[0..j)}. Recurrence: match costs 0, otherwise the cheapest of
 * insert / delete / substitute (plus transposition when the previous characters
 * are swapped). Optimal substructure holds because an optimal alignment of the
 * prefixes is necessarily optimal for each of its own prefixes.</p>
 *
 * <p>Optimisations shipped here:</p>
 * <ul>
 *   <li><b>Rolling rows</b> — unit-cost Levenshtein only ever reads the previous
 *       row, so two rows of length {@code n} replace the full matrix
 *       ({@code O(min(m,n))} space instead of {@code O(mn)}).</li>
 *   <li><b>Band pruning</b> — when the caller only cares whether the distance
 *       is at most {@code k}, cells with {@code |i-j| > k} can never win, so
 *       only {@code O(nk)} cells are touched.</li>
 * </ul>
 */
public final class EditDistance {

    private EditDistance() {
    }

    /** Prices for the four edit operations; {@code -1} forbids one. */
    public record Costs(int insert, int delete, int substitute, int transpose) {

        public static Costs unit() {
            return new Costs(1, 1, 1, 1);
        }

        /** Classic Levenshtein: substitution allowed, transposition not. */
        public static Costs levenshtein() {
            return new Costs(1, 1, 1, Integer.MAX_VALUE);
        }
    }

    /** Unit-cost Levenshtein distance in {@code O(mn)} time, {@code O(min(m,n))} space. */
    public static int levenshtein(String a, String b) {
        Preconditions.requireNonNull(a, "a");
        Preconditions.requireNonNull(b, "b");
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        // Keep the shorter string on the columns so the rolling row is minimal.
        if (a.length() < b.length()) {
            return levenshtein(b, a);
        }

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int matchOrSubstitute = previous[j - 1] + (ca == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), matchOrSubstitute);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /**
     * Early-exit variant for fuzzy search: returns the true distance when it
     * does not exceed {@code threshold}, otherwise {@code threshold + 1}.
     * Cost {@code O(nk)} instead of {@code O(n^2)} when {@code k << n}.
     *
     * <p>Only the diagonal band {@code |i - j| <= k} is evaluated. This loses
     * nothing: any edit path of total cost {@code k} keeps {@code |i-j| <= k}
     * at every step (each edit moves at most one cell), so a cell outside the
     * band already costs more than the threshold and can be treated as
     * unreachable. Once an entire row exceeds the threshold no surviving path
     * exists, and the scan stops.</p>
     */
    public static int levenshteinWithin(String a, String b, int threshold) {
        Preconditions.requireNonNegative(threshold, "threshold");
        if (Math.abs(a.length() - b.length()) > threshold) {
            return threshold + 1;
        }
        int n = a.length();
        int m = b.length();
        final int infinity = Integer.MAX_VALUE / 4;

        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j <= threshold ? j : infinity;
        }
        for (int i = 1; i <= n; i++) {
            int from = Math.max(1, i - threshold);
            int to = Math.min(m, i + threshold);
            java.util.Arrays.fill(current, infinity);
            current[0] = i <= threshold ? i : infinity;

            int rowMinimum = current[0];
            char ca = a.charAt(i - 1);
            for (int j = from; j <= to; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(previous[j - 1] + cost, previous[j] + 1);
                best = Math.min(best, current[j - 1] + 1);
                current[j] = best;
                rowMinimum = Math.min(rowMinimum, best);
            }
            if (rowMinimum > threshold) {
                return threshold + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return Math.min(previous[m], threshold + 1);
    }

    /**
     * Damerau-Levenshtein: also allows swapping two adjacent characters
     * (one operation), which matters for typed supplier names such as
     * {@code "loigstics"}. Full matrix needed because the recurrence reads
     * {@code dp[i-2][j-2]}. {@code O(mn)} time and space.
     */
    public static int damerauLevenshtein(String a, String b) {
        Preconditions.requireNonNull(a, "a");
        Preconditions.requireNonNull(b, "b");
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                }
            }
        }
        return dp[n][m];
    }

    /** Weighted variant: caller-defined prices, including a forbidden operation via {@code -1}. */
    public static int weighted(String a, String b, Costs costs) {
        Preconditions.requireNonNull(costs, "costs");
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            dp[i][0] = dp[i - 1][0] + usable(costs.delete());
        }
        for (int j = 1; j <= m; j++) {
            dp[0][j] = dp[0][j - 1] + usable(costs.insert());
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int best = Integer.MAX_VALUE / 4;
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    best = dp[i - 1][j - 1];
                } else if (costs.substitute() >= 0) {
                    best = dp[i - 1][j - 1] + costs.substitute();
                }
                if (costs.delete() >= 0) {
                    best = Math.min(best, dp[i - 1][j] + costs.delete());
                }
                if (costs.insert() >= 0) {
                    best = Math.min(best, dp[i][j - 1] + costs.insert());
                }
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1) && costs.transpose() >= 0) {
                    best = Math.min(best, dp[i - 2][j - 2] + costs.transpose());
                }
                dp[i][j] = best;
            }
        }
        return dp[n][m];
    }

    private static int usable(int cost) {
        return cost < 0 ? 0 : cost;
    }
}
