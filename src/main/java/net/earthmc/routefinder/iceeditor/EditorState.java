package net.earthmc.routefinder.iceeditor;

import java.util.Objects;

public final class EditorState {
  private EditorTool tool = EditorTool.SELECT;
  private EditorSelection selection = new EditorSelection.None();
  private boolean dirty;
  private long revision;

  public EditorTool tool() {
    return tool;
  }

  public EditorSelection selection() {
    return selection;
  }

  public boolean dirty() {
    return dirty;
  }

  public long revision() {
    return revision;
  }

  public void select(EditorSelection next) {
    selection = Objects.requireNonNull(next);
  }

  public void activate(EditorTool next) {
    tool = Objects.requireNonNull(next);
  }

  public void changed() {
    dirty = true;
    revision++;
  }

  public void saved() {
    dirty = false;
  }

  public void resetTransientState() {
    tool = EditorTool.SELECT;
    selection = new EditorSelection.None();
  }
}
