package com.supplysphere.algorithm.randomised;

import com.supplysphere.core.util.Preconditions;

import java.util.Random;

/**
 * Miller-Rabin primality testing — Module 6 / CO6.
 *
 * <p>Used as the engine's "is this rate-card quantity a large prime?" guard
 * before expensive simulation runs. A single modular exponentiation cannot
 * certify primality, but a battery of them can with overwhelming confidence,
 * and a fixed battery can be <em>deterministic</em> below a known bound.</p>
 *
 * <p>Correctness idea: for odd {@code n}, write {@code n-1 = d * 2^s}. If
 * {@code n} is prime then by Fermat every {@code a} satisfies
 * {@code a^(n-1) ≡ 1}; more strongly, the first non-1 element of
 * {@code a^d, a^(2d), …, a^(n-1)} must be {@code n-1} (the only square roots of
 * 1 modulo a prime). A base that breaks either rule is a <em>witness</em>:
 * {@code a} proves {@code n} composite. Composites admit few witnesses, so
 * several random bases bound the error to {@code 4^-rounds}.</p>
 *
 * <p>Jumping to determinism: below the classic bound
 * {@code 341 550 071 728 321}, the fixed witness set
 * {@code {2, 3, 5, 7, 11, 13, 17}} certifies
 * primality with zero error probability. This class restricts itself to that
 * range, where the arithmetic used below is provably overflow free.</p>
 *
 * <p>All modular products are computed by the additive doubling trick (no
 * {@code long} multiplication), because {@code a * b mod n} overflows the
 * 64-bit register for {@code n} near {@code 2^63}, and every algorithm must be
 * hand-built here.</p>
 */
public final class MillerRabin {

    /** Deterministic range for the fixed Basis set ({@code n < 341 550 071 728 321 = 2^48.28…}). */
    public static final long DETERMINISTIC_LIMIT = 341_550_071_728_321L;

    /** Fixed basis set: proven to certify every prime below {@link #DETERMINISTIC_LIMIT}. */
    private static final long[] BASES = {2, 3, 5, 7, 11, 13, 17};

    private MillerRabin() {
    }

    /**
     * Deterministic primality test for {@code 2 <= n < DETERMINISTIC_LIMIT}.
     * Runs the full seven-base battery; every prime in range is certified,
     * every composite in range is witnessed.
     */
    public static boolean isPrime(long n) {
        Preconditions.requireInRange(n, 2, DETERMINISTIC_LIMIT - 1, "n");
        for (long base : BASES) {
            if (base >= n) {
                continue;
            }
            if (!passes(base, n)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Probabilistic variant used when the input may approach the limit: picks
     * {@code rounds} bases deterministically when possible and randomly
     * otherwise. Error probability is at most {@code 4^-rounds} for a true
     * composite — the same certificate machinery, a much larger failure bound.
     */
    public static boolean isProbablyPrime(long n, int rounds, Random rng) {
        Preconditions.requireInRange(n, 2, DETERMINISTIC_LIMIT - 1, "n");
        Preconditions.requirePositive(rounds, "rounds");
        Preconditions.requireNonNull(rng, "rng");
        for (int i = 0; i < rounds; i++) {
            if (i < BASES.length && BASES[i] < n) {
                if (!passes(BASES[i], n)) {
                    return false;
                }
                continue;
            }
            long span = n - 3;
            if (span <= 0) {
                // n in {2, 3}: the small fixed bases already cover every
                // candidate below 4, so no random draw is needed.
                continue;
            }
            long base = 2 + (long) (rng.nextDouble() * span);
            if (!passes(base, n)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One Miller-Rabin round for base {@code a} against odd {@code n}.
     * Decomposes {@code n-1 = d * 2^s}, raises {@code a^d mod n}, then squares
     * {@code s - 1} times watching for a first non-1 value that is not {@code n-1}.
     */
    private static boolean passes(long a, long n) {
        long d = n - 1;
        int s = 0;
        while ((d & 1) == 0) {
            d >>= 1;
            s++;
        }
        long x = powMod(a, d, n);
        if (x == 1 || x == n - 1) {
            return true;
        }
        for (int r = 1; r < s; r++) {
            x = mulMod(x, x, n);
            if (x == n - 1) {
                return true;
            }
            if (x == 1) {
                return false;
            }
        }
        return false;
    }

    /** {@code value^exponent mod modulus} via square-and-multiply. */
    private static long powMod(long value, long exponent, long modulus) {
        long result = 1 % modulus;
        long base = value % modulus;
        long e = exponent;
        while (e > 0) {
            if ((e & 1) == 1) {
                result = mulMod(result, base, modulus);
            }
            base = mulMod(base, base, modulus);
            e >>= 1;
        }
        return result;
    }

    /**
     * {@code (a * b) mod modulus} by repeated doubling. Each operand is below
     * {@code modulus < 2^49} here, so {@code a + a} and {@code result + a}
     * never overflow a signed {@code long}.
     */
    private static long mulMod(long a, long b, long modulus) {
        long result = 0;
        long addend = a % modulus;
        long factor = b % modulus;
        while (factor > 0) {
            if ((factor & 1) == 1) {
                result = (result + addend) % modulus;
            }
            addend = (addend + addend) % modulus;
            factor >>= 1;
        }
        return result;
    }
}