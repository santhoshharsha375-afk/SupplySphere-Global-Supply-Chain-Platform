package com.supplysphere.engine;

import com.supplysphere.core.util.Preconditions;

import java.util.List;

/**
 * Per-module algorithm rationale — one record per course module.
 *
 * <p>Each module ships exactly one main algorithm and one benchmark that must
 * agree with it; every other algorithm considered for the module is recorded
 * here with the reason it was left out. The console {@code report} command and
 * the web Report view both render this single source of truth, so the engine
 * never has to explain itself twice.</p>
 */
public record ModuleReport(
        int module,
        String code,
        String title,
        String task,
        Choice main,
        Choice benchmark,
        List<Choice> rejected) {

    /** An algorithm together with the plain-language reason for its fate. */
    public record Choice(String name, String why) {
        public Choice {
            Preconditions.requireNonBlank(name, "name");
            Preconditions.requireNonBlank(why, "why");
        }
    }

    public ModuleReport {
        Preconditions.requireInRange(module, 1, 6, "module");
        Preconditions.requireNonBlank(code, "code");
        Preconditions.requireNonBlank(title, "title");
        Preconditions.requireNonBlank(task, "task");
        Preconditions.requireNonBlank(main.name(), "main");
        Preconditions.requireNonBlank(benchmark.name(), "benchmark");
        rejected = List.copyOf(rejected);
    }

    public int rejectedCount() {
        return rejected.size();
    }

    /** The six modules, in course order. */
    public static List<ModuleReport> all() {
        return MODULES;
    }

    private static final List<ModuleReport> MODULES = List.of(
            new ModuleReport(
                    1, "CO1", "Exact phrase retrieval",
                    "Find an exact phrase inside a document, as typed by the user.",
                    new Choice("KMP (Knuth-Morris-Pratt)",
                            "linear time with no backtracking, so a whole corpus scans in one pass; "
                                    + "its failure function also feeds the keyword and suffix stages."),
                    new Choice("Rabin-Karp (double rolling hash)",
                            "reaches the same matches by hashing windows instead of walking borders, "
                                    + "so the two use different machinery and agreeing proves the search."),
                    List.of(
                            new Choice("Z-function (prefix matching)",
                                    "solves the same exact match but is built for pattern preprocessing, "
                                            + "not for locating every occurrence across many documents."))),
            new ModuleReport(
                    2, "CO2", "Multi-pattern and suffix search",
                    "Find many keywords at once, and answer substring and similarity questions.",
                    new Choice("Aho-Corasick automaton",
                            "one pass over the text reports every keyword simultaneously via failure links; "
                                    + "it is the natural engine for the keywords mode."),
                    new Choice("Suffix array + Kasai LCP",
                            "answers repeated substring queries and powers shingle similarity; "
                                    + "pairing it with Aho-Corasick covers the structural questions the automaton cannot."),
                    List.of(
                            new Choice("Naive multi-pattern scan",
                                    "restarts the scan for every pattern, which is quadratic and only kept as a "
                                            + "test oracle, never as a shipped matcher."),
                            new Choice("Suffix tree",
                                    "same asymptotics as the suffix array with far heavier memory and construction, "
                                            + "so the lighter array is preferred."),
                            new Choice("Suffix automaton",
                                    "excellent for counting distinct substrings, but that is not a user-facing task here; "
                                            + "the suffix array already covers substring search."))),
            new ModuleReport(
                    3, "CO3", "Fuzzy and approximate matching",
                    "Match supply-chain terms even when the query contains typos or small edits.",
                    new Choice("Edit distance (bounded Levenshtein)",
                            "the textbook way to score typo tolerance, with band pruning that keeps the fuzzy mode "
                                    + "linear on short queries."),
                    new Choice("Sequence alignment (Needleman-Wunsch / Smith-Waterman)",
                            "the affine-gap relative of edit distance; it validates the scoring model from a second "
                                    + "angle and handles local matches the edit distance declines."),
                    List.of(
                            new Choice("Interval DP (matrix-chain / OBST)",
                                    "solves optimal parenthesisation, an ordering problem unrelated to text matching."),
                            new Choice("Tree DP",
                                    "targets tree-shaped optimisation, which the supply-chain corpus does not expose."),
                            new Choice("Subset-sum (SOS) DP",
                                    "answers set-valued queries, not the approximate string matching this module needs."),
                            new Choice("Bitmask DP",
                                    "handles small combinatorial state spaces such as TSP, unrelated to retrieval."))),
            new ModuleReport(
                    4, "CO4", "Flow and cut on the supply network",
                    "Model capacity and bottlenecks between suppliers, hubs and customers.",
                    new Choice("Dinic's algorithm",
                            "blocking-flow level graph gives the best worst case on the dense logistics graphs, "
                                    + "and returns the min-cut certificate the console reports."),
                    new Choice("Ford-Fulkerson / Edmonds-Karp",
                            "augmenting-path family; its BFS variant is a slower but independent method that must "
                                    + "produce the same max-flow value."),
                    List.of(
                            new Choice("Min-cost max-flow",
                                    "adds cost optimisation on top of flow, a richer objective than the throughput "
                                            + "analysis this module asks for."),
                            new Choice("Bipartite matching",
                                    "is a special case of max flow; rather than ship a narrow routine it is covered "
                                            + "by the general flow solver."))),
            new ModuleReport(
                    5, "CO5", "Scheduling and NP-hard approximation",
                    "Assign jobs to machines and pick covers where optimal answers are intractable.",
                    new Choice("Scheduling approximation (LPT)",
                            "longest-processing-time-first guarantees a 4/3-1/(3m) makespan and is the workload-planning task."),
                    new Choice("Vertex-cover 2-approximation",
                            "an independent approximation with a tight 2x guarantee; it exercises the same "
                                    + "approximation reasoning on a second, unrelated problem."),
                    List.of(
                            new Choice("Knapsack FPTAS",
                                    "a value-scaling scheme for a single knapsack, not the machine-scheduling problem "
                                            + "we present."),
                            new Choice("NP reductions (SAT / 3SAT / set cover)",
                                    "proves hardness rather than producing a usable answer, so it is theory support, "
                                            + "not a shipped solver."))),
            new ModuleReport(
                    6, "CO6", "Randomised and parallel algorithms",
                    "Use randomness and multiple cores honestly, with deterministic verification.",
                    new Choice("Miller-Rabin primality",
                            "a randomised test that is deterministic for all 64-bit integers with fixed bases, "
                                    + "used to validate part codes and key sizes."),
                    new Choice("Parallel prefix (scan)",
                            "a work-efficient parallel primitive whose output is checked against a serial scan, "
                                    + "showing real speed-up without sacrificing correctness."),
                    List.of(
                            new Choice("Universal hashing",
                                    "supports randomised dictionaries, but the corpus index already relies on the "
                                            + "suffix array, so a second hash family adds nothing."),
                            new Choice("Randomised quicksort",
                                    "a general sort, off-topic for a retrieval engine whose ordering is by relevance."),
                            new Choice("Reservoir sampling",
                                    "stream sampling has no task in the current document workflow."),
                            new Choice("Parallel merge sort",
                                    "duplicates the work of the prefix benchmark with the same parallel lesson."),
                            new Choice("Parallel reduction",
                                    "trivially cheap to test but adds little over the scan's verified parallel model."))));
}
