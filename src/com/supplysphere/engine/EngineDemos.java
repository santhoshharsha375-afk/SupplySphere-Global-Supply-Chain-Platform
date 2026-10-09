package com.supplysphere.engine;

import com.supplysphere.algorithm.flow.Dinic;
import com.supplysphere.algorithm.flow.FlowNetwork;
import com.supplysphere.algorithm.flow.FlowResult;
import com.supplysphere.algorithm.np.SchedulingApprox;
import com.supplysphere.algorithm.randomised.MillerRabin;

import java.util.Locale;

/**
 * The engine's "beyond retrieval" demonstrations of the other syllabus
 * families — Module 1 / CO1, wired to Modules 4, 5 and 6.
 *
 * <p>The engine is the platform's single front door, so the rarer algorithms
 * run here too:</p>
 * <ul>
 *   <li><b>primality</b> — Miller-Rabin on a typed number (Module 6).</li>
 *   <li><b>scheduling</b> — parallel-machine makespan for a shipment task
 *       list; list vs LPT vs the lower bound (Module 5).</li>
 *   <li><b>citation flow</b> — a max-flow/min-cut over a small citation and
 *       throughput network among warehouse modules (Module 4).</li>
 * </ul>
 */
public final class EngineDemos {

    private static final String[] VERTEX_NAMES = {"SOURCE", "D1", "D2", "D3", "D4", "TARGET"};

    private EngineDemos() {
    }

    public record SchedulingStats(long listMakespan, long lptMakespan, long lowerBound) {
    }

    public record CitationFlowStats(int maxFlow, int minCutCapacity, int saturatedOriginalArcs,
                                    String bottleneckArcs) {
    }

    /** Deterministic Miller-Rabin verdict for one number. */
    public static String primalityReport(long n) {
        boolean prime = MillerRabin.isPrime(n);
        return String.format(Locale.ROOT,
                "primality | n = %d -> %s | Miller-Rabin, deterministic bases 2,3,5,7,11,13,17 for n < 341,550,071,728,321%n",
                n, prime ? "PRIME" : "COMPOSITE");
    }

    public static SchedulingStats schedulingStats(int[] processingTimes, int machines) {
        SchedulingApprox.Schedule list = SchedulingApprox.listSchedule(machines, processingTimes);
        SchedulingApprox.Schedule lpt = SchedulingApprox.lptSchedule(machines, processingTimes);
        return new SchedulingStats(list.makespan(), lpt.makespan(), list.lowerBound());
    }

    /** Makespan comparison for a shipment-preparation task list. */
    public static String schedulingReport(int[] processingTimes, int machines) {
        SchedulingStats stats = schedulingStats(processingTimes, machines);
        StringBuilder out = new StringBuilder();
        out.append("scheduling | P||Cmax makespan for ").append(processingTimes.length)
                .append(" tasks on ").append(machines).append(" machines\n");
        out.append("  list scheduling makespan: ").append(stats.listMakespan()).append('\n');
        out.append("  LPT makespan:             ").append(stats.lptMakespan()).append('\n');
        out.append("  lower bound (OPT >=):     ").append(stats.lowerBound()).append('\n');
        out.append("  LPT within ").append(ratio(stats.lptMakespan(), stats.lowerBound()))
                .append("x of the bound\n");
        return out.toString();
    }

    /** Max flow, min cut and bottleneck arcs of the fixed citation network. */
    public static CitationFlowStats citationFlowStats() {
        FlowNetwork network = citationNetwork();
        FlowResult result = Dinic.run(network, 0, 5);
        int saturated = 0;
        for (FlowNetwork.Edge edge : result.network().edges()) {
            if (edge.isOriginal() && edge.flow() == edge.capacity() && edge.flow() > 0) {
                saturated++;
            }
        }
        StringBuilder arcs = new StringBuilder();
        for (FlowNetwork.Edge edge : result.minCutEdges()) {
            if (arcs.length() > 0) {
                arcs.append(", ");
            }
            arcs.append(VERTEX_NAMES[edge.from()]).append("->").append(VERTEX_NAMES[edge.to()]);
        }
        return new CitationFlowStats(result.maxFlow(), result.minCutCapacity(),
                saturated, arcs.toString());
    }

    public static String citationFlowReport() {
        CitationFlowStats stats = citationFlowStats();
        StringBuilder out = new StringBuilder();
        out.append("citation-flow | max flow over the warehouse citation network\n");
        out.append("  max flow: ").append(stats.maxFlow()).append(" units\n");
        out.append("  min cut capacity: ").append(stats.minCutCapacity()).append(" units\n");
        out.append("  bottleneck arcs: ").append(stats.bottleneckArcs()).append('\n');
        out.append("  saturated original arcs: ").append(stats.saturatedOriginalArcs()).append('\n');
        return out.toString();
    }

    /**
     * Fixed demonstration: SOURCE feeds D1/D2, which cite forward into D3/D4,
     * which both discharge into TARGET. Capacities are the demonstration's
     * "transferable insight" units, so the numbers are stable and replayable.
     */
    private static FlowNetwork citationNetwork() {
        FlowNetwork network = new FlowNetwork(6);
        network.addEdge(0, 1, 4);
        network.addEdge(0, 2, 5);
        network.addEdge(1, 3, 3);
        network.addEdge(1, 4, 2);
        network.addEdge(2, 3, 2);
        network.addEdge(2, 4, 4);
        network.addEdge(3, 5, 3);
        network.addEdge(4, 5, 4);
        return network;
    }

    private static String ratio(long makespan, long bound) {
        return String.format(Locale.ROOT, "%.2f", (double) makespan / bound);
    }
}