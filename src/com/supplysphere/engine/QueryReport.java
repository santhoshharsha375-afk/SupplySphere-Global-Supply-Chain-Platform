package com.supplysphere.engine;

import com.supplysphere.core.util.Preconditions;
import com.supplysphere.core.util.Text;

import java.util.List;
import java.util.Locale;

/**
 * A finished query: what was asked, how it was answered, how long it took —
 * Module 1 / CO1.
 *
 * <p>The report owns rendering so the console stays a dumb loop and the same
 * block can be dropped into a file, a test assertion or a benchmark log.</p>
 */
public record QueryReport(String query, SearchMode mode, List<SearchHit> hits,
                          long elapsedNanos, int articlesScanned) {

    public QueryReport {
        query = Preconditions.requireNonBlank(query, "query");
        mode = Preconditions.requireNonNull(mode, "mode");
        hits = List.copyOf(Preconditions.requireNonNull(hits, "hits"));
        Preconditions.requireNonNegative(elapsedNanos, "elapsedNanos");
        Preconditions.requireNonNegative(articlesScanned, "articlesScanned");
    }

    public int hitCount() {
        return hits.size();
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    /** Human-readable block: mode explanation, headline, ranked hits, hint. */
    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("Search: \"").append(query).append("\"\n");
        out.append("  Mode: ").append(mode.description()).append('\n');
        if (hits.isEmpty()) {
            out.append(String.format(Locale.ROOT, "  No matches out of %d documents (%.1f ms)%n",
                    articlesScanned, elapsedMillis()));
            out.append("  Tip: try 'keywords', 'fuzzy' or 'similar', or shorten the query.\n");
            return out.toString();
        }
        out.append(String.format(Locale.ROOT, "  Found %d of %d documents (%.1f ms)%n%n",
                hitCount(), articlesScanned, elapsedMillis()));
        int rank = 1;
        boolean first = true;
        for (SearchHit hit : hits) {
            if (!first) {
                out.append('\n');
            }
            first = false;
            out.append(String.format(Locale.ROOT, "  %2d. %s%n", rank++, hit.article().title()));
            out.append("      why: ").append(why(hit)).append('\n');
            out.append("      ").append(hit.snippet()).append('\n');
        }
        return out.toString();
    }

    /** Plain-language relevance line so raw scores never reach the user. */
    private String why(SearchHit hit) {
        return switch (mode) {
            case EXACT -> (long) hit.score() == 1
                    ? "the phrase appears once"
                    : String.format(Locale.ROOT, "the phrase appears %d times", (long) hit.score());
            case MULTI_KEYWORD -> hit.matchedTerms().size() == 1
                    ? "matched 1 keyword"
                    : String.format(Locale.ROOT, "matched %d keywords", hit.matchedTerms().size());
            case FUZZY -> Math.round(hit.score() * 100) + "% match after typo correction";
            case SIMILAR -> {
                long total = Text.words(query).stream().distinct().count();
                yield hit.matchedTerms().size() + " of " + total + " query words present";
            }
        };
    }
}
