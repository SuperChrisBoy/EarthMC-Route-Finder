package net.earthmc.routefinder.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WebsiteGeometryTest {
  private static WebsiteGeometry.Vertex p(double x, double z) {
    return new WebsiteGeometry.Vertex(x, z, 64);
  }
  private static Map<Set<WebsiteGeometry.Vertex>, Integer> edges(List<List<WebsiteGeometry.Vertex>> paths) {
    Map<Set<WebsiteGeometry.Vertex>, Integer> result = new HashMap<>();
    for (var path : paths) for (int i = 1; i < path.size(); i++)
      result.merge(new HashSet<>(List.of(path.get(i - 1), path.get(i))), 1, Integer::sum);
    return result;
  }
  @Test void joinsReversedRunsAndLinksWithoutLosingEdges() {
    var runs = List.of(List.of(p(0,0),p(1,0)), List.of(p(3,0),p(2,0)),
        List.of(p(1,0),p(2,0)), List.of(p(2,0)));
    var paths = WebsiteGeometry.compact(runs);
    assertEquals(1, paths.size());
    assertEquals(edges(runs), edges(paths));
    assertEquals(paths, WebsiteGeometry.compact(runs));
  }
  @Test void preservesCrossoversLoopsDuplicateEdgesAndDisconnectedGeometry() {
    var runs = List.of(List.of(p(0,0),p(1,0),p(2,0)),
        List.of(p(1,0),p(1,1),p(2,0)), List.of(p(0,0),p(1,0)),
        List.of(p(10,0),p(11,0)), List.of(p(1,0),p(1,0)));
    assertEquals(edges(runs), edges(WebsiteGeometry.compact(runs)));
  }
  @Test void doesNotJoinDifferentHeightsOrCrossingsWithoutSharedEndpoints() {
    var runs = List.of(List.of(p(0,0),p(2,0)),
        List.of(new WebsiteGeometry.Vertex(2,0,70),new WebsiteGeometry.Vertex(3,0,70)),
        List.of(p(1,-1),p(1,1)));
    assertEquals(3, WebsiteGeometry.compact(runs).size());
    assertEquals(edges(runs), edges(WebsiteGeometry.compact(runs)));
  }
  @Test void preservesEmptyBranchAndStandalonePoint() {
    assertEquals(List.of(List.of()), WebsiteGeometry.compact(List.of()));
    assertEquals(List.of(List.of(p(1,2))), WebsiteGeometry.compact(List.of(List.of(p(1,2)))));
  }
}
