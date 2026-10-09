package com.supplysphere.algorithm.np;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertex cover: greedy 2-approximation and exact brute force — Module 5 / CO5.
 *
 * <p>Finding the smallest vertex cover is NP-hard, so the exact option costs
 * {@code O(2^n · m)}. The greedy scheme — repeatedly take both endpoints of
 * some uncovered edge — runs in {@code O(n + m)} and never uses more than
 * twice the optimum: each pick straddles an edge, and any optimal cover must
 * pay for at least one endpoint of that edge, so the charges balance.</p>
 */
public final class VertexCoverApprox {

    private VertexCoverApprox() {
    }

    /**
     * Greedy 2-approximation: while an edge is uncovered, add both of its
     * endpoints to the cover. Every edge is scanned at most twice (once per
     * endpoint that claims it), hence {@code O(n + m)}.
     *
     * @return cover vertices in ascending order; never larger than
     *         {@code 2 × optimum}
     */
    public static int[] twoApprox(int vertexCount, int[][] edges) {
        Preconditions.requirePositive(vertexCount, "vertexCount");
        Preconditions.requireNonNull(edges, "edges");

        @SuppressWarnings("unchecked")
        List<Integer>[] incident = new List[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            incident[v] = new ArrayList<>();
        }
        for (int i = 0; i < edges.length; i++) {
            int[] edge = edges[i];
            Preconditions.checkState(edge.length == 2, "each edge must have two endpoints");
            int u = edge[0];
            int w = edge[1];
            Preconditions.requireInRange(u, 0, vertexCount - 1, "edge endpoint");
            Preconditions.requireInRange(w, 0, vertexCount - 1, "edge endpoint");
            incident[u].add(i);
            if (w != u) {
                incident[w].add(i);
            }
        }

        boolean[] covered = new boolean[edges.length];
        boolean[] inCover = new boolean[vertexCount];
        int size = 0;
        for (int i = 0; i < edges.length; i++) {
            if (covered[i]) {
                continue;
            }
            for (int endpoint : new int[]{edges[i][0], edges[i][1]}) {
                if (inCover[endpoint]) {
                    continue;
                }
                inCover[endpoint] = true;
                size++;
                for (int edgeIndex : incident[endpoint]) {
                    covered[edgeIndex] = true;
                }
            }
        }

        int[] cover = new int[size];
        int next = 0;
        for (int v = 0; v < vertexCount; v++) {
            if (inCover[v]) {
                cover[next++] = v;
            }
        }
        return cover;
    }

    /** Checks that every edge has at least one endpoint inside {@code cover}. */
    public static boolean isVertexCover(int vertexCount, int[][] edges, int[] cover) {
        Preconditions.requireNonNull(edges, "edges");
        Preconditions.requireNonNull(cover, "cover");
        boolean[] inCover = new boolean[vertexCount];
        for (int vertex : cover) {
            Preconditions.requireInRange(vertex, 0, vertexCount - 1, "cover vertex");
            inCover[vertex] = true;
        }
        for (int[] edge : edges) {
            if (!inCover[edge[0]] && !inCover[edge[1]]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Exact minimum vertex cover by scanning all {@code 2^n} subsets —
     * the reference used to score the approximation's ratio. Hard-capped at
     * 20 vertices ({@code ~10^6} subsets).
     *
     * @return the smallest cover, ties broken by lowest vertex numbering
     */
    public static int[] exactBruteForce(int vertexCount, int[][] edges) {
        Preconditions.requireInRange(vertexCount, 1, 20,
                "vertexCount for brute-force vertex cover");
        Preconditions.requireNonNull(edges, "edges");

        int bestMask = 0;
        int bestSize = vertexCount + 1;
        for (int mask = 0; mask < (1 << vertexCount); mask++) {
            int size = Integer.bitCount(mask);
            if (size >= bestSize) {
                continue;
            }
            if (covers(mask, edges)) {
                bestMask = mask;
                bestSize = size;
            }
        }

        int[] cover = new int[bestSize];
        int next = 0;
        for (int v = 0; v < vertexCount; v++) {
            if ((bestMask & (1 << v)) != 0) {
                cover[next++] = v;
            }
        }
        return cover;
    }

    private static boolean covers(int mask, int[][] edges) {
        for (int[] edge : edges) {
            if ((mask & (1 << edge[0])) == 0 && (mask & (1 << edge[1])) == 0) {
                return false;
            }
        }
        return true;
    }
}
