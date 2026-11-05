package io.github.sethwillemsvg.lotrrevival.nodemap.graph;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Outcome of a route search. FOUND: {@code chain} runs departure to destination, {@code legs} holds each route's length,
 * {@code length} is L. BLOCKED: no path through usable nodes, but there is one through {@code blockedBy}, a barred node.
 * NO_PATH: no chain of known routes at all.
 */
public record PathResult(Status status, List<NodeRef> chain, List<Double> legs, double length, @Nullable NodeRef blockedBy) {
    public enum Status { FOUND, BLOCKED, NO_PATH }

    public static PathResult noPath() {
        return new PathResult(Status.NO_PATH, List.of(), List.of(), 0, null);
    }

    public boolean found() {
        return status == Status.FOUND;
    }

    /** For log lines: {@code L=2600 chain=[a, b(1000), c(1600)]} or {@code no path} / {@code no path (blocked by <id>)}. */
    public String describe() {
        if (status == Status.NO_PATH) return "no path";
        if (status == Status.BLOCKED) return "no path (blocked by " + blockedBy.shortName() + ")";
        StringBuilder sb = new StringBuilder("L=").append(Math.round(length)).append(" chain=[");
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) sb.append(", ").append(chain.get(i).shortName()).append('(').append(Math.round(legs.get(i - 1))).append(')');
            else sb.append(chain.get(i).shortName());
        }
        return sb.append(']').toString();
    }

    public String chainIds() {
        return chain.stream().map(NodeRef::shortName).collect(Collectors.joining(","));
    }
}
