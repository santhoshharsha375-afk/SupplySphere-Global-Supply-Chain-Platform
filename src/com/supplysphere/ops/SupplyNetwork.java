package com.supplysphere.ops;

import com.supplysphere.core.util.Preconditions;

import java.util.List;

/**
 * A directed supply-chain network: suppliers feeding plants, plants feeding
 * distribution centres, centres feeding markets — Module 4 / CO4.
 *
 * <p>Each {@link Link} carries a daily capacity. The network is the domain
 * input the max-flow / min-cut engine runs on; the cut names the bottleneck
 * links where the chain breaks.</p>
 *
 * @param nodes  every node id, in the order first seen while loading
 * @param links  the directed, capacity-bearing arcs
 * @param source the single node with in-degree zero
 * @param sink   the single node with out-degree zero
 */
public record SupplyNetwork(List<String> nodes, List<Link> links, String source, String sink) {

    /** Role of a node, derived from the id so the UI can group the chain. */
    public enum Kind { SOURCE, SUPPLIER, PLANT, DISTRIBUTION, MARKET, SINK, HUB }

    /** One capacity-bearing arc. */
    public record Link(String from, String to, int capacity) {
        public Link {
            Preconditions.requireNonBlank(from, "from");
            Preconditions.requireNonBlank(to, "to");
            Preconditions.requirePositive(capacity, "capacity");
        }
    }

    public SupplyNetwork {
        nodes = List.copyOf(Preconditions.requireNotEmpty(nodes, "nodes"));
        links = List.copyOf(Preconditions.requireNotEmpty(links, "links"));
        Preconditions.requireNonBlank(source, "source");
        Preconditions.requireNonBlank(sink, "sink");
        Preconditions.checkState(nodes.contains(source), "source " + source + " is not a node");
        Preconditions.checkState(nodes.contains(sink), "sink " + sink + " is not a node");
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int linkCount() {
        return links.size();
    }

    public int indexOf(String node) {
        int index = nodes.indexOf(node);
        Preconditions.checkState(index >= 0, "unknown node " + node);
        return index;
    }

    public Kind kindOf(String node) {
        if (node.equals(source)) {
            return Kind.SOURCE;
        }
        if (node.equals(sink)) {
            return Kind.SINK;
        }
        if (node.startsWith("S")) {
            return Kind.SUPPLIER;
        }
        if (node.startsWith("P")) {
            return Kind.PLANT;
        }
        if (node.startsWith("D")) {
            return Kind.DISTRIBUTION;
        }
        if (node.startsWith("M")) {
            return Kind.MARKET;
        }
        return Kind.HUB;
    }
}
