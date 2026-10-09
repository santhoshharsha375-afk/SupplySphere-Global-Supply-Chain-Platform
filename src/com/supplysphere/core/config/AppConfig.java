package com.supplysphere.core.config;

import com.supplysphere.core.util.Preconditions;

/**
 * Central, immutable search configuration. Passed explicitly into the services
 * rather than read from statics so tests can build a tiny world and the
 * console app can build a large one from the same code.
 */
public final class AppConfig {

    private final int fuzzyMatchMaxDistance;
    private final double fuzzyMatchMinConfidence;
    private final int searchResultLimit;
    private final int shingleSize;

    private AppConfig(Builder builder) {
        this.fuzzyMatchMaxDistance = builder.fuzzyMatchMaxDistance;
        this.fuzzyMatchMinConfidence = builder.fuzzyMatchMinConfidence;
        this.searchResultLimit = builder.searchResultLimit;
        this.shingleSize = builder.shingleSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Configuration used by the console application. */
    public static AppConfig productionDefaults() {
        return builder().build();
    }

    public int fuzzyMatchMaxDistance() {
        return fuzzyMatchMaxDistance;
    }

    public double fuzzyMatchMinConfidence() {
        return fuzzyMatchMinConfidence;
    }

    public int searchResultLimit() {
        return searchResultLimit;
    }

    public int shingleSize() {
        return shingleSize;
    }

    @Override
    public String toString() {
        return "AppConfig[searchResultLimit=" + searchResultLimit
                + ", shingleSize=" + shingleSize + "]";
    }

    /** Mutable builder following the standard JDK builder contract. */
    public static final class Builder {
        private int fuzzyMatchMaxDistance = 3;
        private double fuzzyMatchMinConfidence = 0.62;
        private int searchResultLimit = 10;
        private int shingleSize = 2;

        public Builder fuzzyMatchMaxDistance(int value) {
            this.fuzzyMatchMaxDistance = Preconditions.requirePositive(value, "fuzzyMatchMaxDistance");
            return this;
        }

        public Builder fuzzyMatchMinConfidence(double value) {
            this.fuzzyMatchMinConfidence = Preconditions.requireInRange(
                    (int) Math.round(value * 1000), 0, 1000, "fuzzyMatchMinConfidence") / 1000.0;
            return this;
        }

        public Builder searchResultLimit(int value) {
            this.searchResultLimit = Preconditions.requirePositive(value, "searchResultLimit");
            return this;
        }

        public Builder shingleSize(int value) {
            this.shingleSize = Preconditions.requirePositive(value, "shingleSize");
            return this;
        }

        public AppConfig build() {
            return new AppConfig(this);
        }
    }
}