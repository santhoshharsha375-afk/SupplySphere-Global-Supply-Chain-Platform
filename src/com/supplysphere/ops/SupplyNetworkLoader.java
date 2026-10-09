package com.supplysphere.ops;

import com.supplysphere.core.util.DomainException;
import com.supplysphere.core.util.Preconditions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a supply network from a CSV file — Module 4 / CO4.
 *
 * <p>Format: one link per line, {@code from,to,capacity}. Blank lines and lines
 * beginning with {@code #} are ignored. The source is the node that is never a
 * link target and the sink the node that is never a link source, so the file
 * only ever describes the topology.</p>
 */
public final class SupplyNetworkLoader {

    private SupplyNetworkLoader() {
    }

    public static SupplyNetwork load(Path file) {
        Preconditions.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw DomainException.notFound("supply network file", file);
        }
        try {
            return fromLines(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw DomainException.illegalState(
                    "cannot read supply network " + file + ": " + failure.getMessage());
        }
    }

    public static SupplyNetwork fromLines(List<String> lines) {
        Preconditions.requireNonNull(lines, "lines");
        Set<String> nodes = new LinkedHashSet<>();
        Set<String> targets = new LinkedHashSet<>();
        Set<String> sources = new LinkedHashSet<>();
        List<SupplyNetwork.Link> links = new ArrayList<>();

        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length != 3) {
                throw new DomainException("BAD_NETWORK",
                        "network line " + lineNumber + " must be from,to,capacity but was '" + line + "'");
            }
            String from = parts[0].trim();
            String to = parts[1].trim();
            int capacity;
            try {
                capacity = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException invalid) {
                throw new DomainException("BAD_NETWORK",
                        "network line " + lineNumber + " capacity is not a number: '" + parts[2].trim() + "'");
            }
            links.add(new SupplyNetwork.Link(from, to, capacity));
            nodes.add(from);
            nodes.add(to);
            sources.add(from);
            targets.add(to);
        }

        if (links.isEmpty()) {
            throw new DomainException("EMPTY_COLLECTION", "supply network has no links");
        }
        List<String> sourcesOnly = new ArrayList<>(sources);
        sourcesOnly.removeAll(targets);
        List<String> targetsOnly = new ArrayList<>(targets);
        targetsOnly.removeAll(sources);
        if (sourcesOnly.size() != 1 || targetsOnly.size() != 1) {
            throw new DomainException("BAD_NETWORK",
                    "network needs exactly one source and one sink but found sources=" + sourcesOnly
                            + " sinks=" + targetsOnly);
        }
        return new SupplyNetwork(new ArrayList<>(nodes), links, sourcesOnly.get(0), targetsOnly.get(0));
    }
}
