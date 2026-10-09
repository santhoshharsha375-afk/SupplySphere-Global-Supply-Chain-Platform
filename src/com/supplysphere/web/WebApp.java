package com.supplysphere.web;

import com.supplysphere.core.util.DomainException;
import com.supplysphere.dataset.Article;
import com.supplysphere.engine.ModuleReport;
import com.supplysphere.engine.QueryReport;
import com.supplysphere.engine.SearchHit;
import com.supplysphere.engine.SearchMode;
import com.supplysphere.ops.OperationsService;
import com.supplysphere.service.SearchService;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional platform web console — Modules 1, 2, 4, 5 and 6.
 *
 * <p>Sits in its own package and only consumes the public service APIs, so it
 * can be added or dropped without touching the engine, service or console.
 * Serves a single self-contained platform page at {@code /} plus JSON
 * endpoints:</p>
 *
 * <pre>
 *   /api/search?q=&lt;query&gt;&amp;mode=exact|keywords|fuzzy|similar
 *   /api/stats
 *   /api/report
 *   /api/network
 *   /api/schedule?machines=&lt;n&gt;
 *   /api/validate?code=&lt;code&gt;
 *   /api/duplicates
 * </pre>
 *
 * <p>Runs on the JDK's built-in {@link HttpServer} — no web framework, no
 * external libraries. Start it with:</p>
 *
 * <pre>
 *   java -cp bin com.supplysphere.web.WebApp [corpusDirectory] [port]
 * </pre>
 */
public final class WebApp {

    private final SearchService service;
    private final OperationsService operations;

    public WebApp(SearchService service) {
        this(service, OperationsService.loadIfPresent(resolveSupply()));
    }

    public WebApp(SearchService service, OperationsService operations) {
        this.service = service;
        this.operations = operations;
    }

    private static Path resolveSupply() {
        for (String candidate : new String[]{"data/supply", "SupplySphere/data/supply"}) {
            if (Files.isDirectory(Path.of(candidate))) {
                return Path.of(candidate);
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        Path corpus = args.length > 0 ? Path.of(args[0]) : Path.of("data/corpus");
        if (!Files.isDirectory(corpus)) {
            System.err.println("corpus directory not found: " + corpus.toAbsolutePath());
            System.err.println("usage: java -cp bin com.supplysphere.web.WebApp [corpusDirectory] [port]");
            return;
        }
        SearchService service = SearchService.open(
                com.supplysphere.core.config.AppConfig.productionDefaults(), corpus);
        WebApp app = new WebApp(service);
        if (app.operations == null) {
            System.err.println("note: supply data not found — network/scheduling/batch screens disabled");
        }
        app.dispatch(requestedPort(args));
    }

    private static int requestedPort(String[] args) {
        if (args.length > 1) {
            return Integer.parseInt(args[1]);
        }
        String env = System.getenv("PORT");
        if (env == null || env.isBlank()) {
            return 8080;
        }
        return Integer.parseInt(env);
    }

    /** Starts the server, retrying the next few ports when one is taken. */
    public void dispatch(int port) throws IOException {
        for (int candidate = port; candidate < port + 8; candidate++) {
            try {
                start(candidate);
                return;
            } catch (IOException busy) {
                System.err.println("port " + candidate + " unavailable, trying " + (candidate + 1));
            }
        }
        throw new IOException("no free port near " + port);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService pool = Executors.newCachedThreadPool();
        server.setExecutor(pool);
        server.createContext("/", exchange -> respond(exchange, "text/html; charset=utf-8", PAGE));
        server.createContext("/api/search", this::handleSearch);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/network", this::handleNetwork);
        server.createContext("/api/schedule", this::handleSchedule);
        server.createContext("/api/validate", this::handleValidate);
        server.createContext("/api/duplicates", this::handleDuplicates);
        server.start();
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            pool.shutdown();
            latch.countDown();
        }, "webapp-shutdown"));
        System.out.println("SupplySphereWeb on http://localhost:" + port + "  (Ctrl+C to stop)");
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("{\"articles\":").append(service.repository().count())
                .append(",\"characters\":").append(service.repository().totalCharacters())
                .append(",\"words\":").append(service.repository().totalWords())
                .append(",\"entries\":[");
        boolean first = true;
        for (Article article : service.repository().all()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"title\":").append(json(article.title()))
                    .append(",\"characters\":").append(article.characterCount())
                    .append(",\"words\":").append(article.wordCount())
                    .append(",\"preview\":").append(json(article.preview(70)))
                    .append('}');
        }
        out.append("]}");
        respond(exchange, "application/json; charset=utf-8", out.toString());
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("{\"modules\":[");
        boolean firstModule = true;
        for (ModuleReport module : ModuleReport.all()) {
            if (!firstModule) {
                out.append(',');
            }
            firstModule = false;
            out.append("{\"module\":").append(module.module())
                    .append(",\"code\":").append(json(module.code()))
                    .append(",\"title\":").append(json(module.title()))
                    .append(",\"task\":").append(json(module.task()))
                    .append(",\"main\":").append(jsonChoice(module.main()))
                    .append(",\"benchmark\":").append(jsonChoice(module.benchmark()))
                    .append(",\"rejected\":[");
            boolean firstRejected = true;
            for (ModuleReport.Choice choice : module.rejected()) {
                if (!firstRejected) {
                    out.append(',');
                }
                firstRejected = false;
                out.append(jsonChoice(choice));
            }
            out.append("]}");
        }
        out.append("]}");
        respond(exchange, "application/json; charset=utf-8", out.toString());
    }

    private String jsonChoice(ModuleReport.Choice choice) {
        return "{\"name\":" + json(choice.name()) + ",\"why\":" + json(choice.why()) + "}";
    }

    private void handleNetwork(HttpExchange exchange) throws IOException {
        if (operations == null) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Supply data not found (expected data/supply).\"}");
            return;
        }
        OperationsService.FlowAnalysis flow = operations.analyzeNetwork();
        StringBuilder out = new StringBuilder();
        out.append("{\"maxFlow\":").append(flow.maxFlow())
                .append(",\"minCut\":").append(flow.minCutCapacity())
                .append(",\"certified\":").append(flow.certified())
                .append(",\"nodes\":").append(flow.nodes())
                .append(",\"links\":").append(flow.links())
                .append(",\"cut\":[");
        boolean firstCut = true;
        for (OperationsService.FlowAnalysis.CutLink link : flow.cut()) {
            if (!firstCut) {
                out.append(',');
            }
            firstCut = false;
            out.append("{\"from\":").append(json(link.from()))
                    .append(",\"to\":").append(json(link.to()))
                    .append(",\"capacity\":").append(link.capacity()).append('}');
        }
        out.append("],\"links\":[");
        boolean firstLink = true;
        for (OperationsService.FlowAnalysis.LinkFlow link : flow.linkFlows()) {
            if (!firstLink) {
                out.append(',');
            }
            firstLink = false;
            out.append("{\"from\":").append(json(link.from()))
                    .append(",\"to\":").append(json(link.to()))
                    .append(",\"capacity\":").append(link.capacity())
                    .append(",\"flow\":").append(link.flow())
                    .append(",\"saturated\":").append(link.saturated()).append('}');
        }
        out.append("]}");
        respond(exchange, "application/json; charset=utf-8", out.toString());
    }

    private void handleSchedule(HttpExchange exchange) throws IOException {
        if (operations == null) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Supply data not found (expected data/supply).\"}");
            return;
        }
        QueryParams params = parse(exchange);
        int machines = 3;
        if (!params.value("machines").isBlank()) {
            try {
                machines = Integer.parseInt(params.value("machines").trim());
            } catch (NumberFormatException invalid) {
                respond(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"machines must be a whole number\"}");
                return;
            }
        }
        if (machines < 1 || machines > 16) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"machines must be between 1 and 16\"}");
            return;
        }
        OperationsService.ScheduleAnalysis plan = operations.schedule(machines);
        StringBuilder out = new StringBuilder();
        out.append("{\"machines\":").append(plan.machines())
                .append(",\"jobs\":").append(plan.jobCount())
                .append(",\"list\":").append(plan.listMakespan())
                .append(",\"lpt\":").append(plan.lptMakespan())
                .append(",\"optimal\":").append(plan.optimalMakespan())
                .append(",\"lowerBound\":").append(plan.lowerBound())
                .append(",\"assignment\":[");
        boolean firstMachine = true;
        for (OperationsService.ScheduleAnalysis.Machine machine : plan.assignment()) {
            if (!firstMachine) {
                out.append(',');
            }
            firstMachine = false;
            out.append("{\"index\":").append(machine.index())
                    .append(",\"load\":").append(machine.load())
                    .append(",\"tasks\":[");
            boolean firstTask = true;
            for (OperationsService.ScheduleAnalysis.Task task : machine.tasks()) {
                if (!firstTask) {
                    out.append(',');
                }
                firstTask = false;
                out.append("{\"label\":").append(json(task.label()))
                        .append(",\"minutes\":").append(task.minutes()).append('}');
            }
            out.append("]}");
        }
        out.append("]}");
        respond(exchange, "application/json; charset=utf-8", out.toString());
    }

    private void handleValidate(HttpExchange exchange) throws IOException {
        if (operations == null) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Supply data not found (expected data/supply).\"}");
            return;
        }
        QueryParams params = parse(exchange);
        String code = params.value("code");
        if (code.isBlank()) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Type a batch or part code to validate.\"}");
            return;
        }
        try {
            OperationsService.BatchResult result = operations.validateBatch(code);
            respond(exchange, "application/json; charset=utf-8",
                    "{\"code\":" + json(result.code())
                            + ",\"digits\":" + json(result.digits())
                            + ",\"number\":" + result.number()
                            + ",\"prime\":" + result.prime()
                            + ",\"probable\":" + result.probable()
                            + ",\"verdict\":" + json(result.verdict()) + "}");
        } catch (DomainException failure) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":" + json(failure.getMessage()) + "}");
        }
    }

    private void handleDuplicates(HttpExchange exchange) throws IOException {
        if (operations == null) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Supply data not found (expected data/supply).\"}");
            return;
        }
        List<Article> articles = service.repository().all();
        List<OperationsService.DuplicatePair> pairs = operations.duplicates(articles, 0.0);
        StringBuilder out = new StringBuilder();
        out.append("{\"documents\":").append(articles.size())
                .append(",\"shingleSize\":").append(OperationsService.DUPLICATE_SHINGLE_SIZE)
                .append(",\"pairs\":[");
        boolean first = true;
        for (OperationsService.DuplicatePair pair : pairs) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"leftId\":").append(json(pair.leftId()))
                    .append(",\"leftTitle\":").append(json(pair.leftTitle()))
                    .append(",\"rightId\":").append(json(pair.rightId()))
                    .append(",\"rightTitle\":").append(json(pair.rightTitle()))
                    .append(",\"similarity\":").append(String.format(Locale.ROOT, "%.4f", pair.similarity()))
                    .append('}');
        }
        out.append("]}");
        respond(exchange, "application/json; charset=utf-8", out.toString());
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        QueryParams params = parse(exchange);
        if (params.missing("q") || params.value("q").isBlank()) {
            respond(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Query is empty — type something to search.\"}");
            return;
        }
        String modeName = params.value("mode").toLowerCase(Locale.ROOT);
        SearchMode mode = switch (modeName) {
            case "", "exact", "phrase" -> SearchMode.EXACT;
            case "key", "keywords", "kw" -> SearchMode.MULTI_KEYWORD;
            case "fuzzy", "typo" -> SearchMode.FUZZY;
            case "similar", "rel", "topic" -> SearchMode.SIMILAR;
            default -> null;
        };
        if (mode == null) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Unknown mode: " + json(modeName) + ". Use exact, keywords, fuzzy or similar.\"}");
            return;
        }
        String query = params.value("q").trim();
        QueryReport report = service.search(query, mode);
        respond(exchange, "application/json; charset=utf-8", jsonReport(report, modeName));
    }

    private String jsonReport(QueryReport report, String modeName) {
        StringBuilder out = new StringBuilder();
        out.append("{\"query\":").append(json(report.query()))
                .append(",\"mode\":").append(json(modeName))
                .append(",\"modeDescription\":").append(json(report.mode().description()))
                .append(",\"found\":").append(report.hitCount())
                .append(",\"scanned\":").append(report.articlesScanned())
                .append(",\"takenMs\":").append(String.format(Locale.ROOT, "%.2f", report.elapsedMillis()))
                .append(",\"hits\":[");
        boolean first = true;
        int rank = 1;
        for (SearchHit hit : report.hits()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"rank\":").append(rank++)
                    .append(",\"title\":").append(json(hit.article().title()))
                    .append(",\"why\":").append(json(why(report.mode(), report.query(), hit)))
                    .append(",\"snippet\":").append(json(hit.snippet()))
                    .append(",\"score\":").append(String.format(Locale.ROOT, "%.3f", hit.score()))
                    .append(",\"terms\":[");
            boolean termFirst = true;
            for (String term : hit.matchedTerms()) {
                if (!termFirst) {
                    out.append(',');
                }
                termFirst = false;
                out.append(json(term));
            }
            out.append("]}");
        }
        out.append("]}");
        return out.toString();
    }

    private String why(SearchMode mode, String query, SearchHit hit) {
        return switch (mode) {
            case EXACT -> "the phrase appears " + (int) hit.score() + " time(s)";
            case MULTI_KEYWORD -> "matched " + hit.matchedTerms().size() + " keyword(s)";
            case FUZZY -> Math.round(hit.score() * 100) + "% match after typo correction";
            case SIMILAR -> "topic overlap " + Math.round(hit.score() * 100) + "%";
        };
    }

    private void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        respond(exchange, 200, contentType, body);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private QueryParams parse(HttpExchange exchange) {
        QueryParams params = new QueryParams();
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = decode(pair.substring(0, separator));
            String value = decode(pair.substring(separator + 1));
            if (key.isEmpty()) {
                continue;
            }
            params.values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** {@code "key" -> ["value", ...]} in request order; first value wins for reads. */
    private static final class QueryParams {
        private final java.util.Map<String, List<String>> values = new java.util.HashMap<>();

        boolean missing(String key) {
            return !values.containsKey(key);
        }

        String value(String key) {
            List<String> list = values.get(key);
            return list == null || list.isEmpty() ? "" : list.get(0);
        }
    }

    /** JSON string escaping (quotes, backslash, control characters). */
    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    // ---------------------------------------------------------------------
    // Embedded platform console: a single self-contained page, no external
    // resources. Reaches the engine only through the /api/* endpoints.
    // ---------------------------------------------------------------------
    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>SupplySphere — Global Supply Chain Management Platform</title>
            <style>
              :root{
                --bg:#f6f7f9; --panel:#ffffff; --panel2:#fbfcfe;
                --text:#1a1d24; --muted:#6b7280; --line:#e6e8ee;
                --accent:#2563eb; --accent-soft:#eef3ff; --accent-line:#c9d8ff;
                --green:#15803d; --green-soft:#e9f7ee;
                --amber:#b45309; --amber-soft:#fdf3e6;
                --red:#b91c1c;
                --shadow:0 1px 2px rgba(16,24,40,.04),0 8px 24px rgba(16,24,40,.05);
              }
              *{box-sizing:border-box}
              html{-webkit-text-size-adjust:100%}
              body{
                margin:0;min-height:100vh;color:var(--text);background:var(--bg);
                font-family:ui-sans-serif,system-ui,-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
                font-size:15px;line-height:1.5;
              }
              .app{display:grid;grid-template-columns:236px minmax(0,1fr);min-height:100vh}
              .side{
                position:sticky;top:0;height:100vh;display:flex;flex-direction:column;gap:22px;
                padding:20px 14px;background:var(--panel);border-right:1px solid var(--line);
              }
              .brand{display:flex;align-items:center;gap:11px;padding:4px 6px}
              .logo{width:38px;height:38px;border-radius:10px;flex:none;display:grid;place-items:center;color:#fff;background:var(--accent)}
              .brand h1{margin:0;font-size:1.02rem;font-weight:750;letter-spacing:-.01em;line-height:1.2}
              .brand p{margin:2px 0 0;color:var(--muted);font-size:.68rem;line-height:1.35}
              nav{display:flex;flex-direction:column;gap:2px}
              .nav{
                display:flex;align-items:center;gap:10px;padding:9px 11px;border:0;border-radius:9px;cursor:pointer;
                background:transparent;color:#4b5563;font-size:.86rem;font-weight:600;text-align:left;
                transition:background .15s,color .15s;
              }
              .nav:hover{background:#f2f4f7;color:var(--text)}
              .nav.active{color:var(--accent);background:var(--accent-soft)}
              .nav svg{flex:none;width:17px;height:17px}
              .side-foot{margin-top:auto}
              .side-foot .status{display:flex;align-items:center;gap:8px;font-size:.74rem;color:var(--muted)}
              .dot{width:7px;height:7px;border-radius:50%;background:#22c55e;flex:none}
              .side-foot .tech{margin-top:8px;color:#9aa3b2;font-size:.7rem;line-height:1.6}

              .main{min-width:0;padding:30px clamp(20px,4vw,52px) 72px}
              .wrap{max-width:1080px;margin:0 auto}
              .topbar{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:26px;flex-wrap:wrap}
              .topbar h1{margin:0;font-size:1.45rem;font-weight:750;letter-spacing:-.02em}
              .topbar .sub{color:var(--muted);font-size:.82rem;margin-top:4px}
              .pill{display:flex;align-items:center;gap:8px;padding:7px 13px;border:1px solid var(--line);border-radius:999px;background:var(--panel);color:var(--muted);font-size:.76rem;font-weight:600}
              .pill b{color:var(--text)}

              .view{display:none}
              .view.active{display:block;animation:fade .22s ease}
              @keyframes fade{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:none}}

              .card{background:var(--panel);border:1px solid var(--line);border-radius:14px;box-shadow:var(--shadow)}
              .pad{padding:20px 22px}
              .grid{display:grid;gap:14px}
              .kpis{grid-template-columns:repeat(auto-fit,minmax(150px,1fr));margin-bottom:8px}
              .kpi{padding:16px 18px}
              .kpi .label{color:var(--muted);font-size:.7rem;font-weight:700;letter-spacing:.1em;text-transform:uppercase}
              .kpi .value{margin-top:6px;font-size:1.8rem;font-weight:750;font-variant-numeric:tabular-nums;color:var(--text)}
              .kpi .value.small{font-size:1.3rem}
              .kpi .foot{color:var(--muted);font-size:.72rem;margin-top:2px}

              h2.section{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:.95rem;font-weight:700;margin:26px 0 12px;color:var(--text)}
              h2.section:first-child{margin-top:0}

              .tiles{grid-template-columns:repeat(auto-fill,minmax(240px,1fr))}
              .tile{padding:16px 18px;display:flex;flex-direction:column;gap:8px}
              .tile .tag{font-size:.68rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--accent)}
              .tile .t-name{font-weight:700;font-size:.95rem}
              .tile .t-task{color:var(--muted);font-size:.8rem;flex:1}
              .tile .t-algo{font-size:.74rem;color:#4b5563;border-top:1px solid var(--line);padding-top:9px}
              .tile .t-algo b{color:var(--text);font-weight:650}
              details{margin-top:4px;border-top:1px solid var(--line);padding-top:9px}
              summary{cursor:pointer;color:var(--muted);font-size:.76rem;font-weight:600}
              .rejected{margin:9px 0 0;padding-left:18px;display:flex;flex-direction:column;gap:5px}
              .rej{color:#4b5563;font-size:.78rem;line-height:1.5}
              .rej b{color:var(--text)}

              .btn{
                border:0;border-radius:9px;cursor:pointer;font-weight:650;font-size:.84rem;padding:10px 18px;
                background:var(--accent);color:#fff;transition:filter .15s,transform .1s;
              }
              .btn:hover{filter:brightness(1.07)}
              .btn:active{transform:scale(.985)}
              .btn:disabled{opacity:.55;cursor:wait}
              .btn.ghost{background:var(--panel);color:var(--accent);border:1px solid var(--accent-line);padding:7px 14px}
              .btn.ghost:hover{background:var(--accent-soft)}
              .input{
                width:100%;padding:11px 14px;border:1px solid var(--line);border-radius:10px;font-size:.95rem;
                color:var(--text);background:var(--panel);outline:none;transition:border-color .15s,box-shadow .15s;
              }
              .input:focus{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}
              .searchbar{display:flex;gap:10px}
              .examples{display:flex;flex-wrap:wrap;gap:7px;margin-top:12px}
              .example{cursor:pointer;border:1px solid var(--line);background:var(--panel2);color:#4b5563;font-size:.76rem;font-weight:600;padding:6px 12px;border-radius:999px}
              .example:hover{border-color:var(--accent-line);color:var(--accent)}

              .segmented{display:inline-flex;flex-wrap:wrap;gap:4px;margin-top:14px;padding:4px;background:#eef0f4;border-radius:11px}
              .segmented button{border:0;border-radius:8px;cursor:pointer;background:transparent;color:var(--muted);font-size:.82rem;font-weight:600;padding:7px 14px}
              .segmented button.active{background:var(--panel);color:var(--accent);box-shadow:var(--shadow)}
              .hint{margin:11px 2px 0;color:var(--muted);font-size:.82rem;min-height:1.2em}

              .result{display:flex;gap:14px;padding:16px 18px;margin-top:12px}
              .result .r-rank{flex:none;width:34px;height:34px;border-radius:9px;display:grid;place-items:center;font-weight:700;font-size:.86rem;color:var(--accent);background:var(--accent-soft);font-variant-numeric:tabular-nums}
              .result h3{margin:0 0 6px;font-size:1rem;font-weight:700}
              .result .snippet{color:#4b5563;font-size:.88rem;line-height:1.6;margin:9px 0 0}
              .badge{display:inline-block;padding:3px 10px;border-radius:999px;font-size:.72rem;font-weight:650;color:#4b5563;background:#f2f4f7}

              .cols{display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(240px,1fr))}
              .machine{padding:16px 18px}
              .machine .m-top{display:flex;align-items:center;justify-content:space-between}
              .machine .m-name{font-weight:700;font-size:.92rem}
              .machine .m-load{color:var(--muted);font-size:.78rem;font-variant-numeric:tabular-nums}
              .tasks{display:flex;flex-direction:column;gap:6px;margin-top:12px}
              .task{display:flex;justify-content:space-between;gap:10px;font-size:.82rem;color:#374151;background:var(--panel2);border:1px solid var(--line);border-radius:8px;padding:7px 11px}
              .task .min{color:var(--muted);font-variant-numeric:tabular-nums;flex:none}

              table{width:100%;border-collapse:collapse}
              th{text-align:left;color:var(--muted);font-size:.68rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;padding:0 12px 9px}
              td{padding:10px 12px;border-top:1px solid var(--line);font-size:.86rem}
              td.num{text-align:right;font-variant-numeric:tabular-nums}
              .bar-mini{height:6px;background:#eef0f4;border-radius:999px;overflow:hidden;min-width:70px}
              .bar-mini>div{height:100%;background:var(--accent);border-radius:999px}
              .bar-mini>div.full{background:var(--amber)}

              .verdict{display:inline-flex;align-items:center;font-size:.98rem;font-weight:750;padding:9px 18px;border-radius:10px}
              .verdict.prime{color:var(--green);background:var(--green-soft)}
              .verdict.comp{color:var(--amber);background:var(--amber-soft)}

              .doc{display:flex;gap:14px;align-items:flex-start;padding:16px 18px;margin-bottom:10px}
              .doc .d-idx{flex:none;width:36px;height:36px;border-radius:9px;display:grid;place-items:center;font-weight:700;color:#4b5563;background:#f2f4f7;font-variant-numeric:tabular-nums}
              .doc h3{margin:0 0 4px;font-size:.96rem;font-weight:700}
              .doc p{margin:7px 0 0;color:#4b5563;font-size:.84rem}
              .doc .d-act{margin-left:auto;flex:none;align-self:center}
              .meta-line{color:var(--muted);font-size:.78rem}

              .empty,.error{text-align:center;padding:34px 18px;border:1px dashed var(--line);border-radius:14px;color:var(--muted);background:var(--panel)}
              .error{color:var(--red);border-color:#f3c5c0;background:#fdf3f2}

              @media (max-width:860px){
                .app{grid-template-columns:1fr}
                .side{position:static;height:auto;flex-direction:row;flex-wrap:wrap;align-items:center;gap:12px;border-right:0;border-bottom:1px solid var(--line)}
                nav{flex-direction:row;flex-wrap:wrap}
                .side-foot{display:none}
              }
              @media (max-width:600px){
                .main{padding:20px 14px 60px}
                .searchbar{flex-direction:column}
                .result,.doc{flex-direction:column}
                .doc .d-act{margin-left:0}
              }
            </style>
            </head>
            <body>
            <div class="app">
              <aside class="side">
                <div class="brand">
                  <div class="logo" aria-hidden="true">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M3 7h13l4 4v6h-2"/><circle cx="7" cy="17" r="2"/><circle cx="17" cy="17" r="2"/><path d="M3 7v10h2"/>
                    </svg>
                  </div>
                  <div>
                    <h1>SupplySphere</h1>
                    <p>Global Supply Chain Platform</p>
                  </div>
                </div>
                <nav aria-label="platform menu">
                  <button class="nav active" data-view="dashboard">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>
                    Overview
                  </button>
                  <button class="nav" data-view="search">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
                    Document search
                  </button>
                  <button class="nav" data-view="network">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="5" cy="12" r="2.5"/><circle cx="19" cy="6" r="2.5"/><circle cx="19" cy="18" r="2.5"/><path d="M7.3 11 16.6 7M7.3 13l9.3 4"/></svg>
                    Supply network
                  </button>
                  <button class="nav" data-view="schedule">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
                    Scheduling
                  </button>
                  <button class="nav" data-view="batch">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M4 12h16M4 17h10"/></svg>
                    Batch codes
                  </button>
                  <button class="nav" data-view="duplicates">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
                    Duplicates
                  </button>
                  <button class="nav" data-view="kb">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                    Knowledge base
                  </button>
                  <button class="nav" data-view="report">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
                    Algorithm report
                  </button>
                </nav>
                <div class="side-foot">
                  <div class="status"><span class="dot"></span>platform online</div>
                  <div class="tech">Search &middot; Logistics<br>Procurement &middot; Inventory</div>
                </div>
              </aside>

              <main class="main">
                <div class="wrap">
                  <header class="topbar">
                    <div>
                      <h1 id="viewTitle">Operations overview</h1>
                      <div class="sub" id="viewSub">Search, plan and validate across the supply chain</div>
                    </div>
                    <div class="pill"><span class="dot"></span>corpus <b id="pillCount">&hellip;</b></div>
                  </header>

                  <section id="view-dashboard" class="view active">
                    <div class="grid kpis" id="kpis"></div>
                    <h2 class="section">Recent documents <button class="btn ghost" data-goto="kb">Knowledge base</button></h2>
                    <div id="overviewDocs"></div>
                  </section>

                  <section id="view-search" class="view">
                    <div class="card pad">
                      <div class="searchbar">
                        <input id="query" class="input" type="text" placeholder="Search procurement, shipments, suppliers, stock…" autocomplete="off" spellcheck="false">
                        <button id="go" class="btn">Search</button>
                      </div>
                      <div class="segmented" role="group" aria-label="search mode">
                        <button class="active" data-mode="exact">Exact phrase</button>
                        <button data-mode="keywords">Keywords</button>
                        <button data-mode="fuzzy">Fuzzy</button>
                        <button data-mode="similar">Similar</button>
                      </div>
                      <div class="hint" id="modeHint"></div>
                      <div class="examples" id="quickChips"></div>
                    </div>
                    <div class="meta-line" id="resultMeta" style="margin-top:16px"></div>
                    <div id="results">
                      <div class="empty" style="margin-top:12px">Run a search and the matching documents will appear here.</div>
                    </div>
                  </section>

                  <section id="view-network" class="view">
                    <div class="grid kpis" id="netKpis"></div>
                    <div class="card pad" style="margin-top:16px">
                      <h2 class="section" style="margin-top:0">Bottleneck links</h2>
                      <div id="netCut"></div>
                    </div>
                    <div class="card pad" style="margin-top:16px">
                      <h2 class="section" style="margin-top:0">Link utilisation</h2>
                      <table>
                        <thead><tr><th>Route</th><th class="num">Flow</th><th class="num">Capacity</th><th>Utilisation</th></tr></thead>
                        <tbody id="netLinks"></tbody>
                      </table>
                    </div>
                  </section>

                  <section id="view-schedule" class="view">
                    <div class="card pad">
                      <label class="meta-line" for="machines">Warehouses</label>
                      <div class="searchbar" style="margin-top:8px">
                        <input id="machines" class="input" type="number" min="1" max="16" value="3" style="max-width:140px">
                        <button id="runSchedule" class="btn">Schedule</button>
                      </div>
                    </div>
                    <div class="grid kpis" id="scheduleKpis" style="margin-top:16px"></div>
                    <div class="cols" id="scheduleMachines" style="margin-top:16px"></div>
                  </section>

                  <section id="view-batch" class="view">
                    <div class="card pad">
                      <div class="searchbar">
                        <input id="codeInput" class="input" type="text" placeholder="e.g. SS-1000000007-IN" autocomplete="off" spellcheck="false">
                        <button id="runValidate" class="btn">Validate</button>
                      </div>
                      <div class="examples" id="codesChips"></div>
                    </div>
                    <div id="validateOut" style="margin-top:16px"></div>
                  </section>

                  <section id="view-duplicates" class="view">
                    <div class="card pad">
                      <h2 class="section" style="margin-top:0">Near-duplicate documents</h2>
                      <p class="meta-line" id="dupLead">Each pair is scored by how much wording the documents share.</p>
                      <div id="dupList" style="margin-top:14px"></div>
                    </div>
                  </section>

                  <section id="view-kb" class="view">
                    <h2 class="section">Loaded documents</h2>
                    <div id="kbList"></div>
                  </section>

                  <section id="view-report" class="view">
                    <p class="meta-line" id="reportLead">Each card names the main algorithm, the benchmark that must agree with it, and why every other candidate was left out.</p>
                    <div class="grid tiles" id="reportGrid" style="margin-top:14px"></div>
                  </section>
                </div>
              </main>
            </div>

            <script>
            const MODES = {
              exact:   {label:"Exact phrase", desc:"Your words must appear together, as typed."},
              keywords:{label:"Keywords",     desc:"Each word may appear anywhere in the article."},
              fuzzy:   {label:"Fuzzy",        desc:"Tolerates typos, like a spell checker."},
              similar: {label:"Similar",      desc:"Ranks articles by topic overlap, not exact wording."}
            };
            const VIEW_META = {
              dashboard:["Operations overview","Search, plan and validate across the supply chain"],
              search:["Document search","Find documents by phrase, keywords, typos or topic"],
              network:["Supply network","Maximum throughput and bottleneck links"],
              schedule:["Delivery scheduling","Balance delivery tasks across warehouses"],
              batch:["Batch codes","Validate incoming batch and part codes"],
              duplicates:["Duplicate detection","Surface documents that overlap the most"],
              kb:["Knowledge base","Every document indexed and available to search"],
              report:["Algorithm report","One main algorithm and one benchmark per module"]
            };
            const SUGGESTIONS = ["supplier evaluation","shipment tracking","shipmnt","inventory management","supply"];
            const CODES = ["SS-1000000007-IN","BATCH-1000000008","SKU-97","LOT-561"];
            let mode = "exact";
            let flags = {network:false, schedule:false, duplicates:false};

            const $ = id => document.getElementById(id);
            function esc(v){
              return String(v).replace(/[&<>"']/g, function(c){
                return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c];
              });
            }
            function dec(n){ return Number(n).toLocaleString(); }
            function kpi(label, value, foot, small){
              return '<div class="card kpi"><div class="label">' + esc(label) + '</div>' +
                '<div class="value' + (small ? " small" : "") + '">' + esc(value) + '</div>' +
                '<div class="foot">' + esc(foot) + '</div></div>';
            }

            function showView(name){
              document.querySelectorAll(".nav").forEach(b => b.classList.toggle("active", b.dataset.view === name));
              document.querySelectorAll(".view").forEach(v => v.classList.toggle("active", v.id === "view-" + name));
              $("viewTitle").textContent = VIEW_META[name][0];
              $("viewSub").textContent = VIEW_META[name][1];
              if (name === "network" && !flags.network) loadNetwork();
              if (name === "schedule" && !flags.schedule) loadSchedule();
              if (name === "duplicates" && !flags.duplicates) loadDuplicates();
            }
            document.querySelectorAll(".nav").forEach(b => b.addEventListener("click", () => showView(b.dataset.view)));
            document.querySelectorAll("[data-goto]").forEach(b => b.addEventListener("click", () => showView(b.dataset.goto)));

            document.querySelectorAll(".segmented button").forEach(b => b.addEventListener("click", () => {
              mode = b.dataset.mode;
              document.querySelectorAll(".segmented button").forEach(x => x.classList.toggle("active", x === b));
              $("modeHint").textContent = MODES[mode].desc;
              if ($("query").value.trim()) runSearch();
            }));
            $("modeHint").textContent = MODES.exact.desc;
            $("go").addEventListener("click", runSearch);
            $("query").addEventListener("keydown", e => { if (e.key === "Enter") runSearch(); });
            $("quickChips").innerHTML = SUGGESTIONS.map(s => '<button class="example">' + esc(s) + "</button>").join("");
            document.querySelectorAll("#quickChips .example").forEach(b => b.addEventListener("click", () => {
              $("query").value = b.textContent.trim(); showView("search"); runSearch();
            })
            );
            $("runSchedule").addEventListener("click", loadSchedule);
            $("machines").addEventListener("keydown", e => { if (e.key === "Enter") loadSchedule(); });
            $("runValidate").addEventListener("click", runValidate);
            $("codesChips").innerHTML = CODES.map(s => '<button class="example">' + esc(s) + "</button>").join("");
            document.querySelectorAll("#codesChips .example").forEach(b => b.addEventListener("click", () => {
              $("codeInput").value = b.textContent.trim(); runValidate();
            }));

            function docRow(index, e, action, q){
              return '<div class="card doc"><div class="d-idx">' + String(index).padStart(2, "0") + '</div>' +
                '<div><h3>' + esc(e.title) + '</h3>' +
                '<div class="meta-line">' + dec(e.characters) + ' chars &middot; ' + dec(e.words) + ' words</div>' +
                '<p>' + esc(e.preview) + '</p></div>' +
                '<button class="btn ghost d-act" data-q="' + esc(q) + '">' + action + '</button></div>';
            }

            async function loadStats(){
              const r = await fetch("/api/stats"); const d = await r.json();
              $("pillCount").textContent = d.articles;
              $("overviewDocs").innerHTML = d.entries.slice(0, 4).map((e, i) => docRow(i + 1, e, "Search", e.title)).join("");
              $("kbList").innerHTML = d.entries.map((e, i) => docRow(i + 1, e, "Similar", e.title)).join("");
              document.querySelectorAll("#overviewDocs .d-act").forEach(b => b.addEventListener("click", () => {
                $("query").value = b.dataset.q; showView("search"); runSearch();
              }));
              document.querySelectorAll("#kbList .d-act").forEach(b => b.addEventListener("click", () => {
                $("query").value = b.dataset.q; mode = "similar";
                document.querySelectorAll(".segmented button").forEach(x => x.classList.toggle("active", x.dataset.mode === "similar"));
                $("modeHint").textContent = MODES.similar.desc; showView("search"); runSearch();
              }));
              return d;
            }

            function moduleCard(m){
              const rejected = m.rejected.length
                ? '<details><summary>Not used (' + m.rejected.length + ')</summary><ul class="rej">' +
                  m.rejected.map(x => "<li><b>" + esc(x.name) + ":</b> " + esc(x.why) + "</li>").join("") + "</ul></details>"
                : '<div class="meta-line" style="margin-top:8px">No other algorithms considered.</div>';
              return '<div class="card tile"><div class="meta-line" style="font-weight:700;color:var(--accent)">Module ' + m.module + ' &middot; ' + esc(m.code) + '</div>' +
                '<div class="t-name">' + esc(m.title) + '</div>' +
                '<div class="t-task">' + esc(m.task) + '</div>' +
                '<div class="t-algo"><b>Main:</b> ' + esc(m.main.name) + '<br><b>Benchmark:</b> ' + esc(m.benchmark.name) + '</div>' +
                rejected + "</div>";
            }

            async function loadReport(){
              const r = await fetch("/api/report"); const d = await r.json();
              $("reportGrid").innerHTML = d.modules.map(moduleCard).join("");
              return d;
            }

            async function loadNetwork(){
              try{
                const d = await (await fetch("/api/network")).json();
                flags.network = true;
                $("netKpis").innerHTML =
                  kpi("Max throughput", dec(d.maxFlow), "units/day", false) +
                  kpi("Bottleneck capacity", dec(d.minCut), d.certified ? "verified" : "unverified", false) +
                  kpi("Links", dec(d.links), d.nodes + " nodes", false);
                $("netCut").innerHTML = d.cut.length
                  ? d.cut.map(c => '<div class="task"><span>' + esc(c.from) + " &rarr; " + esc(c.to) + '</span><span class="min">' + dec(c.capacity) + "/day</span></div>").join("")
                  : '<div class="empty">No bottleneck links found.</div>';
                $("netLinks").innerHTML = d.links.map(l => {
                  const pct = l.capacity ? Math.round(l.flow / l.capacity * 100) : 0;
                  return "<tr><td>" + esc(l.from) + " &rarr; " + esc(l.to) + '</td><td class="num">' + dec(l.flow) +
                    '</td><td class="num">' + dec(l.capacity) + '</td><td><div class="bar-mini"><div class="' +
                    (l.saturated ? "full" : "") + '" style="width:' + pct + '%"></div></div></td></tr>';
                }).join("");
              }catch(e){ $("netLinks").innerHTML = '<tr><td colspan="4"><div class="error">Could not load the supply network.</div></td></tr>'; }
            }

            async function loadSchedule(){
              const machines = Math.max(1, Math.min(16, parseInt($("machines").value || "3", 10) || 3));
              $("machines").value = machines;
              try{
                const r = await fetch("/api/schedule?machines=" + machines); const d = await r.json();
                if (!r.ok) throw new Error(d.error || "Scheduling failed");
                flags.schedule = true;
                const opt = d.optimal > 0 ? dec(d.optimal) + " min" : "n/a";
                const gap = d.optimal > 0 ? " · gap " + (d.lpt - d.optimal) + " min" : "";
                $("scheduleKpis").innerHTML =
                  kpi("Recommended plan", dec(d.lpt) + " min", "total horizon" + gap, false) +
                  kpi("Baseline plan", dec(d.list) + " min", "simpler assignment", false) +
                  kpi("Optimal plan", opt, "exact where feasible", true) +
                  kpi("Best possible", dec(d.lowerBound) + " min", "lower limit", true);
                $("scheduleMachines").innerHTML = d.assignment.map(m => {
                  const pct = d.lpt ? Math.round(m.load / d.lpt * 100) : 0;
                  return '<div class="card machine"><div class="m-top"><span class="m-name">Warehouse ' + (m.index + 1) +
                    '</span><span class="m-load">' + dec(m.load) + ' min</span></div>' +
                    '<div class="bar-mini" style="margin-top:10px"><div style="width:' + pct + '%"></div></div>' +
                    '<div class="tasks">' + m.tasks.map(t => '<div class="task"><span>' + esc(t.label) +
                    '</span><span class="min">' + t.minutes + ' min</span></div>').join("") + "</div></div>";
                }).join("");
              }catch(e){ $("scheduleKpis").innerHTML = '<div class="error">' + esc(e.message) + "</div>"; }
            }

            async function runValidate(){
              const code = $("codeInput").value.trim();
              if (!code){ $("validateOut").innerHTML = '<div class="error">Type a batch or part code to validate.</div>'; return; }
              try{
                const r = await fetch("/api/validate?code=" + encodeURIComponent(code)); const d = await r.json();
                if (!r.ok) throw new Error(d.error || "Validation failed");
                $("validateOut").innerHTML = '<div class="card pad"><div class="verdict ' + (d.prime ? "prime" : "comp") + '">' +
                  (d.prime ? "PRIME" : "COMPOSITE") + '</div>' +
                  '<div class="meta-line" style="margin-top:14px">Code <b>' + esc(d.code) + '</b> &middot; numeric part <b>' +
                  esc(d.digits) + '</b> = ' + dec(d.number) + '</div>' +
                  '<p style="color:#4b5563;margin:10px 0 0">The code check reports this number as ' +
                  (d.prime ? "prime &mdash; a valid batch identifier." : "composite &mdash; flag for review.") + '</p></div>';
              }catch(e){ $("validateOut").innerHTML = '<div class="error">' + esc(e.message) + "</div>"; }
            }

            async function loadDuplicates(){
              try{
                const d = await (await fetch("/api/duplicates")).json();
                flags.duplicates = true;
                $("dupLead").textContent = "Scored " + d.documents + " documents by how much wording they share. Higher means more overlap.";
                if (!d.pairs.length){ $("dupList").innerHTML = '<div class="empty">No pairs to compare.</div>'; return; }
                const max = Math.max.apply(null, d.pairs.map(p => p.similarity)) || 1;
                $("dupList").innerHTML = d.pairs.map(p => {
                  const pct = Math.round(p.similarity * 100);
                  const width = p.similarity > 0 ? Math.max(6, Math.round(p.similarity / max * 100)) : 0;
                  return '<div class="card result"><div style="flex:1;min-width:0"><h3>' +
                    esc(p.leftTitle) + " &harr; " + esc(p.rightTitle) + '</h3>' +
                    '<div class="meta-line">' + esc(p.leftId) + " &middot; " + esc(p.rightId) + '</div>' +
                    '<div class="bar-mini" style="margin-top:10px"><div style="width:' + width + '%"></div></div>' +
                    '<div class="meta-line" style="margin-top:6px">' + pct + '% overlap</div></div></div>';
                }).join("");
              }catch(e){ $("dupList").innerHTML = '<div class="error">Could not load duplicate analysis.</div>'; }
            }

            async function runSearch(){
              const q = $("query").value.trim();
              if (!q){ $("results").innerHTML = '<div class="empty">Type something to search.</div>'; $("resultMeta").textContent = ""; return; }
              const go = $("go"); go.disabled = true; $("resultMeta").textContent = "";
              $("results").innerHTML = '<div class="empty">Searching &hellip;</div>';
              try{
                const r = await fetch("/api/search?q=" + encodeURIComponent(q) + "&mode=" + encodeURIComponent(mode));
                const d = await r.json();
                if (!r.ok) throw new Error(d.error || "Search failed");
                if (d.found === 0){
                  $("resultMeta").innerHTML = "No matches out of <b>" + d.scanned + "</b> documents";
                  $("results").innerHTML = '<div class="empty">No matches. Try Keywords, Fuzzy or Similar mode.</div>';
                  return;
                }
                $("resultMeta").innerHTML = "Found <b>" + d.found + "</b> of " + d.scanned + " documents &middot; " + d.takenMs + " ms";
                $("results").innerHTML = d.hits.map(h => {
                  const pct = mode === "exact" ? Math.min(100, Math.round(h.score * 8)) : Math.min(100, Math.round(h.score * 100));
                  return '<div class="card result"><div class="r-rank">' + String(h.rank).padStart(2, "0") + '</div>' +
                    '<div style="flex:1;min-width:0"><h3>' + esc(h.title) + '</h3>' +
                    '<span class="badge">' + esc(h.why) + '</span>' +
                    '<p class="snippet">' + esc(h.snippet) + '</p>' +
                    '<div class="bar-mini" style="margin-top:12px"><div style="width:' + pct + '%"></div></div></div></div>';
                }).join("");
              }catch(e){ $("results").innerHTML = '<div class="error">' + esc(e.message) + "</div>"; }
              finally{ $("go").disabled = false; }
            }

            (async function boot(){
              try{
                const stats = await loadStats();
                await loadReport();
                let net = null, sched = null;
                try{ net = await (await fetch("/api/network")).json(); }catch(e){}
                try{ sched = await (await fetch("/api/schedule?machines=3")).json(); }catch(e){}
                let html = kpi("Documents", dec(stats.articles), "indexed and searchable", false);
                if (net) html += kpi("Max throughput", dec(net.maxFlow), "units/day across the chain", false);
                if (sched) html += kpi("Delivery tasks", dec(sched.jobs), "over " + sched.machines + " warehouses", false) +
                  kpi("Plan horizon", dec(sched.lpt) + " min", "recommended plan", false);
                else html += kpi("Words", dec(stats.words), "in the corpus", false);
                $("kpis").innerHTML = html;
              }catch(e){ $("pillCount").textContent = "offline"; }
            })();
            </script>
            </body>
            </html>
            """;
}
