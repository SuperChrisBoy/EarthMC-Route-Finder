package net.earthmc.routefinder.gui;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpstreamMapMergeTest {
  private JsonObject map() {
    return JsonParser.parseString("""
      {"stations":[{"id":0,"name":"A","x":0,"z":0,"lines":{"C":{"R":["","Main"]}}}],
       "lines":{"C":{"R":{"color":"ffffff","y":64,"branches":{
       "Main":{"vertices":[[0,0],[1,1]],"stations":[[0,1]]}}}}}}
      """).getAsJsonObject();
  }
  private JsonObject road(JsonObject map) { return map.getAsJsonObject("lines").getAsJsonObject("C").getAsJsonObject("R"); }
  @Test void mergesIndependentFieldsOnSameRoadWithoutMutatingInputs() {
    JsonObject base=map(), local=map(), upstream=map();
    road(local).addProperty("color","ff0000");
    road(upstream).addProperty("y",80);
    JsonObject before=local.deepCopy();
    JsonObject result=UpstreamMapMerge.merge(base,local,upstream);
    assertEquals("ff0000",road(result).get("color").getAsString());
    assertEquals(80,road(result).get("y").getAsInt());
    assertEquals(before,local);
    assertEquals(map(),base);
  }
  @Test void rejectsOverlappingFieldsAndDeleteVersusEdit() {
    JsonObject base=map(), local=map(), upstream=map();
    road(local).addProperty("y",70); road(upstream).addProperty("y",80);
    assertThrows(UpstreamMapMerge.Conflict.class,()->UpstreamMapMerge.merge(base,local,upstream));
    local.getAsJsonObject("lines").getAsJsonObject("C").remove("R");
    assertThrows(UpstreamMapMerge.Conflict.class,()->UpstreamMapMerge.merge(base,local,upstream));
  }
  @Test void stationIdRenumberingDoesNotConflictAndUpstreamStationEditSurvives() {
    JsonObject base=map(), local=map(), upstream=map();
    road(local).addProperty("color","ff0000");
    upstream.getAsJsonArray("stations").get(0).getAsJsonObject().addProperty("id",19);
    upstream.getAsJsonArray("stations").get(0).getAsJsonObject().addProperty("name","Updated");
    road(upstream).getAsJsonObject("branches").getAsJsonObject("Main").getAsJsonArray("stations")
        .get(0).getAsJsonArray().set(0,new JsonPrimitive(19));
    JsonObject result=UpstreamMapMerge.merge(base,local,upstream);
    assertEquals("Updated",result.getAsJsonArray("stations").get(0).getAsJsonObject().get("name").getAsString());
    assertEquals(0,result.getAsJsonArray("stations").get(0).getAsJsonObject().get("id").getAsInt());
  }
  @Test void overlappingGeometryFailsButIdenticalEditsSucceed() {
    JsonObject base=map(), local=map(), upstream=map();
    road(local).getAsJsonObject("branches").getAsJsonObject("Main").getAsJsonArray("vertices").add(new JsonArray());
    road(upstream).getAsJsonObject("branches").getAsJsonObject("Main").getAsJsonArray("vertices").remove(1);
    assertThrows(UpstreamMapMerge.Conflict.class,()->UpstreamMapMerge.merge(base,local,upstream));
    assertDoesNotThrow(()->UpstreamMapMerge.merge(base,local,local));
  }
}
