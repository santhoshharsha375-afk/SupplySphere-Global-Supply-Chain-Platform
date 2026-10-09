package com.supplysphere.engine;

/**
 * Retrieval strategy offered by the query engine — Module 1 / CO1.
 *
 * <p>Each mode is a different syllabus algorithm wearing the same interface,
 * which is exactly what makes the engine a meaningful benchmark: the console
 * swaps the mode and the report shows how the strategies differ on one corpus.</p>
 */
public enum SearchMode {

    /** Literal phrase scan; KMP with a pre-built prefix function. */
    EXACT,

    /** Query split into terms, all of them located in one Aho-Corasick pass. */
    MULTI_KEYWORD,

    /** Typo-tolerant scan; bounded Levenshtein distance against article words. */
    FUZZY,

    /** Ranked topical overlap; shingle Jaccard similarity. */
    SIMILAR;

    /** Stable single-word command token used by the console and reports. */
    public String command() {
        return name().toLowerCase();
    }

    /** Plain-language explanation shown in every report and in help. */
    public String description() {
        return switch (this) {
            case EXACT -> "exact phrase - your words must appear together, as typed";
            case MULTI_KEYWORD -> "keywords - each word may appear anywhere in the article";
            case FUZZY -> "fuzzy - tolerates typos, like a spell checker";
            case SIMILAR -> "similar - ranks articles by topic overlap, not exact wording";
        };
    }
}
