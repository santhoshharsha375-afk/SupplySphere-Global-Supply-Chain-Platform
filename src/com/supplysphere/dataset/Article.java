package com.supplysphere.dataset;

import com.supplysphere.core.util.Preconditions;
import com.supplysphere.core.util.Text;

/**
 * One document of the in-memory search corpus — Module 1 / CO1.
 *
 * <p>An article is the unit the query engine retrieves. The record keeps the
 * raw text exactly as loaded and derives its two viewing forms on demand:
 * a case-preserved, whitespace-collapsed {@link #displayText()} for snippets
 * and a lower-cased {@link #searchText()} for matching. Both forms collapse
 * whitespace identically, so a match offset found in the search text is
 * already a valid offset in the display text.</p>
 */
public record Article(String id, String title, String content) {

    public Article {
        id = Preconditions.requireNonBlank(id, "id");
        title = Preconditions.requireNonBlank(title, "title");
        content = Preconditions.requireNonNull(content, "content");
    }

    /** Case-preserved title and body with collapsed whitespace. */
    public String displayText() {
        return Text.collapseWhitespace(title + " " + content);
    }

    /** Lower-cased title and body; offsets align with {@link #displayText()}. */
    public String searchText() {
        return Text.normalize(title + " " + content);
    }

    /** Characters of the document text (title plus body). */
    public int characterCount() {
        return displayText().length();
    }

    /** Word tokens of the document text (title plus body). */
    public int wordCount() {
        return Text.words(searchText()).size();
    }

    /** First {@code max} characters of the body, for table rows. */
    public String preview(int max) {
        return Text.ellipsize(Text.collapseWhitespace(content), max);
    }
}
