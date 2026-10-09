import com.supplysphere.core.config.AppConfig;
import com.supplysphere.service.SearchService;
import com.supplysphere.ui.SearchConsole;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SupplySphere console entry point.
 *
 * <p>Opens the document corpus from {@code data/corpus} (or the path given as
 * the first argument), prints the corpus statistics, runs one demo query per
 * search mode so the platform's behaviour is visible without typing, then
 * hands over to the interactive console.</p>
 */
public final class SupplySphereApp {

    private SupplySphereApp() {
    }

    public static void main(String[] args) throws IOException {
        Path corpus = args.length > 0 ? Path.of(args[0]) : Path.of("data", "corpus");
        if (!Files.isDirectory(corpus)) {
            System.err.println("corpus directory not found: " + corpus.toAbsolutePath());
            System.err.println("usage: java SupplySphereApp [corpusDirectory]");
            System.exit(2);
        }
        AppConfig config = AppConfig.productionDefaults();
        SearchService service = SearchService.open(config, corpus);
        SearchConsole console = new SearchConsole(service);

        System.out.println("SupplySphere - Global Supply Chain Management Platform");
        System.out.println("Loaded " + service.repository().count()
                + " documents into the search index. Type help for commands.\n");
        System.out.print(console.execute("stats"));

        System.out.println();
        demo(console, "find contract terms", "exact supplier evaluation");
        demo(console, "match several keywords", "keywords shipment tracking");
        demo(console, "search despite typos", "fuzzy shipmnt");
        demo(console, "find related documents", "similar inventory management");

        System.out.println("interactive session (type quit to leave)");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("> ");
        String line;
        while ((line = stdin.readLine()) != null) {
            String output = console.execute(line);
            if (output == null) {
                break;
            }
            System.out.print(output);
            System.out.print("> ");
        }
        System.out.println("bye");
    }

    private static void demo(SearchConsole console, String title, String command) {
        System.out.println("--- demo: " + title + " ---");
        System.out.print(console.execute(command));
        System.out.println();
    }
}
