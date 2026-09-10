package net.earthmc.routefinder.iceeditor;

public record ValidationIssue(Severity severity, String message) {
  public enum Severity {
    ERROR,
    WARNING
  }
}
