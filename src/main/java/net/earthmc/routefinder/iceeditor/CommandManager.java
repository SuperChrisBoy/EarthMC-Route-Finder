package net.earthmc.routefinder.iceeditor;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CommandManager {
  private final Deque<EditorCommand> undo = new ArrayDeque<>();
  private final Deque<EditorCommand> redo = new ArrayDeque<>();
  private final int capacity;

  public CommandManager(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  public void execute(EditorCommand command) {
    command.execute();
    undo.addLast(command);
    while (undo.size() > capacity) undo.removeFirst();
    redo.clear();
  }

  public boolean undo() {
    if (undo.isEmpty()) return false;
    EditorCommand command = undo.removeLast();
    command.undo();
    redo.addLast(command);
    return true;
  }

  public boolean redo() {
    if (redo.isEmpty()) return false;
    EditorCommand command = redo.removeLast();
    command.execute();
    undo.addLast(command);
    return true;
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  public void clear() {
    undo.clear();
    redo.clear();
  }
}
