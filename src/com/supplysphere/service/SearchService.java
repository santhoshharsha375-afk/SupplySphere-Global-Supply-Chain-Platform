package com.supplysphere.service;

import com.supplysphere.core.config.AppConfig;
import com.supplysphere.core.util.Preconditions;
import com.supplysphere.dataset.Article;
import com.supplysphere.dataset.CorpusLoader;
import com.supplysphere.engine.QueryEngine;
import com.supplysphere.engine.QueryReport;
import com.supplysphere.engine.SearchMode;
import com.supplysphere.repository.ArticleRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Application facade over the retrieval engine — Module 1 / CO1.
 *
 * <p>Wires the four layers in one place: {@link CorpusLoader} reads the
 * directory, {@link ArticleRepository} holds the corpus, {@link QueryEngine}
 * indexes and searches it, and {@link AppConfig} supplies every tunable
 * (result limit, fuzzy distance, shingle size). The console and the tests
 * both drive the platform through this class only.</p>
 */
public final class SearchService {

    private final AppConfig config;
    private final ArticleRepository repository;
    private final QueryEngine engine;

    public SearchService(AppConfig config, List<Article> articles) {
        this.config = Preconditions.requireNonNull(config, "config");
        this.repository = new ArticleRepository(articles);
        this.engine = new QueryEngine(repository.all(), config);
    }

    /** Loads {@code *.txt} from {@code corpusDirectory} and builds the index. */
    public static SearchService open(AppConfig config, Path corpusDirectory) {
        return new SearchService(config, CorpusLoader.load(corpusDirectory));
    }

    public QueryReport search(String query, SearchMode mode) {
        return engine.search(query, mode);
    }

    /** Cross-algorithm consistency scan of the corpus for {@code query}. */
    public String scanReport(String query) {
        return engine.scanReport(query);
    }

    /** Plain-language corpus summary: totals, then one titled entry per document. */
    public String corpusReport() {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Corpus: %d documents, %,d characters, %,d words%n%n",
                repository.count(), repository.totalCharacters(), repository.totalWords()));
        int rank = 1;
        for (Article article : repository.all()) {
            out.append(String.format(Locale.ROOT, "  %2d. %s%n", rank++, article.title()));
            out.append(String.format(Locale.ROOT, "      %,d chars, %,d words%n",
                    article.characterCount(), article.wordCount()));
            out.append("      ").append(article.preview(70)).append('\n');
        }
        return out.toString();
    }

    public ArticleRepository repository() {
        return repository;
    }

    public AppConfig config() {
        return config;
    }
}
