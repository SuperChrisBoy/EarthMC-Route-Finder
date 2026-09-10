package net.earthmc.routefinder.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import net.earthmc.routefinder.iceeditor.SnapSettings;
import org.junit.jupiter.api.Test;

class IceRoadPlannerOverlayTest {
  @Test
  void websiteExportRemapsSparseIdsAndJunctionReferencesWithoutChangingInput() {
    JsonObject source = JsonParser.parseString("""
        {"stations":[
          {"id":16,"name":"Shared","x":0,"z":0,"lines":{"Road":{"A":["","Main to Spur"],"B":["","Main"]}}},
          {"id":9,"name":"End","x":10,"z":0,"lines":{"Road":{"A":["","Main"]}}}
        ],"lines":{"Road":{
          "A":{"branches":{
            "Main":{"vertices":[[0,0],[10,0]],"stations":[[16,"Spur"],9]},
            "Spur":{"vertices":[[0,0],[0,10]],"stations":[[16,"Main"]]}}},
          "B":{"branches":{"Main":{"vertices":[[0,0],[20,0]],"stations":[16]}}}
        }}}
        """).getAsJsonObject();
    JsonObject output = IceRoadPlannerOverlay.websiteExportForTest(source);
    assertEquals(2, output.getAsJsonArray("stations").size());
    assertEquals(0, output.getAsJsonArray("stations").get(0).getAsJsonObject().get("id").getAsInt());
    assertEquals(1, output.getAsJsonArray("stations").get(1).getAsJsonObject().get("id").getAsInt());
    JsonObject lines = output.getAsJsonObject("lines").getAsJsonObject("Road");
    assertEquals("[[0,\"Spur\"],1]", lines.getAsJsonObject("A").getAsJsonObject("branches")
        .getAsJsonObject("Main").getAsJsonArray("stations").toString());
    assertEquals("[0]", lines.getAsJsonObject("B").getAsJsonObject("branches")
        .getAsJsonObject("Main").getAsJsonArray("stations").toString());
    assertEquals(16, source.getAsJsonArray("stations").get(0).getAsJsonObject().get("id").getAsInt());
  }

  @Test
  void websiteExportHandlesUnassignedMarkersAndThreeBranchJunctions() {
    JsonObject source = JsonParser.parseString("""
        {"stations":[
          {"id":7,"name":"Junction","x":0,"z":0,"lines":{"Road":{"A":["","Main"]}}},
          {"id":12,"name":"Unassigned","x":5,"z":5,"lines":{"Road":{"A":["",""]}}}
        ],"lines":{"Road":{"A":{"branches":{
          "Main":{"vertices":[[0,0],[10,0]],"stations":[7]},
          "Spur":{"vertices":[[0,0],[0,10]],"stations":[7]},
          "Third":{"vertices":[[0,0],[-10,0]],"stations":[7]}
        }}}}}
        """).getAsJsonObject();
    JsonObject output = IceRoadPlannerOverlay.websiteExportForTest(source);
    assertEquals("Main and Spur and Third", output.getAsJsonArray("stations").get(0)
        .getAsJsonObject().getAsJsonObject("lines").getAsJsonObject("Road")
        .getAsJsonArray("A").get(1).getAsString());
    JsonObject unassigned = IceRoadPlannerOverlay.unassignedMarkerExportForTest();
    assertTrue(unassigned.getAsJsonArray("stations").get(0).getAsJsonObject()
        .getAsJsonObject("lines").isEmpty());
  }

  @Test
  void localRecoveredExportUsesWebsiteArrayIndices() throws Exception {
    Path source = Path.of(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft",
        "earthmcroutefinder", "ice-highway-planner", "json", "earthmcroutefinder-ice-highway-recovered-draft-1.json");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(source));
    JsonObject exported = IceRoadPlannerOverlay.websiteExportForTest(
        JsonParser.parseString(Files.readString(source)).getAsJsonObject());
    for (int i = 0; i < exported.getAsJsonArray("stations").size(); i++)
      assertEquals(i, exported.getAsJsonArray("stations").get(i).getAsJsonObject().get("id").getAsInt());
    Files.writeString(Path.of("build", "website-compatible-recovered.json"), exported.toString());
  }

  @Test
  void localRecoveryExportRetainsTheCompleteNetworkWhenPresent() throws Exception {
    Path export =
        Path.of(
            System.getProperty("user.home"),
            "AppData", "Roaming", ".minecraft", "earthmcroutefinder", "ice-highway-planner", "json",
            "earthmcroutefinder-ice-highway-draft-1.json");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(export));
    int[] counts =
        IceRoadPlannerOverlay.parsedContentCountsForTest(
            JsonParser.parseString(Files.readString(export)).getAsJsonObject());
    assertArrayEquals(new int[] {1, 1, 65, 16, 45}, counts);
  }

  @Test
  void exportUsesWebsiteHighwaysSchema() {
    JsonObject json = IceRoadPlannerOverlay.websiteJsonForTest();
    assertTrue(json.has("stations"));
    JsonObject planned =
        json.getAsJsonObject("lines").getAsJsonObject("My Highway").getAsJsonObject("Planned Line");
    assertEquals("", planned.get("prefix").getAsString());
    assertEquals("", planned.get("code").getAsString());
    assertEquals("ff55dd", planned.get("color").getAsString());
    assertEquals(64, planned.get("y").getAsDouble());
    assertTrue(planned.has("branches"));
    planned
        .getAsJsonObject("branches")
        .entrySet()
        .forEach(
            entry -> {
              JsonObject branch = entry.getValue().getAsJsonObject();
              assertFalse(branch.has("y"));
              assertTrue(branch.has("vertices"));
              assertTrue(branch.has("stations"));
            });
  }

  @Test
  void websiteJsonAndImporterPreserveMultipleLinesInOneDraft() {
    JsonObject json = IceRoadPlannerOverlay.multiLineJsonForTest();
    JsonObject company = json.getAsJsonObject("lines").getAsJsonObject("My Highway");
    assertTrue(company.has("First"));
    assertTrue(company.has("Second"));
    assertEquals(2, IceRoadPlannerOverlay.parsedLineCountForTest(json));
  }

  @Test
  void oneMarkerCanBelongToMultipleLinesWithoutBeingDuplicatedInWebsiteJson() {
    JsonObject json = IceRoadPlannerOverlay.sharedMarkerJsonForTest();
    assertEquals(1, json.getAsJsonArray("stations").size());
    JsonObject memberships =
        json.getAsJsonArray("stations")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("lines")
            .getAsJsonObject("My Highway");
    assertTrue(memberships.has("Planned Line"));
    assertTrue(memberships.has("Second"));
    assertArrayEquals(
        new int[] {2, 2, 0, 2, 0}, IceRoadPlannerOverlay.parsedContentCountsForTest(json));
  }

  @Test
  void oneVertexCanBelongToMultipleBranchesAndLinesInWebsiteJson() {
    JsonObject json = IceRoadPlannerOverlay.sharedVertexJsonForTest();
    JsonObject company = json.getAsJsonObject("lines").getAsJsonObject("My Highway");
    JsonObject plannedBranches = company.getAsJsonObject("Planned Line").getAsJsonObject("branches");
    assertEquals(2, plannedBranches.size());
    assertEquals(
        plannedBranches.getAsJsonObject("Main").getAsJsonArray("vertices").get(0),
        plannedBranches.getAsJsonObject("Second branch").getAsJsonArray("vertices").get(0));
    assertEquals(
        plannedBranches.getAsJsonObject("Main").getAsJsonArray("vertices").get(0),
        company.getAsJsonObject("Second line")
            .getAsJsonObject("branches").getAsJsonObject("Main")
            .getAsJsonArray("vertices").get(0));
    assertFalse(json.toString().contains("vertexId"));
    assertArrayEquals(
        new int[] {2, 3, 3, 0, 0}, IceRoadPlannerOverlay.parsedContentCountsForTest(json));
  }

  @Test
  void movingSharedVertexUpdatesEveryLineMembership() {
    double[][] points = IceRoadPlannerOverlay.moveSharedVertexForTest();
    assertArrayEquals(new double[] {30.5, 60, 40.5}, points[0]);
    assertArrayEquals(points[0], points[1]);
  }

  @Test
  void markerHitTestingWinsOverAnOverlappingLineVertex() {
    assertTrue(IceRoadPlannerOverlay.hitPriority(true) < IceRoadPlannerOverlay.hitPriority(false));
  }

  @Test
  void lineColorsUseWebsiteHexFormatAndPreserveRequestedAlpha() {
    assertEquals("55aaff", IceRoadPlannerOverlay.normalizeColor("#55AAFF"));
    assertNull(IceRoadPlannerOverlay.normalizeColor("blue"));
    assertEquals(0x8055AAFF, IceRoadPlannerOverlay.lineArgb("55aaff", 0x80));
    assertEquals(0xFFFF55DD, IceRoadPlannerOverlay.lineArgb("invalid", 0xFF));
  }

  @Test
  void lineVisibilityDefaultsShownAndRestoresSavedHiddenState() {
    JsonObject json = IceRoadPlannerOverlay.multiLineJsonForTest();
    assertTrue(IceRoadPlannerOverlay.parsedLineVisibilityForTest(json, 0));
    com.google.gson.JsonArray states = new com.google.gson.JsonArray();
    JsonObject first = new JsonObject();
    first.addProperty("visible", false);
    states.add(first);
    states.add(new JsonObject());
    json.add("plannerLineStates", states);
    assertFalse(IceRoadPlannerOverlay.parsedLineVisibilityForTest(json, 0));
    assertTrue(IceRoadPlannerOverlay.parsedLineVisibilityForTest(json, 1));
  }

  @Test
  void branchVisibilityDefaultsShownAndRestoresPlannerOnlyHiddenState() {
    JsonObject json = IceRoadPlannerOverlay.multiLineJsonForTest();
    com.google.gson.JsonArray states = new com.google.gson.JsonArray();
    JsonObject first = new JsonObject();
    JsonObject branches = new JsonObject();
    branches.addProperty("Main", false);
    first.add("branchVisibility", branches);
    states.add(first);
    states.add(new JsonObject());
    json.add("plannerLineStates", states);
    assertFalse(IceRoadPlannerOverlay.parsedBranchVisibilityForTest(json, 0, 0));
    assertTrue(IceRoadPlannerOverlay.parsedBranchVisibilityForTest(json, 1, 0));
    assertFalse(
        json.getAsJsonObject("lines")
            .getAsJsonObject("My Highway")
            .getAsJsonObject("First")
            .has("branchVisibility"));
  }

  @Test
  void bulkVisibilityControlsUpdateEveryPlannedLine() {
    assertArrayEquals(new boolean[] {false, false, false},
        IceRoadPlannerOverlay.bulkLineVisibilityForTest(false));
    assertArrayEquals(new boolean[] {true, true, true},
        IceRoadPlannerOverlay.bulkLineVisibilityForTest(true));
  }

  @Test
  void visibleInactiveLineSegmentsRemainSelectableFromTheMap() {
    int line =
        IceRoadPlannerOverlay.nearestSegmentLineForTest(
            50,
            52,
            List.of(
                new double[] {0, 0, 0, 10, 0},
                new double[] {1, 20, 50, 80, 50}));
    assertEquals(1, line);
  }

  @Test
  void selectModeUsesAPracticalLineHitboxWithoutLettingVerticesMaskTheSegment() {
    assertEquals(1, IceRoadPlannerOverlay.nearestSegmentLineForTest(
        50, 9, List.of(new double[] {1, 0, 0, 100, 0})));
    assertEquals(25, IceRoadPlannerOverlay.selectVertexHitDistance2(
        net.earthmc.routefinder.iceeditor.EditorTool.SELECT));
    assertEquals(100, IceRoadPlannerOverlay.selectVertexHitDistance2(
        net.earthmc.routefinder.iceeditor.EditorTool.DRAW));
  }

  @Test
  void linksBetweenExistingVerticesAreSelectableAndRemovableAsSegments() {
    assertEquals(-1, IceRoadPlannerOverlay.linkedSegmentHitForTest(50, 8));
    assertEquals(0, IceRoadPlannerOverlay.removeLinkConnectionForTest());
  }

  @Test
  void segmentMembershipCanBeAddedAndRemovedWithoutDeletingEitherVertex() {
    assertArrayEquals(
        new boolean[] {true, true, true},
        IceRoadPlannerOverlay.segmentMembershipCycleForTest());
  }

  @Test
  void websiteImportDoesNotOverwriteParsedContentWithBlankEditorState() {
    JsonObject json =
        JsonParser.parseString(
                """
                {
                  "stations": [{"id": 7, "name": "Central", "x": 10.5, "y": 50, "z": 20.5,
                    "lines": {"My Highway": {"Planned Line": "Main"}}}],
                  "lines": {"My Highway": {"Planned Line": {
                    "color": "ff55dd", "y": 50,
                    "branches": {"Main": {
                      "vertices": [[10.5, 20.5], [30.5, 20.5], [30.5, 40.5]],
                      "stations": [7]
                    }}
                  }}}
                }
                """)
            .getAsJsonObject();

    assertArrayEquals(
        new int[] {1, 1, 3, 1, 0}, IceRoadPlannerOverlay.parsedContentCountsForTest(json));
  }

  @Test
  void defaultLineSnapChoosesStraightOrDiagonalDirections() {
    assertArrayEquals(
        new double[] {10.5, 0.5}, IceRoadPlannerOverlay.snapDirection(0.5, 0.5, 10, 2));
    assertArrayEquals(new double[] {7.5, 7.5}, IceRoadPlannerOverlay.snapDirection(0.5, 0.5, 8, 7));
  }

  @Test
  void segmentSnapCanAttachBetweenExistingMarkers() {
    assertArrayEquals(
        new double[] {40.5, 0.5},
        IceRoadPlannerOverlay.projectOntoSegment(40, 9, 0.5, 0.5, 100.5, 0.5));
    assertArrayEquals(
        new double[] {50.5, 50.5},
        IceRoadPlannerOverlay.projectOntoSegment(55, 45, 0.5, 0.5, 100.5, 100.5));
  }

  @Test
  void coordinatesSnapToBlockCentersIncludingNegativeValues() {
    assertEquals(12.5, IceRoadPlannerOverlay.blockCenter(12.01));
    assertEquals(-12.5, IceRoadPlannerOverlay.blockCenter(-12.01));
  }

  @Test
  void stalePointEditNeverDragsOrKeepsTheWrongSelectedPoint() {
    assertFalse(IceRoadPlannerOverlay.shouldDragPoint(true, 0, 8, false, -1, -1, 0, 3));
    assertTrue(IceRoadPlannerOverlay.shouldDragPoint(true, 0, 8, false, -1, -1, 0, 8));
  }

  @Test
  void staleSegmentEditOnlyDragsItsOwnTwoEndpoints() {
    assertTrue(IceRoadPlannerOverlay.shouldDragPoint(false, -1, -1, true, 2, 5, 2, 5));
    assertTrue(IceRoadPlannerOverlay.shouldDragPoint(false, -1, -1, true, 2, 5, 2, 6));
    assertFalse(IceRoadPlannerOverlay.shouldDragPoint(false, -1, -1, true, 2, 5, 2, 4));
    assertFalse(IceRoadPlannerOverlay.shouldDragPoint(false, -1, -1, true, 2, 5, 1, 5));
  }

  @Test
  void selectionAloneNeverAuthorizesCoordinateMutation() {
    assertFalse(IceRoadPlannerOverlay.shouldEditSelectedPoint(false));
    assertTrue(IceRoadPlannerOverlay.shouldEditSelectedPoint(true));
  }

  @Test
  void ordinaryLineModePlacementsCreateStandalonePointsWithoutLines() {
    List<double[]> points = new ArrayList<>();
    NavigableSet<Integer> breaks = new TreeSet<>();
    IceRoadPlannerOverlay.addStandalonePointForTest(points, breaks, 10, 20);
    IceRoadPlannerOverlay.addStandalonePointForTest(points, breaks, 30, 40);
    IceRoadPlannerOverlay.addStandalonePointForTest(points, breaks, 50, 60);
    assertEquals(3, points.size());
    assertEquals(new TreeSet<>(List.of(1, 2)), breaks);
    assertArrayEquals(new double[] {10.5, 20.5}, points.get(0));
    assertArrayEquals(new double[] {50.5, 60.5}, points.get(2));
  }

  @Test
  void coordinateLabelRectanglesOnlyCollideWhenTheyActuallyOverlap() {
    assertTrue(IceRoadPlannerOverlay.rectanglesOverlap(0, 0, 20, 12, 10, 5, 30, 17));
    assertFalse(IceRoadPlannerOverlay.rectanglesOverlap(0, 0, 20, 12, 20, 0, 40, 12));
    assertFalse(IceRoadPlannerOverlay.rectanglesOverlap(0, 0, 20, 12, 0, 12, 20, 24));
  }

  @Test
  void topologyViewShowsTurnsAndHeightChangesButNotIntersections() {
    assertTrue(IceRoadPlannerOverlay.shouldShowTopologyCoordinate(2, true, false));
    assertFalse(IceRoadPlannerOverlay.shouldShowTopologyCoordinate(3, true, false));
    assertTrue(IceRoadPlannerOverlay.shouldShowTopologyCoordinate(3, false, true));
    assertFalse(IceRoadPlannerOverlay.shouldShowTopologyCoordinate(2, false, false));
  }

  @Test
  void mapPanIsNotTreatedAsAPlacementClick() {
    assertFalse(IceRoadPlannerOverlay.movedBeyondClickThreshold(100, 100, 103, 102));
    assertTrue(IceRoadPlannerOverlay.movedBeyondClickThreshold(100, 100, 105, 100));
    assertTrue(IceRoadPlannerOverlay.movedBeyondClickThreshold(100, 100, 100, 95));
  }

  @Test
  void cursorWorldConversionExactlyMatchesTheRenderedMapTransform() {
    double camX = 42_900.5, camZ = -3_688.5, scale = 0.775;
    int width = 2560, height = 1018;
    double cursorX = 943.25, cursorY = 577.75;
    double[] world =
        IceRoadPlannerOverlay.worldFromScreen(
            cursorX, cursorY, camX, camZ, scale, width, height);
    assertEquals(cursorX, (world[0] - camX) * scale + width / 2.0, 0.000001);
    assertEquals(cursorY, (world[1] - camZ) * scale + height / 2.0, 0.000001);
  }

  @Test
  void placementSnapDefaultsNeverMoveAwayFromTheCursorBlock() {
    assertTrue(new SnapSettings().enabledTargets().isEmpty());
  }

  @Test
  void drawMarkerAndVertexModesPermitDirectDragging() {
    assertTrue(IceRoadPlannerOverlay.directDragTool(net.earthmc.routefinder.iceeditor.EditorTool.SELECT));
    assertTrue(IceRoadPlannerOverlay.directDragTool(net.earthmc.routefinder.iceeditor.EditorTool.DRAW));
    assertTrue(IceRoadPlannerOverlay.directDragTool(net.earthmc.routefinder.iceeditor.EditorTool.ADD_VERTEX));
    assertTrue(IceRoadPlannerOverlay.directDragTool(net.earthmc.routefinder.iceeditor.EditorTool.MARKER));
    assertFalse(IceRoadPlannerOverlay.directDragTool(net.earthmc.routefinder.iceeditor.EditorTool.REMOVE));
  }

  @Test
  void nativeMapAlwaysReceivesReleaseAfterAnEmptyMapPress() {
    assertFalse(IceRoadPlannerOverlay.shouldConsumeRelease(false));
    assertTrue(IceRoadPlannerOverlay.shouldConsumeRelease(true));
  }

  @Test
  void branchSplitAssignsMarkersToTheirSideAndSharesBoundaryMarker() {
    List<double[]> vertices =
        List.of(new double[] {0.5, 0.5}, new double[] {10.5, 0.5}, new double[] {20.5, 0.5});
    List<double[]> markers =
        List.of(new double[] {5.5, 0.5}, new double[] {10.5, 0.5}, new double[] {15.5, 0.5});

    assertEquals(
        List.of(1, 3, 2),
        IceRoadPlannerOverlay.splitMarkerMembershipForTest(vertices, 1, markers));
  }

  @Test
  void movingEndpointsToNewLineTransfersTheExactExistingPath() {
    List<double[]> original =
        List.of(
            new double[] {0.5, 0.5},
            new double[] {10.5, 0.5},
            new double[] {10.5, 10.5},
            new double[] {20.5, 10.5},
            new double[] {30.5, 10.5});
    List<List<double[]>> result =
        IceRoadPlannerOverlay.transferRangeForTest(original, new TreeSet<>(), 1, 3);

    assertEquals(2, result.get(0).size());
    assertArrayEquals(new double[] {0.5, 0.5}, result.get(0).get(0));
    assertArrayEquals(new double[] {30.5, 10.5}, result.get(0).get(1));
    assertEquals(3, result.get(1).size());
    assertArrayEquals(new double[] {10.5, 0.5}, result.get(1).get(0));
    assertArrayEquals(new double[] {10.5, 10.5}, result.get(1).get(1));
    assertArrayEquals(new double[] {20.5, 10.5}, result.get(1).get(2));
  }

  @Test
  void extractionUsesConnectedTopologyInsteadOfTheVertexStorageRange() {
    List<double[]> storageOrder =
        List.of(
            new double[] {0.5, 0.5},
            new double[] {500.5, 500.5},
            new double[] {600.5, 500.5},
            new double[] {10.5, 0.5});
    List<double[]> path =
        IceRoadPlannerOverlay.shortestPathForTest(
            storageOrder, new TreeSet<>(List.of(1, 3)), List.of(new int[] {0, 3}), 0, 3);

    assertEquals(2, path.size());
    assertArrayEquals(new double[] {0.5, 0.5}, path.get(0));
    assertArrayEquals(new double[] {10.5, 0.5}, path.get(1));
  }

  @Test
  void selectedMarkerResolvesToItsOwnedLineVertexForRouteExtraction() {
    assertArrayEquals(
        new double[] {10.5, 50, 20.5}, IceRoadPlannerOverlay.markerRouteNodeForTest());
  }

  @Test
  void everyMultiSelectionPanelButtonMapsToItsOwnAction() {
    assertEquals(1, IceRoadPlannerOverlay.multiSelectionActionAt(55));
    assertEquals(2, IceRoadPlannerOverlay.multiSelectionActionAt(79));
    assertEquals(3, IceRoadPlannerOverlay.multiSelectionActionAt(103));
    assertEquals(4, IceRoadPlannerOverlay.multiSelectionActionAt(127));
    assertEquals(5, IceRoadPlannerOverlay.multiSelectionActionAt(151));
    assertEquals(6, IceRoadPlannerOverlay.multiSelectionActionAt(175));
    assertEquals(0, IceRoadPlannerOverlay.multiSelectionActionAt(68));
  }

  @Test
  void copiedNodesAndMarkersPasteIndividuallyWithoutCopyingConnections() {
    JsonObject result = IceRoadPlannerOverlay.copiedRouteAcrossLinesForTest();
    assertEquals(3, result.get("sourceVertices").getAsInt());
    assertEquals(2, result.get("destinationVertices").getAsInt());
    assertEquals(1, result.get("destinationMarkers").getAsInt());
    assertEquals(52, result.get("secondY").getAsDouble());
    assertEquals(1, result.get("breaks").getAsInt());
    assertEquals(0, result.get("links").getAsInt());
    assertTrue(
        result.getAsJsonObject("export")
            .getAsJsonObject("lines")
            .getAsJsonObject("My Highway")
            .has("Destination"));
  }

  @Test
  void extractionToNewLinePreservesEveryIntermediateVertex() {
    JsonObject result = IceRoadPlannerOverlay.extractionScenarioForTest(5, new int[] {0, 4}, false, -1);
    assertEquals("[0.5,1.5,2.5,3.5,4.5]", result.getAsJsonArray("moved").toString());
    assertEquals(2, result.get("lineCount").getAsInt());
  }

  @Test
  void multipleClickedAnchorsPreserveRouteOrderAndIntermediateVertices() {
    JsonObject result = IceRoadPlannerOverlay.extractionScenarioForTest(5, new int[] {0, 2, 4}, false, -1);
    assertEquals("[0.5,1.5,2.5,3.5,4.5]", result.getAsJsonArray("moved").toString());
  }

  @Test
  void partialExtractionSplitsSourceWithoutInventingGapConnection() {
    JsonObject result = IceRoadPlannerOverlay.extractionScenarioForTest(7, new int[] {2, 5}, false, -1);
    assertEquals("[2.5,3.5,4.5,5.5]", result.getAsJsonArray("moved").toString());
    String edges = result.getAsJsonArray("sourceEdges").toString();
    assertTrue(edges.contains("[0.5,1.5]"));
    assertTrue(edges.contains("[1.5,2.5]"));
    assertTrue(edges.contains("[5.5,6.5]"));
    assertFalse(edges.contains("[1.5,6.5]"));
  }

  @Test
  void intermediateStationMovesWithExtractedGeometryEvenWhenNotClicked() {
    JsonObject result = IceRoadPlannerOverlay.extractionScenarioForTest(5, new int[] {0, 4}, false, 2);
    assertTrue(result.get("destinationHasStation").getAsBoolean());
  }

  @Test
  void disconnectedSelectionReturnsVisibleSpecificFailureReason() {
    assertTrue(IceRoadPlannerOverlay.disconnectedExtractionErrorForTest().contains("not connected"));
  }

  @Test
  void extractionToNewBranchPreservesGeometryAndCreatesBranch() {
    JsonObject result = IceRoadPlannerOverlay.extractionScenarioForTest(5, new int[] {0, 4}, true, -1);
    assertEquals("[0.5,1.5,2.5,3.5,4.5]", result.getAsJsonArray("moved").toString());
    assertEquals(1, result.get("lineCount").getAsInt());
    assertEquals(1, result.get("destinationBranchCount").getAsInt());
  }

  @Test
  void extractedTopologySurvivesWebsiteExportAndReload() {
    JsonObject result = IceRoadPlannerOverlay.extractionScenarioForTest(5, new int[] {1, 4}, false, 3);
    assertTrue(result.getAsJsonObject("export").has("lines"));
    assertEquals(2, result.get("reloadLines").getAsInt());
  }

  @Test
  void extractionCrossesExistingJunctionLinkWithoutInventingStraightGeometry() {
    assertEquals(
        "[[0.5,0.5],[1.5,0.5],[1.5,1.5],[2.5,1.5]]",
        IceRoadPlannerOverlay.junctionExtractionForTest().toString());
  }

  @Test
  void staleSelectedVertexProducesCleanValidationFailure() {
    assertTrue(IceRoadPlannerOverlay.staleExtractionErrorForTest().contains("Could not resolve"));
  }

  @Test
  void rapidRepeatedExtractionCannotCreateDuplicateLine() {
    assertEquals(2, IceRoadPlannerOverlay.rapidRepeatExtractionLineCountForTest());
  }

  @Test
  void extractionSnapshotsSupportExactAtomicUndoAndRedo() {
    assertArrayEquals(
        new boolean[] {true, true, true}, IceRoadPlannerOverlay.extractionSnapshotCycleForTest());
  }

  @Test
  void newPlannedLineUsesWebsiteSchemaAndUniqueName() {
    JsonObject json = IceRoadPlannerOverlay.newPlannedLineJsonForTest();
    JsonObject company = json.getAsJsonObject("lines").getAsJsonObject("My Highway");
    assertTrue(company.has("Planned Line"));
    assertTrue(company.has("Planned Line 2"));
    JsonObject created = company.getAsJsonObject("Planned Line 2");
    assertEquals("ff55dd", created.get("color").getAsString());
    assertTrue(created.getAsJsonObject("branches").has("Main"));
    assertEquals(2, IceRoadPlannerOverlay.parsedLineCountForTest(json));
  }

  @Test
  void deletingSelectedPlannedLineActivatesAValidRemainingLineAndSurvivesReload() {
    JsonObject result = IceRoadPlannerOverlay.deletePlannedLineForTest(false);
    assertEquals("Planned Line", result.get("removed").getAsString());
    assertEquals(1, result.get("lineCount").getAsInt());
    assertEquals("Planned Line 2", result.get("activeLine").getAsString());
    assertEquals(1, result.get("reloadLines").getAsInt());
  }

  @Test
  void deletingTheLastPlannedLineLeavesOneSafeEmptyReplacement() {
    JsonObject result = IceRoadPlannerOverlay.deletePlannedLineForTest(true);
    assertEquals(1, result.get("lineCount").getAsInt());
    assertEquals("Planned Line", result.get("activeLine").getAsString());
    assertEquals(1, result.get("branchCount").getAsInt());
    assertEquals(1, result.get("reloadLines").getAsInt());
  }

  @Test
  void uiActionsRunExactlyOnceWithReleaseFallback() {
    assertFalse(IceRoadPlannerOverlay.shouldFallbackToUiRelease(true));
    assertTrue(IceRoadPlannerOverlay.shouldFallbackToUiRelease(false));
  }
}
