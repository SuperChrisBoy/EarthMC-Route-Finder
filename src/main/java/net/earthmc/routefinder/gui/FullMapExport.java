package net.earthmc.routefinder.gui;

import com.google.gson.*;
import java.util.*;

/** Combines a draft with the untouched network without reusing its station IDs. */
final class FullMapExport {
  static JsonObject merge(JsonObject network, JsonObject edits, JsonArray lineStates) {
    JsonObject result = network.deepCopy();
    JsonObject lines = result.getAsJsonObject("lines");
    Set<List<String>> replaced = new HashSet<>();
    for (var company : edits.getAsJsonObject("lines").entrySet())
      for (String line : company.getValue().getAsJsonObject().keySet())
        replaced.add(List.of(company.getKey(), line));
    for (JsonElement element : lineStates) {
      JsonObject state = element.getAsJsonObject();
      if (state.has("sourceCompany") && !state.get("sourceCompany").getAsString().equals("$station"))
        replaced.add(List.of(state.get("sourceCompany").getAsString(), state.get("sourceLine").getAsString()));
    }
    for (List<String> key : replaced) {
      if (lines.has(key.get(0))) {
        JsonObject company = lines.getAsJsonObject(key.get(0));
        company.remove(key.get(1));
        if (company.isEmpty()) lines.remove(key.get(0));
      }
    }
    JsonArray stations = result.getAsJsonArray("stations");
    int nextId = 0;
    for (Iterator<JsonElement> iterator = stations.iterator(); iterator.hasNext();) {
      JsonElement element = iterator.next();
      JsonObject station = element.getAsJsonObject();
      nextId = Math.max(nextId, station.get("id").getAsInt() + 1);
      if (!station.has("lines")) continue;
      JsonObject memberships = station.getAsJsonObject("lines");
      boolean hadMembership = !memberships.isEmpty();
      for (List<String> key : replaced) {
        if (!memberships.has(key.get(0))) continue;
        JsonObject company = memberships.getAsJsonObject(key.get(0));
        company.remove(key.get(1));
        if (company.isEmpty()) memberships.remove(key.get(0));
      }
      if (hadMembership && memberships.isEmpty()) iterator.remove();
    }
    JsonObject incoming = edits.deepCopy();
    Map<Integer, Integer> ids = new HashMap<>();
    for (JsonElement element : incoming.getAsJsonArray("stations")) {
      JsonObject station = element.getAsJsonObject();
      ids.put(station.get("id").getAsInt(), nextId);
      station.addProperty("id", nextId++);
      stations.add(station);
    }
    for (var company : incoming.getAsJsonObject("lines").entrySet()) {
      if (!lines.has(company.getKey())) lines.add(company.getKey(), new JsonObject());
      for (var line : company.getValue().getAsJsonObject().entrySet()) {
        for (JsonElement branch : line.getValue().getAsJsonObject().getAsJsonObject("branches").asMap().values()) {
          JsonArray refs = branch.getAsJsonObject().getAsJsonArray("stations");
          for (int i = 0; i < refs.size(); i++) {
            JsonElement ref = refs.get(i);
            int old = ref.isJsonArray() ? ref.getAsJsonArray().get(0).getAsInt() : ref.getAsInt();
            Integer id = ids.get(old);
            if (id == null) throw new IllegalStateException("Missing edited station " + old);
            if (ref.isJsonArray()) ref.getAsJsonArray().set(0, new JsonPrimitive(id));
            else refs.set(i, new JsonPrimitive(id));
          }
        }
        lines.getAsJsonObject(company.getKey()).add(line.getKey(), line.getValue());
      }
    }
    return result;
  }
}
