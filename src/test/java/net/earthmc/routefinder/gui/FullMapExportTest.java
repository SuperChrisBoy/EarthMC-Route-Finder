package net.earthmc.routefinder.gui;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FullMapExportTest {
  @Test
  void fullEditedMapRetainsNetworkAndHasValidDenseStationReferences() throws Exception {
    JsonObject library = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
        "src/test/resources/ice-roads/edited-norway-library.json"))).getAsJsonObject();
    assertThrows(UpstreamMapMerge.Conflict.class, () -> IceRoadPlannerOverlay.fullMapJson(library));
    library.getAsJsonArray("drafts").get(library.get("activeDraft").getAsInt()).getAsJsonObject()
        .add("upstreamBase", net.earthmc.routefinder.ice.IceRoadNetwork.get().editorSource());
    JsonObject before = library.deepCopy();
    JsonObject result = IceRoadPlannerOverlay.fullMapJson(library);
    assertEquals(before, library);
    assertTrue(result.getAsJsonObject("lines").size() > 1);
    JsonArray stations = result.getAsJsonArray("stations");
    for (int i = 0; i < stations.size(); i++)
      assertEquals(i, stations.get(i).getAsJsonObject().get("id").getAsInt());
    for (JsonElement company : result.getAsJsonObject("lines").asMap().values())
      for (JsonElement line : company.getAsJsonObject().asMap().values())
        for (JsonElement branch : line.getAsJsonObject().getAsJsonObject("branches").asMap().values())
          for (JsonElement ref : branch.getAsJsonObject().getAsJsonArray("stations")) {
            int id = ref.isJsonArray() ? ref.getAsJsonArray().get(0).getAsInt() : ref.getAsInt();
            assertTrue(id >= 0 && id < stations.size());
          }
  }

  @Test
  void preservesOtherRoadsAndRemapsCollidingStationIdsOnRename() {
    JsonObject network = JsonParser.parseString("""
        {"stations":[
          {"id":0,"name":"Shared","lines":{"C":{"Old":{},"Other":{}}}},
          {"id":1,"name":"Old only","lines":{"C":{"Old":{}}}}],
        "lines":{"C":{
          "Old":{"branches":{"Main":{"vertices":[[0,0],[1,1]],"stations":[0,1]}}},
          "Other":{"branches":{"Main":{"vertices":[[0,0],[2,2]],"stations":[0]}}}}}}
        """).getAsJsonObject();
    JsonObject edits = JsonParser.parseString("""
        {"stations":[{"id":0,"name":"Edited","lines":{"C":{"Renamed":{}}}}],
        "lines":{"C":{"Renamed":{"branches":{"Main":{
          "vertices":[[0,0],[3,3]],"stations":[[0,1]]}}}}}}
        """).getAsJsonObject();
    JsonArray states = JsonParser.parseString("""
        [{"sourceCompany":"C","sourceLine":"Old"}]
        """).getAsJsonArray();
    JsonObject before = network.deepCopy(), editsBefore = edits.deepCopy();
    JsonObject result = FullMapExport.merge(network, edits, states);
    JsonObject routes = result.getAsJsonObject("lines").getAsJsonObject("C");
    assertFalse(routes.has("Old"));
    assertEquals(before.getAsJsonObject("lines").getAsJsonObject("C").get("Other"), routes.get("Other"));
    assertEquals(2, result.getAsJsonArray("stations").size());
    assertEquals(2, result.getAsJsonArray("stations").get(1).getAsJsonObject().get("id").getAsInt());
    assertEquals(2, routes.getAsJsonObject("Renamed").getAsJsonObject("branches")
        .getAsJsonObject("Main").getAsJsonArray("stations").get(0).getAsJsonArray().get(0).getAsInt());
    assertEquals(before, network);
    assertEquals(editsBefore, edits);
  }
}
