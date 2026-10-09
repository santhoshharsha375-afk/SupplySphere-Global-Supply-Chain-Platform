package com.supplysphere.algorithm.stringalgorithms;

import com.supplysphere.core.util.Preconditions;

/**
 * A half-open {@code [start, end)} occurrence of a pattern inside a text,
 * together with the identity of the pattern that matched.
 *
 * <p>Immutable value object so results can be streamed to the UI, sorted and
 * compared without aliasing the searcher's internal buffers.</p>
 *
 * @param pattern  the pattern (or keyword) that matched
 * @param patternId index of the pattern within a multi-pattern search
 * @param start    inclusive start offset in the text
 * @param end      exclusive end offset in the text
 */
public record PatternMatch(String pattern, int patternId, int start, int end) {

    public PatternMatch {
        Preconditions.requireNonNull(pattern, "pattern");
        Preconditions.requireNonNegative(patternId, "patternId");
        Preconditions.requireNonNegative(start, "start");
        Preconditions.requireNonNegative(end, "end");
        if (end < start) {
            throw new com.supplysphere.core.util.DomainException("INVALID_RANGE",
                    "end (" + end + ") must be >= start (" + start + ")");
        }
    }

    public int length() {
        return end - start;
    }

    public boolean overlaps(PatternMatch other) {
        return start < other.end && other.start < end;
    }

    @Override
    public String toString() {
        return pattern + "@[" + start + "," + end + ")";
    }
}
