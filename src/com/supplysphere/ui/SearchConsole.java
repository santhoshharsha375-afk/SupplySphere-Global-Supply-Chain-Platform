package com.supplysphere.ui;

import com.supplysphere.core.util.DomainException;
import com.supplysphere.dataset.Article;
import com.supplysphere.engine.EngineDemos;
import com.supplysphere.engine.ModuleReport;
import com.supplysphere.engine.QueryReport;
import com.supplysphere.engine.SearchMode;
import com.supplysphere.ops.OperationsService;
import com.supplysphere.service.SearchService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Line-oriented console front end — Module 1 / CO1.
 *
 * <p>Command grammar (one command per line, case-insensitive):</p>
 * <pre>
 *   stats                       corpus statistics
 *   exact   &lt;query&gt;            literal phrase scan (KMP)
 *   keywords &lt;terms...&gt;        multi-term scan (Aho-Corasick)
 *   fuzzy   &lt;query&gt;            typo-tolerant scan (bounded Levenshtein)
 *   similar &lt;query&gt;            topical ranking (shingle Jaccard)
 *   scan    &lt;query&gt;            main vs bench matcher must agree (KMP/Rabin-Karp)
 *   engine  primality &lt;n&gt;      Miller-Rabin primality check
 *   engine  schedule            parallel-machine makespan demo (LPT)
 *   engine  citation            max-flow / min-cut over a citation network
 *   network                     live supply-network throughput and bottleneck
 *   schedule [machines]         schedule real delivery tasks (LPT vs optimum)
 *   validate &lt;code&gt;            Miller-Rabin check on a batch / part code
 *   duplicates                  near-duplicate documents by shingle similarity
 *   report                      algorithm main/benchmark choice per module
 *   help                        this text
 *   quit | exit                 leave
 * </pre>
 *
 * <p>A bare line with no verb is treated as {@code exact}, so the interactive
 * session reads like a search box rather than a command shell.</p>
 */
public final class SearchConsole {

    private final SearchService service;
    private final OperationsService operations;

    public SearchConsole(SearchService service) {
        this(service, OperationsService.loadIfPresent(operationsDirectory()));
    }

    public SearchConsole(SearchService service, OperationsService operations) {
        this.service = java.util.Objects.requireNonNull(service, "service");
        this.operations = operations;
    }

    private static Path operationsDirectory() {
        Path local = Path.of("data", "supply");
        if (Files.isDirectory(local)) {
            return local;
        }
        Path nested = Path.of("SupplySphere", "data", "supply");
        return Files.isDirectory(nested) ? nested : local;
    }

    /**
     * Executes one command line and returns everything it printed.
     * Returns {@code null} only for quit commands.
     */
    public String execute(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("\\s+", 2);
        String verb = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1] : "";
        try {
            return switch (verb) {
                case "quit", "exit" -> null;
                case "help", "?" -> help();
                case "stats", "corpus" -> service.corpusReport();
                case "exact", "kw", "keywords", "fuzzy", "similar" -> search(verb, rest);
                case "scan", "probe" -> scan(rest);
                case "engine", "algo" -> engineCommand(rest);
                case "network", "flow" -> networkReport();
                case "schedule", "plan" -> scheduleReport(rest);
                case "validate", "check" -> validateReport(rest);
                case "duplicates", "dupes" -> duplicatesReport();
                case "report", "algorithms" -> report();
                case "mode" -> "modes: exact | keywords | fuzzy | similar (default: exact)\n"
                        + "type help for what each one does";
                default -> search("exact", trimmed);
            };
        } catch (DomainException failure) {
            return "error [" + failure.getCode() + "] " + failure.getMessage() + "\n";
        }
    }

    public boolean isQuit(String line) {
        if (line == null) {
            return true;
        }
        String verb = line.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return verb.equals("quit") || verb.equals("exit");
    }

    private String scan(String query) {
        if (query.isBlank()) {
            return "usage: scan <query>   (compares the main matcher KMP with the Rabin-Karp benchmark)\n";
        }
        return service.scanReport(query);
    }

    private String engineCommand(String rest) {
        String[] parts = rest.trim().split("\\s+", 2);
        if (rest.isBlank()) {
            return "usage: engine <primality n | schedule | citation>\n";
        }
        return switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "primality", "prime" -> {
                if (parts.length < 2) {
                    yield "usage: engine primality <n>\n";
                }
                try {
                    yield EngineDemos.primalityReport(Long.parseLong(parts[1].trim()));
                } catch (NumberFormatException invalid) {
                    yield "error [BAD_ARGUMENT] " + parts[1] + " is not a whole number\n";
                }
            }
            case "schedule" -> EngineDemos.schedulingReport(
                    new int[]{3, 5, 2, 7, 4, 6, 1, 3, 9}, 3);
            case "citation", "flow" -> EngineDemos.citationFlowReport();
            default -> "usage: engine <primality n | schedule | citation>\n";
        };
    }

    private String report() {
        StringBuilder out = new StringBuilder();
        out.append("Algorithm report - one main algorithm plus one benchmark per module\n");
        out.append("(every other algorithm considered is listed with why it was left out)\n");
        for (ModuleReport module : ModuleReport.all()) {
            out.append('\n');
            out.append(String.format(Locale.ROOT, "Module %d / %s - %s%n",
                    module.module(), module.code(), module.title()));
            out.append("  task      : ").append(module.task()).append('\n');
            out.append("  main      : ").append(module.main().name()).append('\n');
            out.append("              ").append(module.main().why()).append('\n');
            out.append("  benchmark : ").append(module.benchmark().name()).append('\n');
            out.append("              ").append(module.benchmark().why()).append('\n');
            if (!module.rejected().isEmpty()) {
                out.append("  left out  :\n");
                for (ModuleReport.Choice choice : module.rejected()) {
                    out.append("      - ").append(choice.name()).append(": ")
                            .append(choice.why()).append('\n');
                }
            }
        }
        return out.toString();
    }

    private String networkReport() {
        if (operations == null) {
            return "supply data not found (expected data/supply/network.csv and jobs.csv)\n";
        }
        OperationsService.FlowAnalysis flow = operations.analyzeNetwork();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "supply network | %d nodes, %d links%n",
                flow.nodes(), flow.links()));
        out.append(String.format(Locale.ROOT, "  max throughput : %d units/day%n", flow.maxFlow()));
        out.append(String.format(Locale.ROOT, "  min cut        : %d units/day%s%n",
                flow.minCutCapacity(), flow.certified() ? "  (certified)" : ""));
        out.append("  bottleneck links:\n");
        for (OperationsService.FlowAnalysis.CutLink link : flow.cut()) {
            out.append(String.format(Locale.ROOT, "      %s -> %s  (%d/day)%n",
                    link.from(), link.to(), link.capacity()));
        }
        out.append("  routing:\n");
        for (OperationsService.FlowAnalysis.LinkFlow link : flow.linkFlows()) {
            out.append(String.format(Locale.ROOT, "      %-16s %4d / %-4d%s%n",
                    link.from() + " -> " + link.to(), link.flow(), link.capacity(),
                    link.saturated() ? "  saturated" : ""));
        }
        return out.toString();
    }

    private String scheduleReport(String rest) {
        if (operations == null) {
            return "supply data not found (expected data/supply/network.csv and jobs.csv)\n";
        }
        int machines = 3;
        if (!rest.isBlank()) {
            try {
                machines = Integer.parseInt(rest.trim());
            } catch (NumberFormatException invalid) {
                return "usage: schedule [machines]\n";
            }
        }
        if (machines < 1) {
            return "error [BAD_ARGUMENT] machines must be at least 1\n";
        }
        OperationsService.ScheduleAnalysis plan = operations.schedule(machines);
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "delivery scheduling | %d tasks on %d warehouses%n",
                plan.jobCount(), plan.machines()));
        out.append(String.format(Locale.ROOT, "  list makespan : %d%n", plan.listMakespan()));
        out.append(String.format(Locale.ROOT, "  LPT makespan  : %d%n", plan.lptMakespan()));
        out.append(plan.optimalMakespan() >= 0
                ? String.format(Locale.ROOT, "  optimal       : %d%n", plan.optimalMakespan())
                : "  optimal       : (not computed for this size)\n");
        out.append(String.format(Locale.ROOT, "  lower bound   : %d%n", plan.lowerBound()));
        out.append("  plan (LPT):\n");
        for (OperationsService.ScheduleAnalysis.Machine machine : plan.assignment()) {
            out.append(String.format(Locale.ROOT, "      warehouse %d  load %d%n",
                    machine.index() + 1, machine.load()));
            for (OperationsService.ScheduleAnalysis.Task task : machine.tasks()) {
                out.append(String.format(Locale.ROOT, "          - %-28s %d min%n",
                        task.label(), task.minutes()));
            }
        }
        return out.toString();
    }

    private String validateReport(String rest) {
        if (operations == null) {
            return "supply data not found (expected data/supply/network.csv and jobs.csv)\n";
        }
        if (rest.isBlank()) {
            return "usage: validate <code>   e.g. validate SS-1000000007-IN\n";
        }
        OperationsService.BatchResult result = operations.validateBatch(rest.trim());
        return String.format(Locale.ROOT,
                "batch code | %s%n  number : %d%n  verdict: %s (Miller-Rabin)%n",
                result.code(), result.number(), result.verdict());
    }

    private String duplicatesReport() {
        if (operations == null) {
            return "supply data not found (expected data/supply/network.csv and jobs.csv)\n";
        }
        List<Article> articles = service.repository().all();
        List<OperationsService.DuplicatePair> pairs = operations.duplicates(articles, 0.0);
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT,
                "duplicate scan | %d documents, word-overlap Jaccard%n", articles.size()));
        if (pairs.isEmpty()) {
            out.append("  no pairs to compare\n");
            return out.toString();
        }
        int rank = 1;
        for (OperationsService.DuplicatePair pair : pairs) {
            out.append(String.format(Locale.ROOT, "  %2d. %-4s %-22s %s%n",
                    rank++, pair.percent(), pair.leftId() + " ~ " + pair.rightId(),
                    pair.leftTitle() + " / " + pair.rightTitle()));
        }
        return out.toString();
    }

    private String search(String verb, String query) {
        if (query.isBlank()) {
            return "usage: " + verb + " <query>   (or type help)\n";
        }
        SearchMode mode = switch (verb) {
            case "kw", "keywords" -> SearchMode.MULTI_KEYWORD;
            case "fuzzy" -> SearchMode.FUZZY;
            case "similar" -> SearchMode.SIMILAR;
            default -> SearchMode.EXACT;
        };
        QueryReport report = service.search(query, mode);
        return report.render();
    }

    private String help() {
        return """
                stats                      how many documents are loaded, and what they are
                exact <query>              find an exact phrase   e.g. exact supplier evaluation
                keywords <terms...>        match several words    e.g. keywords shipment tracking
                fuzzy <query>              search despite typos   e.g. fuzzy shipmnt
                similar <query>            find a related topic   e.g. similar inventory management
                scan <query>               prove the two matchers agree (KMP vs Rabin-Karp)
                engine primality <n>       Miller-Rabin primality check
                engine schedule            parallel-machine makespan demo (list vs LPT)
                engine citation            max flow / min cut over a citation network
                network                    supply-network throughput and bottleneck links
                schedule [machines]        schedule delivery tasks (default 3 warehouses)
                validate <code>            Miller-Rabin check on a batch / part code
                duplicates                 near-duplicate documents by similarity
                report                     main/benchmark choice for each of the six modules
                help                       show this list
                quit | exit                close the console

                A line without a command (e.g. just "supplier evaluation") runs an exact search.
                """;
    }
}
