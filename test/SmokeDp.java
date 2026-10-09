import com.supplysphere.algorithm.dp.EditDistance;
import com.supplysphere.algorithm.dp.SequenceAlignment;

public class SmokeDp {
    static int failures = 0;

    static void eq(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.out.println("FAIL " + label + ": expected " + expected + " got " + actual);
        } else {
            System.out.println("ok   " + label);
        }
    }

    public static void main(String[] args) {
        // Edit distance
        eq("levenshtein kitten/sitting", 3, EditDistance.levenshtein("kitten", "sitting"));
        eq("levenshtein within 3", 3, EditDistance.levenshteinWithin("kitten", "sitting", 5));
        eq("levenshtein within cutoff", 3, EditDistance.levenshteinWithin("kitten", "sitting", 2));
        eq("damerau transpose", 1, EditDistance.damerauLevenshtein("lo", "ol"));
        eq("damerau equals levenshtein on equal", 3, EditDistance.damerauLevenshtein("kitten", "sitting"));
        eq("weighted unit == levenshtein", 3,
                EditDistance.weighted("kitten", "sitting", EditDistance.Costs.unit()));
        // brute-force cross-check of band pruning
        String[] samples = {"", "a", "abc", "supply", "sphere", "global supply chain", "abcdef"};
        for (String x : samples) {
            for (String y : samples) {
                int full = EditDistance.levenshtein(x, y);
                for (int k = 0; k <= 8; k++) {
                    int band = EditDistance.levenshteinWithin(x, y, k);
                    int expect = full <= k ? full : k + 1;
                    eq("band " + x + "|" + y + " k=" + k, expect, band);
                }
            }
        }

        // Alignment
        SequenceAlignment.Alignment global =
                SequenceAlignment.needlemanWunsch("GATTACA", "GCATGCU", 1, -1, -1);
        eq("NW score GATTACA/GCATGCU", 0, global.score());
        eq("NW same length", global.first().length(), global.second().length());
        eq("SW local HELLO", 10,
                SequenceAlignment.smithWaterman("XYZHELLOXYZ", "ABHELLOCD", 2, -1, -1).score());
        eq("affine >= linear-ish", true,
                SequenceAlignment.affineGapScore("AAA", "ABB", 2, -1, -2, -1)
                        > SequenceAlignment.affineGapScore("AAA", "BBB", 2, -1, -5, -3) - 100);

        System.out.println(failures == 0 ? "ALL DP SMOKE TESTS PASSED" : failures + " FAILURES");
        if (failures > 0) System.exit(1);
    }
}
