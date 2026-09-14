package net.earthmc.routefinder.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Packs existing edges into trails without bridging gaps or changing edge multiplicity. */
final class WebsiteGeometry {
  record Vertex(double x, double z, double y) {}
  private record Edge(Vertex a, Vertex b) {
    Vertex other(Vertex at) { return a.equals(at) ? b : a; }
  }

  static List<List<Vertex>> compact(List<List<Vertex>> runs) {
    List<Edge> edges = new ArrayList<>();
    for (List<Vertex> run : runs)
      for (int i = 1; i < run.size(); i++) edges.add(new Edge(run.get(i - 1), run.get(i)));
    boolean[] used = new boolean[edges.size()];
    List<List<Vertex>> result = new ArrayList<>();
    for (int seed = 0; seed < edges.size(); seed++) {
      if (used[seed]) continue;
      used[seed] = true;
      List<Vertex> trail = new ArrayList<>(List.of(edges.get(seed).a, edges.get(seed).b));
      extend(trail, edges, used);
      Collections.reverse(trail);
      extend(trail, edges, used);
      Collections.reverse(trail);
      result.add(trail);
    }
    // A wholly empty/point-only branch may still own station references.
    if (result.isEmpty()) result.add(runs.isEmpty() ? List.of() : List.copyOf(runs.getFirst()));
    return result;
  }

  private static void extend(List<Vertex> trail, List<Edge> edges, boolean[] used) {
    while (true) {
      Vertex at = trail.getLast(), previous = trail.get(trail.size() - 2);
      int best = -1;
      double bestScore = -Double.MAX_VALUE;
      for (int i = 0; i < edges.size(); i++) {
        Edge edge = edges.get(i);
        if (used[i] || (!edge.a.equals(at) && !edge.b.equals(at))) continue;
        Vertex next = edge.other(at);
        double ax = at.x - previous.x, az = at.z - previous.z;
        double bx = next.x - at.x, bz = next.z - at.z;
        double length = Math.hypot(ax, az) * Math.hypot(bx, bz);
        double score = length == 0 ? -1 : (ax * bx + az * bz) / length;
        // Prefer straight continuations; source order breaks ties deterministically.
        if (best < 0 || score > bestScore) { best = i; bestScore = score; }
      }
      if (best < 0) return;
      used[best] = true;
      trail.add(edges.get(best).other(at));
    }
  }
}
