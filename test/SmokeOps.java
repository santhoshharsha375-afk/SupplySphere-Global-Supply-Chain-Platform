import com.supplysphere.core.config.AppConfig;
import com.supplysphere.core.util.DomainException;
import com.supplysphere.dataset.Article;
import com.supplysphere.ops.OperationsService;
import com.supplysphere.service.SearchService;
import com.supplysphere.ui.SearchConsole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Smoke tests for the platform operations: supply-network flow, delivery
 * scheduling, batch-code validation and duplicate detection.
 */
public class SmokeOps {

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
        OperationsService ops = OperationsService.load(resolve("data/supply"));
        testNetwork(ops);
        testSchedule(ops);
        testBatch(ops);
        testDuplicates(ops);
        testConsole(ops);
        if (failures == 0) {
            System.out.println("ALL OPS SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
        }
    }

    static void testNetwork(OperationsService ops) {
        OperationsService.FlowAnalysis flow = ops.analyzeNetwork();
        ok("flow positive", flow.maxFlow() > 0);
        ok("cut equals flow", flow.maxFlow() == flow.minCutCapacity());
        ok("certified", flow.certified());
        ok("has cut links", !flow.cut().isEmpty());
        eq("link flows cover all links", flow.links(), flow.linkFlows().size());

        for (OperationsService.FlowAnalysis.LinkFlow link : flow.linkFlows()) {
            ok("flow within capacity " + link.from() + "->" + link.to(),
                    link.flow() >= 0 && link.flow() <= link.capacity());
        }
        for (OperationsService.FlowAnalysis.CutLink link : flow.cut()) {
            ok("cut link has capacity " + link.from() + "->" + link.to(), link.capacity() > 0);
        }
    }

    static void testSchedule(OperationsService ops) {
        OperationsService.ScheduleAnalysis schedule = ops.schedule(3);
        eq("machines", 3, schedule.machines());
        eq("job count", ops.jobs().size(), schedule.jobCount());
        ok("lpt <= list", schedule.lptMakespan() <= schedule.listMakespan());
        ok("lower bound <= lpt", schedule.lowerBound() <= schedule.lptMakespan());
        if (schedule.optimalMakespan() > 0) {
            ok("optimal <= lpt", schedule.optimalMakespan() <= schedule.lptMakespan());
            ok("optimal >= lower bound", schedule.optimalMakespan() >= schedule.lowerBound());
        }

        boolean[] seen = new boolean[schedule.jobCount()];
        long total = 0;
        long maxLoad = 0;
        for (OperationsService.ScheduleAnalysis.Machine machine : schedule.assignment()) {
            long load = 0;
            for (OperationsService.ScheduleAnalysis.Task task : machine.tasks()) {
                if (task.index() < 0 || task.index() >= seen.length || seen[task.index()]) {
                    ok("task index unique", false);
                    return;
                }
                seen[task.index()] = true;
                load += task.minutes();
                total += task.minutes();
            }
            eq("machine load matches tasks " + machine.index(), load, machine.load());
            maxLoad = Math.max(maxLoad, load);
        }
        for (boolean present : seen) {
            if (!present) {
                ok("every task scheduled once", false);
                return;
            }
        }
        ok("every task scheduled once", true);
        eq("makespan is busiest machine", maxLoad, schedule.lptMakespan());
        eq("total minutes preserved", 129, total);
    }

    static void testBatch(OperationsService ops) {
        ok("prime code accepted", ops.validateBatch("SS-1000000007-IN").prime());
        ok("composite code rejected", !ops.validateBatch("BATCH-1000000008").prime());
        ok("small prime", ops.validateBatch("97").prime());
        ok("probable agrees with prime", ops.validateBatch("1000000009").prime()
                == ops.validateBatch("1000000009").probable());
        eq("longest digit run", 1000000007L, ops.validateBatch("AB12-CD1000000007-EF3").number());
        boolean threw = false;
        try {
            ops.validateBatch("no-digits-here");
        } catch (DomainException expected) {
            threw = true;
        }
        ok("no digits is an error", threw);
    }

    static void testDuplicates(OperationsService ops) {
        SearchService service = SearchService.open(AppConfig.productionDefaults(), resolve("data/corpus"));
        List<Article> articles = service.repository().all();
        List<OperationsService.DuplicatePair> pairs = ops.duplicates(articles, 0.0);
        eq("all pairs considered", (long) articles.size() * (articles.size() - 1) / 2, pairs.size());
        boolean sorted = true;
        for (int i = 1; i < pairs.size(); i++) {
            if (pairs.get(i - 1).similarity() < pairs.get(i).similarity()) {
                sorted = false;
            }
        }
        ok("pairs sorted descending", sorted);
        boolean inRange = true;
        for (OperationsService.DuplicatePair pair : pairs) {
            if (pair.similarity() < 0.0 || pair.similarity() > 1.0) {
                inRange = false;
            }
        }
        ok("similarity within 0..1", inRange);
    }

    static void testConsole(OperationsService ops) {
        SearchService service = SearchService.open(AppConfig.productionDefaults(), resolve("data/corpus"));
        com.supplysphere.ui.SearchConsole console =
                new com.supplysphere.ui.SearchConsole(service, ops);
        ok("console network lists throughput", console.execute("network").contains("max throughput"));
        ok("console schedule lists LPT", console.execute("schedule 3").contains("LPT makespan"));
        ok("console validate reports PRIME", console.execute("validate SS-1000000007-IN").contains("PRIME"));
        ok("console duplicates ranks pairs", console.execute("duplicates").contains("duplicate scan"));
        ok("console validate without digits errors",
                console.execute("validate abc").contains("error"));
    }

    static Path resolve(String relative) {
        for (String candidate : new String[]{relative, "SupplySphere/" + relative}) {
            if (Files.isDirectory(Path.of(candidate))) {
                return Path.of(candidate);
            }
        }
        return Path.of(relative);
    }
}
