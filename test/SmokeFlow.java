import com.supplysphere.algorithm.flow.Dinic;
import com.supplysphere.algorithm.flow.FlowNetwork;
import com.supplysphere.algorithm.flow.FlowResult;
import com.supplysphere.algorithm.flow.MaxFlow;

import java.util.Random;

/**
 * Module 4 smoke tests: the shipped pair is Dinic (main) and the Ford-Fulkerson /
 * Edmonds-Karp family in {@link MaxFlow} (benchmark). Both must agree on the
 * same max-flow value and the min-cut certificate, on textbook and random graphs.
 */
public class SmokeFlow {

    static int failures = 0;

    static void eq(String name, long expected, long actual) {
        if (expected == actual) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name + ": expected " + expected + " got " + actual);
        }
    }

    static void ok(String name, boolean condition) {
        if (condition) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    public static void main(String[] args) {
        testClassicGraph();
        testRandomGraphs();
        if (failures == 0) {
            System.out.println("ALL FLOW SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
        }
    }

    /** CLRS textbook network: s=0, t=5, max flow = 23. */
    static void testClassicGraph() {
        FlowNetwork network = new FlowNetwork(6);
        network.addEdge(0, 1, 16);
        network.addEdge(0, 2, 13);
        network.addEdge(1, 3, 12);
        network.addEdge(1, 2, 10);
        network.addEdge(2, 1, 4);
        network.addEdge(2, 4, 14);
        network.addEdge(3, 2, 9);
        network.addEdge(3, 5, 20);
        network.addEdge(4, 3, 7);
        network.addEdge(4, 5, 4);

        FlowResult ff = MaxFlow.fordFulkerson(copy(network), 0, 5);
        FlowResult ek = MaxFlow.edmondsKarp(copy(network), 0, 5);
        FlowResult dinic = Dinic.run(copy(network), 0, 5);

        eq("fordFulkerson classic", 23, ff.maxFlow());
        eq("edmondsKarp classic", 23, ek.maxFlow());
        eq("dinic classic", 23, dinic.maxFlow());

        eq("ff minCut == maxFlow", ff.maxFlow(), ff.minCutCapacity());
        ok("ff certified", ff.isCertified());
        ok("ek certified", dinic.isCertified());
        eq("min cut edges count", 3, ff.minCutEdges().size());
        int cutSum = ff.minCutEdges().stream().mapToInt(FlowNetwork.Edge::capacity).sum();
        eq("min cut edge capacity sum", 23, cutSum);
        ok("sink off source side", !ff.sourceSide()[5]);
        ok("source on source side", ff.sourceSide()[0]);
        ok("flow conservation", conserves(copy(network), ff, 0, 5));
    }

    /** Dinic (main) vs Edmonds-Karp/Ford-Fulkerson (bench) on random graphs. */
    static void testRandomGraphs() {
        Random rng = new Random(13);
        for (int trial = 0; trial < 25; trial++) {
            int n = 4 + rng.nextInt(7);
            FlowNetwork network = new FlowNetwork(n);
            int extra = rng.nextInt(n * 3);
            for (int i = 0; i < extra; i++) {
                int u = rng.nextInt(n);
                int v = rng.nextInt(n);
                if (u != v) {
                    network.addEdge(u, v, 1 + rng.nextInt(20));
                }
            }
            FlowResult ek = MaxFlow.edmondsKarp(copy(network), 0, n - 1);
            FlowResult dinic = Dinic.run(copy(network), 0, n - 1);
            FlowResult ff = MaxFlow.fordFulkerson(copy(network), 0, n - 1);

            eq("random trial " + trial + " ek==dinic", ek.maxFlow(), dinic.maxFlow());
            eq("random trial " + trial + " ek==ff", ek.maxFlow(), ff.maxFlow());
            eq("random trial " + trial + " cut==flow", ek.maxFlow(), ek.minCutCapacity());
            ok("random trial " + trial + " certified", ek.isCertified());
            ok("random trial " + trial + " conservation", conserves(copy(network), ek, 0, n - 1));
            eq("random trial " + trial + " dinic cut", dinic.maxFlow(), dinic.minCutCapacity());
        }
    }

    static FlowNetwork copy(FlowNetwork network) {
        FlowNetwork clone = new FlowNetwork(network.vertexCount());
        for (FlowNetwork.Edge edge : network.edges()) {
            clone.addEdge(edge.from(), edge.to(), edge.capacity());
        }
        return clone;
    }

    static boolean conserves(FlowNetwork network, FlowResult result, int source, int sink) {
        int n = network.vertexCount();
        int[] balance = new int[n];
        for (FlowNetwork.Edge edge : result.network().edges()) {
            balance[edge.from()] -= edge.flow();
            balance[edge.to()] += edge.flow();
        }
        for (int v = 0; v < n; v++) {
            if (v != source && v != sink && balance[v] != 0) {
                return false;
            }
        }
        return balance[source] == -result.maxFlow() && balance[sink] == result.maxFlow();
    }
}
