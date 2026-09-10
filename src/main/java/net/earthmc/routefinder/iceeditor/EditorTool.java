package net.earthmc.routefinder.iceeditor;

public enum EditorTool {
  SELECT("Select", "V"),
  DRAW("Draw", "L"),
  ADD_VERTEX("Vertex", "A"),
  SPLIT("Split", "S"),
  REMOVE("Remove", "Del"),
  MARKER("Marker", "M"),
  MULTI_SELECT("Multi", "B"),
  MEASURE("Measure", "R");

  private final String label;
  private final String shortcut;

  EditorTool(String label, String shortcut) {
    this.label = label;
    this.shortcut = shortcut;
  }

  public String label() {
    return label;
  }

  public String shortcut() {
    return shortcut;
  }
}
