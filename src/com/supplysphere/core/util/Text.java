package com.supplysphere.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text canonicalisation shared by every string algorithm in the platform.
 *
 * <p>Supply-chain data arrives from ERP exports, supplier emails and carrier
 * scanning devices, so the same supplier name is routinely written as
 * {@code "ACME  Logistics"}, {@code "acme-logistics"} or {@code "Acme_Logistics"}.
 * Normalising once at the boundary makes the downstream KMP / Rabin-Karp /
 * Aho-Corasick results consistent and keeps those algorithms free of
 * case-folding concerns.</p>
 */
public final class Text {

    private Text() {
    }

    /** Collapses runs of whitespace, trims, and lower-cases using the root locale. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String collapsed = WHITESPACE.matcher(value).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        return collapsed;
    }

    /** Normalises while preserving case; used where display fidelity matters. */
    public static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * Splits normalised text into word tokens, dropping punctuation. Used for
     * shingling in document similarity and for repeated-pattern detection.
     */
    public static List<String> words(String value) {
        List<String> tokens = new ArrayList<>();
        if (isBlank(value)) {
            return tokens;
        }
        for (String raw : normalize(value).split("[^a-z0-9]+")) {
            if (!raw.isEmpty()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    /** Keeps only letters and digits — strips separators from codes such as {@code PO-2024-8871}. */
    public static String stripSeparators(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    /** Cyclic shift of a character array, used by Rabin-Karp for O(1) re-hashing. */
    public static int[] shift(char[] pattern, int base, int mod) {
        int highOrder = 0;
        for (int i = 0; i < pattern.length; i++) {
            highOrder = (highOrder * base + pattern[i]) % mod;
        }
        return new int[]{base, highOrder};
    }

    /** Abbreviates long text for table rendering. */
    public static String ellipsize(String value, int max) {
        Preconditions.requirePositive(max, "max");
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        if (max <= 3) {
            return value.substring(0, max);
        }
        return value.substring(0, max - 3) + "...";
    }

    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
}
