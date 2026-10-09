package com.supplysphere.ops;

import com.supplysphere.algorithm.flow.Dinic;
import com.supplysphere.algorithm.flow.FlowNetwork;
import com.supplysphere.algorithm.flow.FlowResult;
import com.supplysphere.algorithm.np.SchedulingApprox;
import com.supplysphere.algorithm.randomised.MillerRabin;
import com.supplysphere.algorithm.suffixstructures.SimilarityIndex;
import com.supplysphere.core.util.DomainException;
import com.supplysphere.core.util.Preconditions;
import com.supplysphere.dataset.Article;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The platform's non-retrieval operations — Modules 4, 5 and 6.
 *
 * <p>Where {@link com.supplysphere.service.SearchService} answers "what does the
 * corpus say", this service answers "will the chain cope, how should the work be
 * scheduled, and is this batch code sound". It runs the shipped flow, scheduling,
 * primality and similarity algorithms over the {@code data/supply} scenario, so
 * every module of the project is visible as a real supply-chain task rather than
 * a console demo.</p>
 */
public final class OperationsService {

    private static final int OPTIMAL_JOB_LIMIT = 30;
    private static final int OPTIMAL_MACHINE_LIMIT = 8;

    /** Word shingle size used by {@link #duplicates(List, double)}. */
    public static final int DUPLICATE_SHINGLE_SIZE = 1;

    private final SupplyNetwork network;
    private final JobSet jobs;

    public OperationsService(SupplyNetwork network, JobSet jobs) {
        this.network = Preconditions.requireNonNull(network, "network");
        this.jobs = Preconditions.requireNonNull(jobs, "jobs");
    }

    /** Loads {@code network.csv} and {@code jobs.csv} from {@code directory}. */
    public static OperationsService load(Path directory) {
        Preconditions.requireNonNull(directory, "directory");
        return new OperationsService(
                SupplyNetworkLoader.load(directory.resolve("network.csv")),
                JobSet.load(directory.resolve("jobs.csv")));
    }

    /** Loads the scenario when present, otherwise returns {@code null}. */
    public static OperationsService loadIfPresent(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        if (!Files.isRegularFile(directory.resolve("network.csv"))
                || !Files.isRegularFile(directory.resolve("jobs.csv"))) {
            return null;
        }
        return load(directory);
    }

    public SupplyNetwork network() {
        return network;
    }

    public JobSet jobs() {
        return jobs;
    }

    // ------------------------------------------------------------- Module 4

    /** Max flow, min cut and bottleneck links over the supply network (Dinic). */
    public FlowAnalysis analyzeNetwork() {
        FlowNetwork flow = new FlowNetwork(network.nodeCount());
        for (SupplyNetwork.Link link : network.links()) {
            flow.addEdge(network.indexOf(link.from()), network.indexOf(link.to()), link.capacity());
        }
        FlowResult result = Dinic.run(flow, network.indexOf(network.source()), network.indexOf(network.sink()));

        List<FlowAnalysis.LinkFlow> linkFlows = new ArrayList<>();
        for (FlowNetwork.Edge edge : result.network().edges()) {
            linkFlows.add(new FlowAnalysis.LinkFlow(
                    network.nodes().get(edge.from()),
                    network.nodes().get(edge.to()),
                    edge.capacity(),
                    edge.flow(),
                    edge.flow() == edge.capacity()));
        }
        List<FlowAnalysis.CutLink> cut = new ArrayList<>();
        for (FlowNetwork.Edge edge : result.minCutEdges()) {
            cut.add(new FlowAnalysis.CutLink(
                    network.nodes().get(edge.from()),
                    network.nodes().get(edge.to()),
                    edge.capacity()));
        }
        return new FlowAnalysis(result.maxFlow(), result.minCutCapacity(), result.isCertified(),
                network.nodeCount(), network.linkCount(), linkFlows, cut);
    }

    /** Max throughput, min-cut certificate and the per-link routing. */
    public record FlowAnalysis(int maxFlow, int minCutCapacity, boolean certified,
            int nodes, int links, List<LinkFlow> linkFlows, List<CutLink> cut) {

        /** How many units are routed over one link and whether it is at capacity. */
        public record LinkFlow(String from, String to, int capacity, int flow, boolean saturated) {
        }

        /** One link that straddles the minimum cut, i.e. a bottleneck. */
        public record CutLink(String from, String to, int capacity) {
        }
    }

    // ------------------------------------------------------------- Module 5

    /** LPT schedule, list-scheduling comparison and the exact optimum when small. */
    public ScheduleAnalysis schedule(int machines) {
        Preconditions.requirePositive(machines, "machines");
        int[] times = jobs.processingTimes();
        SchedulingApprox.Schedule lpt = SchedulingApprox.lptSchedule(machines, times);
        SchedulingApprox.Schedule list = SchedulingApprox.listSchedule(machines, times);
        long optimal = times.length <= OPTIMAL_JOB_LIMIT && machines <= OPTIMAL_MACHINE_LIMIT
                ? SchedulingApprox.optimalSchedule(machines, times).makespan()
                : -1L;

        List<ScheduleAnalysis.Machine> assignment = new ArrayList<>();
        int[][] layout = lpt.machines();
        for (int m = 0; m < layout.length; m++) {
            long load = 0;
            List<ScheduleAnalysis.Task> tasks = new ArrayList<>();
            for (int job : layout[m]) {
                load += times[job];
                tasks.add(new ScheduleAnalysis.Task(job, jobs.labelOf(job), times[job]));
            }
            assignment.add(new ScheduleAnalysis.Machine(m, load, tasks));
        }
        return new ScheduleAnalysis(machines, times.length, list.makespan(), lpt.makespan(),
                optimal, lpt.lowerBound(), assignment);
    }

    /** The recommended (LPT) assignment plus list/optimal comparison. */
    public record ScheduleAnalysis(int machines, int jobCount, long listMakespan, long lptMakespan,
            long optimalMakespan, long lowerBound, List<Machine> assignment) {

        public record Machine(int index, long load, List<Task> tasks) {
        }

        public record Task(int index, String label, int minutes) {
        }
    }

    // ------------------------------------------------------------- Module 6

    /**
     * Primality check of the numeric part of a batch / SKU code using Miller-Rabin.
     * The longest run of digits is taken so codes such as {@code SS-1000000007-IN}
     * are handled.
     */
    public BatchResult validateBatch(String code) {
        Preconditions.requireNonBlank(code, "code");
        String digits = longestDigitRun(code);
        if (digits.isEmpty()) {
            throw new DomainException("BAD_CODE", "code '" + code + "' contains no digits to validate");
        }
        long number;
        try {
            number = Long.parseLong(digits);
        } catch (NumberFormatException tooLarge) {
            throw new DomainException("BAD_CODE",
                    "numeric part '" + digits + "' is larger than a 64-bit integer");
        }
        boolean prime = MillerRabin.isPrime(number);
        boolean probable = MillerRabin.isProbablyPrime(number, 24, new Random(number + 1));
        String verdict = prime ? "PRIME" : "COMPOSITE";
        return new BatchResult(code.trim(), digits, number, prime, probable, verdict);
    }

    private static String longestDigitRun(String value) {
        String best = "";
        int i = 0;
        while (i < value.length()) {
            if (!Character.isDigit(value.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < value.length() && Character.isDigit(value.charAt(i))) {
                i++;
            }
            if (i - start > best.length()) {
                best = value.substring(start, i);
            }
        }
        return best;
    }

    /** Outcome of validating one batch / part code. */
    public record BatchResult(String code, String digits, long number,
            boolean prime, boolean probable, String verdict) {
    }

    // ------------------------------------------------------------- Module 2

    /**
     * Near-duplicate document pairs, ranked by shingle Jaccard similarity.
     * Powers the duplicate-detection screen using the suffix/shingle machinery.
     */
    public List<DuplicatePair> duplicates(List<Article> articles, double threshold) {
        return duplicates(articles, DUPLICATE_SHINGLE_SIZE, threshold);
    }

    public List<DuplicatePair> duplicates(List<Article> articles, int shingleSize, double threshold) {
        Preconditions.requireNonNull(articles, "articles");
        Preconditions.requirePositive(shingleSize, "shingleSize");
        Preconditions.requireInRange((int) Math.round(threshold * 100), 0, 100, "threshold");
        List<DuplicatePair> pairs = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            for (int j = i + 1; j < articles.size(); j++) {
                double similarity = similarity(articles.get(i), articles.get(j), shingleSize);
                if (similarity >= threshold) {
                    pairs.add(new DuplicatePair(
                            articles.get(i).id(), articles.get(i).title(),
                            articles.get(j).id(), articles.get(j).title(),
                            similarity));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(DuplicatePair::similarity).reversed());
        return List.copyOf(pairs);
    }

    private static double similarity(Article left, Article right, int shingleSize) {
        String a = left.title() + " " + left.content();
        String b = right.title() + " " + right.content();
        return SimilarityIndex.jaccard(a, b, shingleSize);
    }

    /** Two documents and how similar they are. */
    public record DuplicatePair(String leftId, String leftTitle,
            String rightId, String rightTitle, double similarity) {

        public String percent() {
            return String.format(Locale.ROOT, "%.0f%%", similarity * 100);
        }
    }
}
