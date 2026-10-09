import com.supplysphere.algorithm.parallel.ParallelPrefix;
import com.supplysphere.algorithm.randomised.MillerRabin;

import java.util.Random;

/**
 * Module 6 smoke tests: randomised + parallel. The shipped pair is MillerRabin
 * (main, with its deterministic witness set) and ParallelPrefix (benchmark,
 * checked against a sequential running sum including an overflow case).
 */
public class SmokeRandomParallel {

    static int failures = 0;

    static void eq(String name, long expected, long actual) {
        if (expected == actual) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name + ": expected " + expected + " got " + actual);
        }
    }

    static void ok(String name, boolean condition) {
        if (condition) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    public static void main(String[] args) {
        testPrimality();
        testPrefix();
        if (failures == 0) {
            System.out.println("ALL RANDOMISED/PARALLEL SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
        }
    }

    static void testPrimality() {
        // Miller-Rabin near-trivial cases
        for (long n : new long[]{2, 3, 5, 7, 11}) {
            ok("mr small prime " + n, MillerRabin.isPrime(n));
        }
        for (long n : new long[]{4, 6, 8, 9, 10, 15, 100}) {
            ok("mr small composite " + n, !MillerRabin.isPrime(n));
        }
        // Large primes in range: 2^31-1, 2^32-5, the largest prime below 10^9
        for (long n : new long[]{999999937L, 1000000007L, 1000000009L, 2147483647L, 4294967291L}) {
            ok("mr large prime " + n, MillerRabin.isPrime(n));
        }
        // Carmichael numbers (Fermat liars) must still be witnessed composite
        for (long n : new long[]{561L, 1105L, 1729L, 3215031751L}) {
            ok("mr carmichael composite " + n, !MillerRabin.isPrime(n));
        }

        // Cross-check the deterministic test against a trial-division sieve
        Random rng = new Random(23);
        for (int trial = 0; trial < 300; trial++) {
            int n = 2 + rng.nextInt(1_000_000);
            ok("mr agrees with sieve trial " + trial, MillerRabin.isPrime(n) == naivePrime(n));
        }
    }

    static boolean naivePrime(int n) {
        if (n < 2) {
            return false;
        }
        for (int d = 2; (long) d * d <= n; d++) {
            if (n % d == 0) {
                return false;
            }
        }
        return true;
    }

    static void testPrefix() {
        Random rng = new Random(1010);
        int[] sizes = {0, 1, 2, 63, 64, 65, 5000};
        for (int n : sizes) {
            int[] values = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = rng.nextInt(2000) - 1000;
            }
            long[] exclusive = ParallelPrefix.exclusiveScan(values, 16);
            long[] inclusive = ParallelPrefix.inclusiveScan(values);
            long running = 0;
            boolean exclusiveOk = exclusive.length == n;
            boolean inclusiveOk = inclusive.length == n;
            for (int i = 0; i < n; i++) {
                exclusiveOk &= exclusive[i] == running;
                running += values[i];
                inclusiveOk &= inclusive[i] == running;
            }
            ok("exclusive scan n=" + n, exclusiveOk);
            ok("inclusive scan n=" + n, inclusiveOk);
        }
        // Large array with default grain, overflow-prone values.
        int[] big = new int[50000];
        for (int i = 0; i < big.length; i++) {
            big[i] = Integer.MAX_VALUE;
        }
        long[] scan = ParallelPrefix.exclusiveScan(big);
        eq("scan big last", (big.length - 1L) * Integer.MAX_VALUE, scan[big.length - 1]);
        eq("scan big first", 0, scan[0]);
    }
}
