package net.earthmc.routefinder.iceeditor;

public interface EditorCommand {
  String description();

  void execute();

  void undo();
}
