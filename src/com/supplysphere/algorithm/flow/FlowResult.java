package com.supplysphere.algorithm.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * The answer produced by every max-flow algorithm: the optimum value together
 * with the certificate that proves it — the minimum cut — Module 4 / CO4.
 *
 * <p>Max-flow and min-cut are the same number seen from two sides (the
 * max-flow min-cut theorem), so a result without the cut is only half an
 * answer: the cut is what the platform uses to name the bottlenecks.</p>
 *
 * @param maxFlow       value of the maximum flow
 * @param sourceSide    true for vertices on the source side of the min-cut
 * @param network       the network the flow was computed on (now holding residuals)
 */
public record FlowResult(int maxFlow, boolean[] sourceSide, FlowNetwork network) {

    /** Capacity of the reported cut — equals {@link #maxFlow()} when integral. */
    public int minCutCapacity() {
        int total = 0;
        for (FlowNetwork.Edge edge : network.edges()) {
            if (sourceSide[edge.from()] && !sourceSide[edge.to()]) {
                total += edge.capacity();
            }
        }
        return total;
    }

    /** The cut arcs themselves, i.e. the saturated bottleneck links. */
    public List<FlowNetwork.Edge> minCutEdges() {
        List<FlowNetwork.Edge> cut = new ArrayList<>();
        for (FlowNetwork.Edge edge : network.edges()) {
            if (sourceSide[edge.from()] && !sourceSide[edge.to()]) {
                cut.add(edge);
            }
        }
        return List.copyOf(cut);
    }

    /** True when the reported cut really saturates — a cheap correctness check. */
    public boolean isCertified() {
        return minCutCapacity() == maxFlow;
    }
}
