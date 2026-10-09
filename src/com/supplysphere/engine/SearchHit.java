package com.supplysphere.engine;

import com.supplysphere.core.util.Preconditions;
import com.supplysphere.dataset.Article;

import java.util.List;

/**
 * One ranked answer from the query engine — Module 1 / CO1.
 *
 * <p>{@code score} is the mode's native relevance number: occurrence count for
 * {@link SearchMode#EXACT}, fraction of distinct terms found for
 * {@link SearchMode#MULTI_KEYWORD}, best fuzzy confidence in {@code [0, 1]} for
 * {@link SearchMode#FUZZY}, and a word-coverage/phrase blend in {@code [0, 1]}
 * for {@link SearchMode#SIMILAR}. Scores are only comparable within one report.</p>
 */
public record SearchHit(Article article, double score, String snippet, List<String> matchedTerms) {

    public SearchHit {
        article = Preconditions.requireNonNull(article, "article");
        snippet = Preconditions.requireNonNull(snippet, "snippet");
        matchedTerms = List.copyOf(Preconditions.requireNonNull(matchedTerms, "matchedTerms"));
        if (Double.isNaN(score)) {
            throw com.supplysphere.core.util.DomainException.illegalState("score must not be NaN");
        }
    }
}
