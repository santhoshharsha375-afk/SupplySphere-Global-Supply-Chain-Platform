package com.supplysphere.core.util;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Nanosecond-precision timing helper used to report the cost of each
 * algorithm. Comparing {@code KMP} against {@code Rabin-Karp} on a real
 * product catalogue is far more convincing for a report than an asymptotic
 * claim, so the services emit measured timings rather than guesses.
 */
public final class Stopwatch {

    private final String label;
    private long startNanos;
    private long elapsedNanos;

    private Stopwatch(String label) {
        this.label = label;
    }

    public static Stopwatch start(String label) {
        Stopwatch watch = new Stopwatch(label);
        watch.startNanos = System.nanoTime();
        return watch;
    }

    /** Times {@code work}, closes the watch and returns its result. */
    public static <T> Timed<T> measure(String label, Supplier<T> work) {
        Preconditions.requireNonNull(work, "work");
        Stopwatch watch = start(label);
        try {
            return new Timed<>(work.get(), watch.stop());
        } catch (RuntimeException | Error failure) {
            watch.stop();
            throw failure;
        }
    }

    public Stopwatch stop() {
        if (elapsedNanos == 0L) {
            elapsedNanos = System.nanoTime() - startNanos;
        }
        return this;
    }

    public long nanos() {
        return elapsedNanos;
    }

    public double micros() {
        return elapsedNanos / 1_000.0;
    }

    public double millis() {
        return elapsedNanos / 1_000_000.0;
    }

    public String label() {
        return label;
    }

    public String report() {
        if (elapsedNanos >= TimeUnit.SECONDS.toNanos(1)) {
            return String.format("%-28s %8.3f s", label, millis() / 1000.0);
        }
        if (elapsedNanos >= TimeUnit.MILLISECONDS.toNanos(1)) {
            return String.format("%-28s %8.3f ms", label, millis());
        }
        if (elapsedNanos >= TimeUnit.MICROSECONDS.toNanos(1)) {
            return String.format("%-28s %8.1f us", label, micros());
        }
        return String.format("%-28s %8d ns", label, elapsedNanos);
    }

    @Override
    public String toString() {
        return report();
    }

    /** Result of {@link #measure(String, Supplier)} paired with its duration. */
    public static final class Timed<T> {
        private final T value;
        private final Stopwatch watch;

        Timed(T value, Stopwatch watch) {
            this.value = value;
            this.watch = watch;
        }

        public T value() {
            return value;
        }

        public Stopwatch timing() {
            return watch;
        }

        public String report() {
            return watch.report();
        }
    }
}
