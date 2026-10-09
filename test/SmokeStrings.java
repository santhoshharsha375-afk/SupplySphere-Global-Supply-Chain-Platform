import com.supplysphere.algorithm.stringalgorithms.*;
import com.supplysphere.algorithm.suffixstructures.*;

import java.util.*;

public class SmokeStrings {
    static int failures = 0;

    static void eq(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.out.println("FAIL " + label + ": expected " + expected + " got " + actual);
        }
    }

    static void ok(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL " + label);
        }
    }

    public static void main(String[] args) {
        Random random = new Random(42);

        // Suffix array vs oracle over random strings with small alphabet
        for (int trial = 0; trial < 300; trial++) {
            int n = 1 + random.nextInt(40);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append((char) ('a' + random.nextInt(4)));
            String s = sb.toString();
            if (!Arrays.equals(SuffixArray.build(s), SuffixArray.naive(s))) {
                eq("suffix array " + s, Arrays.toString(SuffixArray.naive(s)), Arrays.toString(SuffixArray.build(s)));
            }
        }
        eq("suffix array empty", 0, SuffixArray.build("").length);
        eq("suffix array single", "[0]", Arrays.toString(SuffixArray.build("z")));

        // LCP vs brute force
        for (int trial = 0; trial < 200; trial++) {
            int n = 1 + random.nextInt(50);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append((char) ('a' + random.nextInt(3)));
            String s = sb.toString();
            int[] sa = SuffixArray.build(s);
            int[] lcp = KasaiLcp.build(s, sa);
            for (int i = 0; i < lcp.length; i++) {
                int a = sa[i], b = sa[i + 1], shared = 0;
                while (a + shared < n && b + shared < n && s.charAt(a + shared) == s.charAt(b + shared)) shared++;
                if (lcp[i] != shared) { eq("lcp " + s + " idx " + i, shared, lcp[i]); break; }
            }
        }

        // Aho-Corasick vs naive multi-pattern
        List<String> patterns = List.of("delayed", "customs hold", "damaged", "abc", "bc");
        AhoCorasick ac = new AhoCorasick();
        for (String p : patterns) ac.add(p);
        ac.build();
        for (int trial = 0; trial < 100; trial++) {
            StringBuilder sb = new StringBuilder();
            int n = 5 + random.nextInt(60);
            for (int i = 0; i < n; i++) {
                if (random.nextInt(6) == 0) sb.append(' ');
                else if (random.nextInt(8) == 0) sb.append(patterns.get(random.nextInt(patterns.size())));
                else sb.append((char) ('a' + random.nextInt(4)));
            }
            String text = sb.toString();
            List<PatternMatch> fromAc = ac.findAll(text);
            List<PatternMatch> oracle = new ArrayList<>();
            for (int p = 0; p < patterns.size(); p++) {
                String lower = text.toLowerCase(Locale.ROOT);
                String needle = patterns.get(p).toLowerCase(Locale.ROOT);
                int from = 0;
                while (true) {
                    int at = lower.indexOf(needle, from);
                    if (at < 0) break;
                    oracle.add(new PatternMatch(needle, p, at, at + needle.length()));
                    from = at + 1;
                }
            }
            oracle.sort(Comparator.comparingInt(PatternMatch::start).thenComparingInt(PatternMatch::patternId));
            fromAc.sort(Comparator.comparingInt(PatternMatch::start).thenComparingInt(PatternMatch::patternId));
            if (!fromAc.equals(oracle)) {
                eq("aho '" + text + "'", oracle, fromAc);
            }
        }

        // KMP: textbook answers + cross-check against the brute-force oracle
        Kmp kmp = Kmp.of("ana");
        eq("kmp finds ana", 1, kmp.search("banana"));
        eq("kmp count ana", 2, kmp.countOccurrences("banana"));
        eq("kmp missing", -1, Kmp.of("xyz").search("banana"));
        for (int trial = 0; trial < 150; trial++) {
            int n = 2 + random.nextInt(40);
            StringBuilder textBuilder = new StringBuilder();
            for (int i = 0; i < n; i++) {
                textBuilder.append((char) ('a' + random.nextInt(3)));
            }
            String text = textBuilder.toString();
            int plen = 1 + random.nextInt(Math.min(n, 8));
            StringBuilder patternBuilder = new StringBuilder();
            for (int i = 0; i < plen; i++) {
                patternBuilder.append((char) ('a' + random.nextInt(3)));
            }
            String pat = patternBuilder.toString();
            eq("kmp trial " + trial, naiveOccurrences(text, pat), Kmp.of(pat).countOccurrences(text));
        }

        // Similarity
        String doc1 = "the quick brown fox jumps over the lazy dog near the river bank";
        String doc2 = "a quick brown fox jumps over the lazy dog by the river bank today";
        String lcs = SimilarityIndex.longestCommonSubstring(doc1, doc2);
        eq("lcs content", " quick brown fox jumps over the lazy dog ", lcs);
        double j = SimilarityIndex.jaccard(doc1, doc2, 2);
        eq("jaccard in range", true, j > 0.3 && j < 0.8);
        eq("jaccard identical", 1.0, SimilarityIndex.jaccard(doc1, doc1, 2));

        // Rabin-Karp double hash agrees with brute force even on a tiny alphabet
        RabinKarp doubleHash = new RabinKarp("ana", 257, 1_000_000_007, 269, 1_000_000_009);
        ok("double hash uses two moduli", doubleHash.usesDoubleHashing());
        eq("double hash ana", 2, doubleHash.countOccurrencesDoubleHashed("banana"));
        for (int trial = 0; trial < 150; trial++) {
            int n = 2 + random.nextInt(40);
            StringBuilder textBuilder = new StringBuilder();
            for (int i = 0; i < n; i++) {
                textBuilder.append((char) ('a' + random.nextInt(3)));
            }
            String text = textBuilder.toString();
            int plen = 1 + random.nextInt(Math.min(n, 8));
            StringBuilder patternBuilder = new StringBuilder();
            for (int i = 0; i < plen; i++) {
                patternBuilder.append((char) ('a' + random.nextInt(3)));
            }
            String pat = patternBuilder.toString();
            RabinKarp rk = new RabinKarp(pat, 257, 1_000_000_007, 269, 1_000_000_009);
            eq("double hash trial " + trial, naiveOccurrences(text, pat), rk.countOccurrencesDoubleHashed(text));
        }

        System.out.println(failures == 0 ? "ALL STRING/SUFFIX SMOKE TESTS PASSED" : failures + " FAILURES");
        if (failures > 0) System.exit(1);
    }

    static int naiveOccurrences(String text, String pattern) {
        String lower = text.toLowerCase(Locale.ROOT);
        String needle = pattern.toLowerCase(Locale.ROOT);
        int count = 0;
        int from = 0;
        while (true) {
            int at = lower.indexOf(needle, from);
            if (at < 0) {
                break;
            }
            count++;
            from = at + 1;
        }
        return count;
    }
}
