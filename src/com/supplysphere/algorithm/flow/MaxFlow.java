package com.supplysphere.algorithm.flow;

import com.supplysphere.core.util.Preconditions;

/**
 * The Ford-Fulkerson method and its BFS specialisation, Edmonds-Karp —
 * Module 4 / CO4.
 *
 * <p>Repeatedly find an augmenting path source-to-sink in the residual graph
 * and push the path's bottleneck capacity along it. Ford-Fulkerson with DFS
 * paths can take exponential time on irrational capacities; with integer
 * capacities it stops after at most {@code |f*|} augmentations. Edmonds-Karp
 * always augments along a hop-shortest path, which forbids long paths from
 * recurring and gives the classic {@code O(V E^2)} bound in practice.</p>
 *
 * <p>Supply-chain reading: lanes are arcs, warehouse throughput is capacity,
 * and the optimum flow is the most orders that can move origin-to-destination
 * in one planning window.</p>
 */
public final class MaxFlow {

    private MaxFlow() {
    }

    /** Ford-Fulkerson with depth-first augmenting paths. Teaching reference. */
    public static FlowResult fordFulkerson(FlowNetwork network, int source, int sink) {
        validate(network, source, sink);
        int n = network.vertexCount();
        int[] parentVertex = new int[n];
        int[] parentEdge = new int[n];
        boolean[] seen = new boolean[n];
        int total = 0;

        while (true) {
            java.util.Arrays.fill(seen, false);
            java.util.Arrays.fill(parentVertex, -1);
            parentVertex[source] = source;
            if (!network.depthFirstPath(source, sink, seen, parentVertex, parentEdge)) {
                break;
            }
            total += augment(network, source, sink, parentVertex, parentEdge);
        }
        return new FlowResult(total, network.sourceSide(source), network);
    }

    /** Edmonds-Karp: the same method with BFS shortest paths, {@code O(V E^2)}. */
    public static FlowResult edmondsKarp(FlowNetwork network, int source, int sink) {
        validate(network, source, sink);
        int n = network.vertexCount();
        int[] parentVertex = new int[n];
        int[] parentEdge = new int[n];
        int total = 0;

        while (network.breadthFirstPath(source, sink, parentVertex, parentEdge)) {
            total += augment(network, source, sink, parentVertex, parentEdge);
        }
        return new FlowResult(total, network.sourceSide(source), network);
    }

    /** Walks the parent pointers back to the source, pushing the bottleneck. */
    private static int augment(FlowNetwork network, int source, int sink,
                               int[] parentVertex, int[] parentEdge) {
        int bottleneck = Integer.MAX_VALUE;
        for (int v = sink; v != source; v = parentVertex[v]) {
            FlowNetwork.Edge arc = network.adjacent(parentVertex[v]).get(parentEdge[v]);
            bottleneck = Math.min(bottleneck, arc.residual());
        }
        for (int v = sink; v != source; v = parentVertex[v]) {
            network.push(network.adjacent(parentVertex[v]).get(parentEdge[v]), bottleneck);
        }
        return bottleneck;
    }

    private static void validate(FlowNetwork network, int source, int sink) {
        Preconditions.requireNonNull(network, "network");
        Preconditions.checkState(source != sink, "source and sink must differ");
        Preconditions.checkState(sink >= 0 && sink < network.vertexCount(), "sink out of range");
    }
}
