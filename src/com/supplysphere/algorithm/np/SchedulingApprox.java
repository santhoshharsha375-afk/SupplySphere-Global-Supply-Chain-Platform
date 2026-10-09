package com.supplysphere.algorithm.np;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Parallel machine scheduling: makespan minimisation — Module 5 / CO5.
 *
 * <p>Assigning {@code n} jobs to {@code m} identical machines so the busiest
 * machine finishes as early as possible ({@code P||Cmax}) is NP-hard for two
 * or more machines. This class ships the two classic heuristics with their
 * proven worst-case ratios, plus an exact branch-and-bound used to verify
 * those ratios on small instances:</p>
 *
 * <ul>
 *   <li><b>List scheduling</b> — give each next job to the currently
 *       lightest machine. Any job that opens the final makespan ran alone on
 *       its machine while everything else finished elsewhere, giving
 *       {@code makespan ≤ (2 − 1/m)·OPT}.</li>
 *   <li><b>LPT (longest processing time first)</b> — the same rule after
 *       sorting jobs largest-first; short jobs fill the gaps left behind.
 *       Graham's bound: {@code makespan ≤ (4/3 − 1/(3m))·OPT}.</li>
 * </ul>
 *
 * <p>Both bounds hold against {@code lowerBound(m, times)} =
 * {@code max(longest job, ceil(total/m))}, the standard dual feasible
 * lower bound.</p>
 */
public final class SchedulingApprox {

    private SchedulingApprox() {
    }

    /** {@code machines[i]} lists the jobs run on machine {@code i}. */
    public record Schedule(int[][] machines, long makespan, long lowerBound) {
    }

    /**
     * Graham list scheduling: next job → lightest machine, ties broken by
     * lowest machine index. Runs in {@code O(n log m)} with a heap.
     * Guaranteed {@code makespan ≤ (2 − 1/m)·OPT}.
     */
    public static Schedule listSchedule(int machineCount, int[] processingTimes) {
        validate(machineCount, processingTimes);
        int n = processingTimes.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        return runListSchedule(machineCount, processingTimes, order);
    }

    /**
     * LPT: list scheduling after sorting jobs longest-first (ties by index).
     * Guaranteed {@code makespan ≤ (4/3 − 1/(3m))·OPT} — never worse than
     * plain list scheduling's ratio.
     */
    public static Schedule lptSchedule(int machineCount, int[] processingTimes) {
        validate(machineCount, processingTimes);
        Integer[] jobs = new Integer[processingTimes.length];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = i;
        }
        Arrays.sort(jobs, Comparator
                .comparingInt((Integer job) -> processingTimes[job]).reversed()
                .thenComparingInt(job -> job));
        int[] order = new int[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            order[i] = jobs[i];
        }
        return runListSchedule(machineCount, processingTimes, order);
    }

    /**
     * Exact optimum by branch-and-bound over job→machine assignments,
     * seeded with the LPT solution as an upper bound. Jobs are placed
     * longest-first and identical machine loads are explored once
     * (symmetry breaking), so verification instances up to about 30 jobs
     * on 8 machines stay instant — beyond that the problem is exponential
     * and the bound is not enforced.
     */
    public static Schedule optimalSchedule(int machineCount, int[] processingTimes) {
        validate(machineCount, processingTimes);
        int n = processingTimes.length;
        Preconditions.requireInRange(n, 1, 30, "job count for exact scheduling");
        Preconditions.requireInRange(machineCount, 1, 8, "machine count for exact scheduling");

        Integer[] sortedJobs = new Integer[n];
        for (int i = 0; i < n; i++) {
            sortedJobs[i] = i;
        }
        Arrays.sort(sortedJobs, Comparator
                .comparingInt((Integer job) -> processingTimes[job]).reversed()
                .thenComparingInt(job -> job));
        int[] sorted = new int[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = sortedJobs[i];
        }
        long total = 0;
        for (int time : processingTimes) {
            total += time;
        }

        long[] loads = new long[machineCount];
        int[] slotMachine = new int[n];
        Schedule initial = lptSchedule(machineCount, processingTimes);
        int[] bestSlotMachine = new int[n];
        long[] bestLoads = new long[machineCount];
        for (int m = 0; m < machineCount; m++) {
            for (int job : initial.machines()[m]) {
                bestLoads[m] += processingTimes[job];
            }
        }
        int[] jobMachine = new int[n];
        for (int m = 0; m < machineCount; m++) {
            for (int job : initial.machines()[m]) {
                jobMachine[job] = m;
            }
        }
        for (int slot = 0; slot < n; slot++) {
            bestSlotMachine[slot] = jobMachine[sorted[slot]];
        }
        long[] best = {initial.makespan()};

        search(sorted, processingTimes, total, 0, loads, 0, slotMachine,
                bestSlotMachine, bestLoads, best, machineCount);

        long makespan = 0;
        for (long load : bestLoads) {
            makespan = Math.max(makespan, load);
        }
        List<List<Integer>> machines = new ArrayList<>();
        for (int i = 0; i < machineCount; i++) {
            machines.add(new ArrayList<>());
        }
        for (int slot = 0; slot < n; slot++) {
            machines.get(bestSlotMachine[slot]).add(sorted[slot]);
        }
        return new Schedule(toArrays(machines), makespan, lowerBound(machineCount, processingTimes));
    }

    /** {@code max(longest job, ceil(total/m))} — always ≤ OPT, used to grade heuristics. */
    public static long lowerBound(int machineCount, int[] processingTimes) {
        Preconditions.requirePositive(machineCount, "machineCount");
        long total = 0;
        long longest = 0;
        for (int time : processingTimes) {
            total += time;
            longest = Math.max(longest, time);
        }
        long average = (total + machineCount - 1) / machineCount;
        return Math.max(longest, average);
    }

    // -------------------------------------------------------------- internals

    private static void validate(int machineCount, int[] processingTimes) {
        Preconditions.requirePositive(machineCount, "machineCount");
        Preconditions.requireNonNull(processingTimes, "processingTimes");
        for (int i = 0; i < processingTimes.length; i++) {
            Preconditions.requireNonNegative(processingTimes[i], "processingTimes[" + i + "]");
        }
    }

    private static Schedule runListSchedule(int machineCount, int[] times, int[] order) {
        long[] loads = new long[machineCount];
        List<List<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < machineCount; i++) {
            jobs.add(new ArrayList<>());
        }
        PriorityQueue<long[]> lightest = new PriorityQueue<>(Comparator
                .comparingLong((long[] entry) -> entry[0])
                .thenComparingLong(entry -> entry[1]));
        for (int i = 0; i < machineCount; i++) {
            lightest.offer(new long[]{0, i});
        }
        for (int job : order) {
            long[] pick = lightest.poll();
            int machine = (int) pick[1];
            jobs.get(machine).add(job);
            loads[machine] += times[job];
            lightest.offer(new long[]{loads[machine], machine});
        }
        long makespan = 0;
        for (long load : loads) {
            makespan = Math.max(makespan, load);
        }
        return new Schedule(toArrays(jobs), makespan, lowerBound(machineCount, times));
    }

    private static void search(int[] sorted, int[] times, long total, int depth, long[] loads,
            long currentMax, int[] slotMachine, int[] bestSlotMachine, long[] bestLoads,
            long[] best, int machineCount) {
        if (currentMax >= best[0]) {
            return;
        }
        if (depth == sorted.length) {
            best[0] = currentMax;
            System.arraycopy(loads, 0, bestLoads, 0, machineCount);
            System.arraycopy(slotMachine, 0, bestSlotMachine, 0, sorted.length);
            return;
        }
        long average = (total + machineCount - 1) / machineCount;
        if (Math.max(currentMax, average) >= best[0]) {
            return;
        }
        int job = sorted[depth];
        int time = times[job];
        for (int machine = 0; machine < machineCount; machine++) {
            if (machine > 0 && loads[machine] == loads[machine - 1]) {
                continue;
            }
            loads[machine] += time;
            slotMachine[depth] = machine;
            search(sorted, times, total, depth + 1, loads,
                    Math.max(currentMax, loads[machine]), slotMachine,
                    bestSlotMachine, bestLoads, best, machineCount);
            loads[machine] -= time;
        }
    }

    private static int[][] toArrays(List<List<Integer>> jobs) {
        int[][] result = new int[jobs.size()][];
        for (int i = 0; i < jobs.size(); i++) {
            result[i] = new int[jobs.get(i).size()];
            for (int j = 0; j < result[i].length; j++) {
                result[i][j] = jobs.get(i).get(j);
            }
        }
        return result;
    }
}
