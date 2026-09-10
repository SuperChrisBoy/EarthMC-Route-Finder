package net.earthmc.routefinder.iceeditor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EditorArchitectureTest {
  @Test
  void editorStartsInSafeSelectionMode() {
    EditorState state = new EditorState();
    assertEquals(EditorTool.SELECT, state.tool());
    assertInstanceOf(EditorSelection.None.class, state.selection());
  }

  @Test
  void commandsUndoAndRedoTheSameMutation() {
    AtomicInteger value = new AtomicInteger();
    CommandManager history = new CommandManager(100);
    history.execute(
        new EditorCommand() {
          public String description() {
            return "increment";
          }

          public void execute() {
            value.incrementAndGet();
          }

          public void undo() {
            value.decrementAndGet();
          }
        });
    assertEquals(1, value.get());
    assertTrue(history.undo());
    assertEquals(0, value.get());
    assertTrue(history.redo());
    assertEquals(1, value.get());
  }

  @Test
  void autosaveIsDebounced() {
    AutosaveController autosave = new AutosaveController(300);
    autosave.changed(1_000);
    assertFalse(autosave.shouldSave(1_299));
    assertTrue(autosave.shouldSave(1_300));
    autosave.saved();
    assertFalse(autosave.shouldSave(Long.MAX_VALUE - 1));
  }
}
