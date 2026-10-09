package com.supplysphere.algorithm.dp;

import com.supplysphere.core.util.Preconditions;

/**
 * Sequence alignment — Needleman-Wunsch (global) and Smith-Waterman (local),
 * Module 3 / CO3.
 *
 * <p>Used to compare shipment-event narratives or SKUs character by character:
 * a global alignment scores two whole records end to end, while a local
 * alignment finds the best-matching fragment inside two long, mostly unrelated
 * texts.</p>
 *
 * <p>Both are Wagner-Fischer-shaped recurrences with a scoring twist. Global:
 * every position must be aligned, so the answer reads from the bottom-right
 * corner and gaps are charged. Local: cells drop to 0 on a negative score, the
 * answer is the maximum cell, and traceback stops at the first zero — only
 * genuinely related fragments survive. Smith-Waterman is optimal for its
 * fragment by construction.</p>
 *
 * <p>Gap models: the linear penalty used here charges {@code gap} per gapped
 * position. The affine model ({@code gapOpen + gapExtend} per gap run) better
 * matches biology and carrier notes — a single long outage should hurt less
 * than many one-character slips — and is implemented in
 * {@link #affineGapScore} with Gotoh's three-matrix formulation.</p>
 */
public final class SequenceAlignment {

    private SequenceAlignment() {
    }

    /**
     * @param first   aligned rendering of the first sequence
     * @param second  aligned rendering of the second sequence ({@code '-'} is a gap)
     * @param score   score of this alignment
     */
    public record Alignment(String first, String second, int score) {

        /** Human-readable block, two rows plus a match/mismatch ruler. */
        public String render() {
            StringBuilder ruler = new StringBuilder(first.length());
            for (int i = 0; i < first.length(); i++) {
                ruler.append(first.charAt(i) == second.charAt(i) ? '|' : ' ');
            }
            return first + "\n" + ruler + "\n" + second;
        }
    }

    /**
     * Global alignment (Needleman-Wunsch) with linear gap penalty.
     *
     * @param match     score for equal characters
     * @param mismatch  score (typically negative) for unequal characters
     * @param gap       score (typically negative) per gap position
     */
    public static Alignment needlemanWunsch(String a, String b, int match, int mismatch, int gap) {
        Preconditions.requireNonNull(a, "a");
        Preconditions.requireNonNull(b, "b");
        int n = a.length();
        int m = b.length();
        int[][] score = new int[n + 1][m + 1];
        byte[][] move = new byte[n + 1][m + 1];   // 1 = diagonal, 2 = up, 3 = left

        for (int i = 1; i <= n; i++) {
            score[i][0] = i * gap;
            move[i][0] = 2;
        }
        for (int j = 1; j <= m; j++) {
            score[0][j] = j * gap;
            move[0][j] = 3;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int diagonal = score[i - 1][j - 1]
                        + (a.charAt(i - 1) == b.charAt(j - 1) ? match : mismatch);
                int up = score[i - 1][j] + gap;
                int left = score[i][j - 1] + gap;
                int best = Math.max(diagonal, Math.max(up, left));
                score[i][j] = best;
                move[i][j] = (byte) (best == diagonal ? 1 : (best == up ? 2 : 3));
            }
        }
        return traceback(a, b, score, move, n, m, false);
    }

    /**
     * Local alignment (Smith-Waterman). Cells that would go negative are reset
     * to zero so unrelated flanks cannot drag the score down; traceback stops
     * where the running score first hits zero.
     */
    public static Alignment smithWaterman(String a, String b, int match, int mismatch, int gap) {
        Preconditions.requireNonNull(a, "a");
        Preconditions.requireNonNull(b, "b");
        int n = a.length();
        int m = b.length();
        int[][] score = new int[n + 1][m + 1];
        byte[][] move = new byte[n + 1][m + 1];

        int bestScore = 0;
        int bestI = 0;
        int bestJ = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int diagonal = score[i - 1][j - 1]
                        + (a.charAt(i - 1) == b.charAt(j - 1) ? match : mismatch);
                int up = score[i - 1][j] + gap;
                int left = score[i][j - 1] + gap;
                int best = Math.max(0, Math.max(diagonal, Math.max(up, left)));
                score[i][j] = best;
                move[i][j] = (byte) (best == diagonal ? 1 : (best == up ? 2 : (best == left ? 3 : 0)));
                if (best > bestScore) {
                    bestScore = best;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        return traceback(a, b, score, move, bestI, bestJ, true);
    }

    /**
     * Affine gap score (Gotoh): opening a gap costs {@code gapOpen}, each
     * further position in the same gap costs {@code gapExtend}. Score only —
     * traceback would need a third direction matrix and is omitted at preview
     * level.
     *
     * <p>Three states per cell: {@code M} (align a pair), {@code X} (gap in
     * {@code b}), {@code Y} (gap in {@code a}). Running all three separately is
     * what makes one long gap cheaper than two short ones.</p>
     */
    public static int affineGapScore(String a, String b, int match, int mismatch,
                                     int gapOpen, int gapExtend) {
        Preconditions.requireNonNull(a, "a");
        Preconditions.requireNonNull(b, "b");
        int n = a.length();
        int m = b.length();
        final int neginf = Integer.MIN_VALUE / 4;

        int[] mPrev = new int[m + 1];
        int[] xPrev = new int[m + 1];
        int[] yPrev = new int[m + 1];
        int[] mCur = new int[m + 1];
        int[] xCur = new int[m + 1];
        int[] yCur = new int[m + 1];

        mPrev[0] = 0;
        for (int j = 1; j <= m; j++) {
            mPrev[j] = neginf;
            xPrev[j] = neginf;
            yPrev[j] = gapOpen + (j - 1) * gapExtend;
        }
        for (int i = 1; i <= n; i++) {
            mCur[0] = neginf;
            xCur[0] = gapOpen + (i - 1) * gapExtend;
            yCur[0] = neginf;
            for (int j = 1; j <= m; j++) {
                mCur[j] = Math.max(mPrev[j - 1], Math.max(xPrev[j - 1], yPrev[j - 1]))
                        + (a.charAt(i - 1) == b.charAt(j - 1) ? match : mismatch);
                xCur[j] = Math.max(xPrev[j] + gapExtend, mPrev[j] + gapOpen);
                yCur[j] = Math.max(yCur[j - 1] + gapExtend, mCur[j - 1] + gapOpen);
            }
            int[] t;
            t = mPrev; mPrev = mCur; mCur = t;
            t = xPrev; xPrev = xCur; xCur = t;
            t = yPrev; yPrev = yCur; yCur = t;
        }
        return Math.max(mPrev[m], Math.max(xPrev[m], yPrev[m]));
    }

    /** Walks the move matrix back to the origin (or to a zero cell locally). */
    private static Alignment traceback(String a, String b, int[][] score, byte[][] move,
                                       int i, int j, boolean local) {
        int finalScore = score[i][j];   // cell we start from is the alignment's score
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        while (i > 0 || j > 0) {
            if (local && score[i][j] == 0) {
                break;
            }
            byte step = move[i][j];
            if (step == 1) {
                first.append(a.charAt(i - 1));
                second.append(b.charAt(j - 1));
                i--;
                j--;
            } else if (step == 2) {
                first.append(a.charAt(i - 1));
                second.append('-');
                i--;
            } else if (step == 3) {
                first.append('-');
                second.append(b.charAt(j - 1));
                j--;
            } else {
                break;
            }
        }
        return new Alignment(first.reverse().toString(), second.reverse().toString(), finalScore);
    }
}
