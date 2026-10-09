package com.supplysphere.engine;

import com.supplysphere.algorithm.dp.EditDistance;
import com.supplysphere.algorithm.stringalgorithms.AhoCorasick;
import com.supplysphere.algorithm.stringalgorithms.Kmp;
import com.supplysphere.algorithm.stringalgorithms.PatternMatch;
import com.supplysphere.algorithm.stringalgorithms.RabinKarp;
import com.supplysphere.algorithm.suffixstructures.SimilarityIndex;
import com.supplysphere.core.config.AppConfig;
import com.supplysphere.core.util.Preconditions;
import com.supplysphere.core.util.Stopwatch;
import com.supplysphere.core.util.Text;
import com.supplysphere.dataset.Article;

import java.util.List;

/**
 * The TextHack-style retrieval engine — Module 1 / CO1.
 *
 * <p>The engine owns the corpus index: every article is normalised once at
 * construction (case-folded search text, case-preserved display text, word
 * tokens), so a query only ever touches pre-computed arrays. Each
 * {@link SearchMode} is a thin adapter over a syllabus algorithm — KMP,
 * Aho-Corasick, bounded Levenshtein, shingle Jaccard — and every query is
 * timed with {@link Stopwatch} so reports carry measured numbers instead of
 * asymptotic guesses.</p>
 *
 * <p>Deliberately hand-built: the ranking pipeline (candidate lists, term
 * dedup, found-term sets, relevance sort) uses the engine-local
 * {@link EngineList}, {@link EngineSet} and {@link EngineSort} rather than the
 * JDK collections, so the retrieval algorithms are implemented here rather than
 * imported. The only {@code java.util} types are the {@code List<SearchHit>}
 * contract of the public {@link #search} results and the one at the boundary
 * where a hand-built array is turned into that contract list.</p>
 *
 * <p>Offsets: match positions are found in the normalised search text and
 * reused against the display text. Both apply the same whitespace collapse,
 * so the positions line up for the ASCII corpora the platform ships with.</p>
 */
public final class QueryEngine {

    private static final int SNIPPET_LEAD = 30;
    private static final int SNIPPET_TAIL = 50;

    private final AppConfig config;
    private final List<Article> articles;
    private final String[] displayText;
    private final String[] searchText;
    private final String[][] words;

    public QueryEngine(List<Article> articles, AppConfig config) {
        this.articles = List.copyOf(Preconditions.requireNotEmpty(articles, "articles"));
        this.config = Preconditions.requireNonNull(config, "config");
        int size = this.articles.size();
        this.displayText = new String[size];
        this.searchText = new String[size];
        this.words = new String[size][];
        for (int i = 0; i < size; i++) {
            Article article = this.articles.get(i);
            displayText[i] = article.displayText();
            searchText[i] = article.searchText();
            words[i] = Text.words(searchText[i]).toArray(new String[0]);
        }
        for (SearchMode warm : SearchMode.values()) {
            dispatch("warm up", warm);
        }
    }

    public int articleCount() {
        return articles.size();
    }

    /** Runs {@code query} under {@code mode} and returns a timed, ranked report. */
    public QueryReport search(String query, SearchMode mode) {
        Preconditions.requireNonBlank(query, "query");
        Preconditions.requireNonNull(mode, "mode");
        Stopwatch.Timed<List<SearchHit>> timed = Stopwatch.measure(
                "query:" + mode.command(), () -> dispatch(query, mode));
        List<SearchHit> ranked = timed.value();
        SearchHit[] rankedArray = ranked.toArray(new SearchHit[0]);
        EngineSort.sortByRelevance(rankedArray, rankedArray.length);
        int limit = Math.min(config.searchResultLimit(), rankedArray.length);
        SearchHit[] top = new SearchHit[limit];
        System.arraycopy(rankedArray, 0, top, 0, limit);
        return new QueryReport(query, mode, List.of(top), timed.timing().nanos(), articles.size());
    }

    /**
     * Cross-algorithm consistency scan: the module's main matcher ({@link Kmp})
     * and its benchmark ({@link RabinKarp}, double-hash) are run over every
     * article and must return identical occurrence counts. The other candidate
     * matchers (naive scan, Z-function) are documented in the module report but
     * not shipped — this is the "two choices, one answer" check for the exact
     * search task.
     */
    public String scanReport(String query) {
        String phrase = Text.normalize(query);
        Preconditions.requireNonBlank(phrase, "query");
        Kmp kmp = Kmp.of(phrase);
        RabinKarp rabin = new RabinKarp(phrase, 257, 1_000_000_007, 269, 1_000_000_009);
        int[] kmpCounts = new int[articles.size()];
        int[] rabinCounts = new int[articles.size()];
        for (int i = 0; i < articles.size(); i++) {
            String search = searchText[i];
            kmpCounts[i] = kmp.countOccurrences(search);
            rabinCounts[i] = rabin.searchAllDoubleHashed(search).size();
        }
        boolean agree = agreeBetween(kmpCounts, rabinCounts);
        StringBuilder out = new StringBuilder();
        out.append("Scan: \"").append(phrase).append("\" across ")
                .append(articles.size()).append(" documents\n");
        out.append("  Kmp                      ").append(sum(kmpCounts)).append(" occurrences\n");
        out.append("  RabinKarp (double-hash)  ").append(sum(rabinCounts)).append(" occurrences\n");
        out.append(agree
                ? "  both algorithms agree\n"
                : "  DISAGREEMENT between algorithms\n");
        return out.toString();
    }

    private static boolean agreeBetween(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private List<SearchHit> dispatch(String query, SearchMode mode) {
        return switch (mode) {
            case EXACT -> exact(query);
            case MULTI_KEYWORD -> multiKeyword(query);
            case FUZZY -> fuzzy(query);
            case SIMILAR -> similar(query);
        };
    }

    /**
     * Literal phrase scan: one {@link Kmp} automaton built from the normalised
     * phrase, replayed over every article; the score is the occurrence count.
     */
    private List<SearchHit> exact(String query) {
        String phrase = Text.normalize(query);
        Preconditions.requireNonBlank(phrase, "query");
        Kmp kmp = Kmp.of(phrase);
        EngineList<SearchHit> hits = new EngineList<>();
        for (int i = 0; i < articles.size(); i++) {
            int at = kmp.search(searchText[i]);
            if (at < 0) {
                continue;
            }
            int count = kmp.countOccurrences(searchText[i]);
            hits.add(new SearchHit(articles.get(i), count,
                    snippet(i, at, phrase.length()), List.of(phrase)));
        }
        return List.of(hits.toArray(new SearchHit[0]));
    }

    /**
     * One {@link AhoCorasick} automaton over all query terms, one pass per
     * article; the score is the fraction of distinct terms found.
     */
    private List<SearchHit> multiKeyword(String query) {
        String[] terms = distinctWords(query);
        AhoCorasick automaton = new AhoCorasick();
        for (String term : terms) {
            automaton.add(term);
        }
        automaton.build();
        EngineList<SearchHit> hits = new EngineList<>();
        for (int i = 0; i < articles.size(); i++) {
            List<PatternMatch> matches = automaton.findAll(searchText[i]);
            if (matches.isEmpty()) {
                continue;
            }
            EngineSet found = new EngineSet();
            int firstStart = Integer.MAX_VALUE;
            int firstLength = 0;
            for (PatternMatch match : matches) {
                found.add(match.pattern());
                if (match.start() < firstStart) {
                    firstStart = match.start();
                    firstLength = match.end() - match.start();
                }
            }
            double score = (double) found.size() / terms.length;
            hits.add(new SearchHit(articles.get(i), score,
                    snippet(i, firstStart, firstLength), List.of(found.toStringArray())));
        }
        return List.of(hits.toArray(new SearchHit[0]));
    }

    /**
     * Bounded Levenshtein between each query token and each article word; the
     * score is the mean best confidence, where confidence of a match at
     * distance {@code d} is {@code 1 - d / maxLen}. Only articles whose mean
     * clears {@link AppConfig#fuzzyMatchMinConfidence()} are reported.
     */
    private List<SearchHit> fuzzy(String query) {
        List<String> tokens = Text.words(query);
        Preconditions.requireNotEmpty(tokens, "query tokens");
        int maxDistance = config.fuzzyMatchMaxDistance();
        double minConfidence = config.fuzzyMatchMinConfidence();
        EngineList<SearchHit> hits = new EngineList<>();
        for (int i = 0; i < articles.size(); i++) {
            double confidenceSum = 0.0;
            EngineList<String> matchedTerms = new EngineList<>();
            String anchorWord = null;
            double anchorConfidence = -1.0;
            String[] articleWords = words[i];
            for (String token : tokens) {
                double best = 0.0;
                String bestWord = null;
                for (String word : articleWords) {
                    int distance = EditDistance.levenshteinWithin(token, word, maxDistance);
                    if (distance > maxDistance) {
                        continue;
                    }
                    int longest = Math.max(token.length(), word.length());
                    double confidence = longest == 0 ? 1.0 : 1.0 - (double) distance / longest;
                    if (confidence > best) {
                        best = confidence;
                        bestWord = word;
                    }
                }
                confidenceSum += best;
                if (best >= minConfidence && bestWord != null) {
                    matchedTerms.add(token);
                    if (best > anchorConfidence) {
                        anchorConfidence = best;
                        anchorWord = bestWord;
                    }
                }
            }
            double score = confidenceSum / tokens.size();
            if (score < minConfidence || anchorWord == null) {
                continue;
            }
            int at = searchText[i].indexOf(anchorWord);
            hits.add(new SearchHit(articles.get(i), score,
                    snippet(i, at < 0 ? 0 : at, anchorWord.length()),
                    List.of(matchedTerms.toArray(new String[0]))));
        }
        return List.of(hits.toArray(new SearchHit[0]));
    }

    /**
     * Topic ranking. An article is a candidate as soon as it contains any query
     * word; its score blends four signals, dominant first:
     * <ul>
     *   <li><b>coverage</b> — fraction of the distinct query words present.
     *       Plain Jaccard of a two-word query against a whole article is always
     *       near zero and would hide every related document.</li>
     *   <li><b>title match</b> — a query word in the article title, the
     *       strongest possible hint that the article is <em>about</em> it.</li>
     *   <li><b>term weight</b> — a saturating count of the query words in the
     *       body, so an article that uses them often outranks one that merely
     *       mentions them.</li>
     *   <li><b>phrase</b> — shingle {@link SimilarityIndex#jaccard Jaccard},
     *       rewarding query words that also appear together in order.</li>
     * </ul>
     */
    private List<SearchHit> similar(String query) {
        List<String> tokens = Text.words(query);
        Preconditions.requireNotEmpty(tokens, "query tokens");
        String[] distinctTokens = distinctWords(query);
        EngineSet wanted = new EngineSet();
        for (String token : distinctTokens) {
            wanted.add(token);
        }
        int shingleSize = Math.min(config.shingleSize(), tokens.size());
        String normalisedQuery = Text.normalize(query);
        EngineList<SearchHit> hits = new EngineList<>();
        for (int i = 0; i < articles.size(); i++) {
            String[] articleWords = words[i];
            EngineList<String> present = new EngineList<>();
            for (String token : distinctTokens) {
                if (wordsOf(articleWords, token)) {
                    present.add(token);
                }
            }
            if (present.isEmpty()) {
                continue;
            }
            int occurrences = 0;
            for (String word : articleWords) {
                if (wanted.contains(word)) {
                    occurrences++;
                }
            }
            boolean titleMatch = titleMentions(i, wanted);
            double coverage = (double) present.size() / distinctTokens.length;
            double termWeight = (double) occurrences / (occurrences + distinctTokens.length);
            double phrase = SimilarityIndex.jaccard(normalisedQuery, searchText[i], shingleSize);
            double score = 0.75 * coverage + 0.15 * (titleMatch ? 1.0 : 0.0)
                    + 0.07 * termWeight + 0.03 * phrase;
            int at = searchText[i].indexOf(present.get(0));
            hits.add(new SearchHit(articles.get(i), score,
                    snippet(i, at < 0 ? 0 : at, present.get(0).length()),
                    List.of(present.toArray(new String[0]))));
        }
        return List.of(hits.toArray(new SearchHit[0]));
    }

    /** Distinct words of the query, in first-appearance order (hand-built). */
    private String[] distinctWords(String query) {
        List<String> terms = Text.words(query);
        Preconditions.requireNotEmpty(terms, "query terms");
        EngineSet seen = new EngineSet();
        EngineList<String> ordered = new EngineList<>();
        for (String term : terms) {
            if (seen.add(term)) {
                ordered.add(term);
            }
        }
        return ordered.toArray(new String[0]);
    }

    private static boolean wordsOf(String[] words, String target) {
        for (String word : words) {
            if (word.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean titleMentions(int articleIndex, EngineSet wanted) {
        String[] titleWords = Text.words(Text.normalize(articles.get(articleIndex).title()))
                .toArray(new String[0]);
        for (String word : titleWords) {
            if (wanted.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Case-preserved window of the display text around a match position. */
    private String snippet(int articleIndex, int start, int length) {
        String text = displayText[articleIndex];
        if (text.isEmpty()) {
            return "";
        }
        int at = Math.min(Math.max(start, 0), text.length() - 1);
        int from = Math.max(0, at - SNIPPET_LEAD);
        int to = Math.min(text.length(), at + Math.max(length, 0) + SNIPPET_TAIL);
        String title = articles.get(articleIndex).title();
        boolean strippedTitle = false;
        if (from == 0 && text.length() > title.length()
                && text.regionMatches(true, 0, title, 0, title.length())) {
            int body = title.length();
            while (body < text.length() && Character.isWhitespace(text.charAt(body))) {
                body++;
            }
            if (body < to) {
                from = body;
                strippedTitle = true;
            }
        }
        String window = text.substring(from, to);
        String head = from > 0 && !strippedTitle ? "..." : "";
        return head + window + (to < text.length() ? "..." : "");
    }
}