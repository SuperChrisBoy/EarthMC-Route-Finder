package net.earthmc.routefinder.gui;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.iceeditor.AutosaveController;
import net.earthmc.routefinder.iceeditor.EditorState;
import net.earthmc.routefinder.iceeditor.EditorTool;
import net.earthmc.routefinder.iceeditor.SnapSettings;
import net.earthmc.routefinder.iceeditor.SnapTarget;
import org.lwjgl.glfw.GLFW;

/** In-map editor and website-compatible highways.json exporter. */
public final class IceRoadPlannerOverlay {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String[] TYPES = {
    "line", "station", "semi1", "semi2", "semi3", "semi4", "jct", "inter1", "inter2", "elev-ew",
    "elev-we"
  };
  private static final String[] LABELS = {
    "Line",
    "Station",
    "Semi N",
    "Semi E",
    "Semi S",
    "Semi W",
    "Junction",
    "Interchange A",
    "Interchange B",
    "Elevator E-W",
    "Elevator W-E"
  };
  private static final String[] COORDINATE_MODES = {
    "All", "Turns + Y changes", "Markers", "Hidden", "Graphic only"
  };
  private static final int[] LABEL_VERTICAL_OFFSETS = {-5, 9, -19, 23, -33, 37, -47};
  private static final int W = 230,
      ROW = 20,
      MAX_BRANCH_ROWS = 4,
      PROJECT_X = 126,
      PROJECT_W = 190,
      HEADER_H = 28,
      TOOL_W = 68,
      BRANCH_PANEL_H = 290;
  private static boolean active,
      loaded,
      toolDropdown,
      draftDropdown,
      dragging,
      dragArmed,
      pendingPlacement,
      dragUndoRecorded,
      markerEditing,
      segmentEditing,
      pointEditing,
      pointConnecting,
      branchPanel,
      newLinePending,
      pendingImageExport,
      exportMenu,
      snapMenu,
      validationPanel,
      markerListDropdown,
      markerMembershipPanel,
      pointMembershipPanel,
      segmentMembershipPanel,
      colorPicker,
      colorPickerDragging,
      colorPickerUndoRecorded,
      uiPressHandled;
  private static String extractionMessage = "";
  private static int tool,
      draftIndex,
      coordinateField,
      nameEditing,
      selectedMarker = -1,
      selectedPointBranch = -1,
      selectedPoint = -1,
      selectedSegmentBranch = -1,
      selectedSegment = -1,
      dragStation = -1,
      dragBranch = -1,
      dragVertex = -1,
      markerListPage,
      markerMembershipPage,
      pointMembershipPage,
      segmentMembershipPage,
      colorPickerDragArea;
  private static double gestureStartX, gestureStartY, pendingWorldX, pendingWorldZ;
  private static double viewCamX, viewCamZ, viewScale;
  private static int viewWidth, viewHeight;
  private static boolean viewTransformValid;
  private static String coordinateX = "",
      coordinateY = "64",
      coordinateZ = "",
      nameInput = "",
      feedback = "";
  private static long feedbackUntil, imageExportStartedAt;
  private static long draftDeleteConfirmUntil;
  private static long lineDeleteConfirmUntil;
  private static float pickerHue, pickerSaturation, pickerBrightness;
  private static final String[] COMMON_LINE_COLORS = {
    "ff55dd", "ff5555", "ff9900", "ffff55", "55ff55", "55ffff", "5599ff", "aa55ff", "ffffff", "777777"
  };
  private static final List<Draft> drafts = new ArrayList<>();
  private static final Deque<JsonObject> undoHistory = new ArrayDeque<>();
  private static final Deque<JsonObject> redoHistory = new ArrayDeque<>();
  private static final EditorState editorState = new EditorState();
  private static final AutosaveController autosave = new AutosaveController(350);
  private static final SnapSettings snapSettings = new SnapSettings();
  private static final List<String> validationMessages = new ArrayList<>();
  private static Point selectedSegmentPoint;
  private static Point connectionStart;
  private static Point measureStart;
  private static Point measureEnd;
  private static long cachedTopologyRevision = -1;
  private static int cachedTopologyDraft = -1;
  private static Set<String> cachedTopologyLabels = Set.of();
  private static List<DragHit> dragHits = List.of();
  private static List<SegmentHit> segmentHits = List.of();
  private static final List<NodeRef> multiSelection = new ArrayList<>();
  private static CopiedRoute copiedRoute;

  private record Point(double x, double z, double y) {
    Point(double x, double z) {
      this(x, z, Double.NaN);
    }

    @Override
    public boolean equals(Object value) {
      return value instanceof Point point
          && Double.doubleToLongBits(x) == Double.doubleToLongBits(point.x)
          && Double.doubleToLongBits(z) == Double.doubleToLongBits(point.z);
    }

    @Override
    public int hashCode() {
      return Objects.hash(x, z);
    }
  }

  private record Link(Point a, Point b) {}

  private record Station(int id, String name, String type, double x, double y, double z) {}

  private record DragHit(int station, int branch, int vertex, double x, double y) {}

  private record SegmentHit(
      int line, int branch, int segment, double ax, double ay, double bx, double by) {}

  private record NodeRef(int station, int branch, int vertex) {}

  private record EdgeRef(int branch, int segment, int link) {}

  private record GraphEdge(Point a, Point b, EdgeRef ref, double length) {
    Point other(Point point) {
      return point.equals(a) ? b : a;
    }
  }

  private record QueuePoint(Point point, double distance) {}

  private record ResolvedRoute(
      List<Point> vertices, Set<EdgeRef> edges, Set<Integer> stationIds, String error) {
    boolean valid() {
      return error == null;
    }
  }

  private record AppliedExtraction(int line, int branch, String destination) {}

  private record CopiedRoute(List<Point> vertices, List<Station> stations) {}

  private record NetworkRow(boolean line, int index, String label) {}

  private static final class Branch {
    String name;
    double y = 64;
    boolean visible = true;
    final List<Point> vertices = new ArrayList<>();
    final NavigableSet<Integer> breaks = new TreeSet<>();
    final List<Link> links = new ArrayList<>();
    final List<Integer> stationIds = new ArrayList<>();

    Branch(String n) {
      name = n;
    }
  }

  private static final class LineData {
    String company = "My Highway", line = "Planned Line", prefix = "", code = "", color = "ff55dd";
    boolean visible = true;
    List<Station> stations = new ArrayList<>();
    List<Branch> branches = new ArrayList<>();
    int branch;

    LineData() {
      branches.add(new Branch("Main"));
    }
  }

  private static final class Draft {
    String name,
        company = "My Highway",
        line = "Planned Line",
        prefix = "",
        code = "",
        color = "ff55dd";
    boolean showMarkerInfo = true;
    int coordinateMode;
    List<Station> stations = new ArrayList<>();
    List<Branch> branches = new ArrayList<>();
    int branch;
    final List<LineData> lines = new ArrayList<>();
    int activeLine;

    Draft(String n) {
      name = n;
      branches.add(new Branch("Main"));
      lines.add(captureLine(this));
    }
  }

  private static LineData captureLine(Draft draft) {
    LineData line = new LineData();
    line.company = draft.company;
    line.line = draft.line;
    line.prefix = draft.prefix;
    line.code = draft.code;
    line.color = draft.color;
    line.stations = draft.stations;
    line.branches = draft.branches;
    line.branch = draft.branch;
    return line;
  }

  private static void storeActiveLine(Draft draft) {
    if (draft.lines.isEmpty()) draft.lines.add(captureLine(draft));
    draft.activeLine = Math.clamp(draft.activeLine, 0, draft.lines.size() - 1);
    LineData line = draft.lines.get(draft.activeLine);
    line.company = draft.company;
    line.line = draft.line;
    line.prefix = draft.prefix;
    line.code = draft.code;
    line.color = draft.color;
    line.stations = draft.stations;
    line.branches = draft.branches;
    line.branch = draft.branch;
  }

  private static void activateLine(Draft draft, int index) {
    storeActiveLine(draft);
    bindLine(draft, index);
  }

  /** Selects an already-populated line without first writing the facade into it. */
  private static void bindLine(Draft draft, int index) {
    draft.activeLine = Math.clamp(index, 0, draft.lines.size() - 1);
    LineData line = draft.lines.get(draft.activeLine);
    draft.company = line.company;
    draft.line = line.line;
    draft.prefix = line.prefix;
    draft.code = line.code;
    draft.color = line.color;
    draft.stations = line.stations;
    draft.branches = line.branches;
    draft.branch = Math.clamp(line.branch, 0, Math.max(0, line.branches.size() - 1));
  }

  private IceRoadPlannerOverlay() {}

  public static boolean active() {
    return active;
  }

  public static void toggle() {
    ensureLoaded();
    active = !active;
    if (!active) {
      uiPressHandled = false;
      clearMarkerSelection();
    }
    else branchPanel = true;
  }

  private static void ensureLoaded() {
    if (loaded) return;
    loaded = true;
    loadLibrary();
    if (drafts.isEmpty()) drafts.add(new Draft("Draft 1"));
  }

  private static Draft draft() {
    ensureLoaded();
    draftIndex = Math.clamp(draftIndex, 0, drafts.size() - 1);
    return drafts.get(draftIndex);
  }

  private static Branch branch() {
    Draft d = draft();
    d.branch = Math.clamp(d.branch, 0, d.branches.size() - 1);
    return d.branches.get(d.branch);
  }

  public static void render(
      GuiGraphics g, double camX, double camZ, double scale, int sw, int sh) {
    if (!active || scale <= 0) return;
    viewCamX = camX;
    viewCamZ = camZ;
    viewScale = scale;
    viewWidth = sw;
    viewHeight = sh;
    viewTransformValid = true;
    if (colorPickerDragging) updateColorPickerFromMouse();
    tickAutosave();
    boolean export = RouteFinderMod.composingPlannerExport();
    if (!export) updateDragGesture();
    if (dragging && !export) updateDrag(camX, camZ, scale, sw, sh);
    Draft d = draft();
    storeActiveLine(d);
    List<SegmentHit> segments = new ArrayList<>();
    drawOtherLines(g, d, camX, camZ, scale, sw, sh, segments);
    boolean activeLineVisible = d.lines.get(d.activeLine).visible;
    boolean closeLod = export || scale >= .75;
    boolean mediumLod = export || scale >= .25;
    List<DragHit> hits = new ArrayList<>();
    for (int bi = 0; activeLineVisible && bi < d.branches.size(); bi++) {
      Branch b = d.branches.get(bi);
      if (!b.visible) continue;
      int color = lineArgb(d.color, bi == d.branch ? 0xFF : 0xAA);
      for (int i = 1; i < b.vertices.size(); i++) {
        if (b.breaks.contains(i)) continue;
        Point a = b.vertices.get(i - 1), c = b.vertices.get(i);
        int ax = sx(a.x, camX, scale, sw),
            ay = sy(a.z, camZ, scale, sh),
            cx = sx(c.x, camX, scale, sw),
            cy = sy(c.z, camZ, scale, sh);
        segments.add(new SegmentHit(d.activeLine, bi, i - 1, ax, ay, cx, cy));
        boolean selected = !export && bi == selectedSegmentBranch && i - 1 == selectedSegment;
        line(
            g,
            ax,
            ay,
            cx,
            cy,
            selected ? 0xFFFFFF55 : color,
            selected ? 4 : bi == d.branch ? 2 : 1);
      }
      for (int i = 0; i < b.vertices.size(); i++) {
        Point p = b.vertices.get(i);
        hits.add(new DragHit(-1, bi, i, sx(p.x, camX, scale, sw), sy(p.z, camZ, scale, sh)));
      }
    }
    if (activeLineVisible) drawPointLinks(g, d, camX, camZ, scale, sw, sh, segments);
    List<int[]> labelOccupied = new ArrayList<>();
    if (activeLineVisible) {
      drawPlannerPoints(
          g,
          hits,
          sw,
          sh,
          d.coordinateMode == 1 && closeLod ? topologyCoordinateKeysCached(d) : Set.of(),
          closeLod,
          mediumLod,
          labelOccupied);
      if (!export && d.coordinateMode != 4) drawRouteNames(g, d, camX, camZ, scale, sw, sh);
    }
    for (Station s : d.stations) {
      if (!activeLineVisible || !stationVisibleInLine(d, s.id)) continue;
      int x = sx(s.x, camX, scale, sw), y = sy(s.z, camZ, scale, sh);
      hits.add(new DragHit(s.id, -1, -1, x, y));
      if (!export && multiSelection.contains(new NodeRef(s.id, -1, -1))) {
        g.fill(x - 7, y - 7, x + 8, y - 5, 0xFFFFFF55);
        g.fill(x - 7, y + 6, x + 8, y + 8, 0xFFFFFF55);
        g.fill(x - 7, y - 5, x - 5, y + 6, 0xFFFFFF55);
        g.fill(x + 6, y - 5, x + 8, y + 6, 0xFFFFFF55);
      }
      IceRoadOverlay.drawStationSymbol(g, x, y, s.type, 14);
      if (d.coordinateMode != 4 && (export || mediumLod || s.id == selectedMarker))
        placePointLabel(
            g,
            x,
            y,
            stationLabel(d, s, export),
            s.id == selectedMarker || multiSelection.contains(new NodeRef(s.id, -1, -1))
                ? 0xFFFFD36A
                : 0xFFFFFFFF,
            labelOccupied,
            sw,
            sh);
    }
    drawMeasurement(g, camX, camZ, scale, sw, sh);
    dragHits = List.copyOf(hits);
    segmentHits = List.copyOf(segments);
    if (!RouteFinderMod.composingScreenshot()) {
      editorChrome(g, sw, sh);
      if (!multiSelection.isEmpty()) multiSelectionPanel(g, sw);
      else {
        markerPanel(g, sw);
        segmentPanel(g, sw);
        pointPanel(g, sw);
        branchPanel(g, sw);
      }
      namePanel(g, sw);
      colorPickerPanel(g, sw);
      creationHud(g, sw, sh);
    }
  }

  private static void drawOtherLines(
      GuiGraphics g,
      Draft d,
      double camX,
      double camZ,
      double scale,
      int sw,
      int sh,
      List<SegmentHit> segments) {
    for (int li = 0; li < d.lines.size(); li++) {
      if (li == d.activeLine) continue;
      LineData other = d.lines.get(li);
      if (!other.visible) continue;
      int color = lineArgb(other.color, 0xCC);
      for (int bi = 0; bi < other.branches.size(); bi++) {
        Branch branch = other.branches.get(bi);
        if (!branch.visible) continue;
        for (int i = 1; i < branch.vertices.size(); i++) {
          if (branch.breaks.contains(i)) continue;
          Point a = branch.vertices.get(i - 1), b = branch.vertices.get(i);
          int ax = sx(a.x, camX, scale, sw),
              ay = sy(a.z, camZ, scale, sh),
              bx = sx(b.x, camX, scale, sw),
              by = sy(b.z, camZ, scale, sh);
          segments.add(new SegmentHit(li, bi, i - 1, ax, ay, bx, by));
          line(
              g,
              ax,
              ay,
              bx,
              by,
              color,
              1);
        }
        for (int linkIndex = 0; linkIndex < branch.links.size(); linkIndex++) {
          Link link = branch.links.get(linkIndex);
          int ax = sx(link.a.x, camX, scale, sw),
              ay = sy(link.a.z, camZ, scale, sh),
              bx = sx(link.b.x, camX, scale, sw),
              by = sy(link.b.z, camZ, scale, sh);
          segments.add(new SegmentHit(li, bi, -linkIndex - 1, ax, ay, bx, by));
          line(
              g,
              ax,
              ay,
              bx,
              by,
              color,
              1);
        }
      }
    }
  }

  private static void label(GuiGraphics g, int x, int y, String text, int color) {
    g.fill(x + 8, y - 5, x + 12 + Minecraft.getInstance().font.width(text), y + 7, 0xCC071014);
    g.drawString(Minecraft.getInstance().font, text, x + 10, y - 3, color, false);
  }

  private static void drawRouteNames(
      GuiGraphics g, Draft d, double camX, double camZ, double scale, int sw, int sh) {
    for (Branch b : d.branches)
      if (b.visible && b.vertices.size() >= 2) {
        Point a = b.vertices.getFirst(), c = b.vertices.get(1);
        double x = (a.x + c.x) / 2, z = (a.z + c.z) / 2;
        label(
            g, sx(x, camX, scale, sw), sy(z, camZ, scale, sh), d.line + " · " + b.name, 0xFF8FD9FF);
      }
  }

  private static void drawPointLinks(
      GuiGraphics g, Draft d, double camX, double camZ, double scale, int sw, int sh,
      List<SegmentHit> segments) {
    for (int bi = 0; bi < d.branches.size(); bi++) {
      Branch b = d.branches.get(bi);
      if (!b.visible) continue;
      int color = lineArgb(d.color, bi == d.branch ? 0xFF : 0xAA);
      for (int linkIndex = 0; linkIndex < b.links.size(); linkIndex++) {
        Link link = b.links.get(linkIndex);
        int ax = sx(link.a.x, camX, scale, sw),
            ay = sy(link.a.z, camZ, scale, sh),
            bx = sx(link.b.x, camX, scale, sw),
            by = sy(link.b.z, camZ, scale, sh);
        segments.add(new SegmentHit(d.activeLine, bi, -linkIndex - 1, ax, ay, bx, by));
        line(
            g,
            ax,
            ay,
            bx,
            by,
            color,
            bi == d.branch ? 2 : 1);
      }
    }
  }

  private static boolean stationVisibleInLine(Draft d, int stationId) {
    boolean referenced = false;
    for (Branch branch : d.branches) {
      if (!branch.stationIds.contains(stationId)) continue;
      referenced = true;
      if (branch.visible) return true;
    }
    return !referenced;
  }

  private static void measure(Point point) {
    if (measureStart == null || measureEnd != null) {
      measureStart = point;
      measureEnd = null;
      notice("Measure start selected");
    } else {
      measureEnd = point;
      notice(
          "Distance: "
              + number(Math.hypot(point.x - measureStart.x, point.z - measureStart.z))
              + " blocks");
    }
  }

  private static void drawMeasurement(
      GuiGraphics g, double camX, double camZ, double scale, int sw, int sh) {
    if (measureStart == null) return;
    Point end = measureEnd;
    if (end == null) return;
    int ax = sx(measureStart.x, camX, scale, sw), ay = sy(measureStart.z, camZ, scale, sh);
    int bx = sx(end.x, camX, scale, sw), by = sy(end.z, camZ, scale, sh);
    line(g, ax, ay, bx, by, 0xFFFFFF55, 2);
    label(
        g,
        (ax + bx) / 2,
        (ay + by) / 2,
        number(Math.hypot(end.x - measureStart.x, end.z - measureStart.z)) + " blocks",
        0xFFFFFF77);
  }

  private static void creationHud(GuiGraphics g, int sw, int sh) {
    if (editorState.tool() != EditorTool.DRAW && !pointConnecting) return;
    Branch b = branch();
    double length = 0;
    for (int i = 1; i < b.vertices.size(); i++)
      if (!b.breaks.contains(i))
        length +=
            Math.hypot(
                b.vertices.get(i).x - b.vertices.get(i - 1).x,
                b.vertices.get(i).z - b.vertices.get(i - 1).z);
    int width = 330, x = Math.max(PROJECT_X + PROJECT_W + 8, sw / 2 - width / 2), y = sh - 66;
    g.fill(x, y, x + width, y + 28, 0xE80A1419);
    g.drawString(
        Minecraft.getInstance().font,
        "Drawing: " + draft().line + " / " + b.name + " | Y " + number(b.y),
        x + 8,
        y + 5,
        0xFFFFFFFF,
        false);
    g.drawString(
        Minecraft.getInstance().font,
        b.vertices.size() + " points | " + number(length) + " blocks | click: place | Esc: cancel",
        x + 8,
        y + 16,
        0xFF8FD9FF,
        false);
  }

  private static DragHit nearestHit(double x, double y) {
    if (newLinePending) return null;
    // Markers are drawn above vertices, so they must also win the hit test. Otherwise a station or
    // junction placed on its line point can never open its own editor/membership controls.
    for (int priority = 0; priority <= 1; priority++) {
      DragHit best = null;
      double distance = priority == 0 ? 100 : selectVertexHitDistance2(editorState.tool());
      for (DragHit hit : dragHits) {
        if (pointConnecting && hit.station >= 0) continue;
        if (hitPriority(hit.station >= 0) != priority) continue;
        double q = (hit.x - x) * (hit.x - x) + (hit.y - y) * (hit.y - y);
        if (q <= distance) {
          distance = q;
          best = hit;
        }
      }
      if (best != null) return best;
    }
    return null;
  }

  static int hitPriority(boolean marker) {
    return marker ? 0 : 1;
  }

  static double selectVertexHitDistance2(EditorTool tool) {
    return tool == EditorTool.SELECT ? 25 : 100;
  }

  static boolean shouldDragPoint(
      boolean editingPoint,
      int selectedBranch,
      int selectedVertex,
      boolean editingSegment,
      int segmentBranch,
      int segmentVertex,
      int hitBranch,
      int hitVertex) {
    return editingPoint && hitBranch == selectedBranch && hitVertex == selectedVertex
        || editingSegment
            && hitBranch == segmentBranch
            && (hitVertex == segmentVertex || hitVertex == segmentVertex + 1);
  }

  private static SegmentHit nearestSegment(double x, double y) {
    if (newLinePending || pointConnecting) return null;
    double distance =
        editorState.tool() == EditorTool.SPLIT
                || editorState.tool() == EditorTool.ADD_VERTEX
                || editorState.tool() == EditorTool.REMOVE
            ? 144
            : 100;
    return nearestSegment(segmentHits, x, y, distance);
  }

  private static SegmentHit nearestSegment(
      List<SegmentHit> candidates, double x, double y, double distance) {
    SegmentHit best = null;
    for (SegmentHit hit : candidates) {
      double q = screenSegmentDistance2(x, y, hit.ax, hit.ay, hit.bx, hit.by);
      if (q <= distance) {
        distance = q;
        best = hit;
      }
    }
    return best;
  }

  static int nearestSegmentLineForTest(double x, double y, List<double[]> candidates) {
    List<SegmentHit> hits = new ArrayList<>();
    for (double[] candidate : candidates)
      hits.add(
          new SegmentHit(
              (int) candidate[0],
              0,
              0,
              candidate[1],
              candidate[2],
              candidate[3],
              candidate[4]));
    SegmentHit hit = nearestSegment(hits, x, y, 100);
    return hit == null ? -1 : hit.line;
  }

  static int linkedSegmentHitForTest(double x, double y) {
    SegmentHit hit =
        nearestSegment(List.of(new SegmentHit(0, 0, -1, 0, 0, 100, 0)), x, y, 100);
    return hit == null ? 0 : hit.segment;
  }

  private static double screenSegmentDistance2(
      double x, double y, double ax, double ay, double bx, double by) {
    double dx = bx - ax,
        dy = by - ay,
        q = dx * dx + dy * dy,
        t = q == 0 ? 0 : Math.clamp(((x - ax) * dx + (y - ay) * dy) / q, 0, 1),
        px = ax + t * dx,
        py = ay + t * dy;
    return (x - px) * (x - px) + (y - py) * (y - py);
  }

  private static void updateDrag(double camX, double camZ, double scale, int sw, int sh) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    if (!dragUndoRecorded) {
      checkpoint();
      dragUndoRecorded = true;
    }
    double[] world =
        worldFromScreen(
            mc.mouseHandler.getScaledXPos(mc.getWindow()),
            mc.mouseHandler.getScaledYPos(mc.getWindow()),
            camX,
            camZ,
            scale,
            sw,
            sh);
    double worldX = world[0], worldZ = world[1];
    Draft d = draft();
    if (dragStation >= 0) {
      Station s = markerById(d, dragStation);
      if (s != null)
        replaceMarkerEverywhere(
            d,
            s,
            new Station(
                s.id, s.name, s.type, blockCenter(worldX), s.y, blockCenter(worldZ)),
            true);
    } else if (dragBranch >= 0 && dragBranch < d.branches.size()) {
      Branch b = d.branches.get(dragBranch);
      if (dragVertex >= 0 && dragVertex < b.vertices.size()) {
        Point old = b.vertices.get(dragVertex);
        replaceSharedPointEverywhere(d, old, blockCenter(worldX), blockCenter(worldZ), null);
      }
    }
  }

  /** Coordinate identity is the website format's portable identity for a shared vertex. */
  private static void replaceSharedPointEverywhere(
      Draft d, Point old, double x, double z, Double explicitY) {
    storeActiveLine(d);
    for (LineData line : d.lines) {
      for (Branch b : line.branches) {
        for (int i = 0; i < b.vertices.size(); i++) {
          Point point = b.vertices.get(i);
          if (point.equals(old))
            b.vertices.set(i, new Point(x, z, explicitY == null ? point.y : explicitY));
        }
        for (int i = 0; i < b.links.size(); i++) {
          Link link = b.links.get(i);
          Point a = movedSharedEndpoint(link.a, old, x, z, explicitY);
          Point c = movedSharedEndpoint(link.b, old, x, z, explicitY);
          if (a != link.a || c != link.b) b.links.set(i, new Link(a, c));
        }
      }
    }
  }

  private static Point movedSharedEndpoint(
      Point point, Point old, double x, double z, Double explicitY) {
    return point.equals(old)
        ? new Point(x, z, explicitY == null ? point.y : explicitY)
        : point;
  }

  public static boolean release() {
    if (colorPickerDragging) {
      uiPressHandled = false;
      colorPickerDragging = false;
      colorPickerUndoRecorded = false;
      colorPickerDragArea = 0;
      saveLibraryQuiet();
      return true;
    }
    Minecraft mc = Minecraft.getInstance();
    double mx = mc == null ? gestureStartX : mc.mouseHandler.getScaledXPos(mc.getWindow()),
        my = mc == null ? gestureStartY : mc.mouseHandler.getScaledYPos(mc.getWindow());
    if (dragging) {
      uiPressHandled = false;
      dragging = false;
      dragArmed = false;
      dragUndoRecorded = false;
      dragStation = dragBranch = dragVertex = -1;
      saveLibraryQuiet();
      return true;
    }
    if (dragArmed) {
      dragArmed = false;
      dragStation = dragBranch = dragVertex = -1;
      return true;
    }
    if (!shouldFallbackToUiRelease(uiPressHandled)) {
      uiPressHandled = false;
      return true;
    }
    if (active && mc != null && clickPlannerUi(mx, my, mc.getWindow().getGuiScaledWidth()))
      return true;
    if (!pendingPlacement) return false;
    pendingPlacement = false;
    if (movedBeyondClickThreshold(gestureStartX, gestureStartY, mx, my)) return false;
    double[] releaseWorld = currentWorldAt(mx, my, pendingWorldX, pendingWorldZ);
    Point p = snapPoint(draft(), branch(), releaseWorld[0], releaseWorld[1]);
    if (pointConnecting) finishPointConnection(p);
    else place(p.x, p.z);
    return shouldConsumeRelease(false);
  }

  private static void armPlacement(double mx, double my, double worldX, double worldZ) {
    dragArmed = false;
    pendingPlacement = true;
    gestureStartX = mx;
    gestureStartY = my;
    pendingWorldX = worldX;
    pendingWorldZ = worldZ;
  }

  private static void armDrag(DragHit hit, double mx, double my) {
    pendingPlacement = false;
    dragArmed = true;
    dragging = false;
    dragUndoRecorded = false;
    gestureStartX = mx;
    gestureStartY = my;
    dragStation = hit.station;
    dragBranch = hit.branch;
    dragVertex = hit.vertex;
  }

  private static void updateDragGesture() {
    if (!dragArmed || dragging) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()),
        my = mc.mouseHandler.getScaledYPos(mc.getWindow());
    if (movedBeyondClickThreshold(gestureStartX, gestureStartY, mx, my)) {
      dragging = true;
      dragArmed = false;
    }
  }

  static boolean movedBeyondClickThreshold(double startX, double startY, double x, double y) {
    double dx = x - startX, dy = y - startY;
    return dx * dx + dy * dy > 16;
  }

  static boolean shouldConsumeRelease(boolean plannerObjectGesture) {
    return plannerObjectGesture;
  }

  static double[] worldFromScreen(
      double screenX,
      double screenY,
      double camX,
      double camZ,
      double scale,
      int width,
      int height) {
    return new double[] {
      camX + (screenX - width / 2.0) / scale,
      camZ + (screenY - height / 2.0) / scale
    };
  }

  private static double[] currentWorldAt(
      double screenX, double screenY, double fallbackX, double fallbackZ) {
    return viewTransformValid
        ? worldFromScreen(
            screenX, screenY, viewCamX, viewCamZ, viewScale, viewWidth, viewHeight)
        : new double[] {fallbackX, fallbackZ};
  }

  static boolean directDragTool(EditorTool tool) {
    return tool == EditorTool.SELECT
        || tool == EditorTool.DRAW
        || tool == EditorTool.ADD_VERTEX
        || tool == EditorTool.MARKER;
  }

  public static boolean click(double mx, double my, double worldX, double worldZ, int sw) {
    if (!active) return false;
    if (clickPlannerUi(mx, my, sw)) {
      uiPressHandled = true;
      return true;
    }
    int x = sw - W - 8, y = 8, py = (int) my - y;
    if (draftDropdown) {
      int row = (py - 46) / ROW, index = firstVisibleDraft() + row;
      if (mx >= x + 8
          && mx <= x + W - 8
          && py >= 46
          && row >= 0
          && row < visibleDraftCount()
          && index < drafts.size()) {
        saveLibraryQuiet();
        draftIndex = index;
        draftDropdown = false;
        clearMarkerSelection();
        notice("Opened " + draft().name);
        return true;
      }
      draftDropdown = false;
    }
    if (toolDropdown) {
      int chosen = (py - 94) / ROW;
      if (mx >= x + 8 && mx <= x + W - 8 && py >= 94 && chosen >= 0 && chosen < TYPES.length) {
        tool = chosen;
        toolDropdown = false;
        return true;
      }
      toolDropdown = false;
    }
    if (false && mx >= x && mx <= x + W && my >= y && my <= y + panelHeight()) {
      if (py >= 26 && py < 46) draftDropdown = !draftDropdown;
      else if (py >= 50 && py < 70) newDraft();
      else if (py >= 74 && py < 94) toolDropdown = !toolDropdown;
      else if (py >= 100 && py < 120) coordinateField = 1;
      else if (py >= 124 && py < 144) coordinateField = 2;
      else if (py >= 148 && py < 168) coordinateField = 3;
      else if (py >= 172 && py < 192) placeCoordinates();
      else if (py >= 196 && py < 216) placeAtPlayer();
      else if (py >= 220 && py < 240) {
        checkpoint();
        draft().showMarkerInfo = !draft().showMarkerInfo;
        syncCoordinateMode(draft());
        saveLibraryQuiet();
      } else if (py >= 254 && py < 254 + visibleBranchCount() * ROW) {
        int index = firstVisibleBranch() + (py - 254) / ROW;
        if (index < draft().branches.size()) {
          draft().branch = index;
          clearMarkerSelection();
          branchPanel = true;
        }
      } else {
        int actions = 254 + visibleBranchCount() * ROW;
        if (py >= actions && py < actions + 20) newBranch();
        else if (py >= actions + 24 && py < actions + 44) deleteBranch();
        else if (py >= actions + 48 && py < actions + 68) undo();
        else if (py >= actions + 72 && py < actions + 92) redo();
        else if (py >= actions + 96 && py < actions + 116) saveLibrary();
        else if (py >= actions + 120 && py < actions + 140) export();
        else if (py >= actions + 144 && py < actions + 164) exportImage();
        else if (py >= actions + 168 && py < actions + 188) deleteDraft();
      }
      return true;
    }
    coordinateField = 0;
    DragHit hit = nearestHit(mx, my);
    if (hit != null) {
      if (editorState.tool() == EditorTool.MULTI_SELECT) {
        toggleMultiSelection(hit);
        return true;
      }
      if (editorState.tool() == EditorTool.REMOVE) {
        if (hit.station >= 0) {
          selectMarker(hit.station);
          deleteSelectedMarker();
        } else {
          selectPoint(hit);
          deleteSelectedPoint();
        }
        return true;
      }
      if (hit.station >= 0) {
        if (selectedMarker != hit.station) selectMarker(hit.station);
        if (editorState.tool() == EditorTool.SPLIT) {
          splitAtSelectedMarker();
          return true;
        }
        if (directDragTool(editorState.tool())) armDrag(hit, mx, my);
        return true;
      } else if (pointConnecting) {
        connectExistingPoint(hit);
        return true;
      } else {
        if (editorState.tool() == EditorTool.SPLIT) {
          splitBranchAt(draft(), hit.branch, hit.vertex);
          return true;
        }
        if (directDragTool(editorState.tool())
            && shouldDragPoint(
                pointEditing,
                selectedPointBranch,
                selectedPoint,
                segmentEditing,
                selectedSegmentBranch,
                selectedSegment,
                hit.branch,
                hit.vertex)) {
          armDrag(hit, mx, my);
          return true;
        }
        selectPoint(hit);
        if (directDragTool(editorState.tool())) armDrag(hit, mx, my);
        return true;
      }
    }
    SegmentHit segment = nearestSegment(mx, my);
    if (segment != null) {
      selectSegment(segment, mx, my);
      if (editorState.tool() == EditorTool.ADD_VERTEX) addPointToSelectedSegment();
      else if (editorState.tool() == EditorTool.SPLIT) separateSelectedBranch();
      else if (editorState.tool() == EditorTool.REMOVE) removeSelectedSegment();
      return true;
    }
    if (editorState.tool() == EditorTool.SELECT) return false;
    if (editorState.tool() == EditorTool.MEASURE) {
      double[] world = currentWorldAt(mx, my, worldX, worldZ);
      measure(new Point(blockCenter(world[0]), blockCenter(world[1])));
      return true;
    }
    if (editorState.tool() == EditorTool.ADD_VERTEX
        || editorState.tool() == EditorTool.SPLIT
        || editorState.tool() == EditorTool.REMOVE
        || editorState.tool() == EditorTool.MULTI_SELECT) return false;
    if (editorState.tool() == EditorTool.MARKER && tool == 0) tool = 1;
    else if (editorState.tool() == EditorTool.DRAW || editorState.tool() == EditorTool.ADD_VERTEX)
      tool = 0;
    double[] world = currentWorldAt(mx, my, worldX, worldZ);
    armPlacement(mx, my, world[0], world[1]);
    return false;
  }

  private static boolean clickPlannerUi(double mx, double my, int sw) {
    // The route panel is rendered as a modal and therefore receives input before every other panel.
    if (!multiSelection.isEmpty() && clickMultiSelectionPanel(mx, my, sw)) return true;
    if (clickColorPicker(mx, my, sw)) return true;
    if (clickEditorChrome(mx, my, sw)) return true;
    return clickMarkerPanel(mx, my, sw);
  }

  static boolean shouldFallbackToUiRelease(boolean handledOnPress) {
    return !handledOnPress;
  }

  private static void panel(GuiGraphics g, int sw) {
    Draft d = draft();
    int x = sw - W - 8, y = 8;
    g.fill(x, y, x + W, y + panelHeight(), 0xF20A1419);
    g.drawString(Minecraft.getInstance().font, "Ice Highway Editor", x + 8, y + 8, 0xFFFFFFFF, false);
    button(g, x + 8, y + 26, W - 16, "Save: " + d.name + " ▼");
    button(g, x + 8, y + 50, W - 16, "New draft (" + drafts.size() + " saved)");
    button(g, x + 8, y + 74, W - 16, "Tool: " + LABELS[tool] + " ▼");
    field(g, x + 8, y + 100, W - 16, "X", coordinateX, coordinateField == 1);
    field(g, x + 8, y + 124, W - 16, "Y / road height", coordinateY, coordinateField == 2);
    field(g, x + 8, y + 148, W - 16, "Z", coordinateZ, coordinateField == 3);
    button(g, x + 8, y + 172, W - 16, "Place exact coordinates");
    button(g, x + 8, y + 196, W - 16, "Place at current position");
    button(g, x + 8, y + 220, W - 16, "Coordinates: " + COORDINATE_MODES[d.coordinateMode]);
    g.drawString(
        Minecraft.getInstance().font,
        "Drag markers/vertices · line drag snaps",
        x + 8,
        y + 245,
        0xFF8FD9FF,
        false);
    int by = y + 254, start = firstVisibleBranch();
    for (int row = 0; row < visibleBranchCount(); row++) {
      int i = start + row;
      Branch b = d.branches.get(i);
      button(
          g,
          x + 8,
          by + row * ROW,
          W - 16,
          (i == d.branch ? "> " : "  ")
              + b.name
              + "  · Y"
              + number(b.y)
              + " · "
              + b.vertices.size()
              + " pts");
    }
    int ay = by + visibleBranchCount() * ROW;
    button(g, x + 8, ay, W - 16, "+ New branch");
    button(g, x + 8, ay + 24, W - 16, "Delete selected branch");
    button(g, x + 8, ay + 48, W - 16, "Undo last point");
    button(g, x + 8, ay + 72, W - 16, "Save this draft");
    button(g, x + 8, ay + 96, W - 16, "Export website JSON");
    button(g, x + 8, ay + 120, W - 16, "Export map image");
    button(g, x + 8, ay + 144, W - 16, "Delete this draft");
    if (toolDropdown)
      for (int i = 0; i < TYPES.length; i++)
        button(g, x + 8, y + 94 + i * ROW, W - 16, (i == tool ? "> " : "  ") + LABELS[i]);
    if (draftDropdown) {
      int startDraft = firstVisibleDraft();
      for (int row = 0; row < visibleDraftCount(); row++) {
        int i = startDraft + row;
        button(
            g,
            x + 8,
            y + 46 + row * ROW,
            W - 16,
            (i == draftIndex ? "> " : "  ") + drafts.get(i).name);
      }
    }
    if (System.currentTimeMillis() < feedbackUntil)
      g.drawString(
          Minecraft.getInstance().font, feedback, x + 8, y + panelHeight() + 4, 0xFFFFFF77, false);
  }

  private static void actionOverlay(GuiGraphics g, int sw) {
    int x = sw - W - 8, y = 8 + 254 + visibleBranchCount() * ROW + 48;
    g.fill(x + 8, y, x + W - 8, y + 140, 0xFF0A1419);
    button(g, x + 8, y, W - 16, "Undo last change");
    button(g, x + 8, y + 24, W - 16, "Redo last change");
    button(g, x + 8, y + 48, W - 16, "Save this draft");
    button(g, x + 8, y + 72, W - 16, "Export website JSON");
    button(g, x + 8, y + 96, W - 16, "Export map image");
    button(g, x + 8, y + 120, W - 16, "Delete this draft");
  }

  private static int visibleBranchCount() {
    return Math.min(MAX_BRANCH_ROWS, draft().branches.size());
  }

  private static int firstVisibleBranch() {
    Draft d = draft();
    return Math.clamp(
        d.branch - MAX_BRANCH_ROWS + 1, 0, Math.max(0, d.branches.size() - MAX_BRANCH_ROWS));
  }

  private static int panelHeight() {
    return 254 + visibleBranchCount() * ROW + 192;
  }

  private static int visibleDraftCount() {
    return Math.min(8, drafts.size());
  }

  private static int firstVisibleDraft() {
    return Math.clamp(draftIndex - 7, 0, Math.max(0, drafts.size() - 8));
  }

  private static void button(GuiGraphics g, int x, int y, int w, String text) {
    g.fill(x, y, x + w, y + 18, 0xFF20343D);
    g.fill(x, y + 16, x + w, y + 18, 0xFF4E7180);
    g.drawCenteredString(Minecraft.getInstance().font, text, x + w / 2, y + 5, 0xFFFFFFFF);
  }

  private static void field(
      GuiGraphics g, int x, int y, int w, String label, String value, boolean focused) {
    g.fill(x, y, x + w, y + 18, focused ? 0xFF315E70 : 0xFF17262D);
    g.fill(x, y + 16, x + w, y + 18, focused ? 0xFF7EDCF2 : 0xFF4E7180);
    g.drawString(
        Minecraft.getInstance().font,
        label + ": " + value + (focused ? "_" : ""),
        x + 6,
        y + 5,
        0xFFFFFFFF,
        false);
  }

  private static boolean handleEditorShortcut(int key) {
    Minecraft mc = Minecraft.getInstance();
    long window = mc == null ? 0 : mc.getWindow().handle();
    boolean
        ctrl =
            window != 0
                && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS),
        shift =
            window != 0
                && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
    if (ctrl && key == GLFW.GLFW_KEY_S) {
      saveLibrary();
      return true;
    }
    if (ctrl && key == GLFW.GLFW_KEY_Z) {
      if (shift) redo();
      else undo();
      return true;
    }
    if (ctrl && key == GLFW.GLFW_KEY_C && !multiSelection.isEmpty()) {
      copyMultiSelection();
      return true;
    }
    if (ctrl && key == GLFW.GLFW_KEY_V && copiedRoute != null) {
      pasteCopiedRoute();
      return true;
    }
    if (coordinateField != 0 || nameEditing != 0) return false;
    if (key == GLFW.GLFW_KEY_V) {
      activateTool(EditorTool.SELECT);
      return true;
    }
    if (key == GLFW.GLFW_KEY_L) {
      activateTool(EditorTool.DRAW);
      return true;
    }
    if (key == GLFW.GLFW_KEY_A) {
      activateTool(EditorTool.ADD_VERTEX);
      return true;
    }
    if (key == GLFW.GLFW_KEY_S) {
      activateTool(EditorTool.SPLIT);
      return true;
    }
    if (key == GLFW.GLFW_KEY_M) {
      activateTool(EditorTool.MARKER);
      if (tool == 0) tool = 1;
      return true;
    }
    if (key == GLFW.GLFW_KEY_B) {
      activateTool(EditorTool.MULTI_SELECT);
      return true;
    }
    if (key == GLFW.GLFW_KEY_R) {
      activateTool(EditorTool.MEASURE);
      return true;
    }
    if (key == GLFW.GLFW_KEY_DELETE) {
      if (selectedStation() != null) deleteSelectedMarker();
      else if (validSelectedPoint()) deleteSelectedPoint();
      else if (validSelectedSegment()) removeSelectedSegment();
      return true;
    }
    if (key == GLFW.GLFW_KEY_ESCAPE) {
      cancelPointConnection();
      clearMarkerSelection();
      measureStart = measureEnd = null;
      exportMenu = snapMenu = draftDropdown = validationPanel = markerListDropdown = toolDropdown = false;
      colorPicker = colorPickerDragging = false;
      editorState.resetTransientState();
      return true;
    }
    return false;
  }

  private static void activateTool(EditorTool next) {
    pendingPlacement = false;
    newLinePending = false;
    pointConnecting = false;
    connectionStart = null;
    dragArmed = false;
    dragging = false;
    dragStation = dragBranch = dragVertex = -1;
    if (next != EditorTool.MEASURE) measureStart = measureEnd = null;
    if (next != EditorTool.MULTI_SELECT) {
      multiSelection.clear();
      extractionMessage = "";
    }
    editorState.activate(next);
  }

  public static boolean keyPressed(int key) {
    if (!active) return false;
    if (handleEditorShortcut(key)) return true;
    if (pointConnecting && key == GLFW.GLFW_KEY_ESCAPE) {
      cancelPointConnection();
      return true;
    }
    if (nameEditing != 0) {
      if (key == GLFW.GLFW_KEY_ESCAPE) cancelNameEdit();
      else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) applyNameEdit();
      else if (key == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty())
        nameInput = nameInput.substring(0, nameInput.length() - 1);
      return true;
    }
    if (coordinateField == 0) return false;
    if (key == GLFW.GLFW_KEY_ESCAPE) {
      coordinateField = 0;
      return true;
    }
    if (key == GLFW.GLFW_KEY_TAB) {
      coordinateField = coordinateField % 3 + 1;
      return true;
    }
    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
      placeCoordinates();
      return true;
    }
    if (key == GLFW.GLFW_KEY_BACKSPACE) {
      if (coordinateField == 1 && !coordinateX.isEmpty())
        coordinateX = coordinateX.substring(0, coordinateX.length() - 1);
      if (coordinateField == 2 && !coordinateY.isEmpty())
        coordinateY = coordinateY.substring(0, coordinateY.length() - 1);
      if (coordinateField == 3 && !coordinateZ.isEmpty())
        coordinateZ = coordinateZ.substring(0, coordinateZ.length() - 1);
      return true;
    }
    return true;
  }

  public static boolean charTyped(char c) {
    if (!active) return false;
    if (nameEditing != 0) {
      if (!Character.isISOControl(c) && nameInput.length() < 40) nameInput += c;
      return true;
    }
    if (coordinateField == 0) return false;
    if ((c >= '0' && c <= '9') || c == '-' || c == '.') {
      if (coordinateField == 1 && coordinateX.length() < 16) coordinateX += c;
      if (coordinateField == 2 && coordinateY.length() < 16) coordinateY += c;
      if (coordinateField == 3 && coordinateZ.length() < 16) coordinateZ += c;
    }
    return true;
  }

  private static void markerPanel(GuiGraphics g, int sw) {
    Station s = selectedStation();
    if (s == null) return;
    int x = markerPanelX(sw), y = 8;
    g.fill(x, y, x + W, y + 194, 0xF20A1419);
    g.drawString(Minecraft.getInstance().font, "Selected marker", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(
        Minecraft.getInstance().font,
        s.name + " · " + labelForType(s.type),
        x + 8,
        y + 24,
        0xFFFFD36A,
        false);
    g.drawString(
        Minecraft.getInstance().font,
        "XYZ " + number(s.x) + ", " + number(s.y) + ", " + number(s.z),
        x + 8,
        y + 38,
        0xFFB9DDEB,
        false);
    button(g, x + 8, y + 54, W - 16, markerEditing ? "Finish editing" : "Edit marker");
    button(g, x + 8, y + 78, W - 16, "Apply X / Y / Z fields");
    button(g, x + 8, y + 102, W - 16, "Rename marker");
    button(g, x + 8, y + 126, W - 16, "Line / branch memberships");
    button(g, x + 8, y + 150, W - 16, "Delete marker");
    button(g, x + 8, y + 174, W - 16, "Close");
    if (markerMembershipPanel) drawMarkerMembershipPanel(g, sw, s);
  }

  private static void multiSelectionPanel(GuiGraphics g, int sw) {
    int x = markerPanelX(sw), y = 8;
    int messageHeight = extractionMessage.isEmpty() ? 0 : 28;
    g.fill(x, y, x + W, y + 184 + messageHeight, 0xF20A1419);
    g.drawString(Minecraft.getInstance().font, "Extract selected route", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(
        Minecraft.getInstance().font,
        multiSelection.size() + " points/markers selected in click order",
        x + 8,
        y + 26,
        0xFFFFD36A,
        false);
    button(g, x + 8, y + 46, W - 16, "Move into new line");
    button(g, x + 8, y + 70, W - 16, "Move into new branch");
    button(g, x + 8, y + 94, W - 16, "Clear selection");
    button(g, x + 8, y + 118, W - 16, "Cancel");
    button(g, x + 8, y + 142, W - 16, "Copy nodes / markers");
    button(
        g,
        x + 8,
        y + 166,
        W - 16,
        copiedRoute == null ? "Paste (clipboard empty)" : "Paste into selected branch");
    if (!extractionMessage.isEmpty())
      g.drawString(Minecraft.getInstance().font, extractionMessage, x + 8, y + 190, 0xFFFF7777, false);
  }

  private static boolean clickMultiSelectionPanel(double mx, double my, int sw) {
    if (multiSelection.isEmpty()) return false;
    int x = markerPanelX(sw), y = 8;
    int height = extractionMessage.isEmpty() ? 184 : 212;
    if (mx < x || mx > x + W || my < y || my > y + height) return false;
    int action = multiSelectionActionAt((int) my - y);
    if (action == 1) extractFromMultiSelection(false);
    else if (action == 2) extractFromMultiSelection(true);
    else if (action == 3) {
      multiSelection.clear();
      extractionMessage = "";
      notice("Selection cleared");
    } else if (action == 4) activateTool(EditorTool.SELECT);
    else if (action == 5) copyMultiSelection();
    else if (action == 6) pasteCopiedRoute();
    return true;
  }

  static int multiSelectionActionAt(int panelY) {
    if (panelY >= 46 && panelY < 66) return 1;
    if (panelY >= 70 && panelY < 90) return 2;
    if (panelY >= 94 && panelY < 114) return 3;
    if (panelY >= 118 && panelY < 136) return 4;
    if (panelY >= 142 && panelY < 162) return 5;
    if (panelY >= 166 && panelY < 184) return 6;
    return 0;
  }

  private static void copyMultiSelection() {
    Draft d = draft();
    storeActiveLine(d);
    CopiedRoute copy = copySelectedItems(d, multiSelection);
    if (copy == null) {
      extractionMessage = "Could not resolve one of the selected nodes or markers.";
      notice(extractionMessage);
      return;
    }
    copiedRoute = copy;
    extractionMessage = "Copied " + copy.vertices.size() + " node(s) and " + copy.stations.size() + " marker(s).";
    notice(extractionMessage + " Select a destination line/branch, then paste.");
  }

  private static CopiedRoute copySelectedItems(Draft d, List<NodeRef> selection) {
    List<Point> vertices = new ArrayList<>();
    List<Station> stations = new ArrayList<>();
    for (NodeRef ref : selection) {
      if (ref.station >= 0) {
        Station station = null;
        for (LineData line : d.lines)
          for (Station candidate : line.stations)
            if (candidate.id == ref.station) station = candidate;
        if (station == null) return null;
        Station selected = station;
        if (stations.stream().noneMatch(existing -> existing.id == selected.id)) stations.add(selected);
      } else {
        Point point = resolveNode(d, ref);
        if (point == null) return null;
        if (!vertices.contains(point)) vertices.add(point);
      }
    }
    return new CopiedRoute(List.copyOf(vertices), List.copyOf(stations));
  }

  private static void pasteCopiedRoute() {
    if (copiedRoute == null || copiedRoute.vertices.isEmpty() && copiedRoute.stations.isEmpty()) {
      extractionMessage = "Copy nodes or markers first.";
      notice(extractionMessage);
      return;
    }
    Draft d = draft();
    storeActiveLine(d);
    if (d.branches.isEmpty()) d.branches.add(new Branch("Main"));
    d.branch = Math.clamp(d.branch, 0, d.branches.size() - 1);
    Branch destination = d.branches.get(d.branch);
    checkpoint();
    applyCopiedRoute(d, destination, copiedRoute);
    storeActiveLine(d);
    saveLibraryQuiet();
    extractionMessage =
        "Pasted " + copiedRoute.vertices.size() + " node(s) and "
            + copiedRoute.stations.size() + " marker(s) into " + d.line + " / " + destination.name + ".";
    notice(extractionMessage);
  }

  private static void applyCopiedRoute(Draft d, Branch destination, CopiedRoute route) {
    for (Point point : route.vertices)
      if (!destination.vertices.contains(point)) addStandalonePoint(destination, point);
    for (Station station : route.stations) {
      if (d.stations.stream().noneMatch(existing -> existing.id == station.id)) d.stations.add(station);
      if (!destination.stationIds.contains(station.id)) destination.stationIds.add(station.id);
    }
  }

  static JsonObject copiedRouteAcrossLinesForTest() {
    Draft d = new Draft("Copy test");
    Branch source = d.branches.getFirst();
    source.vertices.addAll(
        List.of(new Point(0.5, 0.5, 50), new Point(1.5, 0.5, 51), new Point(2.5, 0.5, 52)));
    Station marker = new Station(9, "Shared station", "station", 1.5, 51, 0.5);
    d.stations.add(marker);
    source.stationIds.add(marker.id);
    storeActiveLine(d);
    CopiedRoute clipboard =
        copySelectedItems(
            d, List.of(new NodeRef(-1, 0, 0), new NodeRef(-1, 0, 2), new NodeRef(9, -1, -1)));
    LineData second = new LineData();
    second.line = "Destination";
    second.branches.getFirst().name = "Copied branch";
    d.lines.add(second);
    bindLine(d, 1);
    applyCopiedRoute(d, d.branches.getFirst(), clipboard);
    storeActiveLine(d);
    JsonObject result = new JsonObject();
    result.addProperty("sourceVertices", d.lines.getFirst().branches.getFirst().vertices.size());
    result.addProperty("destinationVertices", d.branches.getFirst().vertices.size());
    result.addProperty("destinationMarkers", d.branches.getFirst().stationIds.size());
    result.addProperty("secondY", d.branches.getFirst().vertices.get(1).y);
    result.addProperty("breaks", d.branches.getFirst().breaks.size());
    result.addProperty("links", d.branches.getFirst().links.size());
    result.add("export", websiteJson(d, false));
    return result;
  }

  private static void toggleMultiSelection(DragHit hit) {
    extractionMessage = "";
    NodeRef ref =
        hit.station >= 0
            ? new NodeRef(hit.station, -1, -1)
            : new NodeRef(-1, hit.branch, hit.vertex);
    if (multiSelection.remove(ref)) notice("Removed from branch selection");
    else {
      multiSelection.add(ref);
      notice("Selected " + multiSelection.size() + " branch nodes");
    }
  }

  private static Point resolveNode(NodeRef ref) {
    return resolveNode(draft(), ref);
  }

  private static Point resolveNode(Draft d, NodeRef ref) {
    if (ref.station >= 0) {
      Station selected = null;
      for (Station station : d.stations) if (station.id == ref.station) selected = station;
      if (selected == null) return null;
      Point nearest = null;
      Branch nearestOwner = null;
      double best = Double.POSITIVE_INFINITY;
      // Prefer an explicit marker membership. Older/imported files may have a marker listed at
      // line level without a branch membership, so fall back to the closest graph vertex below.
      for (Branch candidate : d.branches) {
        if (!candidate.stationIds.contains(ref.station)) continue;
        for (Point point : candidate.vertices) {
          double distance = distance2(selected.x, selected.z, point.x, point.z);
          if (distance < best) {
            best = distance;
            nearest = point;
            nearestOwner = candidate;
          }
        }
      }
      if (nearest == null) {
        for (Branch candidate : d.branches) {
          for (Point point : candidate.vertices) {
            double distance = distance2(selected.x, selected.z, point.x, point.z);
            if (distance < best) {
              best = distance;
              nearest = point;
              nearestOwner = candidate;
            }
          }
        }
      }
      if (nearest != null)
        return new Point(nearest.x, nearest.z, pointY(nearest, nearestOwner));
      return new Point(selected.x, selected.z, selected.y);
    }
    if (ref.branch < 0 || ref.branch >= d.branches.size()) return null;
    Branch owner = d.branches.get(ref.branch);
    if (ref.vertex < 0 || ref.vertex >= owner.vertices.size()) return null;
    Point point = owner.vertices.get(ref.vertex);
    return new Point(point.x, point.z, pointY(point, owner));
  }

  static double[] markerRouteNodeForTest() {
    Draft draft = new Draft("Marker route node");
    Branch branch = draft.branches.getFirst();
    branch.vertices.add(new Point(10.5, 20.5, 50));
    branch.stationIds.add(7);
    draft.stations.add(new Station(7, "Offset marker", "station", 200.5, 64, 300.5));
    Point resolved = resolveNode(draft, new NodeRef(7, -1, -1));
    return new double[] {resolved.x, resolved.y, resolved.z};
  }

  private static boolean extractionInProgress;

  private static void extractFromMultiSelection(boolean intoNewBranch) {
    if (extractionInProgress) return;
    extractionInProgress = true;
    try {
      executeRouteExtraction(intoNewBranch);
    } finally {
      extractionInProgress = false;
    }
  }

  private static void executeRouteExtraction(boolean intoNewBranch) {
    Draft d = draft();
    storeActiveLine(d);
    ResolvedRoute route = resolveSelectedRoute(d, multiSelection);
    if (!route.valid()) {
      extractionMessage = route.error;
      notice(route.error);
      return;
    }
    JsonObject before = libraryJson().deepCopy();
    checkpoint();
    try {
      AppliedExtraction applied = applyResolvedRoute(d, route, intoNewBranch);
      multiSelection.clear();
      extractionMessage = "";
      activateTool(EditorTool.SELECT);
      d = draft();
      d.branch = applied.branch;
      selectPoint(new DragHit(-1, applied.branch, 0, 0, 0));
      branchPanel = true;
      saveLibraryQuiet();
      notice("Moved " + route.edges.size() + " existing segment(s) into " + applied.destination);
    } catch (RuntimeException exception) {
      if (!undoHistory.isEmpty()) undoHistory.removeLast();
      restoreSnapshot(before);
      extractionMessage = "Route extraction failed; original topology restored.";
      notice(extractionMessage);
    }
  }

  private static AppliedExtraction applyResolvedRoute(
      Draft d, ResolvedRoute route, boolean intoNewBranch) {
    d.branch = Math.clamp(d.branch, 0, d.branches.size() - 1);
    String sourceName = d.branches.get(d.branch).name;
    boolean sourceVisible = d.branches.get(d.branch).visible;
    removeExtractedEdges(d, route.edges, route.stationIds);
    Branch created =
        new Branch(intoNewBranch ? uniqueBranchName(d, sourceName + " Split") : "Main");
    created.visible = sourceVisible;
    created.y = route.vertices.getFirst().y;
    created.vertices.addAll(route.vertices);
    created.stationIds.addAll(route.stationIds);
    List<Station> movedStations =
        d.stations.stream().filter(s -> route.stationIds.contains(s.id)).toList();
    int destinationLine, destinationBranch;
    String destination;
    if (intoNewBranch) {
      d.branches.add(created);
      destinationBranch = d.branches.size() - 1;
      d.branch = destinationBranch;
      destinationLine = d.activeLine;
      destination = "new branch " + created.name;
    } else {
      LineData extracted = new LineData();
      extracted.company = d.company;
      extracted.line = uniqueLineName(d, d.line + " Split");
      extracted.prefix = d.prefix;
      extracted.code = d.code;
      extracted.color = d.color;
      extracted.stations.clear();
      extracted.stations.addAll(movedStations);
      extracted.branches.clear();
      extracted.branches.add(created);
      if (d.branches.isEmpty()) d.branches.add(new Branch(sourceName));
      d.lines.add(extracted);
      // Stations wholly moved off the source line no longer belong in its line-level list.
      Set<Integer> stillReferenced = new HashSet<>();
      for (Branch remaining : d.branches) stillReferenced.addAll(remaining.stationIds);
      d.stations.removeIf(s -> route.stationIds.contains(s.id) && !stillReferenced.contains(s.id));
      activateLine(d, d.lines.size() - 1);
      destinationLine = d.activeLine;
      destinationBranch = 0;
      destination = "new line " + extracted.line;
    }
    return new AppliedExtraction(destinationLine, destinationBranch, destination);
  }

  private static ResolvedRoute resolveSelectedRoute(Draft d, List<NodeRef> selection) {
    List<Point> selectedNodes = new ArrayList<>();
    for (NodeRef ref : selection) {
      Point point = resolveNode(d, ref);
      if (point == null)
        return new ResolvedRoute(List.of(), Set.of(), Set.of(),
            "Could not resolve a selected point in the current draft.");
      if (point != null && (selectedNodes.isEmpty() || !selectedNodes.getLast().equals(point)))
        selectedNodes.add(point);
    }
    if (selectedNodes.size() < 2) {
      return new ResolvedRoute(List.of(), Set.of(), Set.of(),
          "At least two connected points are required.");
    }
    List<Point> ordered = new ArrayList<>();
    ordered.add(selectedNodes.getFirst());
    Set<EdgeRef> movedEdges = new LinkedHashSet<>();
    for (int i = 1; i < selectedNodes.size(); i++) {
      List<GraphEdge> path = shortestExistingPath(d, selectedNodes.get(i - 1), selectedNodes.get(i));
      if (path.isEmpty()) {
        return new ResolvedRoute(List.of(), Set.of(), Set.of(),
            "Selected points " + i + " and " + (i + 1)
                + " are not connected by the existing highway network.");
      }
      for (GraphEdge edge : path) {
        Point next = edge.other(ordered.getLast());
        if (!ordered.getLast().equals(next)) ordered.add(next);
        movedEdges.add(edge.ref);
      }
    }
    if (movedEdges.isEmpty())
      return new ResolvedRoute(List.of(), Set.of(), Set.of(), "The selected route contains no segments.");
    Set<Point> routePoints = new HashSet<>(ordered);
    Set<Integer> stationIds = new LinkedHashSet<>();
    for (NodeRef ref : selection) if (ref.station >= 0) stationIds.add(ref.station);
    for (Branch source : d.branches) {
      for (int id : source.stationIds) {
        Station station = markerById(d, id);
        if (station == null) continue;
        Point nearest = nearestVertex(source, station.x, station.z);
        if (nearest != null && routePoints.contains(nearest)) stationIds.add(id);
      }
    }
    return new ResolvedRoute(List.copyOf(ordered), Set.copyOf(movedEdges), Set.copyOf(stationIds), null);
  }

  private static Point nearestVertex(Branch branch, double x, double z) {
    Point nearest = null;
    double best = Double.POSITIVE_INFINITY;
    for (Point point : branch.vertices) {
      double candidate = distance2(x, z, point.x, point.z);
      if (candidate < best) {
        best = candidate;
        nearest = point;
      }
    }
    return nearest;
  }

  static JsonObject extractionScenarioForTest(
      int vertexCount, int[] anchors, boolean intoNewBranch, int stationAt) {
    Draft draft = new Draft("Extraction test");
    Branch source = draft.branches.getFirst();
    source.y = 50;
    for (int i = 0; i < vertexCount; i++) source.vertices.add(new Point(i + 0.5, 0.5, 50));
    if (stationAt >= 0) {
      Station station = new Station(7, "Intermediate", "station", stationAt + 0.5, 50, 0.5);
      draft.stations.add(station);
      source.stationIds.add(station.id);
    }
    List<NodeRef> selected = new ArrayList<>();
    for (int anchor : anchors) selected.add(new NodeRef(-1, 0, anchor));
    ResolvedRoute route = resolveSelectedRoute(draft, selected);
    JsonObject result = new JsonObject();
    if (!route.valid()) {
      result.addProperty("error", route.error);
      return result;
    }
    AppliedExtraction applied = applyResolvedRoute(draft, route, intoNewBranch);
    LineData destination = draft.lines.get(applied.line);
    Branch moved = destination.branches.get(applied.branch);
    JsonArray movedVertices = new JsonArray();
    for (Point point : moved.vertices) movedVertices.add(point.x);
    result.add("moved", movedVertices);
    JsonArray sourceEdges = new JsonArray();
    LineData original = draft.lines.getFirst();
    for (Branch branch : original.branches) {
      for (int i = 1; i < branch.vertices.size(); i++)
        if (!branch.breaks.contains(i)) {
          JsonArray edge = new JsonArray();
          edge.add(branch.vertices.get(i - 1).x);
          edge.add(branch.vertices.get(i).x);
          sourceEdges.add(edge);
        }
      for (Link link : branch.links) {
        JsonArray edge = new JsonArray();
        edge.add(link.a.x);
        edge.add(link.b.x);
        sourceEdges.add(edge);
      }
    }
    result.add("sourceEdges", sourceEdges);
    result.addProperty("lineCount", draft.lines.size());
    result.addProperty("destinationBranchCount", destination.branches.size());
    result.addProperty("destinationHasStation", moved.stationIds.contains(7));
    JsonObject exported = websiteJson(draft, false);
    result.add("export", exported);
    result.addProperty("reloadLines", parseDraft(exported).lines.size());
    return result;
  }

  static String disconnectedExtractionErrorForTest() {
    Draft draft = new Draft("Disconnected extraction");
    Branch branch = draft.branches.getFirst();
    branch.vertices.addAll(List.of(new Point(0.5, 0.5), new Point(1.5, 0.5),
        new Point(10.5, 0.5), new Point(11.5, 0.5)));
    branch.breaks.add(2);
    return resolveSelectedRoute(draft, List.of(new NodeRef(-1, 0, 0), new NodeRef(-1, 0, 3))).error;
  }

  static JsonArray junctionExtractionForTest() {
    Draft draft = new Draft("Junction extraction");
    Branch first = draft.branches.getFirst();
    first.vertices.addAll(List.of(new Point(0.5, 0.5), new Point(1.5, 0.5)));
    Branch second = new Branch("Junction arm");
    second.vertices.addAll(List.of(new Point(1.5, 1.5), new Point(2.5, 1.5)));
    draft.branches.add(second);
    first.links.add(new Link(first.vertices.getLast(), second.vertices.getFirst()));
    ResolvedRoute route = resolveSelectedRoute(
        draft, List.of(new NodeRef(-1, 0, 0), new NodeRef(-1, 1, 1)));
    applyResolvedRoute(draft, route, false);
    Branch moved = draft.lines.getLast().branches.getFirst();
    JsonArray result = new JsonArray();
    for (Point point : moved.vertices) {
      JsonArray coordinate = new JsonArray();
      coordinate.add(point.x);
      coordinate.add(point.z);
      result.add(coordinate);
    }
    return result;
  }

  static String staleExtractionErrorForTest() {
    Draft draft = new Draft("Stale extraction");
    draft.branches.getFirst().vertices.addAll(List.of(new Point(0.5, 0.5), new Point(1.5, 0.5)));
    return resolveSelectedRoute(
        draft, List.of(new NodeRef(-1, 0, 0), new NodeRef(-1, 0, 99))).error;
  }

  static int rapidRepeatExtractionLineCountForTest() {
    Draft draft = new Draft("Repeat extraction");
    draft.branches.getFirst().vertices.addAll(
        List.of(new Point(0.5, 0.5), new Point(1.5, 0.5), new Point(2.5, 0.5)));
    List<NodeRef> selection = List.of(new NodeRef(-1, 0, 0), new NodeRef(-1, 0, 2));
    ResolvedRoute first = resolveSelectedRoute(draft, selection);
    applyResolvedRoute(draft, first, false);
    // The successful UI command atomically clears its selection before another click is handled.
    ResolvedRoute repeated = resolveSelectedRoute(draft, List.of());
    if (repeated.valid()) applyResolvedRoute(draft, repeated, false);
    return draft.lines.size();
  }

  static boolean[] extractionSnapshotCycleForTest() {
    Draft draft = new Draft("History extraction");
    draft.branches.getFirst().vertices.addAll(
        List.of(new Point(0.5, 0.5), new Point(1.5, 0.5), new Point(2.5, 0.5)));
    JsonObject before = websiteJson(draft, false).deepCopy();
    ResolvedRoute route = resolveSelectedRoute(
        draft, List.of(new NodeRef(-1, 0, 0), new NodeRef(-1, 0, 2)));
    applyResolvedRoute(draft, route, false);
    JsonObject after = websiteJson(draft, false).deepCopy();
    JsonObject undone = websiteJson(parseDraft(before), false);
    JsonObject redone = websiteJson(parseDraft(after), false);
    return new boolean[] {
      !before.equals(after), before.equals(undone), after.equals(redone)
    };
  }

  private static List<GraphEdge> shortestExistingPath(Draft d, Point start, Point target) {
    Map<Point, List<GraphEdge>> graph = new HashMap<>();
    for (int bi = 0; bi < d.branches.size(); bi++) {
      int branchIndex = bi;
      Branch branch = d.branches.get(bi);
      for (int i = 1; i < branch.vertices.size(); i++) {
        if (branch.breaks.contains(i)) continue;
        Point a = branch.vertices.get(i - 1), b = branch.vertices.get(i);
        addGraphEdge(graph, new GraphEdge(a, b, new EdgeRef(bi, i - 1, -1), distance(a, b)));
      }
      for (int i = 0; i < branch.links.size(); i++) {
        Link link = branch.links.get(i);
        addGraphEdge(
            graph,
            new GraphEdge(link.a, link.b, new EdgeRef(bi, -1, i), distance(link.a, link.b)));
      }
    }
    if (!graph.containsKey(start) || !graph.containsKey(target)) return List.of();
    Map<Point, Double> distance = new HashMap<>();
    Map<Point, GraphEdge> previous = new HashMap<>();
    PriorityQueue<QueuePoint> queue =
        new PriorityQueue<>(Comparator.comparingDouble(QueuePoint::distance));
    distance.put(start, 0.0);
    queue.add(new QueuePoint(start, 0));
    while (!queue.isEmpty()) {
      QueuePoint current = queue.remove();
      if (current.distance > distance.getOrDefault(current.point, Double.POSITIVE_INFINITY)) continue;
      if (current.point.equals(target)) break;
      for (GraphEdge edge : graph.getOrDefault(current.point, List.of())) {
        Point next = edge.other(current.point);
        double candidate = current.distance + edge.length;
        if (candidate < distance.getOrDefault(next, Double.POSITIVE_INFINITY)) {
          distance.put(next, candidate);
          previous.put(next, edge);
          queue.add(new QueuePoint(next, candidate));
        }
      }
    }
    if (!previous.containsKey(target)) return List.of();
    List<GraphEdge> reversed = new ArrayList<>();
    Point cursor = target;
    while (!cursor.equals(start)) {
      GraphEdge edge = previous.get(cursor);
      if (edge == null) return List.of();
      reversed.add(edge);
      cursor = edge.other(cursor);
    }
    Collections.reverse(reversed);
    return reversed;
  }

  static List<double[]> shortestPathForTest(
      List<double[]> vertices,
      NavigableSet<Integer> breaks,
      List<int[]> links,
      int start,
      int target) {
    Draft draft = new Draft("Path test");
    Branch branch = draft.branches.getFirst();
    branch.vertices.clear();
    for (double[] vertex : vertices) branch.vertices.add(new Point(vertex[0], vertex[1]));
    branch.breaks.addAll(breaks);
    for (int[] link : links)
      branch.links.add(new Link(branch.vertices.get(link[0]), branch.vertices.get(link[1])));
    List<GraphEdge> path =
        shortestExistingPath(draft, branch.vertices.get(start), branch.vertices.get(target));
    List<double[]> result = new ArrayList<>();
    Point cursor = branch.vertices.get(start);
    result.add(new double[] {cursor.x, cursor.z});
    for (GraphEdge edge : path) {
      cursor = edge.other(cursor);
      result.add(new double[] {cursor.x, cursor.z});
    }
    return result;
  }

  private static void addGraphEdge(Map<Point, List<GraphEdge>> graph, GraphEdge edge) {
    graph.computeIfAbsent(edge.a, ignored -> new ArrayList<>()).add(edge);
    graph.computeIfAbsent(edge.b, ignored -> new ArrayList<>()).add(edge);
  }

  private static double distance(Point a, Point b) {
    return Math.hypot(a.x - b.x, a.z - b.z);
  }

  private static void removeExtractedEdges(
      Draft d, Set<EdgeRef> moved, Set<Integer> movedStationIds) {
    for (int bi = 0; bi < d.branches.size(); bi++) {
      int branchIndex = bi;
      Branch branch = d.branches.get(bi);
      List<Point> original = new ArrayList<>(branch.vertices);
      NavigableSet<Integer> originalBreaks = new TreeSet<>(branch.breaks);
      List<Link> originalLinks = new ArrayList<>(branch.links);
      Map<Integer, Point> stationAnchors = new HashMap<>();
      for (int id : branch.stationIds) {
        Station station = markerById(d, id);
        if (station != null) stationAnchors.put(id, nearestVertex(branch, station.x, station.z));
      }
      List<List<Point>> survivingRuns = new ArrayList<>();
      int runStart = 0;
      List<Integer> cuts = new ArrayList<>(originalBreaks);
      cuts.add(original.size());
      for (int runEnd : cuts) {
        if (runEnd - runStart == 1) {
          Point singleton = original.get(runStart);
          survivingRuns.add(new ArrayList<>(List.of(singleton)));
        } else {
          List<Point> current = null;
          for (int segment = runStart; segment < runEnd - 1; segment++) {
            boolean removed = moved.contains(new EdgeRef(branchIndex, segment, -1));
            if (removed) {
              if (current != null) {
                survivingRuns.add(current);
                current = null;
              }
            } else {
              if (current == null) {
                current = new ArrayList<>();
                current.add(original.get(segment));
              }
              current.add(original.get(segment + 1));
            }
          }
          if (current != null) survivingRuns.add(current);
        }
        runStart = runEnd;
      }
      branch.links.clear();
      for (int li = 0; li < originalLinks.size(); li++)
        if (!moved.contains(new EdgeRef(branchIndex, -1, li))) branch.links.add(originalLinks.get(li));
      branch.vertices.clear();
      branch.breaks.clear();
      for (List<Point> run : survivingRuns) {
        if (run.isEmpty()) continue;
        if (!branch.vertices.isEmpty()) branch.breaks.add(branch.vertices.size());
        branch.vertices.addAll(run);
      }
      for (Link link : branch.links) {
        if (!branch.vertices.contains(link.a)) addStandalonePoint(branch, link.a);
        if (!branch.vertices.contains(link.b)) addStandalonePoint(branch, link.b);
      }
      branch.stationIds.removeIf(
          id -> movedStationIds.contains(id)
              && stationAnchors.get(id) != null
              && !branch.vertices.contains(stationAnchors.get(id)));
    }
    d.branches.removeIf(branch -> branch.vertices.isEmpty() && branch.links.isEmpty());
  }

  private static void transferVertexRange(
      Branch source, Branch destination, int from, int to, boolean firstRange) {
    int destinationStart = destination.vertices.size();
    if (!firstRange) destination.breaks.add(destinationStart);
    if (firstRange) destination.y = source.y;
    destination.vertices.addAll(new ArrayList<>(source.vertices.subList(from, to + 1)));
    for (int at : source.breaks)
      if (at > from && at <= to) destination.breaks.add(destinationStart + at - from);
    Set<Point> movedPoints = new HashSet<>(source.vertices.subList(from, to + 1));
    for (Link link : source.links)
      if (movedPoints.contains(link.a) && movedPoints.contains(link.b)) destination.links.add(link);
    removeVertexRange(source, from, to);
  }

  static List<List<double[]>> transferRangeForTest(
      List<double[]> vertices, NavigableSet<Integer> breaks, int from, int to) {
    Branch source = new Branch("Source"), destination = new Branch("Destination");
    for (double[] point : vertices) source.vertices.add(new Point(point[0], point[1]));
    source.breaks.addAll(breaks);
    transferVertexRange(source, destination, from, to, true);
    List<double[]> remaining = new ArrayList<>(), moved = new ArrayList<>();
    for (Point point : source.vertices) remaining.add(new double[] {point.x, point.z});
    for (Point point : destination.vertices) moved.add(new double[] {point.x, point.z});
    return List.of(remaining, moved);
  }

  private static void removeVertexRange(Branch branch, int from, int to) {
    List<Point> original = new ArrayList<>(branch.vertices);
    NavigableSet<Integer> originalBreaks = new TreeSet<>(branch.breaks);
    Set<Point> removed = new HashSet<>(original.subList(from, to + 1));
    branch.vertices.clear();
    List<Integer> originalIndices = new ArrayList<>();
    for (int i = 0; i < original.size(); i++)
      if (i < from || i > to) {
        branch.vertices.add(original.get(i));
        originalIndices.add(i);
      }
    branch.breaks.clear();
    for (int i = 1; i < originalIndices.size(); i++) {
      int previous = originalIndices.get(i - 1), current = originalIndices.get(i);
      if (current != previous + 1 || originalBreaks.contains(current)) branch.breaks.add(i);
    }
    branch.links.removeIf(link -> removed.contains(link.a) && removed.contains(link.b));
  }

  private static String uniqueLineName(Draft draft, String base) {
    String name = base;
    int suffix = 2;
    Set<String> names = new HashSet<>();
    storeActiveLine(draft);
    for (LineData line : draft.lines) names.add(line.line.toLowerCase(Locale.ROOT));
    while (names.contains(name.toLowerCase(Locale.ROOT))) name = base + " " + suffix++;
    return name;
  }

  private static void segmentPanel(GuiGraphics g, int sw) {
    if (!validSelectedSegment()) return;
    drawSelectedSegmentHandles(g);
    Branch b = draft().branches.get(selectedSegmentBranch);
    Point a = selectedSegmentStart(b), c = selectedSegmentEnd(b);
    int x = markerPanelX(sw), y = 8;
    boolean link = selectedSegment < 0;
    g.fill(x, y, x + W, y + (link ? 140 : 280), 0xF20A1419);
    g.drawString(Minecraft.getInstance().font, "Selected line segment", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(
        Minecraft.getInstance().font,
        b.name + (link ? " connection" : " #" + (selectedSegment + 1)),
        x + 8,
        y + 24,
        0xFFFFFF55,
        false);
    g.drawString(
        Minecraft.getInstance().font,
        number(a.x) + ", " + number(a.z) + " to " + number(c.x) + ", " + number(c.z),
        x + 8,
        y + 38,
        0xFFB9DDEB,
        false);
    if (!link && segmentEditing)
      g.drawString(
          Minecraft.getInstance().font,
          "Drag either endpoint to resize",
          x + 8,
          y + 51,
          0xFF8FD9FF,
          false);
    if (link) {
      button(g, x + 8, y + 68, W - 16, "Line / branch memberships");
      button(g, x + 8, y + 92, W - 16, "Remove connection");
      button(g, x + 8, y + 116, W - 16, "Close");
      if (segmentMembershipPanel) drawSegmentMembershipPanel(g, sw, a, c);
      return;
    }
    button(g, x + 8, y + 68, W - 16, segmentEditing ? "Finish editing" : "Edit endpoints");
    button(g, x + 8, y + 92, W - 16, "Add point where clicked");
    button(g, x + 8, y + 116, W - 16, "Remove start point");
    button(g, x + 8, y + 140, W - 16, "Remove end point");
    button(g, x + 8, y + 164, W - 16, "Line / branch memberships");
    button(g, x + 8, y + 188, W - 16, "Remove connection");
    button(g, x + 8, y + 212, W - 16, "Add connecting points");
    button(g, x + 8, y + 236, W - 16, "Separate branch here");
    button(g, x + 8, y + 260, W - 16, "Close");
    if (segmentMembershipPanel) drawSegmentMembershipPanel(g, sw, a, c);
  }

  private static void drawSelectedSegmentHandles(GuiGraphics g) {
    for (SegmentHit hit : segmentHits)
      if (hit.line == draft().activeLine
          && hit.branch == selectedSegmentBranch
          && hit.segment == selectedSegment) {
        int color = segmentEditing ? 0xFFFFFFFF : 0xFFFFFF55;
        drawHandle(g, (int) Math.round(hit.ax), (int) Math.round(hit.ay), color);
        drawHandle(g, (int) Math.round(hit.bx), (int) Math.round(hit.by), color);
        return;
      }
  }

  private static void drawHandle(GuiGraphics g, int x, int y, int color) {
    g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF071014);
    g.fill(x - 2, y - 2, x + 3, y + 3, color);
  }

  private static void drawPlannerPoints(
      GuiGraphics g,
      List<DragHit> hits,
      int sw,
      int sh,
      Set<String> topologyLabels,
      boolean closeLod,
      boolean mediumLod,
      List<int[]> occupied) {
    Draft d = draft();
    Set<String> drawn = new HashSet<>();
    for (DragHit hit : hits)
      if (hit.station < 0 && hit.branch >= 0 && hit.branch < d.branches.size()) {
        Branch b = d.branches.get(hit.branch);
        if (hit.vertex < 0 || hit.vertex >= b.vertices.size()) continue;
        Point p = b.vertices.get(hit.vertex);
        boolean selected =
            !RouteFinderMod.composingPlannerExport()
                && hit.branch == selectedPointBranch
                && hit.vertex == selectedPoint;
        selected |= multiSelection.contains(new NodeRef(-1, hit.branch, hit.vertex));
        boolean visibleHandle = closeLod || mediumLod && hit.branch == d.branch || selected;
        if (!visibleHandle) continue;
        int color =
            selected
                ? 0xFFFFFF55
                : pointConnecting && !RouteFinderMod.composingPlannerExport()
                    ? 0xFF77FFAA
                    : 0xFFB9DDEB;
        int x = (int) Math.round(hit.x), y = (int) Math.round(hit.y);
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xCC071014);
        g.fill(x - 1, y - 1, x + 2, y + 2, color);
        if ((!closeLod && !selected) || !showPointCoordinate(d, b, p, topologyLabels)) continue;
        String text = number(p.x) + ", " + number(pointY(p, b)) + ", " + number(p.z),
            key = number(p.x) + "|" + number(pointY(p, b)) + "|" + number(p.z);
        if (drawn.add(key)) placePointLabel(g, x, y, text, color, occupied, sw, sh);
      }
  }

  private static void syncCoordinateMode(Draft d) {
    d.coordinateMode = (d.coordinateMode + 1) % COORDINATE_MODES.length;
    d.showMarkerInfo = d.coordinateMode != 3 && d.coordinateMode != 4;
    notice("Coordinates: " + COORDINATE_MODES[d.coordinateMode]);
  }

  private static String stationLabel(Draft d, Station s, boolean export) {
    boolean coordinates = d.coordinateMode == 0 || d.coordinateMode == 2;
    return s.name
        + (coordinates ? "  " + number(s.x) + ", " + number(s.y) + ", " + number(s.z) : "");
  }

  private static boolean showPointCoordinate(
      Draft d, Branch b, Point p, Set<String> topologyLabels) {
    if (d.coordinateMode == 0) return true;
    if (d.coordinateMode != 1) return false;
    return topologyLabels.contains(pointHeightKey(p, pointY(p, b)));
  }

  static boolean shouldShowTopologyCoordinate(int connections, boolean turn, boolean yChange) {
    return yChange || connections <= 2 && turn;
  }

  private static Set<String> topologyCoordinateKeys(Draft d) {
    Map<Point, Set<Point>> adjacency = new HashMap<>();
    Map<Point, Set<Double>> heights = new HashMap<>();
    List<Link> links = new ArrayList<>();
    for (Branch b : d.branches) {
      for (Point p : b.vertices)
        heights.computeIfAbsent(new Point(p.x, p.z), ignored -> new HashSet<>()).add(pointY(p, b));
      for (int i = 1; i < b.vertices.size(); i++)
        if (!b.breaks.contains(i))
          connectTopology(adjacency, b.vertices.get(i - 1), b.vertices.get(i));
      links.addAll(b.links);
    }
    for (Link link : links) connectTopology(adjacency, link.a, link.b);
    Set<Point> heightChanges = new HashSet<>();
    for (var entry : heights.entrySet())
      if (entry.getValue().size() > 1) heightChanges.add(entry.getKey());
    for (Link link : links) {
      Set<Double> a = heights.getOrDefault(link.a, Set.of()),
          b = heights.getOrDefault(link.b, Set.of());
      if (!Collections.disjoint(a, b)) continue;
      heightChanges.add(link.a);
      heightChanges.add(link.b);
    }
    Set<String> labels = new HashSet<>();
    for (Branch branch : d.branches)
      for (Point p : branch.vertices) {
        Set<Point> neighbors = adjacency.getOrDefault(p, Set.of());
        boolean turn = false;
        if (neighbors.size() == 2) {
          Iterator<Point> it = neighbors.iterator();
          turn = isCorner(it.next(), p, it.next());
        }
        if (shouldShowTopologyCoordinate(neighbors.size(), turn, heightChanges.contains(p)))
          labels.add(pointHeightKey(p, pointY(p, branch)));
      }
    return labels;
  }

  private static Set<String> topologyCoordinateKeysCached(Draft d) {
    if (cachedTopologyRevision != editorState.revision() || cachedTopologyDraft != draftIndex) {
      cachedTopologyLabels = Set.copyOf(topologyCoordinateKeys(d));
      cachedTopologyRevision = editorState.revision();
      cachedTopologyDraft = draftIndex;
    }
    return cachedTopologyLabels;
  }

  private static void connectTopology(Map<Point, Set<Point>> adjacency, Point a, Point b) {
    if (a.equals(b)) return;
    adjacency.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
    adjacency.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
  }

  private static String pointHeightKey(Point p, double y) {
    return Double.doubleToLongBits(p.x)
        + ":"
        + Double.doubleToLongBits(y)
        + ":"
        + Double.doubleToLongBits(p.z);
  }

  private static void placePointLabel(
      GuiGraphics g,
      int x,
      int y,
      String text,
      int color,
      List<int[]> occupied,
      int sw,
      int sh) {
    int width = Minecraft.getInstance().font.width(text) + 4;
    for (int dy : LABEL_VERTICAL_OFFSETS)
      for (int side = 0; side < 2; side++) {
        int left = side == 0 ? x + 8 : x - width - 8,
            top = y + dy,
            right = left + width,
            bottom = top + 12;
        if (left < 2 || top < 2 || right > sw - 2 || bottom > sh - 2) continue;
        boolean collision = false;
        for (int[] r : occupied)
          if (rectanglesOverlap(left, top, right, bottom, r[0], r[1], r[2], r[3])) {
            collision = true;
            break;
          }
        if (!collision) {
          occupied.add(new int[] {left, top, right, bottom});
          g.fill(left, top, right, bottom, 0xCC071014);
          g.drawString(Minecraft.getInstance().font, text, left + 2, top + 2, color, false);
          return;
        }
      }
  }

  static boolean rectanglesOverlap(int l1, int t1, int r1, int b1, int l2, int t2, int r2, int b2) {
    return l1 < r2 && r1 > l2 && t1 < b2 && b1 > t2;
  }

  private static void pointPanel(GuiGraphics g, int sw) {
    if (!validSelectedPoint()) return;
    Point p = selectedPointValue();
    int x = markerPanelX(sw), y = 8;
    g.fill(x, y, x + W, y + 208, 0xF20A1419);
    g.drawString(
        Minecraft.getInstance().font,
        pointConnecting ? "Connection source selected" : "Selected line point",
        x + 8,
        y + 8,
        pointConnecting ? 0xFF77FFAA : 0xFFFFFFFF,
        false);
    g.drawString(
        Minecraft.getInstance().font,
        branch().name
            + " · XYZ "
            + number(p.x)
            + ", "
            + number(pointY(p, branch()))
            + ", "
            + number(p.z),
        x + 8,
        y + 26,
        0xFFFFFF55,
        false);
    if (pointConnecting)
      g.drawString(
          Minecraft.getInstance().font,
          "Click an existing or new point",
          x + 8,
          y + 40,
          0xFF77FFAA,
          false);
    button(g, x + 8, y + 54, W - 16, pointEditing ? "Finish moving point" : "Move point");
    button(g, x + 8, y + 78, W - 16, "Apply exact X / Y / Z");
    button(
        g,
        x + 8,
        y + 102,
        W - 16,
        pointConnecting ? "Cancel connection" : "Connect from selected point");
    button(g, x + 8, y + 126, W - 16, "Line / branch memberships");
    button(g, x + 8, y + 150, W - 16, "Remove point");
    button(g, x + 8, y + 174, W - 16, "Close");
    if (pointMembershipPanel) drawPointMembershipPanel(g, sw, p);
  }

  private static void branchPanel(GuiGraphics g, int sw) {
    if (!branchPanel) return;
    Branch b = branch();
    int x = markerPanelX(sw), y = 8;
    g.fill(x, y, x + W, y + BRANCH_PANEL_H, 0xF20A1419);
    g.drawString(Minecraft.getInstance().font, "Selected branch", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(
        Minecraft.getInstance().font,
        draft().line + " · " + b.name,
        x + 8,
        y + 26,
        0xFF8FD9FF,
        false);
    button(g, x + 8, y + 46, W - 16, "Start new line on branch");
    button(g, x + 8, y + 70, W - 16, "Rename branch");
    button(g, x + 8, y + 94, W - 16, "Rename line");
    button(g, x + 8, y + 118, W - 16, "Line color: #" + normalizeStoredColor(draft().color));
    button(g, x + 8, y + 142, W - 16, "Merge nearest branch");
    button(g, x + 8, y + 166, W - 16, "Reverse direction");
    button(g, x + 8, y + 190, W - 16, "Duplicate branch");
    button(g, x + 8, y + 214, W - 16, "Delete branch");
    button(
        g,
        x + 8,
        y + 238,
        W - 16,
        System.currentTimeMillis() < lineDeleteConfirmUntil
            ? "Confirm delete line"
            : "Delete planned line");
    button(g, x + 8, y + 262, W - 16, "Close");
  }

  private static void namePanel(GuiGraphics g, int sw) {
    if (nameEditing == 0) return;
    int x = markerPanelX(sw), y = 148;
    g.fill(x, y, x + W, y + 78, 0xFA0A1419);
    g.drawString(Minecraft.getInstance().font, "Rename " + nameKind(), x + 8, y + 8, 0xFFFFFFFF, false);
    field(g, x + 8, y + 26, W - 16, "Name", nameInput, true);
    button(g, x + 8, y + 50, (W - 20) / 2, "Apply");
    button(g, x + 12 + (W - 20) / 2, y + 50, (W - 20) / 2, "Cancel");
  }

  private static void openColorPicker() {
    int rgb = Integer.parseInt(normalizeStoredColor(draft().color), 16);
    float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
    pickerHue = hsb[0];
    pickerSaturation = hsb[1];
    pickerBrightness = hsb[2];
    colorPicker = true;
    colorPickerDragging = false;
  }

  private static int colorPickerX(int sw) {
    return markerPanelX(sw);
  }

  private static void colorPickerPanel(GuiGraphics g, int sw) {
    if (!colorPicker) return;
    int x = colorPickerX(sw), y = 278, fieldX = x + 8, fieldY = y + 28;
    g.fill(x, y, x + W, y + 184, 0xFA0A1419);
    g.drawString(Minecraft.getInstance().font, "Line color", x + 8, y + 8, 0xFFFFFFFF, false);
    for (int py = 0; py < 96; py += 4)
      for (int px = 0; px < 128; px += 4) {
        float saturation = (px + 2) / 128f, brightness = 1f - (py + 2) / 96f;
        g.fill(
            fieldX + px,
            fieldY + py,
            fieldX + px + 4,
            fieldY + py + 4,
            0xFF000000 | (java.awt.Color.HSBtoRGB(pickerHue, saturation, brightness) & 0xFFFFFF));
      }
    for (int py = 0; py < 96; py += 3)
      g.fill(
          x + 144,
          fieldY + py,
          x + 164,
          fieldY + py + 3,
          0xFF000000 | (java.awt.Color.HSBtoRGB(py / 96f, 1, 1) & 0xFFFFFF));
    int selectedX = fieldX + Math.round(pickerSaturation * 127),
        selectedY = fieldY + Math.round((1 - pickerBrightness) * 95);
    g.fill(selectedX - 2, selectedY - 2, selectedX + 3, selectedY + 3, 0xFFFFFFFF);
    int preview = lineArgb(draft().color, 0xFF);
    g.fill(x + 174, fieldY, x + W - 8, fieldY + 96, preview);
    for (int i = 0; i < COMMON_LINE_COLORS.length; i++) {
      int sx = x + 8 + i * 21;
      g.fill(sx, y + 132, sx + 18, y + 150, lineArgb(COMMON_LINE_COLORS[i], 0xFF));
    }
    g.drawString(
        Minecraft.getInstance().font,
        "#" + normalizeStoredColor(draft().color),
        x + 8,
        y + 156,
        0xFFFFFFFF,
        false);
    button(g, x + 118, y + 154, W - 126, "Close");
  }

  private static boolean clickColorPicker(double mx, double my, int sw) {
    if (!colorPicker) return false;
    int x = colorPickerX(sw), y = 278, fieldX = x + 8, fieldY = y + 28;
    if (mx < x || mx > x + W || my < y || my > y + 184) return false;
    if (mx >= fieldX && mx < fieldX + 128 && my >= fieldY && my < fieldY + 96) {
      colorPickerDragArea = 1;
      colorPickerDragging = true;
      setPickerFromScreen(mx, my, sw);
    } else if (mx >= x + 144 && mx < x + 164 && my >= fieldY && my < fieldY + 96) {
      colorPickerDragArea = 2;
      colorPickerDragging = true;
      setPickerFromScreen(mx, my, sw);
    } else if (my >= y + 132 && my < y + 150 && mx >= x + 8) {
      int index = (int) (mx - x - 8) / 21;
      if (index >= 0 && index < COMMON_LINE_COLORS.length) {
        checkpoint();
        draft().color = COMMON_LINE_COLORS[index];
        openColorPicker();
        saveLibraryQuiet();
      }
    } else if (my >= y + 154 && my < y + 178 && mx >= x + 118) {
      colorPicker = false;
    }
    return true;
  }

  private static void updateColorPickerFromMouse() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    setPickerFromScreen(
        mc.mouseHandler.getScaledXPos(mc.getWindow()),
        mc.mouseHandler.getScaledYPos(mc.getWindow()),
        mc.getWindow().getGuiScaledWidth());
  }

  private static void setPickerFromScreen(double mx, double my, int sw) {
    int x = colorPickerX(sw), fieldY = 306;
    if (!colorPickerUndoRecorded) {
      checkpoint();
      colorPickerUndoRecorded = true;
    }
    if (colorPickerDragArea == 1) {
      pickerSaturation = (float) Math.clamp((mx - x - 8) / 127.0, 0, 1);
      pickerBrightness = 1f - (float) Math.clamp((my - fieldY) / 95.0, 0, 1);
    } else if (colorPickerDragArea == 2) {
      pickerHue = (float) Math.clamp((my - fieldY) / 95.0, 0, 1);
    }
    draft().color = String.format(Locale.ROOT, "%06x", java.awt.Color.HSBtoRGB(pickerHue, pickerSaturation, pickerBrightness) & 0xFFFFFF);
  }

  private static int markerPanelX(int sw) {
    return Math.max(PROJECT_X + PROJECT_W + 8, sw - W - 8);
  }

  private static Station selectedStation() {
    for (Station s : draft().stations) if (s.id == selectedMarker) return s;
    return null;
  }

  private static String labelForType(String type) {
    for (int i = 1; i < TYPES.length; i++) if (TYPES[i].equals(type)) return LABELS[i];
    return type;
  }

  private static boolean clickMarkerPanel(double mx, double my, int sw) {
    if (clickMarkerMembershipPanel(mx, my, sw)) return true;
    if (clickNamePanel(mx, my, sw)
        || clickBranchPanel(mx, my, sw)
        || clickPointPanel(mx, my, sw)
        || clickSegmentPanel(mx, my, sw)) return true;
    Station s = selectedStation();
    if (s == null) return false;
    int x = markerPanelX(sw), y = 8;
    if (mx < x || mx > x + W || my < y || my > y + BRANCH_PANEL_H) return false;
    int py = (int) my - y;
    if (py >= 54 && py < 74) {
      markerEditing = !markerEditing;
      if (markerEditing) loadMarkerCoordinates(s);
      notice(markerEditing ? "Drag the marker or enter exact XYZ" : "Finished editing marker");
    } else if (py >= 78 && py < 98) {
      if (!markerEditing) {
        markerEditing = true;
        loadMarkerCoordinates(s);
      }
      applyMarkerCoordinates();
    } else if (py >= 102 && py < 122) startNameEdit(1, s.name);
    else if (py >= 126 && py < 146) markerMembershipPanel = !markerMembershipPanel;
    else if (py >= 150 && py < 170) deleteSelectedMarker();
    else if (py >= 174 && py < 194) clearMarkerSelection();
    return true;
  }

  private record MarkerMembership(int line, int branch, String label) {}

  private static List<MarkerMembership> markerMemberships(Draft d) {
    storeActiveLine(d);
    List<MarkerMembership> rows = new ArrayList<>();
    for (int li = 0; li < d.lines.size(); li++) {
      LineData line = d.lines.get(li);
      for (int bi = 0; bi < line.branches.size(); bi++)
        rows.add(new MarkerMembership(li, bi, line.line + " / " + line.branches.get(bi).name));
    }
    return rows;
  }

  private static boolean hasMarkerMembership(Draft d, int id, MarkerMembership membership) {
    LineData line = d.lines.get(membership.line);
    return line.branches.get(membership.branch).stationIds.contains(id);
  }

  private static void drawMarkerMembershipPanel(GuiGraphics g, int sw, Station marker) {
    Draft d = draft();
    List<MarkerMembership> memberships = markerMemberships(d);
    int rows = Math.min(10, Math.max(1, memberships.size()));
    int pages = Math.max(1, (memberships.size() + rows - 1) / rows);
    markerMembershipPage = Math.clamp(markerMembershipPage, 0, pages - 1);
    int from = markerMembershipPage * rows, count = Math.min(rows, memberships.size() - from);
    int x = Math.max(PROJECT_X + PROJECT_W + 8, markerPanelX(sw) - W - 4), y = 8;
    g.fill(x, y, x + W, y + 36 + (count + 1) * 20, 0xFA0A1419);
    g.drawString(Minecraft.getInstance().font, "Marker memberships", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(Minecraft.getInstance().font, marker.name, x + 8, y + 22, 0xFFFFD36A, false);
    for (int i = 0; i < count; i++) {
      MarkerMembership membership = memberships.get(from + i);
      button(
          g,
          x + 8,
          y + 36 + i * 20,
          W - 16,
          (hasMarkerMembership(d, marker.id, membership) ? "[x] " : "[ ] ") + membership.label);
    }
    button(
        g,
        x + 8,
        y + 36 + count * 20,
        W - 16,
        pages == 1 ? "Close" : "Page " + (markerMembershipPage + 1) + "/" + pages);
  }

  private static boolean clickMarkerMembershipPanel(double mx, double my, int sw) {
    if (!markerMembershipPanel || selectedStation() == null) return false;
    Draft d = draft();
    Station marker = selectedStation();
    List<MarkerMembership> memberships = markerMemberships(d);
    int rows = Math.min(10, Math.max(1, memberships.size()));
    int pages = Math.max(1, (memberships.size() + rows - 1) / rows);
    markerMembershipPage = Math.clamp(markerMembershipPage, 0, pages - 1);
    int from = markerMembershipPage * rows, count = Math.min(rows, memberships.size() - from);
    int x = Math.max(PROJECT_X + PROJECT_W + 8, markerPanelX(sw) - W - 4), y = 8;
    if (mx < x || mx > x + W || my < y || my > y + 36 + (count + 1) * 20) return false;
    int row = ((int) my - y - 36) / 20;
    if (row >= 0 && row < count) {
      toggleMarkerMembership(d, marker, memberships.get(from + row));
    } else if (row == count) {
      if (pages == 1) markerMembershipPanel = false;
      else markerMembershipPage = (markerMembershipPage + 1) % pages;
    }
    return true;
  }

  private static void toggleMarkerMembership(
      Draft d, Station marker, MarkerMembership membership) {
    checkpoint();
    LineData line = d.lines.get(membership.line);
    Branch branch = line.branches.get(membership.branch);
    if (branch.stationIds.remove((Integer) marker.id)) {
      boolean remains = line.branches.stream().anyMatch(b -> b.stationIds.contains(marker.id));
      if (!remains) line.stations.removeIf(s -> s.id == marker.id);
      notice("Removed marker from " + membership.label);
    } else {
      if (line.stations.stream().noneMatch(s -> s.id == marker.id)) line.stations.add(marker);
      branch.stationIds.add(marker.id);
      notice("Added marker to " + membership.label);
    }
    saveLibraryQuiet();
  }

  private static boolean clickPointPanel(double mx, double my, int sw) {
    if (clickPointMembershipPanel(mx, my, sw)) return true;
    if (!validSelectedPoint()) return false;
    int x = markerPanelX(sw), y = 8;
    if (mx < x || mx > x + W || my < y || my > y + 208) return false;
    int py = (int) my - y;
    if (py >= 54 && py < 74) {
      pointEditing = !pointEditing;
      notice(pointEditing ? "Drag the selected point" : "Finished moving point");
    } else if (py >= 78 && py < 98) applySelectedPointCoordinates();
    else if (py >= 102 && py < 122) {
      if (pointConnecting) cancelPointConnection();
      else beginPointConnection();
    } else if (py >= 126 && py < 146) pointMembershipPanel = !pointMembershipPanel;
    else if (py >= 150 && py < 170) deleteSelectedPoint();
    else if (py >= 174 && py < 194) clearPointSelection();
    return true;
  }

  private static boolean hasPointMembership(Draft d, Point point, MarkerMembership membership) {
    return d.lines.get(membership.line).branches.get(membership.branch).vertices.contains(point);
  }

  private static void drawPointMembershipPanel(GuiGraphics g, int sw, Point point) {
    Draft d = draft();
    List<MarkerMembership> memberships = markerMemberships(d);
    int rows = Math.min(10, Math.max(1, memberships.size()));
    int pages = Math.max(1, (memberships.size() + rows - 1) / rows);
    pointMembershipPage = Math.clamp(pointMembershipPage, 0, pages - 1);
    int from = pointMembershipPage * rows, count = Math.min(rows, memberships.size() - from);
    int x = Math.max(PROJECT_X + PROJECT_W + 8, markerPanelX(sw) - W - 4), y = 8;
    g.fill(x, y, x + W, y + 36 + (count + 1) * 20, 0xFA0A1419);
    g.drawString(Minecraft.getInstance().font, "Vertex memberships", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(
        Minecraft.getInstance().font,
        "XYZ " + number(point.x) + ", " + number(pointY(point, branch())) + ", " + number(point.z),
        x + 8, y + 22, 0xFFFFD36A, false);
    for (int i = 0; i < count; i++) {
      MarkerMembership membership = memberships.get(from + i);
      button(g, x + 8, y + 36 + i * 20, W - 16,
          (hasPointMembership(d, point, membership) ? "[x] " : "[ ] ") + membership.label);
    }
    button(g, x + 8, y + 36 + count * 20, W - 16,
        pages == 1 ? "Close" : "Page " + (pointMembershipPage + 1) + "/" + pages);
  }

  private static boolean clickPointMembershipPanel(double mx, double my, int sw) {
    if (!pointMembershipPanel || !validSelectedPoint()) return false;
    Draft d = draft();
    Point point = selectedPointValue();
    List<MarkerMembership> memberships = markerMemberships(d);
    int rows = Math.min(10, Math.max(1, memberships.size()));
    int pages = Math.max(1, (memberships.size() + rows - 1) / rows);
    pointMembershipPage = Math.clamp(pointMembershipPage, 0, pages - 1);
    int from = pointMembershipPage * rows, count = Math.min(rows, memberships.size() - from);
    int x = Math.max(PROJECT_X + PROJECT_W + 8, markerPanelX(sw) - W - 4), y = 8;
    if (mx < x || mx > x + W || my < y || my > y + 36 + (count + 1) * 20) return false;
    int row = ((int) my - y - 36) / 20;
    if (row >= 0 && row < count) togglePointMembership(d, point, memberships.get(from + row));
    else if (row == count) {
      if (pages == 1) pointMembershipPanel = false;
      else pointMembershipPage = (pointMembershipPage + 1) % pages;
    }
    return true;
  }

  private static void togglePointMembership(Draft d, Point point, MarkerMembership membership) {
    checkpoint();
    Branch target = d.lines.get(membership.line).branches.get(membership.branch);
    int index = target.vertices.indexOf(point);
    if (index >= 0) {
      removePoint(target, index);
      notice("Removed vertex from " + membership.label);
      clearPointSelection();
    } else {
      addStandalonePoint(target, new Point(point.x, point.z, point.y));
      notice("Added shared vertex to " + membership.label);
    }
    saveLibraryQuiet();
  }

  private static void drawSegmentMembershipPanel(
      GuiGraphics g, int sw, Point a, Point b) {
    Draft d = draft();
    List<MarkerMembership> memberships = markerMemberships(d);
    int rows = Math.min(10, Math.max(1, memberships.size()));
    int pages = Math.max(1, (memberships.size() + rows - 1) / rows);
    segmentMembershipPage = Math.clamp(segmentMembershipPage, 0, pages - 1);
    int from = segmentMembershipPage * rows, count = Math.min(rows, memberships.size() - from);
    int x = Math.max(PROJECT_X + PROJECT_W + 8, markerPanelX(sw) - W - 4), y = 8;
    g.fill(x, y, x + W, y + 36 + (count + 1) * 20, 0xFA0A1419);
    g.drawString(Minecraft.getInstance().font, "Segment memberships", x + 8, y + 8, 0xFFFFFFFF, false);
    g.drawString(
        Minecraft.getInstance().font,
        number(a.x) + ", " + number(a.z) + " to " + number(b.x) + ", " + number(b.z),
        x + 8, y + 22, 0xFFFFD36A, false);
    for (int i = 0; i < count; i++) {
      MarkerMembership membership = memberships.get(from + i);
      Branch target = d.lines.get(membership.line).branches.get(membership.branch);
      button(g, x + 8, y + 36 + i * 20, W - 16,
          (hasDirectConnection(target, a, b) ? "[x] " : "[ ] ") + membership.label);
    }
    button(g, x + 8, y + 36 + count * 20, W - 16,
        pages == 1 ? "Close" : "Page " + (segmentMembershipPage + 1) + "/" + pages);
  }

  private static boolean clickSegmentMembershipPanel(double mx, double my, int sw) {
    if (!segmentMembershipPanel || !validSelectedSegment()) return false;
    Draft d = draft();
    Branch selectedOwner = d.branches.get(selectedSegmentBranch);
    Point a = selectedSegmentStart(selectedOwner), b = selectedSegmentEnd(selectedOwner);
    List<MarkerMembership> memberships = markerMemberships(d);
    int rows = Math.min(10, Math.max(1, memberships.size()));
    int pages = Math.max(1, (memberships.size() + rows - 1) / rows);
    segmentMembershipPage = Math.clamp(segmentMembershipPage, 0, pages - 1);
    int from = segmentMembershipPage * rows, count = Math.min(rows, memberships.size() - from);
    int x = Math.max(PROJECT_X + PROJECT_W + 8, markerPanelX(sw) - W - 4), y = 8;
    if (mx < x || mx > x + W || my < y || my > y + 36 + (count + 1) * 20) return false;
    int row = ((int) my - y - 36) / 20;
    if (row >= 0 && row < count) {
      MarkerMembership membership = memberships.get(from + row);
      Branch target = d.lines.get(membership.line).branches.get(membership.branch);
      boolean remove = hasDirectConnection(target, a, b);
      checkpoint();
      setSegmentMembership(target, a, b, !remove);
      saveLibraryQuiet();
      notice((remove ? "Removed segment from " : "Added segment to ") + membership.label);
      if (remove && membership.line == d.activeLine && membership.branch == selectedSegmentBranch)
        clearSegmentSelection();
    } else if (row == count) {
      if (pages == 1) segmentMembershipPanel = false;
      else segmentMembershipPage = (segmentMembershipPage + 1) % pages;
    }
    return true;
  }

  private static boolean hasDirectConnection(Branch branch, Point a, Point b) {
    for (int i = 1; i < branch.vertices.size(); i++)
      if (!branch.breaks.contains(i)
          && sameEdge(branch.vertices.get(i - 1), branch.vertices.get(i), a, b)) return true;
    for (Link link : branch.links) if (sameEdge(link.a, link.b, a, b)) return true;
    return false;
  }

  private static boolean sameEdge(Point a, Point b, Point c, Point d) {
    return a.equals(c) && b.equals(d) || a.equals(d) && b.equals(c);
  }

  private static void setSegmentMembership(Branch branch, Point a, Point b, boolean member) {
    if (!member) {
      for (int i = 1; i < branch.vertices.size(); i++)
        if (!branch.breaks.contains(i)
            && sameEdge(branch.vertices.get(i - 1), branch.vertices.get(i), a, b))
          branch.breaks.add(i);
      branch.links.removeIf(link -> sameEdge(link.a, link.b, a, b));
      return;
    }
    if (hasDirectConnection(branch, a, b)) return;
    Point targetA = pointInBranch(branch, a), targetB = pointInBranch(branch, b);
    if (targetA == null) {
      targetA = new Point(a.x, a.z, a.y);
      addStandalonePoint(branch, targetA);
    }
    if (targetB == null) {
      targetB = new Point(b.x, b.z, b.y);
      addStandalonePoint(branch, targetB);
    }
    branch.links.add(new Link(targetA, targetB));
  }

  private static Point pointInBranch(Branch branch, Point target) {
    for (Point point : branch.vertices) if (point.equals(target)) return point;
    return null;
  }

  static boolean[] segmentMembershipCycleForTest() {
    Branch branch = new Branch("Membership");
    Point a = new Point(0.5, 0.5, 50), b = new Point(10.5, 0.5, 51);
    setSegmentMembership(branch, a, b, true);
    boolean added = hasDirectConnection(branch, a, b);
    boolean pointsKept = branch.vertices.size() == 2;
    setSegmentMembership(branch, a, b, false);
    return new boolean[] {added, !hasDirectConnection(branch, a, b), pointsKept && branch.vertices.size() == 2};
  }

  private static boolean clickSegmentPanel(double mx, double my, int sw) {
    if (clickSegmentMembershipPanel(mx, my, sw)) return true;
    if (!validSelectedSegment()) return false;
    int x = markerPanelX(sw), y = 8;
    boolean link = selectedSegment < 0;
    if (mx < x || mx > x + W || my < y || my > y + (link ? 140 : 280)) return false;
    int py = (int) my - y;
    if (link) {
      if (py >= 68 && py < 88) segmentMembershipPanel = !segmentMembershipPanel;
      else if (py >= 92 && py < 112) removeSelectedSegment();
      else if (py >= 116 && py < 136) clearSegmentSelection();
      return true;
    }
    if (py >= 68 && py < 88) {
      segmentEditing = !segmentEditing;
      if (segmentEditing) tool = 0;
      notice(
          segmentEditing
              ? "Drag either endpoint to extend or retract"
              : "Finished editing segment");
    } else if (py >= 92 && py < 112) addPointToSelectedSegment();
    else if (py >= 116 && py < 136) removeSelectedPoint(false);
    else if (py >= 140 && py < 160) removeSelectedPoint(true);
    else if (py >= 164 && py < 184) segmentMembershipPanel = !segmentMembershipPanel;
    else if (py >= 188 && py < 208) removeSelectedSegment();
    else if (py >= 212 && py < 232) continueFromSelectedSegment();
    else if (py >= 236 && py < 256) separateSelectedBranch();
    else if (py >= 260 && py < 280) clearSegmentSelection();
    return true;
  }

  private static boolean clickBranchPanel(double mx, double my, int sw) {
    if (!branchPanel) return false;
    int x = markerPanelX(sw), y = 8;
    if (mx < x || mx > x + W || my < y || my > y + BRANCH_PANEL_H) return false;
    int py = (int) my - y;
    if (py >= 46 && py < 66) startNewLine();
    else if (py >= 70 && py < 90) startNameEdit(2, branch().name);
    else if (py >= 94 && py < 114) startNameEdit(3, draft().line);
    else if (py >= 118 && py < 138) openColorPicker();
    else if (py >= 142 && py < 162) mergeNearestBranch();
    else if (py >= 166 && py < 186) reverseBranch();
    else if (py >= 190 && py < 210) duplicateBranch();
    else if (py >= 214 && py < 234) deleteBranch();
    else if (py >= 238 && py < 258) requestDeleteSelectedLine();
    else if (py >= 262 && py < 282) branchPanel = false;
    return true;
  }

  private static void reverseBranch() {
    checkpoint();
    Branch b = branch();
    int size = b.vertices.size();
    Collections.reverse(b.vertices);
    NavigableSet<Integer> reversed = new TreeSet<>();
    for (int at : b.breaks) reversed.add(size - at);
    b.breaks.clear();
    b.breaks.addAll(reversed);
    Collections.reverse(b.stationIds);
    saveLibraryQuiet();
    notice("Reversed " + b.name);
  }

  private static void duplicateBranch() {
    checkpoint();
    Draft d = draft();
    Branch source = branch(), copy = new Branch(uniqueBranchName(d, source.name + " Copy"));
    copy.visible = source.visible;
    copy.y = source.y;
    copy.vertices.addAll(source.vertices);
    copy.breaks.addAll(source.breaks);
    copy.links.addAll(source.links);
    copy.stationIds.addAll(source.stationIds);
    d.branches.add(copy);
    d.branch = d.branches.size() - 1;
    saveLibraryQuiet();
    notice("Duplicated branch");
  }

  private static boolean clickNamePanel(double mx, double my, int sw) {
    if (nameEditing == 0) return false;
    int x = markerPanelX(sw), y = 148;
    if (mx < x || mx > x + W || my < y || my > y + 78) return true;
    if (my >= y + 50 && my < y + 70) {
      if (mx < x + W / 2) applyNameEdit();
      else cancelNameEdit();
    }
    return true;
  }

  private static boolean validSelectedSegment() {
    if (selectedSegmentBranch < 0 || selectedSegmentBranch >= draft().branches.size()) return false;
    Branch b = draft().branches.get(selectedSegmentBranch);
    if (selectedSegment < 0) return -selectedSegment - 1 < b.links.size();
    return selectedSegment + 1 < b.vertices.size() && !b.breaks.contains(selectedSegment + 1);
  }

  private static boolean validSequentialSelectedSegment() {
    return validSelectedSegment() && selectedSegment >= 0;
  }

  private static Point selectedSegmentStart(Branch b) {
    return selectedSegment < 0 ? b.links.get(-selectedSegment - 1).a : b.vertices.get(selectedSegment);
  }

  private static Point selectedSegmentEnd(Branch b) {
    return selectedSegment < 0 ? b.links.get(-selectedSegment - 1).b : b.vertices.get(selectedSegment + 1);
  }

  private static void selectSegment(SegmentHit hit, double mx, double my) {
    clearMarkerSelection();
    if (hit.line != draft().activeLine) activateLine(draft(), hit.line);
    selectedSegmentBranch = hit.branch;
    selectedSegment = hit.segment;
    segmentEditing = false;
    draft().branch = hit.branch;
    Branch b = branch();
    Point a = selectedSegmentStart(b), c = selectedSegmentEnd(b);
    double dx = hit.bx - hit.ax,
        dy = hit.by - hit.ay,
        q = dx * dx + dy * dy,
        t = q == 0 ? .5 : Math.clamp(((mx - hit.ax) * dx + (my - hit.ay) * dy) / q, 0, 1);
    selectedSegmentPoint =
        new Point(blockCenter(a.x + (c.x - a.x) * t), blockCenter(a.z + (c.z - a.z) * t));
  }

  private static void clearSegmentSelection() {
    selectedSegmentBranch = selectedSegment = -1;
    selectedSegmentPoint = null;
    segmentEditing = false;
    segmentMembershipPanel = false;
    segmentMembershipPage = 0;
  }

  private static boolean validSelectedPoint() {
    return selectedPointBranch >= 0
        && selectedPointBranch < draft().branches.size()
        && selectedPoint >= 0
        && selectedPoint < draft().branches.get(selectedPointBranch).vertices.size();
  }

  private static Point selectedPointValue() {
    return validSelectedPoint()
        ? draft().branches.get(selectedPointBranch).vertices.get(selectedPoint)
        : null;
  }

  private static void selectPoint(DragHit hit) {
    clearMarkerSelection();
    pointMembershipPanel = false;
    pointMembershipPage = 0;
    selectedPointBranch = hit.branch;
    selectedPoint = hit.vertex;
    draft().branch = hit.branch;
    pointEditing = false;
    Point p = selectedPointValue();
    coordinateX = number(p.x);
    coordinateY = number(pointY(p, branch()));
    coordinateZ = number(p.z);
  }

  private static void clearPointSelection() {
    selectedPointBranch = selectedPoint = -1;
    pointEditing = false;
    pointConnecting = false;
    connectionStart = null;
    pointMembershipPanel = false;
    pointMembershipPage = 0;
  }

  private static void applySelectedPointCoordinates() {
    if (!validSelectedPoint()) return;
    try {
      double x = blockCenter(Double.parseDouble(coordinateX)),
          y = Double.parseDouble(coordinateY),
          z = blockCenter(Double.parseDouble(coordinateZ));
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
        throw new NumberFormatException();
      checkpoint();
      Branch b = draft().branches.get(selectedPointBranch);
      Point old = b.vertices.get(selectedPoint);
      replaceSharedPointEverywhere(draft(), old, x, z, y);
      coordinateField = 0;
      saveLibraryQuiet();
      notice("Updated point XYZ to " + number(x) + ", " + number(y) + ", " + number(z));
    } catch (NumberFormatException e) {
      notice("Enter valid X, Y and Z");
    }
  }

  private static void beginPointConnection() {
    Point p = selectedPointValue();
    if (p == null) return;
    connectionStart = p;
    pointConnecting = true;
    pointEditing = false;
    tool = 0;
    notice("Source selected; click an existing or new point");
  }

  private static void cancelPointConnection() {
    pointConnecting = false;
    connectionStart = null;
    notice("Connection cancelled");
  }

  private static void connectExistingPoint(DragHit targetHit) {
    if (connectionStart == null || targetHit.station >= 0) return;
    Point target = draft().branches.get(targetHit.branch).vertices.get(targetHit.vertex);
    if (target.equals(connectionStart)) {
      notice("Choose a different point");
      return;
    }
    Branch owner = draft().branches.get(selectedPointBranch);
    for (Link link : owner.links)
      if (link.a.equals(connectionStart) && link.b.equals(target)
          || link.a.equals(target) && link.b.equals(connectionStart)) {
        cancelPointConnection();
        notice("Those points are already connected");
        return;
      }
    checkpoint();
    owner.links.add(new Link(connectionStart, target));
    pointConnecting = false;
    connectionStart = null;
    coordinateX = number(selectedPointValue().x);
    coordinateY = number(owner.y);
    coordinateZ = number(selectedPointValue().z);
    saveLibraryQuiet();
    notice("Connected points; original source remains selected");
  }

  private static void finishPointConnection(Point target) {
    if (connectionStart == null || !validSelectedPoint()) return;
    Branch selectedOwner = draft().branches.get(selectedPointBranch);
    target =
        new Point(
            blockCenter(target.x), blockCenter(target.z), pointY(connectionStart, selectedOwner));
    if (target.equals(connectionStart)) {
      notice("Choose a different point");
      return;
    }
    checkpoint();
    Branch b = draft().branches.get(selectedPointBranch);
    int source = selectedPoint;
    boolean runStart = source == 0 || b.breaks.contains(source),
        runEnd = source == b.vertices.size() - 1 || b.breaks.contains(source + 1);
    if (runEnd) insertPoint(b, source + 1, target);
    else if (runStart) {
      insertPoint(b, source, target);
      if (source > 0) {
        b.breaks.remove(source + 1);
        b.breaks.add(source);
      }
      selectedPoint = source + 1;
    } else {
      insertPoint(b, source + 1, target);
      b.breaks.add(source + 2);
    }
    pointConnecting = false;
    connectionStart = null;
    Point selected = selectedPointValue();
    coordinateX = number(selected.x);
    coordinateY = number(pointY(selected, b));
    coordinateZ = number(selected.z);
    saveLibraryQuiet();
    notice("Connected new line; original source remains selected");
  }

  private static void deleteSelectedPoint() {
    if (!validSelectedPoint()) return;
    checkpoint();
    Branch b = draft().branches.get(selectedPointBranch);
    removePoint(b, selectedPoint);
    clearPointSelection();
    saveLibraryQuiet();
    notice("Removed line point");
  }

  private static void addPointToSelectedSegment() {
    if (!validSequentialSelectedSegment() || selectedSegmentPoint == null) return;
    Branch b = draft().branches.get(selectedSegmentBranch);
    Point a = b.vertices.get(selectedSegment), c = b.vertices.get(selectedSegment + 1);
    if (selectedSegmentPoint.equals(a) || selectedSegmentPoint.equals(c)) {
      notice("Click farther from an endpoint");
      return;
    }
    checkpoint();
    insertPoint(b, selectedSegment + 1, selectedSegmentPoint);
    selectedSegment++;
    saveLibraryQuiet();
    notice(
        "Added point at " + number(selectedSegmentPoint.x) + ", " + number(selectedSegmentPoint.z));
  }

  private static void removeSelectedPoint(boolean end) {
    if (!validSequentialSelectedSegment()) return;
    checkpoint();
    Branch b = draft().branches.get(selectedSegmentBranch);
    removePoint(b, selectedSegment + (end ? 1 : 0));
    clearSegmentSelection();
    saveLibraryQuiet();
    notice("Removed line point");
  }

  private static void removeSelectedSegment() {
    if (!validSelectedSegment()) return;
    checkpoint();
    Branch b = draft().branches.get(selectedSegmentBranch);
    if (selectedSegment < 0) removeLinkConnection(b, selectedSegment);
    else b.breaks.add(selectedSegment + 1);
    clearSegmentSelection();
    saveLibraryQuiet();
    notice("Removed connection; branch remains merged");
  }

  private static void removeLinkConnection(Branch branch, int encodedSegment) {
    branch.links.remove(-encodedSegment - 1);
  }

  static int removeLinkConnectionForTest() {
    Branch branch = new Branch("Links");
    Point a = new Point(0.5, 0.5), b = new Point(10.5, 0.5);
    branch.links.add(new Link(a, b));
    removeLinkConnection(branch, -1);
    return branch.links.size();
  }

  private static void insertPoint(Branch b, int index, Point point) {
    NavigableSet<Integer> shifted = new TreeSet<>();
    for (int at : b.breaks) shifted.add(at >= index ? at + 1 : at);
    b.breaks.clear();
    b.breaks.addAll(shifted);
    b.vertices.add(index, point);
  }

  private static void removePoint(Branch b, int index) {
    boolean preserveGap = b.breaks.contains(index) || b.breaks.contains(index + 1);
    NavigableSet<Integer> shifted = new TreeSet<>();
    for (int at : b.breaks)
      if (at < index) shifted.add(at);
      else if (at > index + 1) shifted.add(at - 1);
    b.vertices.remove(index);
    if (preserveGap && index > 0 && index < b.vertices.size()) shifted.add(index);
    b.breaks.clear();
    b.breaks.addAll(shifted);
  }

  private static void startNewLine() {
    tool = 0;
    newLinePending = true;
    branchPanel = false;
    notice("Click the first point of the new line");
  }

  private static void continueFromSelectedSegment() {
    if (!validSequentialSelectedSegment()) return;
    checkpoint();
    Branch b = draft().branches.get(selectedSegmentBranch);
    Point start = b.vertices.get(selectedSegment + 1);
    b.breaks.add(b.vertices.size());
    b.vertices.add(start);
    draft().branch = selectedSegmentBranch;
    tool = 0;
    newLinePending = false;
    clearSegmentSelection();
    saveLibraryQuiet();
    notice("Click to add connecting points");
  }

  private static void separateSelectedBranch() {
    if (!validSequentialSelectedSegment()) return;
    Draft d = draft();
    Branch branch = d.branches.get(selectedSegmentBranch);
    Point splitPoint = selectedSegmentPoint;
    int pivot;
    Point a = branch.vertices.get(selectedSegment), b = branch.vertices.get(selectedSegment + 1);
    if (splitPoint == null || splitPoint.equals(a)) pivot = selectedSegment;
    else if (splitPoint.equals(b)) pivot = selectedSegment + 1;
    else {
      checkpoint();
      insertPoint(branch, selectedSegment + 1, splitPoint);
      pivot = selectedSegment + 1;
      splitBranchAt(d, selectedSegmentBranch, pivot, false);
      return;
    }
    splitBranchAt(d, selectedSegmentBranch, pivot);
  }

  private static void splitAtSelectedMarker() {
    Station marker = selectedStation();
    if (marker == null) return;
    Draft d = draft();
    for (int branchIndex = 0; branchIndex < d.branches.size(); branchIndex++) {
      Branch branch = d.branches.get(branchIndex);
      if (!branch.stationIds.contains(marker.id)) continue;
      Point markerPoint = new Point(blockCenter(marker.x), blockCenter(marker.z), marker.y);
      for (int i = 0; i < branch.vertices.size(); i++)
        if (branch.vertices.get(i).equals(markerPoint)) {
          splitBranchAt(d, branchIndex, i);
          return;
        }
      for (int segment = 0; segment + 1 < branch.vertices.size(); segment++) {
        if (branch.breaks.contains(segment + 1)) continue;
        Point projected = project(marker.x, marker.z, branch.vertices.get(segment), branch.vertices.get(segment + 1));
        if (distance2(projected.x, projected.z, marker.x, marker.z) <= .26) {
          checkpoint();
          insertPoint(branch, segment + 1, markerPoint);
          splitBranchAt(d, branchIndex, segment + 1, false);
          return;
        }
      }
    }
    notice("Selected marker is not on a branch line");
  }

  private static void splitBranchAt(Draft d, int branchIndex, int pivot) {
    splitBranchAt(d, branchIndex, pivot, true);
  }

  private static void splitBranchAt(Draft d, int branchIndex, int pivot, boolean recordUndo) {
    Branch left = d.branches.get(branchIndex);
    if (pivot <= 0 || pivot >= left.vertices.size() - 1) {
      notice("Choose an interior segment to separate");
      return;
    }
    if (recordUndo) checkpoint();
    Branch right = new Branch(uniqueBranchName(d, left.name + " Split"));
    right.visible = left.visible;
    right.y = left.y;
    right.vertices.addAll(new ArrayList<>(left.vertices.subList(pivot, left.vertices.size())));
    NavigableSet<Integer> oldBreaks = new TreeSet<>(left.breaks);
    left.breaks.removeIf(at -> at > pivot);
    for (int at : oldBreaks) if (at > pivot) right.breaks.add(at - pivot);
    left.vertices.subList(pivot + 1, left.vertices.size()).clear();
    for (Station s : d.stations)
      if (left.stationIds.contains(s.id)) {
        Point markerPoint = new Point(s.x, s.z);
        boolean onLeft = pointOnBranch(left, markerPoint), onRight = pointOnBranch(right, markerPoint);
        if (onRight && !right.stationIds.contains(s.id)) right.stationIds.add(s.id);
        if (!onLeft && onRight) left.stationIds.remove((Integer) s.id);
      }
    d.branches.add(branchIndex + 1, right);
    d.branch = branchIndex + 1;
    clearSegmentSelection();
    clearMarkerSelection();
    branchPanel = true;
    saveLibraryQuiet();
    notice("Separated into " + left.name + " and " + right.name);
  }

  private static boolean pointOnBranch(Branch branch, Point point) {
    if (branch.vertices.contains(point)) return true;
    for (int i = 1; i < branch.vertices.size(); i++) {
      if (branch.breaks.contains(i)) continue;
      Point projected = project(point.x, point.z, branch.vertices.get(i - 1), branch.vertices.get(i));
      if (distance2(projected.x, projected.z, point.x, point.z) <= .26) return true;
    }
    return false;
  }

  static List<Integer> splitMarkerMembershipForTest(
      List<double[]> vertices, int pivot, List<double[]> markers) {
    Branch left = new Branch("Left"), right = new Branch("Right");
    for (int i = 0; i <= pivot; i++) left.vertices.add(new Point(vertices.get(i)[0], vertices.get(i)[1]));
    for (int i = pivot; i < vertices.size(); i++) right.vertices.add(new Point(vertices.get(i)[0], vertices.get(i)[1]));
    List<Integer> memberships = new ArrayList<>();
    for (double[] marker : markers) {
      Point point = new Point(marker[0], marker[1]);
      memberships.add((pointOnBranch(left, point) ? 1 : 0) | (pointOnBranch(right, point) ? 2 : 0));
    }
    return memberships;
  }

  private static void mergeNearestBranch() {
    Draft d = draft();
    if (d.branches.size() < 2 || branch().vertices.isEmpty()) {
      notice("Another non-empty branch is required");
      return;
    }
    int sourceIndex = d.branch, targetIndex = -1;
    Branch source = branch();
    boolean reverseSource = false, reverseTarget = false;
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i < d.branches.size(); i++) {
      if (i == sourceIndex || d.branches.get(i).vertices.isEmpty()) continue;
      Branch target = d.branches.get(i);
      for (int se = 0; se < 2; se++)
        for (int te = 0; te < 2; te++) {
          Point a = se == 0 ? source.vertices.getFirst() : source.vertices.getLast(),
              b = te == 0 ? target.vertices.getFirst() : target.vertices.getLast();
          double q = distance2(a.x, a.z, b.x, b.z);
          if (q < best) {
            best = q;
            targetIndex = i;
            reverseSource = se == 0;
            reverseTarget = te == 1;
          }
        }
    }
    if (targetIndex < 0) {
      notice("Another non-empty branch is required");
      return;
    }
    checkpoint();
    Branch target = d.branches.get(targetIndex);
    if (reverseSource) Collections.reverse(source.vertices);
    List<Point> tail = new ArrayList<>(target.vertices);
    if (reverseTarget) Collections.reverse(tail);
    if (!source.vertices.getLast().equals(tail.getFirst())) source.vertices.add(tail.getFirst());
    source.vertices.addAll(tail.subList(1, tail.size()));
    for (Integer id : target.stationIds)
      if (!source.stationIds.contains(id)) source.stationIds.add(id);
    String targetName = target.name;
    d.branches.remove(targetIndex);
    d.branch = targetIndex < sourceIndex ? sourceIndex - 1 : sourceIndex;
    branchPanel = true;
    saveLibraryQuiet();
    notice("Merged " + targetName + " into " + source.name);
  }

  private static String uniqueBranchName(Draft d, String base) {
    String name = base;
    int suffix = 2;
    while (hasBranchName(d, name)) name = base + " " + suffix++;
    return name;
  }

  private static boolean hasBranchName(Draft d, String name) {
    for (Branch b : d.branches) if (b.name.equals(name)) return true;
    return false;
  }

  private static void startNameEdit(int kind, String value) {
    nameEditing = kind;
    nameInput = value;
    coordinateField = 0;
  }

  private static String nameKind() {
    return switch (nameEditing) {
      case 1 -> "marker";
      case 2 -> "branch";
      case 3 -> "line";
      case 5 -> "highway";
      case 6 -> "draft";
      default -> "line color";
    };
  }

  private static void cancelNameEdit() {
    nameEditing = 0;
    nameInput = "";
  }

  private static void applyNameEdit() {
    String value = nameInput.trim();
    if (value.isEmpty()) {
      notice("Name cannot be empty");
      return;
    }
    if (nameEditing == 6
        && drafts.stream().anyMatch(d -> d != draft() && d.name.equals(value))) {
      notice("Draft name already exists");
      return;
    }
    if (nameEditing == 5) {
      storeActiveLine(draft());
      if (!draft().company.equals(value)
          && draft().lines.stream().anyMatch(line -> line.company.equals(value))) {
        notice("Highway name already exists");
        return;
      }
    }
    checkpoint();
    if (nameEditing == 1) {
      Station old = selectedStation();
      if (old == null) {
        cancelNameEdit();
        return;
      }
      replaceMarkerEverywhere(
          draft(), old, new Station(old.id, value, old.type, old.x, old.y, old.z), false);
    } else if (nameEditing == 2) {
      if (hasBranchName(draft(), value) && !branch().name.equals(value)) {
        notice("Branch name already exists");
        return;
      }
      branch().name = value;
    } else if (nameEditing == 3) draft().line = value;
    else if (nameEditing == 4) {
      String color = normalizeColor(value);
      if (color == null) {
        notice("Enter a 6-digit hex color, for example #55AAFF");
        return;
      }
      draft().color = color;
    } else if (nameEditing == 5) {
      Draft d = draft();
      String previous = d.company;
      for (LineData line : d.lines)
        if (line.company.equals(previous)) line.company = value;
      d.company = value;
    } else if (nameEditing == 6) {
      draft().name = value;
    }
    String kind = nameKind();
    cancelNameEdit();
    saveLibraryQuiet();
    notice("Renamed " + kind + " to " + value);
  }

  private static void selectMarker(int id) {
    branchPanel = false;
    clearSegmentSelection();
    selectedMarker = id;
    markerEditing = false;
    Station s = selectedStation();
    if (s != null) loadMarkerCoordinates(s);
  }

  private static void loadMarkerCoordinates(Station s) {
    coordinateX = number(s.x);
    coordinateY = number(s.y);
    coordinateZ = number(s.z);
  }

  private static void clearMarkerSelection() {
    selectedMarker = -1;
    markerEditing = false;
    markerMembershipPanel = false;
    markerMembershipPage = 0;
    clearSegmentSelection();
    clearPointSelection();
    pointConnecting = false;
    connectionStart = null;
    branchPanel = false;
    newLinePending = false;
    cancelNameEdit();
    dragging = false;
    dragUndoRecorded = false;
    dragStation = dragBranch = dragVertex = -1;
  }

  private static void applyMarkerCoordinates() {
    Station old = selectedStation();
    if (old == null) return;
    try {
      double x = blockCenter(Double.parseDouble(coordinateX)),
          y = Double.parseDouble(coordinateY),
          z = blockCenter(Double.parseDouble(coordinateZ));
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
        throw new NumberFormatException();
      checkpoint();
      Draft d = draft();
      replaceMarkerEverywhere(
          d, old, new Station(old.id, old.name, old.type, x, y, z), true);
      coordinateField = 0;
      saveLibraryQuiet();
      notice("Updated marker to " + number(x) + ", " + number(y) + ", " + number(z));
    } catch (NumberFormatException e) {
      notice("Enter valid X, Y and Z");
    }
  }

  private static void deleteSelectedMarker() {
    Station s = selectedStation();
    if (s == null) return;
    checkpoint();
    Draft d = draft();
    storeActiveLine(d);
    for (LineData line : d.lines) {
      line.stations.removeIf(candidate -> candidate.id == s.id);
      for (Branch b : line.branches) b.stationIds.removeIf(id -> id == s.id);
    }
    clearMarkerSelection();
    saveLibraryQuiet();
    notice("Deleted " + s.name);
  }

  private static Station markerById(Draft d, int id) {
    storeActiveLine(d);
    for (LineData line : d.lines)
      for (Station station : line.stations) if (station.id == id) return station;
    return null;
  }

  private static void replaceMarkerEverywhere(
      Draft d, Station old, Station replacement, boolean moveAttachedVertices) {
    storeActiveLine(d);
    for (LineData line : d.lines) {
      for (int i = 0; i < line.stations.size(); i++)
        if (line.stations.get(i).id == old.id) line.stations.set(i, replacement);
      if (!moveAttachedVertices) continue;
      for (Branch b : line.branches)
        if (b.stationIds.contains(old.id))
          for (int i = 0; i < b.vertices.size(); i++) {
            Point point = b.vertices.get(i);
            if (point.x == old.x && point.z == old.z)
              b.vertices.set(i, new Point(replacement.x, replacement.z, point.y));
          }
    }
  }

  private static void placeCoordinates() {
    if (markerEditing && selectedStation() != null) {
      applyMarkerCoordinates();
      return;
    }
    if (shouldEditSelectedPoint(pointEditing) && validSelectedPoint()) {
      applySelectedPointCoordinates();
      return;
    }
    try {
      double x = Double.parseDouble(coordinateX),
          y = Double.parseDouble(coordinateY),
          z = Double.parseDouble(coordinateZ);
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
        throw new NumberFormatException();
      if (pointConnecting) finishPointConnection(new Point(x, z));
      else {
        placeExact(x, y, z);
        notice("Placed exact " + x + ", " + y + ", " + z);
      }
      coordinateField = 0;
    } catch (NumberFormatException e) {
      notice("Enter valid X, Y and Z");
    }
  }

  private static void placeAtPlayer() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      notice("Player position unavailable");
      return;
    }
    double scale = RouteFinderMod.dimensionCoordinateScale(),
        x = mc.player.getX() * scale,
        y = mc.player.getY(),
        z = mc.player.getZ() * scale;
    coordinateX = number(x);
    coordinateY = number(y);
    coordinateZ = number(z);
    if (markerEditing && selectedStation() != null) applyMarkerCoordinates();
    else if (shouldEditSelectedPoint(pointEditing) && validSelectedPoint())
      applySelectedPointCoordinates();
    else if (pointConnecting) finishPointConnection(new Point(x, z));
    else {
      placeExact(x, y, z);
      notice("Placed at " + coordinateX + ", " + coordinateY + ", " + coordinateZ);
    }
  }

  static boolean shouldEditSelectedPoint(boolean editMode) {
    return editMode;
  }

  private static void place(double x, double z) {
    checkpoint();
    Draft d = draft();
    Branch b = branch();
    double y = parseY();
    x = blockCenter(x);
    z = blockCenter(z);
    if (tool == 0) {
      b.y = y;
      addPoint(b, x, y, z);
    } else {
      int id = nextId(d);
      d.stations.add(new Station(id, LABELS[tool] + " " + (id + 1), TYPES[tool], x, y, z));
      b.stationIds.add(id);
    }
    saveLibraryQuiet();
  }

  private static void placeExact(double x, double y, double z) {
    checkpoint();
    Draft d = draft();
    Branch b = branch();
    x = blockCenter(x);
    z = blockCenter(z);
    if (tool == 0) {
      addPoint(b, x, y, z);
      b.y = y;
    } else {
      int id = nextId(d);
      d.stations.add(new Station(id, LABELS[tool] + " " + (id + 1), TYPES[tool], x, y, z));
      b.stationIds.add(id);
    }
    saveLibraryQuiet();
  }

  private static double parseY() {
    try {
      double y = Double.parseDouble(coordinateY);
      return Double.isFinite(y) ? y : 64;
    } catch (NumberFormatException e) {
      return 64;
    }
  }

  /**
   * Default click snapping: marker, segment projection, then an 8-way (45 degree) line direction.
   */
  static Point snapPoint(Draft d, Branch current, double x, double z) {
    double limit2 = 32 * 32, best = limit2;
    Point snapped = null;
    if (snapSettings.enabled(SnapTarget.STATION))
      for (Station s : d.stations) {
        double q = distance2(x, z, s.x, s.z);
        if (q <= best) {
          best = q;
          snapped = new Point(blockCenter(s.x), blockCenter(s.z));
        }
      }
    if (snapSettings.enabled(SnapTarget.VERTEX))
      for (Branch b : d.branches)
        for (Point p : b.vertices) {
          double q = distance2(x, z, p.x, p.z);
          if (q <= best) {
            best = q;
            snapped = p;
          }
        }
    if (snapSettings.enabled(SnapTarget.SEGMENT))
      for (Branch b : d.branches)
        for (int i = 1; i < b.vertices.size(); i++) {
          if (b.breaks.contains(i)) continue;
          Point p = project(x, z, b.vertices.get(i - 1), b.vertices.get(i));
          double q = distance2(x, z, p.x, p.z);
          if (q <= best) {
            best = q;
            snapped = p;
          }
        }
    if (snapped != null) return snapped;
    if (snapSettings.enabled(SnapTarget.CHUNK_CENTER))
      return new Point(Math.floor(x / 16) * 16 + 8.5, Math.floor(z / 16) * 16 + 8.5);
    if (current.vertices.isEmpty() || !snapSettings.enabled(SnapTarget.GRID))
      return new Point(blockCenter(x), blockCenter(z));
    Point from = current.vertices.getLast();
    double[] p = snapDirection(from.x, from.z, x, z);
    return new Point(p[0], p[1]);
  }

  static double[] snapDirection(double fromX, double fromZ, double x, double z) {
    double dx = x - fromX, dz = z - fromZ, length = Math.hypot(dx, dz);
    if (length == 0) return new double[] {blockCenter(fromX), blockCenter(fromZ)};
    double angle = Math.round(Math.atan2(dz, dx) / (Math.PI / 4)) * (Math.PI / 4);
    return new double[] {
      blockCenter(fromX + Math.cos(angle) * length), blockCenter(fromZ + Math.sin(angle) * length)
    };
  }

  static double[] projectOntoSegment(
      double x, double z, double ax, double az, double bx, double bz) {
    Point p = project(x, z, new Point(ax, az), new Point(bx, bz));
    return new double[] {p.x, p.z};
  }

  private static Point project(double x, double z, Point a, Point b) {
    double dx = b.x - a.x,
        dz = b.z - a.z,
        q = dx * dx + dz * dz,
        t = q == 0 ? 0 : Math.clamp(((x - a.x) * dx + (z - a.z) * dz) / q, 0, 1);
    return new Point(blockCenter(a.x + dx * t), blockCenter(a.z + dz * t));
  }

  private static double distance2(double x, double z, double a, double b) {
    double dx = x - a, dz = z - b;
    return dx * dx + dz * dz;
  }

  static boolean isCorner(Point a, Point b, Point c) {
    double x1 = b.x - a.x, z1 = b.z - a.z, x2 = c.x - b.x, z2 = c.z - b.z;
    double cross = x1 * z2 - z1 * x2, dot = x1 * x2 + z1 * z2;
    return Math.abs(cross) > 1e-6 || dot < 0;
  }

  private static void previousDraft() {
    saveLibraryQuiet();
    draftIndex = Math.floorMod(draftIndex - 1, drafts.size());
    notice("Opened " + draft().name);
  }

  private static void nextDraft() {
    saveLibraryQuiet();
    draftIndex = (draftIndex + 1) % drafts.size();
    notice("Opened " + draft().name);
  }

  private static void newDraft() {
    checkpoint();
    saveLibraryQuiet();
    drafts.add(new Draft("Draft " + (drafts.size() + 1)));
    draftIndex = drafts.size() - 1;
    clearMarkerSelection();
    saveLibraryQuiet();
    notice("Created " + draft().name);
  }

  private static void deleteDraft() {
    checkpoint();
    String old = draft().name;
    drafts.remove(draftIndex);
    if (drafts.isEmpty()) drafts.add(new Draft("Draft 1"));
    draftIndex = Math.clamp(draftIndex, 0, drafts.size() - 1);
    clearMarkerSelection();
    saveLibraryQuiet();
    notice("Deleted " + old);
  }

  private static void newBranch() {
    checkpoint();
    Draft d = draft();
    d.branches.add(new Branch("Branch " + (d.branches.size() + 1)));
    d.branch = d.branches.size() - 1;
    tool = 0;
    saveLibraryQuiet();
    notice("Editing " + branch().name);
  }

  private static void newPlannedLine() {
    checkpoint();
    Draft d = draft();
    LineData created = addNewPlannedLine(d);
    activateLine(d, d.lines.size() - 1);
    clearMarkerSelection();
    activateTool(EditorTool.SELECT);
    branchPanel = true;
    saveLibraryQuiet();
    notice("Created line " + created.line);
  }

  private static void setAllLinesVisible(boolean visible) {
    Draft d = draft();
    storeActiveLine(d);
    checkpoint();
    for (LineData line : d.lines) line.visible = visible;
    bindLine(d, d.activeLine);
    saveLibraryQuiet();
    notice(visible ? "Showing all planned lines" : "Hiding all planned lines");
  }

  static boolean[] bulkLineVisibilityForTest(boolean visible) {
    Draft d = new Draft("Visibility test");
    d.lines.add(new LineData());
    d.lines.add(new LineData());
    for (LineData line : d.lines) line.visible = visible;
    boolean[] result = new boolean[d.lines.size()];
    for (int i = 0; i < d.lines.size(); i++) result[i] = d.lines.get(i).visible;
    return result;
  }

  private static LineData addNewPlannedLine(Draft d) {
    storeActiveLine(d);
    LineData created = new LineData();
    created.company = d.company;
    created.line = uniqueLineName(d, "Planned Line");
    created.prefix = d.prefix;
    created.code = d.code;
    created.color = d.color;
    if (!d.branches.isEmpty()) created.branches.getFirst().y = d.branches.getFirst().y;
    d.lines.add(created);
    return created;
  }

  static JsonObject newPlannedLineJsonForTest() {
    Draft draft = new Draft("New line test");
    addNewPlannedLine(draft);
    return websiteJson(draft, false);
  }

  private static void requestDeleteSelectedLine() {
    if (System.currentTimeMillis() >= lineDeleteConfirmUntil) {
      lineDeleteConfirmUntil = System.currentTimeMillis() + 4_000;
      notice("Click Confirm delete line to delete " + draft().line);
      return;
    }
    checkpoint();
    Draft d = draft();
    String removed = removeLine(d, d.activeLine);
    lineDeleteConfirmUntil = 0;
    clearMarkerSelection();
    activateTool(EditorTool.SELECT);
    branchPanel = true;
    saveLibraryQuiet();
    notice("Deleted planned line " + removed);
  }

  private static String removeLine(Draft d, int index) {
    storeActiveLine(d);
    index = Math.clamp(index, 0, d.lines.size() - 1);
    LineData removed = d.lines.remove(index);
    if (d.lines.isEmpty()) {
      LineData replacement = new LineData();
      replacement.company = removed.company;
      replacement.prefix = removed.prefix;
      replacement.code = removed.code;
      replacement.color = removed.color;
      replacement.branches.getFirst().y =
          removed.branches.isEmpty() ? 64 : removed.branches.getFirst().y;
      d.lines.add(replacement);
    }
    bindLine(d, Math.min(index, d.lines.size() - 1));
    return removed.line;
  }

  static JsonObject deletePlannedLineForTest(boolean deleteLast) {
    Draft d = new Draft("Delete line test");
    d.branches.getFirst().vertices.addAll(List.of(new Point(0.5, 0.5, 50), new Point(1.5, 0.5, 50)));
    addNewPlannedLine(d);
    bindLine(d, deleteLast ? 1 : 0);
    String removed = removeLine(d, d.activeLine);
    if (deleteLast) removeLine(d, 0);
    JsonObject result = new JsonObject();
    result.addProperty("removed", removed);
    result.addProperty("lineCount", d.lines.size());
    result.addProperty("activeLine", d.line);
    result.addProperty("branchCount", d.branches.size());
    result.add("export", websiteJson(d, false));
    result.addProperty("reloadLines", parseDraft(websiteJson(d, false)).lines.size());
    return result;
  }

  private static void deleteBranch() {
    checkpoint();
    Draft d = draft();
    Branch removed = branch();
    Set<Integer> ids = Set.copyOf(removed.stationIds);
    int removedIndex = d.branch;
    d.branches.remove(removedIndex);
    if (d.branches.isEmpty()) d.branches.add(new Branch("Main"));
    d.branch = Math.clamp(removedIndex, 0, d.branches.size() - 1);
    Set<Integer> stillReferenced = new HashSet<>();
    for (Branch remaining : d.branches) stillReferenced.addAll(remaining.stationIds);
    d.stations.removeIf(s -> ids.contains(s.id) && !stillReferenced.contains(s.id));
    clearMarkerSelection();
    branchPanel = true;
    notice("Deleted " + removed.name);
    saveLibraryQuiet();
  }

  private static void checkpoint() {
    undoHistory.addLast(libraryJson().deepCopy());
    while (undoHistory.size() > 100) undoHistory.removeFirst();
    redoHistory.clear();
    editorState.changed();
    autosave.changed(System.currentTimeMillis());
  }

  private static void undo() {
    if (undoHistory.isEmpty()) {
      notice("Nothing to undo");
      return;
    }
    redoHistory.addLast(libraryJson().deepCopy());
    while (redoHistory.size() > 100) redoHistory.removeFirst();
    restoreSnapshot(undoHistory.removeLast());
    editorState.changed();
    notice("Undid last change");
  }

  private static void redo() {
    if (redoHistory.isEmpty()) {
      notice("Nothing to redo");
      return;
    }
    undoHistory.addLast(libraryJson().deepCopy());
    while (undoHistory.size() > 100) undoHistory.removeFirst();
    restoreSnapshot(redoHistory.removeLast());
    editorState.changed();
    notice("Redid last change");
  }

  private static void restoreSnapshot(JsonObject root) {
    drafts.clear();
    for (JsonElement e : root.getAsJsonArray("drafts")) drafts.add(parseDraft(e.getAsJsonObject()));
    if (drafts.isEmpty()) drafts.add(new Draft("Draft 1"));
    draftIndex =
        Math.clamp(
            root.has("activeDraft") ? root.get("activeDraft").getAsInt() : 0, 0, drafts.size() - 1);
    clearMarkerSelection();
    saveLibraryQuiet();
  }

  private static int nextId(Draft d) {
    storeActiveLine(d);
    int max = -1;
    for (LineData line : d.lines)
      for (Station station : line.stations) max = Math.max(max, station.id);
    return max + 1;
  }

  private static void addPoint(Branch b, double x, double y, double z) {
    Point p = new Point(blockCenter(x), blockCenter(z), y);
    if (b.vertices.contains(p)) {
      notice("That point already exists; select it to connect");
      newLinePending = false;
      return;
    }
    addStandalonePoint(b, p);
    newLinePending = false;
  }

  static void addStandalonePointForTest(
      List<double[]> points, NavigableSet<Integer> breaks, double x, double z) {
    int index = points.size();
    if (index > 0) breaks.add(index);
    points.add(new double[] {blockCenter(x), blockCenter(z)});
  }

  private static void addStandalonePoint(Branch b, Point p) {
    if (!b.vertices.isEmpty()) b.breaks.add(b.vertices.size());
    b.vertices.add(p);
  }

  static JsonObject websiteJson() {
    return websiteJson(draft(), true);
  }

  static JsonObject websiteJsonForTest() {
    return websiteJson(new Draft("Test"), true);
  }

  static JsonObject multiLineJsonForTest() {
    Draft draft = new Draft("Test");
    draft.line = "First";
    storeActiveLine(draft);
    LineData second = new LineData();
    second.company = draft.company;
    second.line = "Second";
    draft.lines.add(second);
    return websiteJson(draft, true);
  }

  static JsonObject sharedMarkerJsonForTest() {
    Draft draft = new Draft("Shared marker");
    Station shared = new Station(7, "Shared Station", "station", 10.5, 64, 20.5);
    draft.stations.add(shared);
    draft.branches.getFirst().stationIds.add(shared.id);
    storeActiveLine(draft);
    LineData second = new LineData();
    second.company = draft.company;
    second.line = "Second";
    second.stations.add(shared);
    second.branches.getFirst().stationIds.add(shared.id);
    draft.lines.add(second);
    return websiteJson(draft, true);
  }

  static JsonObject sharedVertexJsonForTest() {
    Draft draft = new Draft("Shared vertex");
    Point shared = new Point(10.5, 20.5, 50);
    draft.branches.getFirst().vertices.add(shared);
    Branch secondBranch = new Branch("Second branch");
    secondBranch.vertices.add(new Point(shared.x, shared.z, shared.y));
    draft.branches.add(secondBranch);
    storeActiveLine(draft);
    LineData secondLine = new LineData();
    secondLine.company = draft.company;
    secondLine.line = "Second line";
    secondLine.branches.getFirst().vertices.add(new Point(shared.x, shared.z, shared.y));
    draft.lines.add(secondLine);
    return websiteJson(draft, true);
  }

  static double[][] moveSharedVertexForTest() {
    Draft draft = new Draft("Shared vertex movement");
    Point shared = new Point(10.5, 20.5, 50);
    draft.branches.getFirst().vertices.add(shared);
    storeActiveLine(draft);
    LineData secondLine = new LineData();
    secondLine.line = "Second line";
    secondLine.branches.getFirst().vertices.add(new Point(shared.x, shared.z, shared.y));
    draft.lines.add(secondLine);
    replaceSharedPointEverywhere(draft, shared, 30.5, 40.5, 60.0);
    Point first = draft.lines.get(0).branches.getFirst().vertices.getFirst();
    Point second = draft.lines.get(1).branches.getFirst().vertices.getFirst();
    return new double[][] {{first.x, first.y, first.z}, {second.x, second.y, second.z}};
  }

  static int parsedLineCountForTest(JsonObject root) {
    return parseDraft(root).lines.size();
  }

  static boolean parsedLineVisibilityForTest(JsonObject root, int line) {
    Draft draft = parseDraft(root);
    return draft.lines.get(line).visible;
  }

  static boolean parsedBranchVisibilityForTest(JsonObject root, int line, int branch) {
    Draft draft = parseDraft(root);
    return draft.lines.get(line).branches.get(branch).visible;
  }

  static int[] parsedContentCountsForTest(JsonObject root) {
    Draft draft = parseDraft(root);
    int branches = 0, vertices = 0, stations = 0, links = 0;
    for (LineData line : draft.lines) {
      branches += line.branches.size();
      stations += line.stations.size();
      for (Branch branch : line.branches) {
        vertices += branch.vertices.size();
        links += branch.links.size();
      }
    }
    return new int[] {draft.lines.size(), branches, vertices, stations, links};
  }

  private static JsonObject websiteJson(Draft d, boolean splitRuns) {
    storeActiveLine(d);
    int active = d.activeLine;
    JsonObject combined = new JsonObject(), combinedLines = new JsonObject();
    Map<Integer, JsonObject> stations = new LinkedHashMap<>();
    for (int i = 0; i < d.lines.size(); i++) {
      activateLine(d, i);
      JsonObject part = websiteJsonSingle(d, splitRuns);
      for (var companyEntry : part.getAsJsonObject("lines").entrySet()) {
        JsonObject targetCompany =
            combinedLines.has(companyEntry.getKey())
                ? combinedLines.getAsJsonObject(companyEntry.getKey())
                : new JsonObject();
        for (var lineEntry : companyEntry.getValue().getAsJsonObject().entrySet())
          targetCompany.add(lineEntry.getKey(), lineEntry.getValue().deepCopy());
        combinedLines.add(companyEntry.getKey(), targetCompany);
      }
      for (JsonElement element : part.getAsJsonArray("stations")) {
        JsonObject station = element.getAsJsonObject();
        int id = station.get("id").getAsInt();
        if (!stations.containsKey(id)) stations.put(id, station.deepCopy());
        else {
          JsonObject targetLines = stations.get(id).getAsJsonObject("lines");
          for (var companyEntry : station.getAsJsonObject("lines").entrySet()) {
            JsonObject company =
                targetLines.has(companyEntry.getKey())
                    ? targetLines.getAsJsonObject(companyEntry.getKey())
                    : new JsonObject();
            for (var lineEntry : companyEntry.getValue().getAsJsonObject().entrySet())
              company.add(lineEntry.getKey(), lineEntry.getValue().deepCopy());
            targetLines.add(companyEntry.getKey(), company);
          }
        }
      }
    }
    activateLine(d, active);
    JsonArray stationArray = new JsonArray();
    stations.values().forEach(stationArray::add);
    combined.add("stations", stationArray);
    combined.add("lines", combinedLines);
    if (splitRuns) normalizeWebsiteStationIds(combined);
    return combined;
  }

  // The website indexes stations directly by ID. Keep planner IDs stable in saved drafts,
  // but assign dense array indices after merging shared stations for public exports.
  private static void normalizeWebsiteStationIds(JsonObject root) {
    Map<Integer, Integer> ids = new LinkedHashMap<>();
    JsonArray stations = root.getAsJsonArray("stations");
    for (int i = 0; i < stations.size(); i++) {
      JsonObject station = stations.get(i).getAsJsonObject();
      ids.put(station.get("id").getAsInt(), i);
      station.addProperty("id", i);
    }
    for (JsonElement company : root.getAsJsonObject("lines").asMap().values())
      for (JsonElement line : company.getAsJsonObject().asMap().values())
        for (JsonElement branch : line.getAsJsonObject().getAsJsonObject("branches").asMap().values()) {
          JsonArray refs = branch.getAsJsonObject().getAsJsonArray("stations");
          for (int i = 0; i < refs.size(); i++) {
            JsonElement ref = refs.get(i);
            int oldId = ref.isJsonArray() ? ref.getAsJsonArray().get(0).getAsInt() : ref.getAsInt();
            Integer id = ids.get(oldId);
            if (id == null) throw new IllegalStateException("Branch references missing marker " + oldId);
            if (ref.isJsonArray()) ref.getAsJsonArray().set(0, new com.google.gson.JsonPrimitive(id));
            else refs.set(i, new com.google.gson.JsonPrimitive(id));
          }
        }
  }

  static JsonObject websiteExportForTest(JsonObject source) {
    return websiteJson(parseDraft(source), true);
  }

  static JsonObject unassignedMarkerExportForTest() {
    Draft d = new Draft("Unassigned");
    d.stations.add(new Station(12, "Unassigned", "station", 5, 64, 5));
    return websiteJson(d, true);
  }

  private static JsonObject websiteJsonSingle(Draft d, boolean splitRuns) {
    JsonObject root = new JsonObject();
    JsonArray ss = new JsonArray();
    for (Station s : d.stations) {
      JsonObject o = new JsonObject();
      o.addProperty("name", s.name);
      o.addProperty("id", s.id);
      if (!"station".equals(s.type)) o.addProperty("type", s.type);
      o.addProperty("x", s.x);
      o.addProperty("z", s.z);
      if (s.type.startsWith("elev")) {
        o.addProperty("y1", s.y);
        o.addProperty("y2", lineHeight(d));
      }
      JsonObject company = new JsonObject(), line = new JsonObject();
      JsonArray membership = new JsonArray();
      membership.add("");
      membership.add(websiteBranchMembership(d, s.id));
      if (!splitRuns || !branchesForStation(d, s.id).isEmpty()) {
        line.add(d.line, membership);
        company.add(d.company, line);
      }
      o.add("lines", company);
      ss.add(o);
    }
    root.add("stations", ss);
    JsonObject lines = new JsonObject(),
        company = new JsonObject(),
        route = new JsonObject(),
        bs = new JsonObject();
    route.addProperty("prefix", d.prefix);
    route.addProperty("code", d.code);
    route.addProperty("color", d.color);
    route.addProperty("y", lineHeight(d));
    for (Branch b : d.branches) addBranchJson(bs, d, b, splitRuns);
    route.add("branches", bs);
    company.add(d.line, route);
    lines.add(d.company, company);
    root.add("lines", lines);
    return root;
  }

  private static String branchFor(Draft d, int id) {
    for (Branch b : d.branches) if (b.stationIds.contains(id)) return b.name;
    return "Main";
  }

  private static double lineHeight(Draft d) {
    return d.branches.isEmpty() ? 64 : d.branches.getFirst().y;
  }

  private static List<Branch> branchesForStation(Draft d, int id) {
    List<Branch> found = new ArrayList<>();
    for (Branch b : d.branches) if (b.stationIds.contains(id)) found.add(b);
    return found;
  }

  private static String websiteBranchMembership(Draft d, int id) {
    List<Branch> found = branchesForStation(d, id);
    if (found.isEmpty()) return "";
    StringJoiner names = new StringJoiner(found.size() > 2 ? " and " : " to ");
    for (Branch b : found) names.add(b.name);
    return names.toString();
  }

  private static void addWebsiteStationReference(JsonArray out, Draft d, Branch current, int id) {
    List<Branch> found = branchesForStation(d, id);
    if (found.size() < 2) {
      out.add(id);
      return;
    }
    Branch other = null;
    for (Branch candidate : found)
      if (candidate != current) {
        other = candidate;
        break;
      }
    if (other == null) {
      out.add(id);
      return;
    }
    JsonArray reference = new JsonArray();
    reference.add(id);
    reference.add(other.name);
    out.add(reference);
  }

  private static void addBranchJson(JsonObject out, Draft d, Branch b, boolean splitRuns) {
    if (!splitRuns || b.breaks.isEmpty()) {
      out.add(b.name, branchJson(d, b, b.vertices, b.stationIds));
      if (!splitRuns) {
        JsonObject saved = out.getAsJsonObject(b.name);
        JsonArray ys = new JsonArray();
        for (Point point : b.vertices) ys.add(pointY(point, b));
        saved.add("plannerVertexYs", ys);
        if (!b.breaks.isEmpty()) {
          JsonArray breaks = new JsonArray();
          b.breaks.forEach(breaks::add);
          saved.add("plannerBreaks", breaks);
        }
        if (!b.links.isEmpty()) {
          JsonArray links = new JsonArray();
          for (Link link : b.links) {
            JsonArray pair = new JsonArray();
            pair.add(pointJson(link.a));
            pair.add(pointJson(link.b));
            links.add(pair);
          }
          saved.add("plannerLinks", links);
        }
      }
    } else {
      List<Integer> cuts = new ArrayList<>(b.breaks);
      cuts.add(b.vertices.size());
      int from = 0, run = 1;
      for (int to : cuts) {
        if (to > from) {
          String name = run == 1 ? b.name : b.name + " Line " + run;
          out.add(
              name,
              branchJson(d, b, b.vertices.subList(from, to), run == 1 ? b.stationIds : List.of()));
          run++;
        }
        from = to;
      }
    }
    if (splitRuns)
      for (int i = 0; i < b.links.size(); i++) {
        Link link = b.links.get(i);
        out.add(
            b.name + " Connection " + (i + 1),
            branchJson(d, b, List.of(link.a, link.b), List.of()));
      }
  }

  private static JsonArray pointJson(Point p) {
    JsonArray value = new JsonArray();
    value.add(p.x);
    value.add(p.z);
    return value;
  }

  private static JsonObject branchJson(Draft d, Branch b, List<Point> points, List<Integer> ids) {
    JsonObject bo = new JsonObject();
    JsonArray vs = new JsonArray();
    for (Point p : points) {
      JsonArray v = new JsonArray();
      v.add(p.x);
      v.add(p.z);
      vs.add(v);
    }
    JsonArray stations = new JsonArray();
    for (int id : ids) addWebsiteStationReference(stations, d, b, id);
    bo.add("vertices", vs);
    bo.add("stations", stations);
    return bo;
  }

  private static JsonObject libraryJson() {
    JsonObject root = new JsonObject();
    root.addProperty("activeDraft", draftIndex);
    JsonArray all = new JsonArray();
    for (Draft d : drafts) {
      storeActiveLine(d);
      JsonObject o = websiteJson(d, false);
      o.addProperty("draftName", d.name);
      o.addProperty("activeLine", d.activeLine);
      o.addProperty("showMarkerInfo", d.coordinateMode != 3 && d.coordinateMode != 4);
      o.addProperty("coordinateMode", d.coordinateMode);
      JsonArray lineStates = new JsonArray();
      for (LineData line : d.lines) {
        JsonObject state = new JsonObject(), ys = new JsonObject(), branchVisibility = new JsonObject();
        state.addProperty("company", line.company);
        state.addProperty("line", line.line);
        state.addProperty("activeBranch", line.branch);
        state.addProperty("visible", line.visible);
        for (Branch branch : line.branches) {
          ys.addProperty(branch.name, branch.y);
          branchVisibility.addProperty(branch.name, branch.visible);
        }
        state.add("branchYs", ys);
        state.add("branchVisibility", branchVisibility);
        lineStates.add(state);
      }
      o.add("plannerLineStates", lineStates);
      all.add(o);
    }
    root.add("drafts", all);
    return root;
  }

  private static void loadLibrary() {
    Path p = libraryPath();
    if (!Files.isRegularFile(p)) {
      p = legacyLibraryPath();
      if (!Files.isRegularFile(p)) {
        loadLegacy();
        return;
      }
    }
    try {
      JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
      if (root.has("drafts")) {
        for (JsonElement e : root.getAsJsonArray("drafts"))
          drafts.add(parseDraft(e.getAsJsonObject()));
        draftIndex = root.has("activeDraft") ? root.get("activeDraft").getAsInt() : 0;
      } else if (root.has("lines")) {
        Draft recovered = parseDraft(root);
        recovered.name = "Recovered Draft 1";
        drafts.add(recovered);
        draftIndex = 0;
      }
      if (drafts.stream().allMatch(IceRoadPlannerOverlay::emptyDraft)) {
        Draft recovered = recoverNewestExport();
        if (recovered != null) {
          drafts.clear();
          drafts.add(recovered);
          draftIndex = 0;
          saveLibraryQuiet();
          notice("Recovered saved network from latest JSON export");
        }
      }
    } catch (Exception e) {
      drafts.clear();
      Draft recovered = recoverNewestExport();
      if (recovered != null) {
        drafts.add(recovered);
        draftIndex = 0;
        saveLibraryQuiet();
      }
    }
  }

  private static boolean emptyDraft(Draft draft) {
    storeActiveLine(draft);
    for (LineData line : draft.lines) {
      if (!line.stations.isEmpty()) return false;
      for (Branch branch : line.branches) if (!branch.vertices.isEmpty()) return false;
    }
    return true;
  }

  private static Draft recoverNewestExport() {
    Path directory = plannerRoot().resolve("json");
    if (!Files.isDirectory(directory)) return null;
    try (var files = Files.list(directory)) {
      for (Path candidate :
          files
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted(Comparator.comparingLong(IceRoadPlannerOverlay::modified).reversed())
              .toList()) {
        try {
          JsonObject root = JsonParser.parseString(Files.readString(candidate)).getAsJsonObject();
          if (!root.has("lines") || libraryContentCount(root) == 0) continue;
          Draft recovered = parseDraft(root);
          recovered.name = "Recovered Draft 1";
          return recovered;
        } catch (Exception ignored) {
        }
      }
    } catch (IOException ignored) {
    }
    return null;
  }

  private static void loadLegacy() {
    Path p =
        FabricLoader.getInstance().getConfigDir().resolve("earthmcroutefinder-ice-highway-draft.json");
    if (!Files.isRegularFile(p)) return;
    try {
      Draft d = parseDraft(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
      d.name = "Draft 1";
      drafts.add(d);
    } catch (Exception ignored) {
    }
  }

  private static Draft parseDraft(JsonObject root) {
    String name = root.has("draftName") ? root.get("draftName").getAsString() : "Draft";
    JsonObject lines = root.getAsJsonObject("lines");
    Draft d = new Draft(name);
    d.showMarkerInfo = !root.has("showMarkerInfo") || root.get("showMarkerInfo").getAsBoolean();
    d.coordinateMode =
        root.has("coordinateMode")
            ? Math.clamp(root.get("coordinateMode").getAsInt(), 0, 4)
            : (d.showMarkerInfo ? 0 : 3);
    d.lines.clear();
    int stateIndex = 0;
    for (var companyEntry : lines.entrySet())
      for (var lineEntry : companyEntry.getValue().getAsJsonObject().entrySet()) {
        JsonObject state = null;
        if (root.has("plannerLineStates")) {
          JsonArray states = root.getAsJsonArray("plannerLineStates");
          if (stateIndex < states.size()) state = states.get(stateIndex).getAsJsonObject();
        }
        d.lines.add(
            parseLine(
                root,
                companyEntry.getKey(),
                lineEntry.getKey(),
                lineEntry.getValue().getAsJsonObject(),
                state));
        stateIndex++;
      }
    if (d.lines.isEmpty()) d.lines.add(new LineData());
    d.activeLine =
        Math.clamp(root.has("activeLine") ? root.get("activeLine").getAsInt() : 0, 0, d.lines.size() - 1);
    // The Draft constructor's facade still points at its discarded blank default line here.
    // activateLine() would persist that blank facade over the parsed first line before selecting it.
    bindLine(d, d.activeLine);
    return d;
  }

  private static LineData parseLine(
      JsonObject root, String companyName, String lineName, JsonObject route, JsonObject state) {
    LineData line = new LineData();
    line.company = companyName;
    line.line = lineName;
    line.prefix = route.has("prefix") ? route.get("prefix").getAsString() : "";
    line.code = route.has("code") ? route.get("code").getAsString() : "";
    line.color = route.has("color") ? route.get("color").getAsString() : "ff55dd";
    line.visible = state == null || !state.has("visible") || state.get("visible").getAsBoolean();
    line.branches.clear();
    JsonObject branchYs = state != null && state.has("branchYs") ? state.getAsJsonObject("branchYs") : null;
    JsonObject branchVisibility =
        state != null && state.has("branchVisibility")
            ? state.getAsJsonObject("branchVisibility")
            : null;
    for (var entry : route.getAsJsonObject("branches").entrySet()) {
      Branch b = new Branch(entry.getKey());
      JsonObject o = entry.getValue().getAsJsonObject();
      b.y =
          branchYs != null && branchYs.has(entry.getKey())
              ? branchYs.get(entry.getKey()).getAsDouble()
              : root.has("plannerBranchYs") && root.getAsJsonObject("plannerBranchYs").has(entry.getKey())
              ? root.getAsJsonObject("plannerBranchYs").get(entry.getKey()).getAsDouble()
              : (route.has("y") ? route.get("y").getAsDouble() : 64);
      b.visible =
          branchVisibility == null
              || !branchVisibility.has(entry.getKey())
              || branchVisibility.get(entry.getKey()).getAsBoolean();
      for (JsonElement v : o.getAsJsonArray("vertices")) {
        JsonArray a = v.getAsJsonArray();
        b.vertices.add(new Point(a.get(0).getAsDouble(), a.get(1).getAsDouble()));
      }
      if (o.has("plannerVertexYs")) {
        JsonArray ys = o.getAsJsonArray("plannerVertexYs");
        for (int i = 0; i < b.vertices.size() && i < ys.size(); i++) {
          Point point = b.vertices.get(i);
          b.vertices.set(i, new Point(point.x, point.z, ys.get(i).getAsDouble()));
        }
      }
      for (JsonElement id : o.getAsJsonArray("stations"))
        b.stationIds.add(id.isJsonArray() ? id.getAsJsonArray().get(0).getAsInt() : id.getAsInt());
      if (o.has("plannerBreaks"))
        for (JsonElement at : o.getAsJsonArray("plannerBreaks")) {
          int index = at.getAsInt();
          if (index > 0 && index < b.vertices.size()) b.breaks.add(index);
        }
      if (o.has("plannerLinks"))
        for (JsonElement linked : o.getAsJsonArray("plannerLinks")) {
          JsonArray pair = linked.getAsJsonArray(),
              a = pair.get(0).getAsJsonArray(),
              c = pair.get(1).getAsJsonArray();
          b.links.add(
              new Link(
                  new Point(a.get(0).getAsDouble(), a.get(1).getAsDouble()),
                  new Point(c.get(0).getAsDouble(), c.get(1).getAsDouble())));
        }
      line.branches.add(b);
    }
    collapseWebsiteExportRuns(line.branches);
    if (line.branches.isEmpty()) line.branches.add(new Branch("Main"));
    Set<Integer> referenced = new HashSet<>();
    for (Branch branch : line.branches) referenced.addAll(branch.stationIds);
    line.stations.clear();
    for (JsonElement e : root.getAsJsonArray("stations")) {
      JsonObject o = e.getAsJsonObject();
      if (!referenced.contains(o.get("id").getAsInt())) continue;
      line.stations.add(
          new Station(
              o.get("id").getAsInt(),
              o.get("name").getAsString(),
              o.has("type") ? o.get("type").getAsString() : "station",
              o.get("x").getAsDouble(),
              o.has("y1")
                  ? o.get("y1").getAsDouble()
                  : (route.has("y") ? route.get("y").getAsDouble() : 64),
              o.get("z").getAsDouble()));
    }
    line.branch =
        Math.clamp(
            state != null && state.has("activeBranch")
                ? state.get("activeBranch").getAsInt()
                : root.has("activeBranch") ? root.get("activeBranch").getAsInt() : 0,
            0,
            line.branches.size() - 1);
    return line;
  }

  /**
   * Reverses addBranchJson(..., true). Website-compatible exports must split one editor branch at
   * every break and serialize free-form links as synthetic branches. When such an export is used
   * for disaster recovery, leaving those synthetic records separate produces dozens of branches
   * and duplicate labels instead of the original editable branch.
   */
  private static void collapseWebsiteExportRuns(List<Branch> branches) {
    Map<String, Branch> byName = new LinkedHashMap<>();
    for (Branch branch : branches) byName.put(branch.name, branch);
    Set<Branch> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
    List<Branch> restored = new ArrayList<>();
    for (Branch base : branches) {
      if (consumed.contains(base)) continue;
      Branch second = byName.get(base.name + " Line 2");
      if (second == null) {
        consumed.add(base);
        restored.add(base);
        continue;
      }
      consumed.add(base);
      Branch merged = new Branch(base.name);
      merged.y = base.y;
      merged.vertices.addAll(base.vertices);
      merged.stationIds.addAll(base.stationIds);
      merged.links.addAll(base.links);
      for (int run = 2; ; run++) {
        Branch part = byName.get(base.name + " Line " + run);
        if (part == null) break;
        consumed.add(part);
        if (!part.vertices.isEmpty()) merged.breaks.add(merged.vertices.size());
        merged.vertices.addAll(part.vertices);
        for (int id : part.stationIds) if (!merged.stationIds.contains(id)) merged.stationIds.add(id);
        merged.links.addAll(part.links);
      }
      for (int connection = 1; ; connection++) {
        Branch part = byName.get(base.name + " Connection " + connection);
        if (part == null) break;
        consumed.add(part);
        if (part.vertices.size() >= 2)
          merged.links.add(new Link(part.vertices.get(0), part.vertices.get(1)));
      }
      restored.add(merged);
    }
    for (Branch branch : branches) if (!consumed.contains(branch)) restored.add(branch);
    branches.clear();
    branches.addAll(restored);
  }

  private static Path libraryPath() {
    return plannerRoot().resolve("drafts").resolve("ice-highway-drafts.json");
  }

  private static Path legacyLibraryPath() {
    return FabricLoader.getInstance()
        .getConfigDir()
        .resolve("earthmcroutefinder-ice-highway-drafts.json");
  }

  private static Path plannerRoot() {
    Minecraft mc = Minecraft.getInstance();
    Path game = mc == null ? FabricLoader.getInstance().getGameDir() : mc.gameDirectory.toPath();
    return game.resolve("earthmcroutefinder").resolve("ice-highway-planner").toAbsolutePath();
  }

  private static Path exportPath() {
    return plannerRoot()
        .resolve("json")
        .resolve("earthmcroutefinder-ice-highway-" + safe(draft().name) + ".json");
  }

  private static String safe(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
  }

  public static boolean imageExportPending() {
    return pendingImageExport;
  }

  private static void tickAutosave() {
    if (editorState.dirty() && autosave.shouldSave(System.currentTimeMillis())) {
      saveLibraryQuiet();
      editorState.saved();
      autosave.saved();
    }
  }

  private static void saveLibrary() {
    saveLibraryQuiet();
    editorState.saved();
    autosave.saved();
    notice("Saved " + draft().name);
  }

  private static void saveLibraryQuiet() {
    Path path = libraryPath(), temporary = path.resolveSibling(path.getFileName() + ".tmp");
    JsonObject next = libraryJson();
    try {
      Files.createDirectories(path.getParent());
      if (Files.isRegularFile(path)) {
        JsonObject previous = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (libraryContentCount(previous) > 0)
          Files.copy(
              path,
              path.resolveSibling(path.getFileName() + ".backup.json"),
              StandardCopyOption.REPLACE_EXISTING);
      }
      Files.writeString(temporary, GSON.toJson(next));
      try {
        Files.move(
            temporary,
            path,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception exception) {
      notice("Could not safely save draft library");
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  private static int libraryContentCount(JsonObject root) {
    int count = 0;
    JsonArray draftRoots = root.has("drafts") ? root.getAsJsonArray("drafts") : null;
    List<JsonObject> documents = new ArrayList<>();
    if (draftRoots == null) documents.add(root);
    else for (JsonElement element : draftRoots) documents.add(element.getAsJsonObject());
    for (JsonObject document : documents) {
      if (document.has("stations")) count += document.getAsJsonArray("stations").size();
      if (!document.has("lines")) continue;
      for (var company : document.getAsJsonObject("lines").entrySet())
        for (var line : company.getValue().getAsJsonObject().entrySet()) {
          JsonObject route = line.getValue().getAsJsonObject();
          if (!route.has("branches")) continue;
          for (var branch : route.getAsJsonObject("branches").entrySet()) {
            JsonObject value = branch.getValue().getAsJsonObject();
            if (value.has("vertices")) count += value.getAsJsonArray("vertices").size();
          }
        }
    }
    return count;
  }

  private static void export() {
    Path p = exportPath();
    write(p, websiteJson());
    copyPath(p);
    notice("JSON saved: " + p + " (path copied)");
  }

  private static void copyJson() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    GLFW.glfwSetClipboardString(mc.getWindow().handle(), GSON.toJson(websiteJson()));
    notice("Website JSON copied to clipboard");
  }

  private static void exportGeoJson() {
    JsonObject root = new JsonObject();
    root.addProperty("type", "FeatureCollection");
    JsonArray features = new JsonArray();
    Draft d = draft();
    for (Branch b : d.branches) {
      int start = 0;
      List<Integer> cuts = new ArrayList<>(b.breaks);
      cuts.add(b.vertices.size());
      for (int end : cuts) {
        if (end - start >= 2) {
          JsonObject feature = new JsonObject(),
              properties = new JsonObject(),
              geometry = new JsonObject();
          feature.addProperty("type", "Feature");
          properties.addProperty("line", d.line);
          properties.addProperty("branch", b.name);
          properties.addProperty("y", b.y);
          geometry.addProperty("type", "LineString");
          JsonArray coordinates = new JsonArray();
          for (Point point : b.vertices.subList(start, end)) {
            JsonArray coordinate = new JsonArray();
            coordinate.add(point.x);
            coordinate.add(pointY(point, b));
            coordinate.add(point.z);
            coordinates.add(coordinate);
          }
          geometry.add("coordinates", coordinates);
          feature.add("properties", properties);
          feature.add("geometry", geometry);
          features.add(feature);
        }
        start = end;
      }
    }
    root.add("features", features);
    Path path =
        plannerRoot()
            .resolve("json")
            .resolve("earthmcroutefinder-ice-highway-" + safe(d.name) + ".geojson");
    write(path, root);
    copyPath(path);
    notice("GeoJSON saved: " + path + " (path copied)");
  }

  private static void importClipboardJson() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    try {
      String text = GLFW.glfwGetClipboardString(mc.getWindow().handle());
      JsonObject source = JsonParser.parseString(text == null ? "" : text).getAsJsonObject();
      JsonObject lines = source.getAsJsonObject("lines");
      JsonArray stations =
          source.has("stations") ? source.getAsJsonArray("stations") : new JsonArray();
      checkpoint();
      int imported = 0;
      for (var companyEntry : lines.entrySet())
        for (var lineEntry : companyEntry.getValue().getAsJsonObject().entrySet()) {
          JsonObject one = new JsonObject(),
              companies = new JsonObject(),
              company = new JsonObject();
          company.add(lineEntry.getKey(), lineEntry.getValue().deepCopy());
          companies.add(companyEntry.getKey(), company);
          one.add("lines", companies);
          one.add("stations", stations.deepCopy());
          one.addProperty("draftName", uniqueDraftName(lineEntry.getKey()));
          drafts.add(parseDraft(one));
          imported++;
        }
      if (imported == 0) throw new IllegalArgumentException("No lines");
      draftIndex = drafts.size() - imported;
      saveLibraryQuiet();
      notice("Imported " + imported + " line" + (imported == 1 ? "" : "s") + " from clipboard");
    } catch (Exception exception) {
      notice("Clipboard does not contain valid Ice Highway Map JSON");
    }
  }

  private static String uniqueDraftName(String base) {
    String candidate = base;
    for (int suffix = 2; ; suffix++) {
      boolean exists = false;
      for (Draft d : drafts) if (d.name.equalsIgnoreCase(candidate)) exists = true;
      if (!exists) return candidate;
      candidate = base + " " + suffix;
    }
  }

  private static void exportImage() {
    saveLibraryQuiet();
    pendingImageExport = true;
    imageExportStartedAt = System.currentTimeMillis();
    Path dir = imageExportDirectory();
    copyPath(dir);
    RouteFinderMod.armMapScreenshot();
    notice("Image will be saved in " + dir + " (path copied)");
  }

  public static void imageExportFinished() {
    if (!pendingImageExport) return;
    Path source = newestPlannerScreenshot();
    if (source == null) return;
    Path dir = imageExportDirectory();
    try {
      Files.createDirectories(dir);
      Path target = dir.resolve(source.getFileName());
      Files.move(source, target);
      pendingImageExport = false;
      copyPath(target);
      notice("Image saved: " + target + " (path copied)");
    } catch (IOException ignored) {
    }
  }

  private static Path screenshotDirectory() {
    Minecraft mc = Minecraft.getInstance();
    Path game = mc == null ? FabricLoader.getInstance().getGameDir() : mc.gameDirectory.toPath();
    return game.resolve("screenshots").toAbsolutePath();
  }

  private static Path imageExportDirectory() {
    return plannerRoot().resolve("images");
  }

  private static Path newestPlannerScreenshot() {
    Path dir = screenshotDirectory();
    try (var files = Files.list(dir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
          .filter(p -> modified(p) >= imageExportStartedAt - 500)
          .max(Comparator.comparingLong(IceRoadPlannerOverlay::modified))
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private static long modified(Path p) {
    try {
      return Files.getLastModifiedTime(p).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }

  private static void copyPath(Path p) {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.keyboardHandler != null) mc.keyboardHandler.setClipboard(p.toString());
  }

  private static void write(Path p, JsonObject json) {
    try {
      Files.createDirectories(p.getParent());
      Files.writeString(p, GSON.toJson(json));
    } catch (IOException e) {
      notice("Save failed: " + p.toAbsolutePath());
    }
  }

  private static int sx(double x, double c, double s, int w) {
    return (int) Math.round(w / 2.0 + (x - c) * s);
  }

  static String normalizeColor(String value) {
    if (value == null) return null;
    String color = value.trim();
    if (color.startsWith("#")) color = color.substring(1);
    return color.matches("(?i)[0-9a-f]{6}") ? color.toLowerCase(Locale.ROOT) : null;
  }

  private static String normalizeStoredColor(String value) {
    String color = normalizeColor(value);
    return color == null ? "ff55dd" : color;
  }

  static int lineArgb(String value, int alpha) {
    return (Math.clamp(alpha, 0, 255) << 24) | Integer.parseInt(normalizeStoredColor(value), 16);
  }

  private static int sy(double z, double c, double s, int h) {
    return (int) Math.round(h / 2.0 + (z - c) * s);
  }

  static double blockCenter(double v) {
    return Math.floor(v) + 0.5;
  }

  private static double pointY(Point point, Branch owner) {
    return Double.isFinite(point.y) ? point.y : owner.y;
  }

  private static String number(double v) {
    return Long.toString((long) Math.floor(v));
  }

  private static void line(
      GuiGraphics g, int x1, int y1, int x2, int y2, int color, int width) {
    double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
    if (len < 1) return;
    var m = g.pose();
    m.pushMatrix();
    try {
      m.translate(x1, y1);
      m.rotate((float) Math.atan2(dy, dx));
      g.fill(0, -width / 2, (int) Math.ceil(len) + 1, (width + 1) / 2, color);
    } finally {
      m.popMatrix();
    }
  }

  private static void notice(String s) {
    feedback = s;
    feedbackUntil = System.currentTimeMillis() + 2_000;
  }

  private static void editorChrome(GuiGraphics g, int sw, int sh) {
    Draft d = draft();
    int right = sw - 8;
    g.fill(PROJECT_X, 4, right, 4 + HEADER_H, 0xF20A1419);
    g.drawString(
        Minecraft.getInstance().font, "ICE HIGHWAY EDITOR", PROJECT_X + 8, 13, 0xFFFFFFFF, false);
    button(g, PROJECT_X + 150, 9, 150, d.name + " v");
    g.drawString(
        Minecraft.getInstance().font,
        editorState.dirty() ? "Saving..." : "Saved",
        PROJECT_X + 310,
        13,
        editorState.dirty() ? 0xFFFFFF77 : 0xFF77FFAA,
        false);
    button(g, right - 188, 9, 86, "Validate");
    button(g, right - 96, 9, 88, "Export v");
    int bottom = sh - 34;
    g.fill(PROJECT_X, 36, PROJECT_X + PROJECT_W, bottom - 6, 0xE80A1419);
    g.drawString(Minecraft.getInstance().font, "NETWORK", PROJECT_X + 8, 44, 0xFF8FD9FF, false);
    button(g, PROJECT_X + 8, 60, PROJECT_W - 16, d.name + " v");
    g.drawString(
        Minecraft.getInstance().font,
        Minecraft.getInstance().font.plainSubstrByWidth("v " + d.company, PROJECT_W - 78),
        PROJECT_X + 10, 86, 0xFFFFFFFF, false);
    button(g, PROJECT_X + PROJECT_W - 64, 82, 56, "Rename");
    storeActiveLine(d);
    int by = 102;
    for (NetworkRow row : networkRows(d)) {
      if (by >= bottom - 170) break;
      button(
          g,
          PROJECT_X + 12,
          by,
          PROJECT_W - 24,
          row.label);
      by += 20;
    }
    button(g, PROJECT_X + 8, bottom - 164, (PROJECT_W - 20) / 2, "Show all");
    button(
        g,
        PROJECT_X + 12 + (PROJECT_W - 20) / 2,
        bottom - 164,
        (PROJECT_W - 20) / 2,
        "Hide all");
    button(g, PROJECT_X + 8, bottom - 140, PROJECT_W - 16, "+ New line");
    button(g, PROJECT_X + 8, bottom - 116, PROJECT_W - 16, "+ New branch");
    button(
        g,
        PROJECT_X + 8,
        bottom - 92,
        PROJECT_W - 16,
        "Markers: " + d.stations.size() + " v");
    button(
        g,
        PROJECT_X + 8,
        bottom - 68,
        PROJECT_W - 16,
        "Marker: " + LABELS[Math.max(1, tool)] + " v");
    button(
        g,
        PROJECT_X + 8,
        bottom - 44,
        PROJECT_W - 16,
        "Coordinates: " + COORDINATE_MODES[d.coordinateMode]);
    g.fill(PROJECT_X, bottom, sw - 8, sh - 6, 0xF20A1419);
    int tx = PROJECT_X + 8;
    for (EditorTool candidate : EditorTool.values()) {
      boolean selected = editorState.tool() == candidate;
      g.fill(tx, bottom + 4, tx + TOOL_W - 4, sh - 10, selected ? 0xFF315E70 : 0xFF20343D);
      g.drawCenteredString(
          Minecraft.getInstance().font,
          candidate.label(),
          tx + (TOOL_W - 4) / 2,
          bottom + 10,
          selected ? 0xFFFFFF77 : 0xFFFFFFFF);
      tx += TOOL_W;
    }
    g.drawString(Minecraft.getInstance().font, "Snap v", tx + 6, bottom + 10, 0xFF8FD9FF, false);
    drawEditorMenus(g, sw, sh);
  }

  private static List<NetworkRow> networkRows(Draft draft) {
    List<NetworkRow> rows = new ArrayList<>();
    storeActiveLine(draft);
    for (int i = 0; i < draft.lines.size(); i++) {
      LineData line = draft.lines.get(i);
      rows.add(
          new NetworkRow(
              true,
              i,
              (line.visible ? "[x] " : "[ ] ")
                  + (i == draft.activeLine ? "v " : "> ")
                  + line.line));
      if (i == draft.activeLine)
        for (int branchIndex = 0; branchIndex < line.branches.size(); branchIndex++) {
          Branch branch = line.branches.get(branchIndex);
          rows.add(
              new NetworkRow(
                  false,
                  branchIndex,
                  "  " + (branch.visible ? "[x] " : "[ ] ")
                      + (branchIndex == line.branch ? "* " : "o ")
                      + branch.name + " " + branch.vertices.size()));
        }
    }
    return rows;
  }

  private static void drawEditorMenus(GuiGraphics g, int sw, int sh) {
    int bottom = sh - 34;
    if (markerListDropdown) {
      int rows = Math.max(1, Math.min(12, (sh - 100) / 19)),
          pages = Math.max(1, (draft().stations.size() + rows - 1) / rows);
      markerListPage = Math.clamp(markerListPage, 0, pages - 1);
      int from = markerListPage * rows,
          count = Math.min(rows, draft().stations.size() - from),
          x = PROJECT_X + PROJECT_W + 4,
          y = Math.max(36, bottom - 92 - (count + 1) * 19);
      g.fill(x, y, x + 205, y + (count + 1) * 19 + 6, 0xFA0A1419);
      for (int i = 0; i < count; i++) {
        Station station = draft().stations.get(from + i);
        button(
            g,
            x + 4,
            y + 3 + i * 19,
            197,
            (station.id == selectedMarker ? "* " : "  ") + station.name);
      }
      button(
          g,
          x + 4,
          y + 3 + count * 19,
          197,
          pages == 1 ? "All markers" : "Page " + (markerListPage + 1) + "/" + pages + " - click for next");
    }
    if (toolDropdown) {
      int x = PROJECT_X + 8, y = Math.max(36, bottom - 68 - (TYPES.length - 1) * 19);
      g.fill(x, y, x + PROJECT_W - 16, bottom - 68, 0xFA0A1419);
      for (int i = 1; i < TYPES.length; i++)
        button(
            g,
            x + 4,
            y + 3 + (i - 1) * 19,
            PROJECT_W - 24,
            (i == tool ? "* " : "  ") + LABELS[i]);
    }
    if (draftDropdown) {
      int x = PROJECT_X + 150, y = 30, h = (drafts.size() + 4) * ROW + 8;
      g.fill(x, y, x + 180, y + h, 0xFA0A1419);
      for (int i = 0; i < drafts.size(); i++)
        button(
            g, x + 4, y + 4 + i * ROW, 172, (i == draftIndex ? "* " : "  ") + drafts.get(i).name);
      button(g, x + 4, y + 4 + drafts.size() * ROW, 172, "+ New draft");
      button(g, x + 4, y + 4 + (drafts.size() + 1) * ROW, 172, "Import clipboard JSON");
      button(
          g,
          x + 4,
          y + 4 + (drafts.size() + 2) * ROW,
          172,
          System.currentTimeMillis() < draftDeleteConfirmUntil
              ? "Confirm delete " + draft().name
              : "Delete current draft");
      button(g, x + 4, y + 4 + (drafts.size() + 3) * ROW, 172, "Rename current draft");
    }
    if (exportMenu) {
      int x = sw - 148, y = 30;
      g.fill(x, y, sw - 8, y + 88, 0xFA0A1419);
      button(g, x + 4, y + 4, 132, "Website JSON");
      button(g, x + 4, y + 24, 132, "Map image");
      button(g, x + 4, y + 44, 132, "GeoJSON");
      button(g, x + 4, y + 64, 132, "Copy JSON");
    }
    if (snapMenu) {
      int x = Math.min(sw - 154, PROJECT_X + 8 + EditorTool.values().length * TOOL_W), y = sh - 138;
      g.fill(x, y, x + 146, y + 100, 0xFA0A1419);
      int row = 0;
      for (SnapTarget target :
          List.of(
              SnapTarget.GRID,
              SnapTarget.VERTEX,
              SnapTarget.SEGMENT,
              SnapTarget.STATION,
              SnapTarget.CHUNK_CENTER))
        button(
            g,
            x + 4,
            y + 4 + row++ * 19,
            138,
            (snapSettings.enabled(target) ? "[x] " : "[ ] ") + target.name());
    }
    if (validationPanel) {
      int x = Math.max(PROJECT_X + PROJECT_W + 8, sw / 2 - 150), y = 38;
      g.fill(x, y, x + 300, y + 42 + validationMessages.size() * 14, 0xFA0A1419);
      g.drawString(Minecraft.getInstance().font, "VALIDATION", x + 8, y + 8, 0xFFFFFFFF, false);
      for (int i = 0; i < validationMessages.size(); i++)
        g.drawString(
            Minecraft.getInstance().font,
            validationMessages.get(i),
            x + 8,
            y + 24 + i * 14,
            0xFFB9DDEB,
            false);
    }
  }

  private static boolean clickEditorChrome(double mx, double my, int sw) {
    Minecraft mc = Minecraft.getInstance();
    int sh = mc == null ? 480 : mc.getWindow().getGuiScaledHeight(), bottom = sh - 34;
    if (markerListDropdown) {
      int rows = Math.max(1, Math.min(12, (sh - 100) / 19)),
          pages = Math.max(1, (draft().stations.size() + rows - 1) / rows),
          from = markerListPage * rows,
          count = Math.min(rows, draft().stations.size() - from),
          menuX = PROJECT_X + PROJECT_W + 4,
          menuY = Math.max(36, bottom - 92 - (count + 1) * 19);
      if (mx >= menuX && mx <= menuX + 205 && my >= menuY && my < menuY + (count + 1) * 19 + 6) {
        int row = ((int) my - menuY - 3) / 19;
        if (row >= 0 && row < count) {
          selectMarker(draft().stations.get(from + row).id);
          markerListDropdown = false;
        } else if (row == count && pages > 1) markerListPage = (markerListPage + 1) % pages;
        return true;
      }
    }
    if (toolDropdown) {
      int menuX = PROJECT_X + 8,
          menuY = Math.max(36, bottom - 68 - (TYPES.length - 1) * 19);
      if (mx >= menuX && mx <= menuX + PROJECT_W - 16 && my >= menuY && my < bottom - 68) {
        int chosen = 1 + ((int) my - menuY - 3) / 19;
        if (chosen >= 1 && chosen < TYPES.length) {
          tool = chosen;
          activateTool(EditorTool.MARKER);
          notice("Marker: " + LABELS[tool]);
        }
        toolDropdown = false;
        return true;
      }
    }
    if (draftDropdown && mx >= PROJECT_X + 150 && mx <= PROJECT_X + 330
        && my >= 34 && my < 34 + (drafts.size() + 4) * ROW) {
      int row = ((int) my - 34) / ROW;
      if (row >= 0 && row < drafts.size()) {
        saveLibraryQuiet();
        draftIndex = row;
        clearMarkerSelection();
        notice("Opened " + draft().name);
      } else if (row == drafts.size()) newDraft();
      else if (row == drafts.size() + 1) importClipboardJson();
      else if (row == drafts.size() + 2) {
        if (System.currentTimeMillis() < draftDeleteConfirmUntil) {
          deleteDraft();
          draftDeleteConfirmUntil = 0;
        } else {
          draftDeleteConfirmUntil = System.currentTimeMillis() + 4_000;
          notice("Click Delete current draft again to confirm");
          return true;
        }
      }
      if (row == drafts.size() + 3) startNameEdit(6, draft().name);
      draftDropdown = false;
      return true;
    }
    if (exportMenu && mx >= sw - 148 && mx <= sw - 8 && my >= 30 && my < 118) {
      int row = ((int) my - 34) / 20;
      if (row == 0) export();
      else if (row == 1) exportImage();
      else if (row == 2) exportGeoJson();
      else if (row == 3) copyJson();
      exportMenu = false;
      return true;
    }
    int snapX = Math.min(sw - 154, PROJECT_X + 8 + EditorTool.values().length * TOOL_W);
    if (snapMenu && mx >= snapX && mx <= snapX + 146 && my >= sh - 138 && my < sh - 38) {
      int row = ((int) my - (sh - 134)) / 19;
      List<SnapTarget> targets =
          List.of(
              SnapTarget.GRID,
              SnapTarget.VERTEX,
              SnapTarget.SEGMENT,
              SnapTarget.STATION,
              SnapTarget.CHUNK_CENTER);
      if (row >= 0 && row < targets.size()) {
        SnapTarget target = targets.get(row);
        snapSettings.set(target, !snapSettings.enabled(target));
      }
      return true;
    }
    if (my >= 9 && my < 27 && mx >= PROJECT_X + 150 && mx < PROJECT_X + 300) {
      draftDropdown = !draftDropdown;
      exportMenu = false;
      return true;
    }
    if (my >= 9 && my < 27 && mx >= sw - 196 && mx < sw - 110) {
      validateDraft();
      return true;
    }
    if (my >= 9 && my < 27 && mx >= sw - 104 && mx < sw - 8) {
      exportMenu = !exportMenu;
      draftDropdown = false;
      return true;
    }
    if (mx >= PROJECT_X && mx <= PROJECT_X + PROJECT_W && my >= 36 && my < bottom) {
      Draft d = draft();
      if (my >= 60 && my < 80) {
        draftDropdown = !draftDropdown;
        return true;
      }
      if (my >= 82 && my < 100 && mx >= PROJECT_X + PROJECT_W - 64
          && mx < PROJECT_X + PROJECT_W - 8) {
        startNameEdit(5, d.company);
        return true;
      }
      List<NetworkRow> rows = networkRows(d);
      if (my >= 102 && my < 102 + rows.size() * 20) {
        int rowIndex = ((int) my - 102) / 20;
        if (rowIndex < rows.size()) {
          NetworkRow row = rows.get(rowIndex);
          if (row.line && mx < PROJECT_X + 58) {
            checkpoint();
            d.lines.get(row.index).visible = !d.lines.get(row.index).visible;
            saveLibraryQuiet();
            notice((d.lines.get(row.index).visible ? "Showing " : "Hiding ") + d.lines.get(row.index).line);
            return true;
          }
          if (!row.line && mx < PROJECT_X + 72) {
            checkpoint();
            Branch target = d.branches.get(row.index);
            target.visible = !target.visible;
            saveLibraryQuiet();
            notice((target.visible ? "Showing " : "Hiding ") + target.name);
            return true;
          }
          if (row.line) activateLine(d, row.index);
          else d.branch = row.index;
          clearMarkerSelection();
          branchPanel = true;
        }
        return true;
      }
      if (my >= bottom - 164 && my < bottom - 144) {
        setAllLinesVisible(mx < PROJECT_X + PROJECT_W / 2);
        return true;
      }
      if (my >= bottom - 140 && my < bottom - 120) {
        newPlannedLine();
        return true;
      }
      if (my >= bottom - 116 && my < bottom - 96) {
        newBranch();
        return true;
      }
      if (my >= bottom - 92 && my < bottom - 72) {
        markerListDropdown = !markerListDropdown;
        markerListPage = 0;
        return true;
      }
      if (my >= bottom - 68 && my < bottom - 48) {
        toolDropdown = !toolDropdown;
        return true;
      }
      if (my >= bottom - 44 && my < bottom - 24) {
        checkpoint();
        syncCoordinateMode(d);
        saveLibraryQuiet();
        return true;
      }
      return true;
    }
    if (my >= bottom && my <= sh - 6 && mx >= PROJECT_X) {
      int index = ((int) mx - PROJECT_X - 8) / TOOL_W;
      if (index >= 0 && index < EditorTool.values().length) {
        activateTool(EditorTool.values()[index]);
        notice("Tool: " + editorState.tool().label());
        return true;
      }
      if (mx >= snapX) {
        snapMenu = !snapMenu;
        return true;
      }
    }
    return false;
  }

  private static void validateDraft() {
    Draft d = draft();
    int errors = 0, warnings = 0;
    validationMessages.clear();
    Set<Integer> ids = new HashSet<>();
    Set<String> branchNames = new HashSet<>();
    for (Station s : d.stations)
      if (!ids.add(s.id)) {
        errors++;
        validationMessages.add("ERROR Duplicate marker ID " + s.id);
      }
    for (Branch b : d.branches) {
      if (!branchNames.add(b.name)) {
        errors++;
        validationMessages.add("ERROR Duplicate branch name: " + b.name);
      }
      if (b.vertices.size() < 2) {
        warnings++;
        validationMessages.add("WARN " + b.name + " has fewer than 2 points");
      }
      Set<Point> unique = new HashSet<>();
      for (Point point : b.vertices)
        if (!unique.add(point)) {
          warnings++;
          validationMessages.add("WARN Overlapping point in " + b.name);
          break;
        }
      for (int id : b.stationIds)
        if (!ids.contains(id)) {
          errors++;
          validationMessages.add("ERROR " + b.name + " references missing marker " + id);
        }
    }
    if (validationMessages.isEmpty()) validationMessages.add("OK Ready to export");
    validationMessages.add(0, errors + " errors / " + warnings + " warnings");
    validationPanel = true;
    notice(
        errors == 0
            ? (warnings == 0
                ? "Validation passed · ready to export"
                : "Valid · " + warnings + " short/disconnected branches")
            : "Validation failed · " + errors + " broken references");
  }
}
