package io.github.sethwillemsvg.lotrrevival.nodemap.graph;

import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.XZ;
import java.util.List;

/**
 * A two-way route: its polyline in blocks (from {@code a} through the bends to {@code b}) and its length after the kind's
 * factor. {@code rawLength} is the polyline's horizontal length.
 */
public record GraphEdge(String id, NodeRef a, NodeRef b, RouteKind kind, List<XZ> points, double rawLength, double length) {
    public NodeRef other(NodeRef from) {
        return from.equals(a) ? b : a;
    }

    public static double polylineLength(List<XZ> pts) {
        double l = 0;
        for (int i = 1; i < pts.size(); i++) l += pts.get(i - 1).dist(pts.get(i));
        return l;
    }

    /** A route whose length is given rather than measured (unit-test fixtures). */
    public static GraphEdge fixedLength(GraphNode a, GraphNode b, RouteKind kind, double raw, double factor) {
        return new GraphEdge(a.ref().shortName() + "-" + b.ref().shortName(), a.ref(), b.ref(), kind,
                List.of(new XZ(a.x(), a.z()), new XZ(b.x(), b.z())), raw, raw * factor);
    }

    public static GraphEdge of(String id, GraphNode a, GraphNode b, RouteKind kind, List<XZ> bends, double factor) {
        List<XZ> pts = new java.util.ArrayList<>(bends.size() + 2);
        pts.add(new XZ(a.x(), a.z()));
        pts.addAll(bends);
        pts.add(new XZ(b.x(), b.z()));
        double raw = polylineLength(pts);
        return new GraphEdge(id, a.ref(), b.ref(), kind, List.copyOf(pts), raw, raw * factor);
    }
}
