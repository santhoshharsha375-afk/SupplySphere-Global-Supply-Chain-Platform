package com.supplysphere.repository;

import com.supplysphere.core.util.DomainException;
import com.supplysphere.core.util.Preconditions;
import com.supplysphere.dataset.Article;

import java.util.List;

/**
 * Read-only access to the loaded corpus — Module 1 / CO1.
 *
 * <p>Holds the articles for the lifetime of the process and answers the two
 * questions the console asks outside a search: "what is in the corpus" (the
 * statistics report) and "give me article {@code x}" (by id).</p>
 */
public final class ArticleRepository {

    private final List<Article> articles;

    public ArticleRepository(List<Article> articles) {
        this.articles = List.copyOf(Preconditions.requireNotEmpty(articles, "articles"));
    }

    public List<Article> all() {
        return articles;
    }

    public int count() {
        return articles.size();
    }

    public Article byId(String id) {
        Preconditions.requireNonBlank(id, "id");
        for (Article article : articles) {
            if (article.id().equals(id)) {
                return article;
            }
        }
        throw DomainException.notFound("article", id);
    }

    public int totalCharacters() {
        return articles.stream().mapToInt(Article::characterCount).sum();
    }

    public int totalWords() {
        return articles.stream().mapToInt(Article::wordCount).sum();
    }
}
