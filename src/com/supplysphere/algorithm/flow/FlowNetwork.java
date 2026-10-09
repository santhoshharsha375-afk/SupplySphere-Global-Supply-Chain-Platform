package com.supplysphere.algorithm.flow;

import com.supplysphere.core.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Residual network for max-flow / min-cut — Module 4 / CO4.
 *
 * <p>Models a directed graph in which every edge carries a non-negative integer
 * capacity. Because all capacities are integers and every augmentation pushes at
 * least one unit, every algorithm that runs on this network terminates with an
 * integer-valued flow (the integrality theorem).</p>
 *
 * <p>Forward and reverse edges are stored together: pushing {@code f} units
 * across an edge lowers its residual capacity by {@code f} and raises the
 * reverse edge by {@code f}, which is exactly "undoing an earlier push". The
 * residual capacity is therefore the only mutable number in the class.</p>
 */
public final class FlowNetwork {

    /** One directed residual arc. {@code residual} is what may still be pushed. */
    public static final class Edge {
        final int from;
        final int to;
        final int rev;          // index of the reverse arc inside graph[to]
        final int capacity;     // original capacity, kept for min-cut reporting
        int residual;

        Edge(int from, int to, int rev, int capacity) {
            this.from = from;
            this.to = to;
            this.rev = rev;
            this.capacity = capacity;
            this.residual = capacity;
        }

        public int from() {
            return from;
        }

        public int to() {
            return to;
        }

        public int capacity() {
            return capacity;
        }

        /** Units currently routed over this arc ({@code capacity - residual}). */
        public int flow() {
            return capacity - residual;
        }

        public int residual() {
            return residual;
        }

        /** True for the caller-inserted arcs (the reverse twin has capacity 0). */
        public boolean isOriginal() {
            return capacity > 0;
        }
    }

    private final int vertexCount;
    private final List<Edge>[] adjacency;
    private final List<Edge> edges = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public FlowNetwork(int vertexCount) {
        Preconditions.requirePositive(vertexCount, "vertexCount");
        this.vertexCount = vertexCount;
        this.adjacency = new ArrayList[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            adjacency[v] = new ArrayList<>();
        }
    }

    public int vertexCount() {
        return vertexCount;
    }

    /** Every original (caller-inserted) arc, in insertion order. */
    public List<Edge> edges() {
        return List.copyOf(edges);
    }

    /** Adjacency list of {@code v}; each entry knows its reverse twin. */
    public List<Edge> adjacent(int v) {
        return adjacency[v];
    }

    /**
     * Adds arc {@code from -> to} with the given capacity plus a zero-capacity
     * reverse arc used for residual pushes.
     */
    public Edge addEdge(int from, int to, int capacity) {
        checkVertex(from);
        checkVertex(to);
        Preconditions.requireNonNegative(capacity, "capacity");
        Edge forward = new Edge(from, to, adjacency[to].size(), capacity);
        Edge backward = new Edge(to, from, adjacency[from].size(), 0);
        adjacency[from].add(forward);
        adjacency[to].add(backward);
        edges.add(forward);
        return forward;
    }

    /** Pushes {@code amount} along arc {@code edge}, updating both directions. */
    void push(Edge edge, int amount) {
        Preconditions.checkState(amount >= 0 && amount <= edge.residual,
                "push " + amount + " exceeds residual " + edge.residual);
        edge.residual -= amount;
        adjacency[edge.to].get(edge.rev).residual += amount;
    }

    /**
     * Residual BFS used by Edmonds-Karp: shortest augmenting path in hops.
     *
     * @param parentVertex filled with the predecessor of each reached vertex
     * @param parentEdge   filled with the adjacency index used to reach it
     * @return true when {@code sink} was reached
     */
    boolean breadthFirstPath(int source, int sink, int[] parentVertex, int[] parentEdge) {
        java.util.Arrays.fill(parentVertex, -1);
        parentVertex[source] = source;
        int[] queue = new int[vertexCount];
        int head = 0;
        int tail = 0;
        queue[tail++] = source;
        while (head < tail) {
            int v = queue[head++];
            List<Edge> arcs = adjacency[v];
            for (int i = 0; i < arcs.size(); i++) {
                Edge arc = arcs.get(i);
                if (arc.residual > 0 && parentVertex[arc.to] < 0) {
                    parentVertex[arc.to] = v;
                    parentEdge[arc.to] = i;
                    if (arc.to == sink) {
                        return true;
                    }
                    queue[tail++] = arc.to;
                }
            }
        }
        return false;
    }

    /** Depth-first augmenting path used by the plain Ford-Fulkerson method. */
    boolean depthFirstPath(int v, int sink, boolean[] seen, int[] parentVertex, int[] parentEdge) {
        if (v == sink) {
            return true;
        }
        seen[v] = true;
        List<Edge> arcs = adjacency[v];
        for (int i = 0; i < arcs.size(); i++) {
            Edge arc = arcs.get(i);
            if (arc.residual > 0 && !seen[arc.to]) {
                parentVertex[arc.to] = v;
                parentEdge[arc.to] = i;
                if (depthFirstPath(arc.to, sink, seen, parentVertex, parentEdge)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Vertices still reachable from {@code source} in the residual graph.
     * By max-flow/min-cut this set's outgoing arcs are exactly the min-cut.
     */
    public boolean[] sourceSide(int source) {
        checkVertex(source);
        boolean[] reached = new boolean[vertexCount];
        int[] stack = new int[vertexCount];
        int top = 0;
        stack[top++] = source;
        reached[source] = true;
        while (top > 0) {
            int v = stack[--top];
            for (Edge arc : adjacency[v]) {
                if (arc.residual > 0 && !reached[arc.to]) {
                    reached[arc.to] = true;
                    stack[top++] = arc.to;
                }
            }
        }
        return reached;
    }

    private void checkVertex(int v) {
        Preconditions.requireInRange(v, 0, vertexCount - 1, "vertex");
    }
}
