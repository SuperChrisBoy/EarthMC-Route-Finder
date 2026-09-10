package net.earthmc.routefinder.iceeditor;

public final class AutosaveController {
  private final long delayMillis;
  private long dueAt = Long.MAX_VALUE;

  public AutosaveController(long delayMillis) {
    this.delayMillis = Math.max(0, delayMillis);
  }

  public void changed(long now) {
    dueAt = now + delayMillis;
  }

  public boolean shouldSave(long now) {
    return now >= dueAt;
  }

  public void saved() {
    dueAt = Long.MAX_VALUE;
  }
}
