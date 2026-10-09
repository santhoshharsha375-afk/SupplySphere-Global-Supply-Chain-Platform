import com.supplysphere.core.config.AppConfig;
import com.supplysphere.core.util.DomainException;
import com.supplysphere.dataset.Article;
import com.supplysphere.dataset.CorpusLoader;
import com.supplysphere.engine.EngineDemos;
import com.supplysphere.engine.ModuleReport;
import com.supplysphere.engine.QueryReport;
import com.supplysphere.engine.SearchHit;
import com.supplysphere.engine.SearchMode;
import com.supplysphere.service.SearchService;
import com.supplysphere.ui.SearchConsole;

import java.nio.file.Path;

public class SmokeEngine {

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

    static void throwsCode(String name, String code, Runnable work) {
        try {
            work.run();
            failures++;
            System.out.println("FAIL " + name + ": no exception");
        } catch (DomainException failure) {
            if (code.equals(failure.getCode())) {
                System.out.println("PASS " + name);
            } else {
                failures++;
                System.out.println("FAIL " + name + ": expected code " + code + " got " + failure.getCode());
            }
        }
    }

    public static void main(String[] args) {
        Path corpus = resolveCorpus(args);
        AppConfig config = AppConfig.productionDefaults();
        SearchService service = SearchService.open(config, corpus);
        SearchConsole console = new SearchConsole(service);

        testCorpus(service);
        testExact(service);
        testMultiKeyword(service);
        testFuzzy(service);
        testSimilar(service);
        testScan(console);
        testReport(console);
        testEngineDemos(console);
        testErrors(service);
        testConsole(console, service);

        if (failures == 0) {
            System.out.println("ALL ENGINE SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
        }
    }

    /** Explicit argument, else data/corpus, else SupplySphere/data/corpus. */
    static Path resolveCorpus(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        for (String candidate : new String[]{"data/corpus", "SupplySphere/data/corpus"}) {
            if (java.nio.file.Files.isDirectory(Path.of(candidate))) {
                return Path.of(candidate);
            }
        }
        return Path.of("data", "corpus");
    }

    static void testCorpus(SearchService service) {
        eq("corpus article count", 5, service.repository().count());
        eq("wiki_03 title", 1, service.repository().byId("wiki_03").title().equals("Shipment tracking") ? 1 : 0);
        ok("character total positive", service.repository().totalCharacters() > 1000);
        ok("word total positive", service.repository().totalWords() > 200);
        ok("preview non-blank", !service.repository().byId("wiki_01").preview(20).isBlank());
        throwsCode("missing corpus directory", "NOT_FOUND",
                () -> CorpusLoader.load(Path.of("no", "such", "dir")));
        throwsCode("unknown article id", "NOT_FOUND",
                () -> service.repository().byId("wiki_99"));

        Article blankId = null;
        try {
            blankId = new Article("  ", "title", "content");
        } catch (DomainException expected) {
            // expected
        }
        ok("blank article id rejected", blankId == null);
    }

    static void testExact(SearchService service) {
        QueryReport report = service.search("supplier evaluation", SearchMode.EXACT);
        eq("exact hit count", 1, report.hitCount());
        eq("exact scanned all articles", 5, report.articlesScanned());
        eq("exact mode", 1, report.mode() == SearchMode.EXACT ? 1 : 0);
        eq("exact occurrences", 4, (long) report.hits().get(0).score());
        eq("exact top article", 1, report.hits().get(0).article().id().equals("wiki_04") ? 1 : 0);
        ok("exact elapsed >= 0", report.elapsedNanos() >= 0);
        ok("exact render has mode explanation", report.render().contains("exact phrase"));
        ok("exact render has snippet", report.render().contains("scored on"));

        QueryReport supply = service.search("supply", SearchMode.EXACT);
        eq("exact supply hits", 5, supply.hitCount());
        eq("exact supply top", 1, supply.hits().get(0).article().id().equals("wiki_01") ? 1 : 0);
        eq("exact supply top score", 5, (long) supply.hits().get(0).score());
        ok("exact scores descending", descending(supply));

        QueryReport miss = service.search("xyzzyplugh", SearchMode.EXACT);
        eq("exact miss hits", 0, miss.hitCount());
        ok("exact miss renders empty state", miss.render().contains("No matches"));
    }

    static void testMultiKeyword(SearchService service) {
        QueryReport both = service.search("shipment tracking", SearchMode.MULTI_KEYWORD);
        eq("keyword hit count", 1, both.hitCount());
        ok("keyword score full", both.hits().get(0).score() == 1.0);
        eq("keyword top article", 1, both.hits().get(0).article().id().equals("wiki_03") ? 1 : 0);
        eq("keyword matched terms", 2, both.hits().get(0).matchedTerms().size());

        QueryReport partial = service.search("procurement supplier", SearchMode.MULTI_KEYWORD);
        eq("keyword partial hits", 2, partial.hitCount());
        eq("keyword partial top", 1, partial.hits().get(0).article().id().equals("wiki_01") ? 1 : 0);
        ok("keyword partial scores descending", descending(partial));
        ok("keyword partial renders terms", partial.render().contains("matched 2 keywords"));
    }

    static void testFuzzy(SearchService service) {
        QueryReport typo = service.search("shipmnt", SearchMode.FUZZY);
        ok("fuzzy has small hit set", typo.hitCount() >= 1 && typo.hitCount() <= 2);
        eq("fuzzy top article", 1, typo.hits().get(0).article().id().equals("wiki_03") ? 1 : 0);
        ok("fuzzy confidence high", typo.hits().get(0).score() > 0.8);
        eq("fuzzy matched term", 1, typo.hits().get(0).matchedTerms().size());

        QueryReport wordTypo = service.search("supplir evaluation", SearchMode.FUZZY);
        ok("fuzzy phrase hit", wordTypo.hitCount() >= 1);
        eq("fuzzy phrase top", 1, wordTypo.hits().get(0).article().id().equals("wiki_04") ? 1 : 0);

        QueryReport miss = service.search("zzzzzzzz", SearchMode.FUZZY);
        eq("fuzzy miss hits", 0, miss.hitCount());
    }

    static void testSimilar(SearchService service) {
        QueryReport report = service.search("inventory management", SearchMode.SIMILAR);
        ok("similar has hits", report.hitCount() >= 1);
        eq("similar top article", 1, report.hits().get(0).article().id().equals("wiki_05") ? 1 : 0);
        ok("similar score in (0,1]", report.hits().get(0).score() > 0.0
                && report.hits().get(0).score() <= 1.0);
        ok("similar scores descending", descending(report));

        QueryReport single = service.search("supplier", SearchMode.SIMILAR);
        ok("similar single word hit", single.hitCount() >= 1);
        eq("similar single word top", 1, single.hits().get(0).article().id().equals("wiki_04") ? 1 : 0);
    }

    static void testScan(SearchConsole console) {
        ok("console scan agree", console.execute("scan shipment tracking").contains("both algorithms agree"));
        ok("console scan shows kmp", console.execute("scan supplier").contains("Kmp"));
        ok("console scan shows double hash", console.execute("scan supplier").contains("double-hash"));
        ok("console scan usage", console.execute("scan").contains("usage: scan"));
    }

    static void testReport(SearchConsole console) {
        eq("report module count", 6, ModuleReport.all().size());
        String report = console.execute("report");
        ok("console report title", report.contains("Algorithm report"));
        for (int module = 1; module <= 6; module++) {
            ok("console report has module " + module, report.contains("Module " + module + " / CO"));
        }
        ok("console report main", report.contains("KMP (Knuth-Morris-Pratt)"));
        ok("console report benchmark", report.contains("Parallel prefix (scan)"));
        ok("console report rejected", report.contains("Suffix tree"));
        ok("console report alias", console.execute("algorithms").contains("Module 6"));
    }

    static void testEngineDemos(SearchConsole console) {
        EngineDemos.CitationFlowStats flow = EngineDemos.citationFlowStats();
        eq("engine citation max flow", 7, flow.maxFlow());
        eq("engine citation min cut", 7, flow.minCutCapacity());
        ok("engine citation bottleneck named", flow.bottleneckArcs().contains("->"));

        EngineDemos.SchedulingStats schedule =
                EngineDemos.schedulingStats(new int[]{3, 5, 2, 7, 4, 6, 1, 3, 9}, 3);
        ok("engine schedule lpt within bound",
                schedule.lptMakespan() >= schedule.lowerBound());
        ok("engine schedule lpt no worse than list",
                schedule.lptMakespan() <= schedule.listMakespan());

        ok("console engine primality prime", console.execute("engine primality 97").contains("PRIME"));
        ok("console engine primality composite",
                console.execute("engine primality 3215031751").contains("COMPOSITE"));
        ok("console engine schedule", console.execute("engine schedule").contains("LPT"));
        ok("console engine citation", console.execute("engine citation").contains("max flow"));
        ok("console engine usage", console.execute("engine").contains("usage: engine"));
        ok("console engine usage topic",
                console.execute("engine bogus").contains("usage: engine"));
        ok("console engine primality usage",
                console.execute("engine primality").contains("usage: engine primality"));
        ok("console engine bad number",
                console.execute("engine primality xyz").contains("BAD_ARGUMENT"));
    }

    static void testErrors(SearchService service) {
        throwsCode("blank query rejected", "BLANK_ARGUMENT",
                () -> service.search("   ", SearchMode.EXACT));
        throwsCode("missing corpus rejected", "NOT_FOUND",
                () -> SearchService.open(AppConfig.productionDefaults(), Path.of("no", "such", "dir")));
        try {
            Path empty = java.nio.file.Files.createTempDirectory("smokeengine");
            throwsCode("empty corpus rejected", "EMPTY_COLLECTION",
                    () -> SearchService.open(AppConfig.productionDefaults(), empty));
            java.nio.file.Files.delete(empty);
        } catch (java.io.IOException failure) {
            failures++;
            System.out.println("FAIL empty corpus setup: " + failure);
        }
    }

    static void testConsole(SearchConsole console, SearchService service) {
        ok("console empty line", "".equals(console.execute("")));
        ok("console null line", "".equals(console.execute(null)));
        ok("console quit", console.execute("quit") == null);
        ok("console exit", console.execute("exit  ") == null);
        ok("console isQuit", console.isQuit(" QUIT") && !console.isQuit("stats"));
        ok("console help", console.execute("help").contains("fuzzy"));
        ok("console stats", console.execute("stats").contains("5 document"));
        ok("console exact", console.execute("exact supplier evaluation").contains("Supplier evaluation"));
        ok("console bare query", console.execute("supplier evaluation").contains("exact phrase"));
        ok("console keywords", console.execute("kw shipment tracking").contains("Shipment tracking"));
        ok("console fuzzy", console.execute("fuzzy shipmnt").contains("tolerates typos"));
        ok("console similar", console.execute("similar inventory management").contains("topic overlap"));
        ok("console usage hint", console.execute("exact").contains("usage:"));
        ok("console error path", console.execute("fuzzy ...").startsWith("error [EMPTY_COLLECTION]"));

        QueryReport viaConsole = service.search("supply", SearchMode.EXACT);
        eq("console and service agree", 5, viaConsole.hitCount());
    }

    static boolean descending(QueryReport report) {
        java.util.List<SearchHit> hits = report.hits();
        for (int i = 1; i < hits.size(); i++) {
            if (hits.get(i).score() > hits.get(i - 1).score()) {
                return false;
            }
        }
        return true;
    }
}
