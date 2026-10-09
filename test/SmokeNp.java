import com.supplysphere.algorithm.np.SchedulingApprox;
import com.supplysphere.algorithm.np.VertexCoverApprox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Module 5 smoke tests: approximation algorithms. The shipped pair is
 * SchedulingApprox (main) with its optimal/lower-bound checks, and
 * VertexCoverApprox (benchmark) validated against brute force.
 */
public class SmokeNp {

    static int failures = 0;

    static void ok(String name, boolean condition) {
        if (condition) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    static void eq(String name, long expected, long actual) {
        if (expected == actual) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name + ": expected " + expected + " got " + actual);
        }
    }

    public static void main(String[] args) {
        testVertexCover();
        testScheduling();
        if (failures == 0) {
            System.out.println("ALL NP SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
        }
    }

    // ---------------------------------------------------------- vertex cover

    static void testVertexCover() {
        Random rng = new Random(11);
        for (int trial = 0; trial < 30; trial++) {
            int n = 7 + rng.nextInt(6);
            int[][] edges = randomEdges(n, 6 + rng.nextInt(12), rng);
            int[] approx = VertexCoverApprox.twoApprox(n, edges);
            int[] exact = VertexCoverApprox.exactBruteForce(n, edges);
            ok("twoApprox valid trial " + trial, VertexCoverApprox.isVertexCover(n, edges, approx));
            ok("twoApprox <= 2*opt trial " + trial, approx.length <= 2 * exact.length);
            ok("exact valid trial " + trial, VertexCoverApprox.isVertexCover(n, edges, exact));
            ok("exact <= approx trial " + trial, exact.length <= approx.length);
        }
    }

    static int[][] randomEdges(int n, int m, Random rng) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            int u = rng.nextInt(n);
            int v = rng.nextInt(n);
            if (u != v) {
                edges.add(new int[]{u, v});
            }
        }
        return edges.toArray(new int[0][]);
    }

    // ------------------------------------------------------------ scheduling

    static void testScheduling() {
        Random rng = new Random(47);
        for (int trial = 0; trial < 30; trial++) {
            int n = 5 + rng.nextInt(6);
            int m = 2 + rng.nextInt(3);
            int[] times = new int[n];
            for (int i = 0; i < n; i++) {
                times[i] = 1 + rng.nextInt(30);
            }

            SchedulingApprox.Schedule opt = SchedulingApprox.optimalSchedule(m, times);
            SchedulingApprox.Schedule list = SchedulingApprox.listSchedule(m, times);
            SchedulingApprox.Schedule lpt = SchedulingApprox.lptSchedule(m, times);

            ok("opt makespan >= lowerBound trial " + trial,
                    opt.makespan() >= opt.lowerBound());
            ok("list well-formed trial " + trial, wellFormed(list, n, times));
            ok("lpt well-formed trial " + trial, wellFormed(lpt, n, times));
            ok("opt well-formed trial " + trial, wellFormed(opt, n, times));

            long listBound = (2 * m - 1) * opt.makespan() / m;
            ok("list <= (2-1/m)*opt trial " + trial + " list=" + list.makespan()
                    + " opt=" + opt.makespan() + " bound=" + listBound,
                    list.makespan() <= listBound);

            long lptBound = (4 * m - 1) * opt.makespan() / (3 * m);
            ok("lpt <= (4/3-1/(3m))*opt trial " + trial + " lpt=" + lpt.makespan()
                    + " opt=" + opt.makespan() + " bound=" + lptBound,
                    lpt.makespan() <= lptBound);

            ok("opt <= list trial " + trial, opt.makespan() <= list.makespan());
            ok("opt <= lpt trial " + trial, opt.makespan() <= lpt.makespan());
        }
    }

    static boolean wellFormed(SchedulingApprox.Schedule schedule, int n, int[] times) {
        boolean[] seen = new boolean[n];
        long maxLoad = 0;
        for (int machine = 0; machine < schedule.machines().length; machine++) {
            long load = 0;
            for (int job : schedule.machines()[machine]) {
                if (job < 0 || job >= n || seen[job]) {
                    return false;
                }
                seen[job] = true;
                load += times[job];
            }
            maxLoad = Math.max(maxLoad, load);
        }
        for (boolean b : seen) {
            if (!b) {
                return false;
            }
        }
        return maxLoad == schedule.makespan();
    }
}
