package com.supplysphere.algorithm.flow;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Dinic's algorithm — Module 4 / CO4.
 *
 * <p>Edmonds-Karp pays for a BFS per unit of flow. Dinic separates the two
 * concerns: a BFS builds the <em>level graph</em> (distance from the source in
 * hops), then a DFS blocks that level graph by saturating every path it can
 * still find before rebuilding levels. One level-graph phase kills a whole
 * class of augmenting paths, giving {@code O(V^2 E)} in general and
 * {@code O(E sqrt(V))} on unit networks — it is the practical reference
 * implementation for bipartite-style supply assignment graphs.</p>
 *
 * <p>The {@code nextArc} cursor per vertex is the key optimisation: each DFS
 * never revisits an arc that is already dead inside the current phase, so a
 * phase runs in {@code O(E)} time.</p>
 */
public final class Dinic {

    private final FlowNetwork network;
    private final int source;
    private final int sink;
    private final int[] level;
    private final int[] nextArc;

    public Dinic(FlowNetwork network, int source, int sink) {
        Preconditions.requireNonNull(network, "network");
        Preconditions.checkState(source != sink, "source and sink must differ");
        this.network = network;
        this.source = source;
        this.sink = sink;
        this.level = new int[network.vertexCount()];
        this.nextArc = new int[network.vertexCount()];
    }

    /** Runs the blocking-flow phases and returns the certified optimum. */
    public FlowResult maxFlow() {
        int total = 0;
        while (buildLevelGraph()) {
            Arrays.fill(nextArc, 0);
            int pushed;
            while ((pushed = sendFlow(source, Integer.MAX_VALUE)) > 0) {
                total += pushed;
            }
        }
        return new FlowResult(total, network.sourceSide(source), network);
    }

    /** Convenience one-shot entry point mirroring {@link MaxFlow#edmondsKarp}. */
    public static FlowResult run(FlowNetwork network, int source, int sink) {
        return new Dinic(network, source, sink).maxFlow();
    }

    /** BFS that assigns hop-distance levels; false when the sink is cut off. */
    private boolean buildLevelGraph() {
        Arrays.fill(level, -1);
        level[source] = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (FlowNetwork.Edge arc : network.adjacent(v)) {
                if (arc.residual() > 0 && level[arc.to()] < 0) {
                    level[arc.to()] = level[v] + 1;
                    queue.add(arc.to());
                }
            }
        }
        return level[sink] >= 0;
    }

    /** DFS along strictly increasing levels; returns the bottleneck found. */
    private int sendFlow(int v, int pushed) {
        if (v == sink) {
            return pushed;
        }
        for (; nextArc[v] < network.adjacent(v).size(); nextArc[v]++) {
            FlowNetwork.Edge arc = network.adjacent(v).get(nextArc[v]);
            if (arc.residual() > 0 && level[arc.to()] == level[v] + 1) {
                int sent = sendFlow(arc.to(), Math.min(pushed, arc.residual()));
                if (sent > 0) {
                    network.push(arc, sent);
                    return sent;
                }
            }
        }
        return 0;
    }
}
