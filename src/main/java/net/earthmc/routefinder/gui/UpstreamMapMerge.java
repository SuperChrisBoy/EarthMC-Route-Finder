package net.earthmc.routefinder.gui;

import com.google.gson.*;
import java.util.*;

/** Three-way merge in a representation where station array indices are not identities. */
final class UpstreamMapMerge {
  static final class Conflict extends RuntimeException {
    Conflict(String path) { super("Upstream conflict at " + path); }
  }

  static JsonObject merge(JsonObject base, JsonObject local, JsonObject upstream) {
    return decode(mergeValue(encode(base), encode(local), encode(upstream), "map").getAsJsonObject());
  }

  private static JsonElement mergeValue(JsonElement base, JsonElement local, JsonElement upstream, String path) {
    if (Objects.equals(local, base)) return upstream;
    if (Objects.equals(upstream, base) || Objects.equals(local, upstream)) return local;
    if (base != null && local != null && upstream != null
        && base.isJsonObject() && local.isJsonObject() && upstream.isJsonObject()) {
      JsonObject result = new JsonObject();
      Set<String> keys = new LinkedHashSet<>(base.getAsJsonObject().keySet());
      keys.addAll(local.getAsJsonObject().keySet());
      keys.addAll(upstream.getAsJsonObject().keySet());
      for (String key : keys) {
        JsonElement value = mergeValue(base.getAsJsonObject().get(key), local.getAsJsonObject().get(key),
            upstream.getAsJsonObject().get(key), path + "/" + key);
        if (value != null) result.add(key, value.deepCopy());
      }
      return result;
    }
    // Geometry/reference arrays are indivisible: index-based merging can silently reconnect roads.
    throw new Conflict(path);
  }

  private static JsonObject encode(JsonObject map) {
    JsonObject result = map.deepCopy();
    Map<Integer, JsonObject> stations = new HashMap<>();
    Set<Integer> referenced = new HashSet<>();
    for (JsonElement element : map.getAsJsonArray("stations"))
      stations.put(element.getAsJsonObject().get("id").getAsInt(), element.getAsJsonObject());
    for (var company : result.getAsJsonObject("lines").entrySet())
      for (var line : company.getValue().getAsJsonObject().entrySet())
        for (JsonElement branch : line.getValue().getAsJsonObject().getAsJsonObject("branches").asMap().values()) {
          JsonArray refs = branch.getAsJsonObject().getAsJsonArray("stations");
          for (int i = 0; i < refs.size(); i++) {
            JsonElement ref = refs.get(i);
            int id = ref.isJsonArray() ? ref.getAsJsonArray().get(0).getAsInt() : ref.getAsInt();
            referenced.add(id);
            JsonObject original = stations.get(id), station = original.deepCopy(), wrapped = new JsonObject();
            station.remove("id");
            station.remove("lines");
            wrapped.add("station", station);
            if (original.has("lines") && original.getAsJsonObject("lines").has(company.getKey())) {
              JsonElement membership = original.getAsJsonObject("lines").getAsJsonObject(company.getKey()).get(line.getKey());
              if (membership != null) wrapped.add("membership", membership.deepCopy());
            }
            if (ref.isJsonArray()) {
              JsonArray tail = ref.getAsJsonArray().deepCopy();
              tail.remove(0);
              wrapped.add("tail", tail);
            }
            refs.set(i, wrapped);
          }
        }
    JsonArray unassigned = new JsonArray();
    for (JsonElement element : map.getAsJsonArray("stations")) {
      JsonObject station = element.getAsJsonObject();
      if (!referenced.contains(station.get("id").getAsInt())) {
        station = station.deepCopy();
        station.remove("id");
        unassigned.add(station);
      }
    }
    result.add("stations", unassigned);
    return result;
  }

  private static JsonObject decode(JsonObject encoded) {
    JsonObject result = encoded.deepCopy();
    JsonArray stations = new JsonArray();
    Map<JsonObject, Integer> ids = new LinkedHashMap<>();
    for (var company : result.getAsJsonObject("lines").entrySet())
      for (var line : company.getValue().getAsJsonObject().entrySet())
        for (JsonElement branch : line.getValue().getAsJsonObject().getAsJsonObject("branches").asMap().values()) {
          JsonArray refs = branch.getAsJsonObject().getAsJsonArray("stations");
          for (int i = 0; i < refs.size(); i++) {
            JsonObject wrapped = refs.get(i).getAsJsonObject(), key = wrapped.getAsJsonObject("station");
            Integer id = ids.get(key);
            if (id == null) {
              id = stations.size();
              ids.put(key.deepCopy(), id);
              JsonObject station = key.deepCopy();
              station.addProperty("id", id);
              station.add("lines", new JsonObject());
              stations.add(station);
            }
            JsonObject memberships = stations.get(id).getAsJsonObject().getAsJsonObject("lines");
            if (wrapped.has("membership")) {
              if (!memberships.has(company.getKey())) memberships.add(company.getKey(), new JsonObject());
              memberships.getAsJsonObject(company.getKey()).add(line.getKey(), wrapped.get("membership").deepCopy());
            }
            JsonElement ref = new JsonPrimitive(id);
            if (wrapped.has("tail")) {
              JsonArray array = new JsonArray();
              array.add(id);
              array.addAll(wrapped.getAsJsonArray("tail"));
              ref = array;
            }
            refs.set(i, ref);
          }
        }
    for (JsonElement element : result.getAsJsonArray("stations")) {
      JsonObject station = element.getAsJsonObject().deepCopy();
      station.addProperty("id", stations.size());
      stations.add(station);
    }
    result.add("stations", stations);
    return result;
  }
}
