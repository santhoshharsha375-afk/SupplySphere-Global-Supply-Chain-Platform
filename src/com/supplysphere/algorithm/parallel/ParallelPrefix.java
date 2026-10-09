package com.supplysphere.algorithm.parallel;

import com.supplysphere.core.util.Preconditions;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Work-efficient parallel prefix sum (scan) — Module 6 / CO6.
 *
 * <p>The scan computes, for every index {@code i}, the sum of all elements
 * before it — the primitive behind running totals (cumulative order value,
 * offsets into a concatenated index, upsweep of segment trees). The
 * sequential running total has span {@code O(n)}: nothing before position
 * {@code i} is known until position {@code i-1} is done. The blocked scan
 * breaks that dependency with three phases over {@code b = ceil(n/grain)}
 * blocks:</p>
 *
 * <ol>
 *   <li><b>Local scan</b> — each block writes its own exclusive prefix in
 *       parallel and reports its total. Work {@code O(n)}, span
 *       {@code O(grain)}.</li>
 *   <li><b>Offsets</b> — a sequential prefix over the {@code b} block
 *       totals: every block learns where its first element starts in the
 *       global sum. Work and span {@code O(b)}.</li>
 *   <li><b>Apply</b> — every block adds its offset to its elements, in
 *       parallel. Work {@code O(n)}, span {@code O(grain)}.</li>
 * </ol>
 *
 * <p>Total work stays at {@code O(n)} — the definition of work-efficient —
 * and Brent's theorem gives time {@code O(n/p + grain + n/grain)} on
 * {@code p} workers. The default grain keeps both additive terms far below
 * the sequential {@code O(n)} span for catalogue-sized inputs.</p>
 */
public final class ParallelPrefix {

    /** Block length: elements scanned sequentially inside one task. */
    public static final int DEFAULT_GRAIN = 1 << 14;

    private ParallelPrefix() {
    }

    /**
     * Returns the <em>exclusive</em> prefix sum of {@code values} in a new
     * array: result{@code [0] = 0} and result{@code [i] = values[0] + ... +
     * values[i-1]}. Work {@code O(n)}.
     */
    public static long[] exclusiveScan(int[] values) {
        return exclusiveScan(values, DEFAULT_GRAIN);
    }

    /** Exclusive scan with an explicit block length (small grains aid testing). */
    public static long[] exclusiveScan(int[] values, int grain) {
        Preconditions.requireNonNull(values, "values");
        Preconditions.requirePositive(grain, "grain");
        long[] out = new long[values.length];
        if (values.length <= 1) {
            return out;
        }
        int blocks = (values.length + grain - 1) / grain;
        long[] totals = new long[blocks];

        ForkJoinPool.commonPool().invoke(new LocalScanTask(values, out, totals, grain, 0, blocks));
        long[] offsets = new long[blocks];
        for (int b = 1; b < blocks; b++) {
            offsets[b] = offsets[b - 1] + totals[b - 1];
        }
        ForkJoinPool.commonPool().invoke(new OffsetTask(out, offsets, grain, 0, blocks));
        return out;
    }

    /**
     * Inclusive prefix sum: result{@code [i] = values[0] + ... + values[i]}.
     * Folded onto the exclusive result in one O(n) pass, which does not
     * change the span order.
     */
    public static long[] inclusiveScan(int[] values) {
        long[] exclusive = exclusiveScan(values);
        for (int i = 0; i < values.length; i++) {
            exclusive[i] += values[i];
        }
        return exclusive;
    }

    /** Phase 1: local exclusive prefix per block plus the block total. */
    private static final class LocalScanTask extends RecursiveAction {

        private final int[] values;
        private final long[] out;
        private final long[] totals;
        private final int grain;
        private final int blockFrom;
        private final int blockTo;

        LocalScanTask(int[] values, long[] out, long[] totals, int grain, int blockFrom, int blockTo) {
            this.values = values;
            this.out = out;
            this.totals = totals;
            this.grain = grain;
            this.blockFrom = blockFrom;
            this.blockTo = blockTo;
        }

        @Override
        protected void compute() {
            if (blockTo - blockFrom == 1) {
                int from = blockFrom * grain;
                int to = Math.min(values.length, from + grain);
                long running = 0L;
                for (int i = from; i < to; i++) {
                    out[i] = running;
                    running += values[i];
                }
                totals[blockFrom] = running;
                return;
            }
            int mid = (blockFrom + blockTo) >>> 1;
            invokeAll(
                    new LocalScanTask(values, out, totals, grain, blockFrom, mid),
                    new LocalScanTask(values, out, totals, grain, mid, blockTo));
        }
    }

    /** Phase 3: add the block's global offset to every element it owns. */
    private static final class OffsetTask extends RecursiveAction {

        private final long[] out;
        private final long[] offsets;
        private final int grain;
        private final int blockFrom;
        private final int blockTo;

        OffsetTask(long[] out, long[] offsets, int grain, int blockFrom, int blockTo) {
            this.out = out;
            this.offsets = offsets;
            this.grain = grain;
            this.blockFrom = blockFrom;
            this.blockTo = blockTo;
        }

        @Override
        protected void compute() {
            if (blockTo - blockFrom == 1) {
                long offset = offsets[blockFrom];
                int from = blockFrom * grain;
                int to = Math.min(out.length, from + grain);
                for (int i = from; i < to; i++) {
                    out[i] += offset;
                }
                return;
            }
            int mid = (blockFrom + blockTo) >>> 1;
            invokeAll(
                    new OffsetTask(out, offsets, grain, blockFrom, mid),
                    new OffsetTask(out, offsets, grain, mid, blockTo));
        }
    }
}
