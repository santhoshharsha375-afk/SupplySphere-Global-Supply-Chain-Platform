package com.supplysphere.ops;

import com.supplysphere.core.util.DomainException;
import com.supplysphere.core.util.Preconditions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The delivery-preparation workload scheduled across warehouses — Module 5 / CO5.
 *
 * <p>Format: one task per line, {@code label,processingMinutes}. Blank lines and
 * lines beginning with {@code #} are ignored. Each task gets a stable index so
 * the scheduler can return machine assignments as indices.</p>
 */
public record JobSet(List<Job> jobs) {

    /** One schedulable task: a label and the minutes one crew needs for it. */
    public record Job(int index, String label, int processingTime) {
        public Job {
            Preconditions.requireNonNegative(index, "index");
            Preconditions.requireNonBlank(label, "label");
            Preconditions.requirePositive(processingTime, "processingTime");
        }
    }

    public JobSet {
        jobs = List.copyOf(Preconditions.requireNotEmpty(jobs, "jobs"));
    }

    public int size() {
        return jobs.size();
    }

    /** Processing times in index order, ready for the scheduler. */
    public int[] processingTimes() {
        int[] times = new int[jobs.size()];
        for (int i = 0; i < times.length; i++) {
            times[i] = jobs.get(i).processingTime();
        }
        return times;
    }

    public String labelOf(int index) {
        return jobs.get(index).label();
    }

    public static JobSet load(Path file) {
        Preconditions.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw DomainException.notFound("job file", file);
        }
        try {
            return fromLines(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw DomainException.illegalState(
                    "cannot read jobs " + file + ": " + failure.getMessage());
        }
    }

    public static JobSet fromLines(List<String> lines) {
        Preconditions.requireNonNull(lines, "lines");
        List<Job> jobs = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int comma = line.lastIndexOf(',');
            if (comma < 0) {
                throw new DomainException("BAD_JOBS",
                        "job line " + lineNumber + " must be label,minutes but was '" + line + "'");
            }
            String label = line.substring(0, comma).trim();
            int minutes;
            try {
                minutes = Integer.parseInt(line.substring(comma + 1).trim());
            } catch (NumberFormatException invalid) {
                throw new DomainException("BAD_JOBS",
                        "job line " + lineNumber + " minutes are not a number: '" + line + "'");
            }
            jobs.add(new Job(jobs.size(), label, minutes));
        }
        return new JobSet(jobs);
    }
}
