package net.earthmc.routefinder.iceeditor;

public sealed interface EditorSelection
    permits EditorSelection.None,
        EditorSelection.Line,
        EditorSelection.Branch,
        EditorSelection.Vertex,
        EditorSelection.Marker,
        EditorSelection.Segment {
  record None() implements EditorSelection {}

  record Line(String name) implements EditorSelection {}

  record Branch(int branchIndex) implements EditorSelection {}

  record Vertex(int branchIndex, int vertexIndex) implements EditorSelection {}

  record Marker(int markerId) implements EditorSelection {}

  record Segment(int branchIndex, int segmentIndex) implements EditorSelection {}
}
